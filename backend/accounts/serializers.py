from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.utils import timezone
from rest_framework import serializers

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    reward_tier = serializers.ReadOnlyField()
    next_tier = serializers.ReadOnlyField()

    class Meta:
        model = User
        fields = ["id", "username", "email", "first_name", "last_name", "role", "phone", "city",
                  "college_email", "is_student_verified", "completed_rentals", "reward_points",
                  "reward_tier", "next_tier", "is_staff"]
        read_only_fields = ["id", "username", "email", "role", "college_email", "is_student_verified",
                            "completed_rentals", "reward_points", "is_staff"]


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True)
    consent = serializers.BooleanField(write_only=True)

    class Meta:
        model = User
        fields = ["username", "email", "password", "first_name", "last_name", "role", "phone", "consent"]

    def validate_consent(self, value):
        if value is not True:
            raise serializers.ValidationError("You must agree to the Terms and Privacy Policy to register.")
        return value

    def validate_email(self, value):
        value = value.lower().strip()
        if User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("An account with this email already exists.")
        return value

    def validate_phone(self, value):
        digits = "".join(c for c in value if c.isdigit())
        if value and len(digits) != 10:
            raise serializers.ValidationError("Enter a 10-digit mobile number.")
        return digits

    def validate(self, attrs):
        validate_password(attrs["password"], User(username=attrs.get("username"), email=attrs.get("email")))
        return attrs

    def create(self, data):
        data.pop("consent")
        password = data.pop("password")
        user = User(**data, consent_given_at=timezone.now())
        user.set_password(password)
        user.save()
        return user
