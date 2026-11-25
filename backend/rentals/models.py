from django.conf import settings
from django.db import models


class Category(models.Model):
    name = models.CharField(max_length=60, unique=True)
    slug = models.SlugField(unique=True)
    icon = models.CharField(max_length=30, default="box")
    description = models.CharField(max_length=200, blank=True)

    class Meta:
        verbose_name_plural = "categories"
        ordering = ["name"]

    def __str__(self):
        return self.name


class Equipment(models.Model):
    class Condition(models.TextChoices):
        NEW = "new", "Like new"
        GOOD = "good", "Good"
        FAIR = "fair", "Fair"

    owner = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="listings")
    category = models.ForeignKey(Category, on_delete=models.PROTECT, related_name="items")
    title = models.CharField(max_length=120)
    description = models.TextField()
    specs = models.CharField(max_length=300, blank=True)
    condition = models.CharField(max_length=10, choices=Condition.choices, default=Condition.GOOD)
    daily_rate = models.PositiveIntegerField()
    weekly_rate = models.PositiveIntegerField()
    monthly_rate = models.PositiveIntegerField()
    market_value = models.PositiveIntegerField(help_text="Used to decide the security deposit tier")
    rent_to_buy = models.BooleanField(default=False)
    sale_price = models.PositiveIntegerField(null=True, blank=True)
    beginner_friendly = models.BooleanField(default=False)
    image_url = models.URLField(blank=True)
    location = models.CharField(max_length=80, default="Ahmedabad")
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return self.title


class Booking(models.Model):
    class Status(models.TextChoices):
        PENDING_PAYMENT = "pending_payment", "Awaiting payment"
        PAYMENT_FAILED = "payment_failed", "Payment failed"
        CONFIRMED = "confirmed", "Confirmed"
        ACTIVE = "active", "Delivered / in use"
        RETURNED = "returned", "Returned, awaiting inspection"
        COMPLETED = "completed", "Completed"
        CANCELLED = "cancelled", "Cancelled"

    # Statuses that hold the item's calendar
    BLOCKING = [Status.PENDING_PAYMENT, Status.CONFIRMED, Status.ACTIVE, Status.RETURNED]

    renter = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="bookings")
    equipment = models.ForeignKey(Equipment, on_delete=models.PROTECT, related_name="bookings")
    start_date = models.DateField()
    end_date = models.DateField()
    days = models.PositiveIntegerField()
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.PENDING_PAYMENT)
    delivery_address = models.CharField(max_length=300)

    rental_amount = models.PositiveIntegerField()
    discount_amount = models.PositiveIntegerField(default=0)
    discount_label = models.CharField(max_length=120, blank=True)
    delivery_fee = models.PositiveIntegerField()
    deposit = models.PositiveIntegerField()
    total_payable = models.PositiveIntegerField()
    commission = models.PositiveIntegerField()
    owner_payout = models.PositiveIntegerField()
    deposit_refunded = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"#{self.pk} {self.equipment} ({self.status})"


class Payment(models.Model):
    class Status(models.TextChoices):
        SUCCESS = "success", "Success"
        FAILED = "failed", "Failed"
        REFUNDED = "refunded", "Refunded"

    booking = models.ForeignKey(Booking, on_delete=models.CASCADE, related_name="payments")
    method = models.CharField(max_length=20)
    amount = models.PositiveIntegerField()
    status = models.CharField(max_length=10, choices=Status.choices)
    reference = models.CharField(max_length=40)
    message = models.CharField(max_length=200, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)


class Review(models.Model):
    booking = models.OneToOneField(Booking, on_delete=models.CASCADE, related_name="review")
    equipment = models.ForeignKey(Equipment, on_delete=models.CASCADE, related_name="reviews")
    author = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    rating = models.PositiveSmallIntegerField()
    comment = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]


class PurchaseRequest(models.Model):
    """Rent-to-Buy: renter asks to buy the exact item they rented."""

    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        ACCEPTED = "accepted", "Accepted"
        DECLINED = "declined", "Declined"

    booking = models.OneToOneField(Booking, on_delete=models.CASCADE, related_name="purchase_request")
    equipment = models.ForeignKey(Equipment, on_delete=models.CASCADE, related_name="purchase_requests")
    buyer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    sale_price = models.PositiveIntegerField()
    rental_credit = models.PositiveIntegerField()
    final_price = models.PositiveIntegerField()
    commission = models.PositiveIntegerField()
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.PENDING)
    created_at = models.DateTimeField(auto_now_add=True)
