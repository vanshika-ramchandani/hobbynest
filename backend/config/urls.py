from django.contrib import admin
from django.urls import include, path
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

from accounts.views import MeView, RegisterView, VerifyStudentView
from rentals.views import (BookingViewSet, CategoryViewSet, EquipmentViewSet, PurchaseRequestViewSet,
                           health, owner_stats)

router = DefaultRouter()
router.register("categories", CategoryViewSet, basename="category")
router.register("equipment", EquipmentViewSet, basename="equipment")
router.register("bookings", BookingViewSet, basename="booking")
router.register("purchase-requests", PurchaseRequestViewSet, basename="purchase-request")

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/health/", health),
    path("api/auth/register/", RegisterView.as_view()),
    path("api/auth/login/", TokenObtainPairView.as_view()),
    path("api/auth/refresh/", TokenRefreshView.as_view()),
    path("api/auth/me/", MeView.as_view()),
    path("api/auth/verify-student/", VerifyStudentView.as_view()),
    path("api/owner/stats/", owner_stats),
    path("api/", include(router.urls)),
]
