from django.contrib import admin
from django.contrib.auth.admin import UserAdmin

from .models import User


@admin.register(User)
class HobbyNestUserAdmin(UserAdmin):
    list_display = ["username", "email", "role", "is_student_verified", "completed_rentals"]
    fieldsets = UserAdmin.fieldsets + (
        ("HobbyNest", {"fields": ("role", "phone", "city", "college_email", "is_student_verified",
                                  "completed_rentals", "reward_points", "consent_given_at")}),
    )
