import time
from datetime import date

from django.db import connection
from django.db.models import Count, Q, Sum
from django.shortcuts import get_object_or_404
from rest_framework import permissions, status, viewsets
from rest_framework.decorators import action, api_view, permission_classes
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response

from config.middleware import METRICS

from . import services
from .exceptions import InvalidRentalPeriod, InvalidTransition
from .models import Booking, Category, Equipment, PurchaseRequest, Review
from .serializers import (BookingSerializer, CategorySerializer, EquipmentSerializer,
                          PurchaseRequestSerializer, ReviewSerializer)


def parse_date(value, field):
    try:
        return date.fromisoformat(value)
    except (TypeError, ValueError):
        raise InvalidRentalPeriod(f"'{field}' must be a date in YYYY-MM-DD format.")


class IsOwnerOrReadOnly(permissions.BasePermission):
    def has_object_permission(self, request, view, obj):
        return request.method in permissions.SAFE_METHODS or obj.owner_id == request.user.id


class CategoryViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = CategorySerializer
    lookup_field = "slug"

    def get_queryset(self):
        return Category.objects.annotate(item_count=Count("items", filter=Q(items__is_active=True)))


class EquipmentViewSet(viewsets.ModelViewSet):
    serializer_class = EquipmentSerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly, IsOwnerOrReadOnly]

    def get_queryset(self):
        qs = Equipment.objects.select_related("category", "owner")
        p = self.request.query_params
        if p.get("mine") == "1" and self.request.user.is_authenticated:
            return qs.filter(owner=self.request.user)
        if self.action != "list" and self.request.user.is_authenticated:
            # Owners can still view and re-activate their own hidden listings.
            qs = qs.filter(Q(is_active=True) | Q(owner=self.request.user))
        else:
            qs = qs.filter(is_active=True)
        if p.get("q"):
            q = p["q"].strip()[:100]
            qs = qs.filter(Q(title__icontains=q) | Q(description__icontains=q) | Q(category__name__icontains=q))
        if p.get("category"):
            qs = qs.filter(category__slug=p["category"])
        if p.get("max_daily", "").isdigit():
            qs = qs.filter(daily_rate__lte=int(p["max_daily"]))
        if p.get("rent_to_buy") == "1":
            qs = qs.filter(rent_to_buy=True)
        if p.get("beginner") == "1":
            qs = qs.filter(beginner_friendly=True)
        sort = p.get("sort")
        if sort == "price":
            qs = qs.order_by("daily_rate")
        elif sort == "-price":
            qs = qs.order_by("-daily_rate")
        return qs

    def perform_create(self, serializer):
        if self.request.user.role != "owner":
            raise PermissionDenied("Switch to an owner account to list equipment.")
        serializer.save(owner=self.request.user)

    def perform_destroy(self, instance):
        # Soft delete keeps booking history intact.
        instance.is_active = False
        instance.save(update_fields=["is_active"])

    @action(detail=True, methods=["get"], permission_classes=[permissions.AllowAny])
    def quote(self, request, pk=None):
        eq = self.get_object()
        start = parse_date(request.query_params.get("start"), "start")
        end = parse_date(request.query_params.get("end"), "end")
        data = services.quote(eq, start, end, request.user)
        data["available"] = services.is_available(eq, start, end)
        return Response(data)

    @action(detail=True, methods=["get"], permission_classes=[permissions.AllowAny])
    def booked(self, request, pk=None):
        eq = self.get_object()
        ranges = services.blocking_bookings(eq).filter(end_date__gte=date.today()) \
            .order_by("start_date").values("start_date", "end_date")
        return Response(list(ranges))

    @action(detail=True, methods=["get"], permission_classes=[permissions.AllowAny])
    def reviews(self, request, pk=None):
        return Response(ReviewSerializer(self.get_object().reviews.all(), many=True).data)


class BookingViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = BookingSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        qs = Booking.objects.select_related("equipment__category", "equipment__owner", "renter") \
            .prefetch_related("payments")
        if self.request.query_params.get("as") == "owner":
            return qs.filter(equipment__owner=user)
        return qs.filter(Q(renter=user) | Q(equipment__owner=user))

    def create(self, request):
        d = request.data
        if not str(d.get("equipment", "")).isdigit():
            raise ValidationError({"equipment": "Equipment id is required."})
        booking = services.create_booking(
            request.user,
            int(d["equipment"]),
            parse_date(d.get("start_date"), "start_date"),
            parse_date(d.get("end_date"), "end_date"),
            d.get("delivery_address", ""),
        )
        return Response(BookingSerializer(booking).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"])
    def pay(self, request, pk=None):
        booking = self.get_object()
        if booking.renter_id != request.user.id:
            raise PermissionDenied("Only the renter can pay for this booking.")
        booking, ref = services.charge(booking, request.data.get("method", ""),
                                       request.data.get("upi_id", ""), request.data.get("card_number", ""))
        return Response({"reference": ref, "booking": BookingSerializer(booking).data})

    @action(detail=True, methods=["post"], url_path=r"(?P<act>cancel|deliver|return|complete)")
    def change_status(self, request, pk=None, act=None):
        booking = services.transition(self.get_object(), act, request.user)
        return Response(BookingSerializer(booking).data)

    @action(detail=True, methods=["post"])
    def review(self, request, pk=None):
        booking = self.get_object()
        if booking.renter_id != request.user.id:
            raise PermissionDenied("Only the renter can review this rental.")
        if booking.status != Booking.Status.COMPLETED:
            raise InvalidTransition("You can review only after the rental is completed.")
        if hasattr(booking, "review"):
            raise InvalidTransition("You have already reviewed this rental.")
        ser = ReviewSerializer(data={**request.data, "booking": booking.pk})
        ser.is_valid(raise_exception=True)
        ser.save(author=request.user, equipment=booking.equipment)
        return Response(ser.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"], url_path="buy")
    def request_purchase(self, request, pk=None):
        booking = self.get_object()
        eq = booking.equipment
        if booking.renter_id != request.user.id:
            raise PermissionDenied("Only the renter can request to buy.")
        if not eq.rent_to_buy or not eq.sale_price:
            raise InvalidTransition("The owner has not enabled Rent-to-Buy for this item.")
        if booking.status not in (Booking.Status.ACTIVE, Booking.Status.RETURNED, Booking.Status.COMPLETED):
            raise InvalidTransition("You can request to buy once you have received the item.")
        if hasattr(booking, "purchase_request"):
            raise InvalidTransition("A purchase request already exists for this rental.")
        credit = min(services.rental_credit(booking), eq.sale_price // 2)
        final = eq.sale_price - credit
        pr = PurchaseRequest.objects.create(
            booking=booking, equipment=eq, buyer=request.user, sale_price=eq.sale_price,
            rental_credit=credit, final_price=final, commission=services.percent_of(final, 8),
        )
        return Response(PurchaseRequestSerializer(pr).data, status=status.HTTP_201_CREATED)


class PurchaseRequestViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = PurchaseRequestSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        u = self.request.user
        return PurchaseRequest.objects.select_related("equipment", "buyer") \
            .filter(Q(buyer=u) | Q(equipment__owner=u))

    @action(detail=True, methods=["post"], url_path=r"(?P<decision>accept|decline)")
    def decide(self, request, pk=None, decision=None):
        pr = self.get_object()
        if pr.equipment.owner_id != request.user.id:
            raise PermissionDenied("Only the owner can respond.")
        if pr.status != PurchaseRequest.Status.PENDING:
            raise InvalidTransition("This request has already been answered.")
        pr.status = PurchaseRequest.Status.ACCEPTED if decision == "accept" else PurchaseRequest.Status.DECLINED
        pr.save(update_fields=["status"])
        if decision == "accept":
            pr.equipment.is_active = False  # sold, remove from marketplace
            pr.equipment.save(update_fields=["is_active"])
        return Response(PurchaseRequestSerializer(pr).data)


@api_view(["GET"])
@permission_classes([permissions.IsAuthenticated])
def owner_stats(request):
    b = Booking.objects.filter(equipment__owner=request.user)
    earned = b.filter(status=Booking.Status.COMPLETED).aggregate(s=Sum("owner_payout"))["s"] or 0
    return Response({
        "listings": Equipment.objects.filter(owner=request.user, is_active=True).count(),
        "bookings": b.count(),
        "active": b.filter(status__in=[Booking.Status.CONFIRMED, Booking.Status.ACTIVE,
                                       Booking.Status.RETURNED]).count(),
        "earned": earned,
        "pending_buy_requests": PurchaseRequest.objects.filter(
            equipment__owner=request.user, status=PurchaseRequest.Status.PENDING).count(),
    })


@api_view(["GET"])
@permission_classes([permissions.AllowAny])
def health(request):
    t = time.perf_counter()
    try:
        with connection.cursor() as c:
            c.execute("SELECT 1")
        db_ok = True
    except Exception:  # noqa: BLE001 - health check must never crash
        db_ok = False
    return Response({
        "status": "ok" if db_ok else "degraded",
        "database": connection.vendor if db_ok else "unreachable",
        "db_latency_ms": round((time.perf_counter() - t) * 1000, 2),
        "uptime_s": int(time.time() - METRICS["started"]),
        "requests_served": METRICS["requests"],
        "server_errors": METRICS["errors"],
    }, status=200 if db_ok else 503)
