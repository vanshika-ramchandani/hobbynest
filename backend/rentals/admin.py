from django.contrib import admin

from .models import Booking, Category, Equipment, Payment, PurchaseRequest, Review

admin.site.register(Category)
admin.site.register(Review)
admin.site.register(PurchaseRequest)


@admin.register(Equipment)
class EquipmentAdmin(admin.ModelAdmin):
    list_display = ["title", "category", "owner", "daily_rate", "rent_to_buy", "is_active"]
    list_filter = ["category", "rent_to_buy", "is_active"]
    search_fields = ["title"]


@admin.register(Booking)
class BookingAdmin(admin.ModelAdmin):
    list_display = ["id", "equipment", "renter", "start_date", "end_date", "status", "total_payable"]
    list_filter = ["status"]


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = ["reference", "booking", "method", "amount", "status", "created_at"]
