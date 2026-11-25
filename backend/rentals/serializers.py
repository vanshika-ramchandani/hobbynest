from django.db.models import Avg
from rest_framework import serializers

from .models import Booking, Category, Equipment, Payment, PurchaseRequest, Review
from .services import deposit_for


class CategorySerializer(serializers.ModelSerializer):
    item_count = serializers.IntegerField(read_only=True)

    class Meta:
        model = Category
        fields = ["id", "name", "slug", "icon", "description", "item_count"]


class EquipmentSerializer(serializers.ModelSerializer):
    category_name = serializers.CharField(source="category.name", read_only=True)
    category_slug = serializers.CharField(source="category.slug", read_only=True)
    owner_name = serializers.SerializerMethodField()
    deposit = serializers.SerializerMethodField()
    rating = serializers.SerializerMethodField()
    review_count = serializers.SerializerMethodField()

    class Meta:
        model = Equipment
        fields = ["id", "title", "description", "specs", "condition", "category", "category_name",
                  "category_slug", "daily_rate", "weekly_rate", "monthly_rate", "market_value",
                  "rent_to_buy", "sale_price", "beginner_friendly", "image_url", "location",
                  "owner", "owner_name", "deposit", "rating", "review_count", "is_active", "created_at"]
        read_only_fields = ["owner", "created_at"]

    def get_owner_name(self, obj):
        return obj.owner.get_full_name() or obj.owner.username

    def get_deposit(self, obj):
        amount, label = deposit_for(obj.market_value)
        return {"amount": amount, "label": label}

    def get_rating(self, obj):
        avg = obj.reviews.aggregate(a=Avg("rating"))["a"]
        return round(avg, 1) if avg else None

    def get_review_count(self, obj):
        return obj.reviews.count()

    def validate(self, attrs):
        get = lambda k: attrs.get(k, getattr(self.instance, k, None))
        daily, weekly, monthly = get("daily_rate"), get("weekly_rate"), get("monthly_rate")
        if daily is not None and daily < 10:
            raise serializers.ValidationError({"daily_rate": "Daily rate must be at least Rs 10."})
        if daily and weekly and weekly > daily * 7:
            raise serializers.ValidationError({"weekly_rate": "Weekly rate should not exceed 7 x daily rate."})
        if weekly and monthly and monthly > weekly * 5:
            raise serializers.ValidationError({"monthly_rate": "Monthly rate looks too high compared to weekly."})
        if get("rent_to_buy") and not get("sale_price"):
            raise serializers.ValidationError({"sale_price": "Set a sale price to enable Rent-to-Buy."})
        return attrs


class ReviewSerializer(serializers.ModelSerializer):
    author_name = serializers.SerializerMethodField()

    class Meta:
        model = Review
        fields = ["id", "booking", "rating", "comment", "author_name", "created_at"]
        read_only_fields = ["author_name", "created_at"]

    def get_author_name(self, obj):
        return obj.author.first_name or obj.author.username

    def validate_rating(self, value):
        if not 1 <= value <= 5:
            raise serializers.ValidationError("Rating must be between 1 and 5.")
        return value


class PaymentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Payment
        fields = ["id", "method", "amount", "status", "reference", "message", "created_at"]


class PurchaseRequestSerializer(serializers.ModelSerializer):
    equipment_title = serializers.CharField(source="equipment.title", read_only=True)
    buyer_name = serializers.CharField(source="buyer.username", read_only=True)

    class Meta:
        model = PurchaseRequest
        fields = ["id", "booking", "equipment", "equipment_title", "buyer_name", "sale_price",
                  "rental_credit", "final_price", "commission", "status", "created_at"]


class BookingSerializer(serializers.ModelSerializer):
    equipment_detail = EquipmentSerializer(source="equipment", read_only=True)
    status_label = serializers.CharField(source="get_status_display", read_only=True)
    renter_name = serializers.CharField(source="renter.username", read_only=True)
    payments = PaymentSerializer(many=True, read_only=True)
    review = ReviewSerializer(read_only=True)
    purchase_request = PurchaseRequestSerializer(read_only=True)

    class Meta:
        model = Booking
        fields = ["id", "equipment", "equipment_detail", "renter_name", "start_date", "end_date", "days",
                  "status", "status_label", "delivery_address", "rental_amount", "discount_amount",
                  "discount_label", "delivery_fee", "deposit", "total_payable", "commission",
                  "owner_payout", "deposit_refunded", "payments", "review", "purchase_request", "created_at"]
        read_only_fields = fields
