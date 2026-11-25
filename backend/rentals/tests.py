from datetime import date, timedelta

from django.test import TestCase
from rest_framework.test import APIClient

from accounts.models import User
from rentals import services
from rentals.models import Booking, Category, Equipment


class PricingTests(TestCase):
    def test_best_price_uses_weekly_when_cheaper(self):
        # 7 days at Rs100/day = 700, weekly = 500
        self.assertEqual(services.best_rental_price(7, 100, 500, 1800), 500)

    def test_best_price_may_overshoot_to_cheaper_plan(self):
        # 6 days daily = 600 > one week 500
        self.assertEqual(services.best_rental_price(6, 100, 500, 1800), 500)

    def test_single_day(self):
        self.assertEqual(services.best_rental_price(1, 100, 500, 1800), 100)

    def test_deposit_tiers_boundaries(self):
        self.assertEqual(services.deposit_for(2999)[0], 0)
        self.assertEqual(services.deposit_for(3000)[0], 300)
        self.assertEqual(services.deposit_for(14999)[0], 1500)
        self.assertEqual(services.deposit_for(15000)[0], 3000)
        self.assertEqual(services.deposit_for(140000)[0], 35000)

    def test_money_rounds_half_up(self):
        # Defect D-05: Python round() is banker's rounding (28.5 -> 28). Money must round half up.
        self.assertEqual(services.percent_of(570, 5), 29)
        self.assertEqual(services.deposit_for(3250)[0], 350)  # 325 -> nearest 50 rounds up
        self.assertEqual(services.percent_of(500, 15), 75)

    def test_period_validation(self):
        today = date(2026, 1, 10)
        with self.assertRaises(services.InvalidRentalPeriod):
            services.validate_period(date(2026, 1, 9), date(2026, 1, 12), today)  # past
        with self.assertRaises(services.InvalidRentalPeriod):
            services.validate_period(date(2026, 1, 12), date(2026, 1, 11), today)  # end before start
        self.assertEqual(services.validate_period(today, today, today), 1)
        self.assertEqual(services.validate_period(today, today + timedelta(days=89), today), 90)
        with self.assertRaises(services.InvalidRentalPeriod):
            services.validate_period(today, today + timedelta(days=90), today)  # 91 days


class FlowTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user("owner", "o@x.com", "pass12345!", role="owner")
        self.renter = User.objects.create_user("renter", "r@x.com", "pass12345!")
        cat = Category.objects.create(name="Music", slug="music")
        self.eq = Equipment.objects.create(
            owner=self.owner, category=cat, title="Guitar", description="d",
            daily_rate=100, weekly_rate=500, monthly_rate=1800, market_value=9000,
            rent_to_buy=True, sale_price=7000)
        self.c = APIClient()
        self.c.force_authenticate(self.renter)
        self.start = date.today() + timedelta(days=2)
        self.end = self.start + timedelta(days=6)

    def book(self, start=None, end=None):
        return self.c.post("/api/bookings/", {
            "equipment": self.eq.id, "start_date": str(start or self.start),
            "end_date": str(end or self.end), "delivery_address": "12 Test Road, Ahmedabad"}, format="json")

    def test_booking_breakdown(self):
        r = self.book()
        self.assertEqual(r.status_code, 201)
        b = r.json()
        self.assertEqual(b["rental_amount"], 500)
        self.assertEqual(b["deposit"], 900)
        self.assertEqual(b["total_payable"], 500 + 99 + 900)
        self.assertEqual(b["commission"] + b["owner_payout"], 500)

    def test_overlapping_booking_rejected(self):
        bid = self.book().json()["id"]
        self.c.post(f"/api/bookings/{bid}/pay/", {"method": "upi", "upi_id": "a@okaxis"}, format="json")
        # A different renter tries overlapping dates
        other = User.objects.create_user("other", "x@x.com", "pass12345!")
        self.c.force_authenticate(other)
        r = self.book(self.end, self.end + timedelta(days=2))
        self.assertEqual(r.status_code, 409)
        self.assertEqual(r.json()["code"], "item_unavailable")

    def test_cancelled_booking_frees_dates(self):
        bid = self.book().json()["id"]
        self.c.post(f"/api/bookings/{bid}/cancel/")
        self.assertEqual(self.book().status_code, 201)

    def test_renter_can_rebook_over_own_unpaid_booking(self):
        first = self.book().json()["id"]
        # Defect D-06: an unpaid booking for non-overlapping dates must not be touched
        far = self.book(self.start + timedelta(days=30), self.start + timedelta(days=31)).json()["id"]
        self.assertEqual(self.book(self.start, self.end + timedelta(days=1)).status_code, 201)
        self.assertEqual(Booking.objects.get(pk=first).status, "cancelled")
        self.assertEqual(Booking.objects.get(pk=far).status, "pending_payment")

    def test_failed_rebook_keeps_original_unpaid_booking(self):
        other = User.objects.create_user("other2", "o2@x.com", "pass12345!")
        oc = APIClient()
        oc.force_authenticate(other)
        far_start = self.start + timedelta(days=10)
        r = oc.post("/api/bookings/", {"equipment": self.eq.id, "start_date": str(far_start),
                                       "end_date": str(far_start + timedelta(days=2)),
                                       "delivery_address": "Other road, Ahmedabad"}, format="json")
        oc.post(f"/api/bookings/{r.json()['id']}/pay/", {"method": "upi", "upi_id": "o@okaxis"}, format="json")
        mine = self.book().json()["id"]
        # New dates overlap both my unpaid booking and the other renter's paid booking
        self.assertEqual(self.book(self.end, far_start).status_code, 409)
        self.assertEqual(Booking.objects.get(pk=mine).status, "pending_payment")

    def test_missing_or_hidden_item_is_unavailable_not_500(self):
        r = self.c.post("/api/bookings/", {"equipment": 99999, "start_date": str(self.start),
                                           "end_date": str(self.end), "delivery_address": "12 Test Road, Ahmedabad"},
                        format="json")
        self.assertEqual(r.status_code, 409)

    def test_owner_can_hide_and_relist(self):
        oc = APIClient()
        oc.force_authenticate(self.owner)
        self.assertEqual(oc.patch(f"/api/equipment/{self.eq.id}/", {"is_active": False}, format="json").status_code, 200)
        self.assertEqual(self.c.get(f"/api/equipment/").json(), [])
        self.assertEqual(oc.patch(f"/api/equipment/{self.eq.id}/", {"is_active": True}, format="json").status_code, 200)
        # Other users cannot edit it
        self.assertEqual(self.c.patch(f"/api/equipment/{self.eq.id}/", {"daily_rate": 1}, format="json").status_code, 403)

    def test_owner_creates_listing_with_validation(self):
        oc = APIClient()
        oc.force_authenticate(self.owner)
        data = {"title": "Ukulele", "category": self.eq.category_id, "description": "Soprano",
                "daily_rate": 50, "weekly_rate": 250, "monthly_rate": 800, "market_value": 3000,
                "rent_to_buy": True, "sale_price": None}
        self.assertEqual(oc.post("/api/equipment/", data, format="json").status_code, 400)  # R2B needs price
        data["sale_price"] = 2500
        self.assertEqual(oc.post("/api/equipment/", data, format="json").status_code, 201)
        # Renters cannot list
        self.assertEqual(self.c.post("/api/equipment/", data, format="json").status_code, 403)

    def test_unpaid_booking_releases_dates_after_hold(self):
        from django.utils import timezone
        bid = self.book().json()["id"]
        Booking.objects.filter(pk=bid).update(created_at=timezone.now() - timedelta(minutes=16))
        self.assertEqual(self.book().status_code, 201)

    def test_failed_payment(self):
        bid = self.book().json()["id"]
        r = self.c.post(f"/api/bookings/{bid}/pay/", {"method": "upi", "upi_id": "fail@test"}, format="json")
        self.assertEqual(r.status_code, 402)
        self.assertEqual(Booking.objects.get(pk=bid).status, "payment_failed")
        r = self.c.post(f"/api/bookings/{bid}/pay/", {"method": "upi", "upi_id": "aarav@okaxis"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(Booking.objects.get(pk=bid).status, "confirmed")

    def test_full_lifecycle_rewards_and_review(self):
        owner_c = APIClient()
        owner_c.force_authenticate(self.owner)
        bid = self.book().json()["id"]
        # Review before completion is blocked
        r = self.c.post(f"/api/bookings/{bid}/review/", {"rating": 5}, format="json")
        self.assertEqual(r.status_code, 409)
        self.c.post(f"/api/bookings/{bid}/pay/", {"method": "card", "card_number": "4111111111111111"}, format="json")
        # Renter cannot mark delivered
        self.assertEqual(self.c.post(f"/api/bookings/{bid}/deliver/").status_code, 409)
        self.assertEqual(owner_c.post(f"/api/bookings/{bid}/deliver/").status_code, 200)
        self.assertEqual(self.c.post(f"/api/bookings/{bid}/return/").status_code, 200)
        self.assertEqual(owner_c.post(f"/api/bookings/{bid}/complete/").status_code, 200)
        self.renter.refresh_from_db()
        self.assertEqual(self.renter.completed_rentals, 1)
        self.assertTrue(Booking.objects.get(pk=bid).deposit_refunded)
        self.assertEqual(self.c.post(f"/api/bookings/{bid}/review/", {"rating": 5}, format="json").status_code, 201)
        # Rent-to-Buy: 50% of 500 paid = 250 credit
        r = self.c.post(f"/api/bookings/{bid}/buy/")
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()["final_price"], 6750)

    def test_reward_tier_discount_applies(self):
        self.renter.completed_rentals = 3
        self.renter.is_student_verified = True
        self.renter.save()
        b = self.book().json()
        self.assertEqual(b["discount_amount"], 50)  # 5% student + 5% bronze of 500

    def test_discount_cap(self):
        from django.conf import settings
        from django.test import override_settings
        self.renter.completed_rentals = 15
        self.renter.is_student_verified = True
        self.renter.save()
        cfg = {**settings.HOBBYNEST, "STUDENT_DISCOUNT_PCT": 10}  # 10% + 15% gold = 25%, capped at 20%
        with override_settings(HOBBYNEST=cfg):
            b = self.book().json()
        self.assertEqual(b["discount_amount"], 100)
        self.assertIn("capped", b["discount_label"])

    def test_cannot_rent_own_listing(self):
        self.c.force_authenticate(self.owner)
        self.assertEqual(self.book().status_code, 400)

    def test_register_requires_consent(self):
        anon = APIClient()
        data = {"username": "newuser", "email": "n@x.com", "password": "Strong#Pass99", "consent": False}
        self.assertEqual(anon.post("/api/auth/register/", data, format="json").status_code, 400)
        data["consent"] = True
        self.assertEqual(anon.post("/api/auth/register/", data, format="json").status_code, 201)
