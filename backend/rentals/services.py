"""Business logic for HobbyNest: pricing, availability, booking, payments, returns, rewards."""
import logging
import math
import uuid
from datetime import date, timedelta

from django.conf import settings
from django.db import transaction
from django.db.models import F, Q
from django.utils import timezone

from .exceptions import InvalidRentalPeriod, InvalidTransition, ItemUnavailable, PaymentFailed
from .models import Booking, Equipment, Payment

logger = logging.getLogger("hobbynest.services")

MAX_RENTAL_DAYS = 90

# Deposit tiers by market value: (upper bound, percent of value, label)
DEPOSIT_TIERS = [
    (3000, 0, "No deposit (low-value item)"),
    (15000, 10, "Small refundable deposit (medium-value item)"),
    (50000, 20, "Refundable deposit + ID check (high-value item)"),
    (math.inf, 25, "Refundable deposit + ID check (premium item)"),
]


def div_half_up(numerator, denominator):
    """Integer division rounding halves up (money rule). Python's round() rounds halves to even."""
    return (2 * numerator + denominator) // (2 * denominator)


def percent_of(amount, pct):
    return div_half_up(amount * pct, 100)


def best_rental_price(days, daily, weekly, monthly):
    """Cheapest combination of daily / weekly / monthly rates covering `days` (may overshoot)."""
    cost = [0] * (days + 1)
    for d in range(1, days + 1):
        cost[d] = min(
            cost[d - 1] + daily,
            cost[max(0, d - 7)] + weekly,
            cost[max(0, d - 30)] + monthly,
        )
    return cost[days]


def deposit_for(market_value):
    for limit, pct, label in DEPOSIT_TIERS:
        if market_value < limit:
            return div_half_up(market_value * pct, 100 * 50) * 50, label
    return 0, ""  # unreachable


def validate_period(start, end, today=None):
    today = today or date.today()
    if start is None or end is None:
        raise InvalidRentalPeriod("Start and end dates are required.")
    if start < today:
        raise InvalidRentalPeriod("Start date cannot be in the past.")
    if end < start:
        raise InvalidRentalPeriod("End date must be on or after the start date.")
    days = (end - start).days + 1
    if days > MAX_RENTAL_DAYS:
        raise InvalidRentalPeriod(f"Rentals can be at most {MAX_RENTAL_DAYS} days.")
    return days


def quote(equipment, start, end, user=None, today=None):
    """Full, transparent price breakdown shown to the customer before payment."""
    cfg = settings.HOBBYNEST
    days = validate_period(start, end, today)
    rental = best_rental_price(days, equipment.daily_rate, equipment.weekly_rate, equipment.monthly_rate)

    discount_pct, labels = 0, []
    if user is not None and user.is_authenticated:
        if user.is_student_verified:
            discount_pct += cfg["STUDENT_DISCOUNT_PCT"]
            labels.append(f"Student {cfg['STUDENT_DISCOUNT_PCT']}%")
        tier = user.reward_tier
        if tier["discount"]:
            discount_pct += tier["discount"]
            labels.append(f"{tier['name']} badge {tier['discount']}%")
    if discount_pct > cfg["MAX_TOTAL_DISCOUNT_PCT"]:
        discount_pct = cfg["MAX_TOTAL_DISCOUNT_PCT"]
        labels.append(f"capped at {cfg['MAX_TOTAL_DISCOUNT_PCT']}%")

    discount = percent_of(rental, discount_pct)
    rental_after = rental - discount
    deposit, deposit_label = deposit_for(equipment.market_value)
    delivery = cfg["DELIVERY_FEE"]
    commission = percent_of(rental_after, cfg["COMMISSION_PCT"])

    return {
        "days": days,
        "rental_amount": rental,
        "discount_amount": discount,
        "discount_label": ", ".join(labels),
        "delivery_fee": delivery,
        "deposit": deposit,
        "deposit_label": deposit_label,
        "total_payable": rental_after + delivery + deposit,
        "commission": commission,
        "owner_payout": rental_after - commission,
    }


PAYMENT_HOLD_MINUTES = 15


def blocking_bookings(equipment):
    """Bookings that hold the calendar. Unpaid bookings only hold dates for a short window."""
    hold_cutoff = timezone.now() - timedelta(minutes=PAYMENT_HOLD_MINUTES)
    held = Q(status__in=[s for s in Booking.BLOCKING if s != Booking.Status.PENDING_PAYMENT])
    fresh_unpaid = Q(status=Booking.Status.PENDING_PAYMENT, created_at__gte=hold_cutoff)
    return Booking.objects.filter(held | fresh_unpaid, equipment=equipment)


def is_available(equipment, start, end, exclude_id=None):
    qs = blocking_bookings(equipment).filter(start_date__lte=end, end_date__gte=start)
    if exclude_id:
        qs = qs.exclude(pk=exclude_id)
    return not qs.exists()


def create_booking(user, equipment_id, start, end, address):
    if not address or len(address.strip()) < 10:
        raise InvalidRentalPeriod("Please enter a complete delivery address.")
    with transaction.atomic():
        # Lock the equipment row so two simultaneous requests cannot double-book it.
        equipment = Equipment.objects.select_for_update().filter(pk=equipment_id, is_active=True).first()
        if equipment is None:
            raise ItemUnavailable("This item is no longer available.")
        if equipment.owner_id == user.id:
            raise InvalidRentalPeriod("You cannot rent your own listing.")
        q = quote(equipment, start, end, user)
        # A renter going back to change dates should not be blocked by their own unpaid attempt.
        # Runs inside transaction.atomic(), so if the availability check below fails this is rolled back.
        Booking.objects.filter(renter=user, equipment=equipment, status=Booking.Status.PENDING_PAYMENT,
                               start_date__lte=end, end_date__gte=start) \
            .update(status=Booking.Status.CANCELLED)
        if not is_available(equipment, start, end):
            raise ItemUnavailable()
        booking = Booking.objects.create(
            renter=user,
            equipment=equipment,
            start_date=start,
            end_date=end,
            delivery_address=address.strip(),
            **{k: v for k, v in q.items() if k != "deposit_label"},
        )
    logger.info("booking_created id=%s equipment=%s user=%s", booking.pk, equipment.pk, user.pk)
    return booking


# ---------- Payments (mock gateway, test mode only) ----------

TEST_FAIL_UPI = "fail@test"
TEST_FAIL_CARD_SUFFIX = "0002"


def charge(booking, method, upi_id="", card_number=""):
    if booking.status not in (Booking.Status.PENDING_PAYMENT, Booking.Status.PAYMENT_FAILED):
        raise InvalidTransition("This booking has already been paid or closed.")

    ref = "TEST-" + uuid.uuid4().hex[:10].upper()
    failed, reason = False, ""
    if method == "upi":
        if "@" not in upi_id or len(upi_id) < 5:
            raise PaymentFailed("Enter a valid UPI ID, for example name@okbank.")
        failed = upi_id.lower() == TEST_FAIL_UPI
        reason = "UPI request declined (test failure ID)."
    elif method == "card":
        digits = "".join(ch for ch in card_number if ch.isdigit())
        if not 12 <= len(digits) <= 19:
            raise PaymentFailed("Enter a valid card number.")
        failed = digits.endswith(TEST_FAIL_CARD_SUFFIX)
        reason = "Card declined by issuer (test failure card)."
    else:
        raise PaymentFailed("Unsupported payment method.")

    with transaction.atomic():
        booking = Booking.objects.select_for_update().get(pk=booking.pk)
        if failed:
            Payment.objects.create(booking=booking, method=method, amount=booking.total_payable,
                                   status=Payment.Status.FAILED, reference=ref, message=reason)
            booking.status = Booking.Status.PAYMENT_FAILED
            booking.save(update_fields=["status"])
        else:
            # Re-check availability in case the slot was released and re-taken while payment failed.
            if not is_available(booking.equipment, booking.start_date, booking.end_date, exclude_id=booking.pk):
                raise ItemUnavailable()
            Payment.objects.create(booking=booking, method=method, amount=booking.total_payable,
                                   status=Payment.Status.SUCCESS, reference=ref, message="Paid (test mode)")
            booking.status = Booking.Status.CONFIRMED
            booking.save(update_fields=["status"])
    logger.info("payment booking=%s method=%s result=%s ref=%s", booking.pk, method,
                "failed" if failed else "success", ref)
    if failed:
        raise PaymentFailed(reason)
    return booking, ref


# ---------- Status transitions ----------

TRANSITIONS = {
    # action: (allowed from, to, who)
    "cancel": ([Booking.Status.PENDING_PAYMENT, Booking.Status.PAYMENT_FAILED, Booking.Status.CONFIRMED],
               Booking.Status.CANCELLED, "renter"),
    "deliver": ([Booking.Status.CONFIRMED], Booking.Status.ACTIVE, "owner"),
    "return": ([Booking.Status.ACTIVE], Booking.Status.RETURNED, "renter"),
    "complete": ([Booking.Status.RETURNED], Booking.Status.COMPLETED, "owner"),
}


def transition(booking, action, user):
    if action not in TRANSITIONS:
        raise InvalidTransition("Unknown action.")
    allowed, target, who = TRANSITIONS[action]
    actor_ok = (booking.renter_id == user.id) if who == "renter" else (booking.equipment.owner_id == user.id)
    if not actor_ok and not user.is_staff:
        raise InvalidTransition(f"Only the {who} can perform '{action}'.")
    if booking.status not in allowed:
        raise InvalidTransition(f"Cannot {action} a booking that is '{booking.get_status_display()}'.")

    with transaction.atomic():
        booking.status = target
        fields = ["status"]
        if action == "cancel" and booking.payments.filter(status=Payment.Status.SUCCESS).exists():
            booking.payments.filter(status=Payment.Status.SUCCESS).update(status=Payment.Status.REFUNDED)
        if action == "complete":
            # Condition confirmed by owner: refund deposit and count the rental toward rewards.
            booking.deposit_refunded = True
            fields.append("deposit_refunded")
            type(booking.renter).objects.filter(pk=booking.renter_id).update(
                completed_rentals=F("completed_rentals") + 1,
                reward_points=F("reward_points") + max(10, booking.rental_amount // 100),
            )
        booking.save(update_fields=fields)
    logger.info("booking_transition id=%s action=%s to=%s", booking.pk, action, target)
    return booking


def rental_credit(booking):
    """Rent-to-Buy: 50% of the rent paid is credited toward the purchase price."""
    return (booking.rental_amount - booking.discount_amount) // 2
