from django.conf import settings
from django.contrib.auth.models import AbstractUser
from django.db import models

# Reward tiers: (name, completed rentals needed, discount percent)
REWARD_TIERS = [
    ("Gold", 15, 15),
    ("Silver", 7, 10),
    ("Bronze", 3, 5),
]


class User(AbstractUser):
    class Role(models.TextChoices):
        RENTER = "renter", "Renter"
        OWNER = "owner", "Equipment owner"

    email = models.EmailField(unique=True)
    role = models.CharField(max_length=10, choices=Role.choices, default=Role.RENTER)
    phone = models.CharField(max_length=15, blank=True)
    city = models.CharField(max_length=60, default="Ahmedabad")
    college_email = models.EmailField(blank=True)
    is_student_verified = models.BooleanField(default=False)
    completed_rentals = models.PositiveIntegerField(default=0)
    reward_points = models.PositiveIntegerField(default=0)
    consent_given_at = models.DateTimeField(null=True, blank=True)

    @property
    def reward_tier(self):
        for name, needed, discount in REWARD_TIERS:
            if self.completed_rentals >= needed:
                return {"name": name, "discount": discount}
        return {"name": "Member", "discount": 0}

    @property
    def next_tier(self):
        for name, needed, discount in reversed(REWARD_TIERS):
            if self.completed_rentals < needed:
                return {"name": name, "needed": needed, "remaining": needed - self.completed_rentals}
        return None

    @staticmethod
    def is_student_domain(email: str) -> bool:
        email = (email or "").lower().strip()
        domains = settings.HOBBYNEST["STUDENT_EMAIL_DOMAINS"]
        return "@" in email and any(email.endswith(d) for d in domains)
