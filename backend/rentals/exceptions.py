"""Domain exceptions. Each maps to a clear HTTP status and message for the client."""
from rest_framework.exceptions import APIException
from rest_framework.views import exception_handler


class HobbyNestError(APIException):
    status_code = 400
    default_detail = "Request could not be processed."
    default_code = "hobbynest_error"


class InvalidRentalPeriod(HobbyNestError):
    default_detail = "Invalid rental period."
    default_code = "invalid_period"


class ItemUnavailable(HobbyNestError):
    status_code = 409
    default_detail = "This item is already booked for the selected dates."
    default_code = "item_unavailable"


class PaymentFailed(HobbyNestError):
    status_code = 402
    default_detail = "Payment was declined by the gateway."
    default_code = "payment_failed"


class InvalidTransition(HobbyNestError):
    status_code = 409
    default_detail = "This action is not allowed for the booking's current status."
    default_code = "invalid_transition"


def api_exception_handler(exc, context):
    response = exception_handler(exc, context)
    if response is not None and isinstance(exc, HobbyNestError):
        response.data = {"detail": str(exc.detail), "code": exc.default_code}
    return response
