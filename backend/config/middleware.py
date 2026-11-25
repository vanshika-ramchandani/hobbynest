import logging
import time

logger = logging.getLogger("hobbynest.requests")

# Simple in-process usage metrics, exposed through /api/health/
METRICS = {"requests": 0, "errors": 0, "started": time.time()}


class RequestLogMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        start = time.perf_counter()
        response = self.get_response(request)
        ms = (time.perf_counter() - start) * 1000
        METRICS["requests"] += 1
        if response.status_code >= 500:
            METRICS["errors"] += 1
        if request.path.startswith("/api/"):
            logger.info("%s %s %s %.1fms", request.method, request.path, response.status_code, ms)
        return response
