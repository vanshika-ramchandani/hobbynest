package com.hobbynest.api;

import com.hobbynest.exception.HobbyNestException;
import com.hobbynest.exception.InvalidRentalPeriodException;
import com.hobbynest.exception.NotFoundException;
import com.hobbynest.model.Booking;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.Quote;
import com.hobbynest.payment.PaymentGateway;
import com.hobbynest.service.OverdueMonitor;
import com.hobbynest.service.RentalService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP API over the rental engine, so the Java core can be deployed and reached on the cloud.
 * Requests are handled by a fixed thread pool: many users are served at the same time,
 * and RentalService's per-item locks keep bookings consistent.
 *
 * Parameters are sent as query string or application/x-www-form-urlencoded body.
 */
public class ApiServer implements AutoCloseable {

    private static final Pattern EQUIPMENT_QUOTE = Pattern.compile("^/api/equipment/(\\d+)/quote/?$");
    private static final Pattern EQUIPMENT_ONE = Pattern.compile("^/api/equipment/(\\d+)/?$");
    private static final Pattern BOOKING_ACTION =
            Pattern.compile("^/api/bookings/(\\d+)/(pay|cancel|deliver|return|complete|buy)/?$");

    private final RentalService service;
    private final OverdueMonitor monitor;
    private final HttpServer server;
    private final ExecutorService pool;
    private final long startedAt = System.currentTimeMillis();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private volatile boolean logRequests = true;

    public ApiServer(RentalService service, OverdueMonitor monitor, int port, int threads) throws IOException {
        this.service = service;
        this.monitor = monitor;
        this.server = HttpServer.create(new InetSocketAddress(port), 128);
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setName("api-worker-" + t.threadId());
            return t;
        });
        server.setExecutor(pool);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) {
        requests.incrementAndGet();
        long t0 = System.nanoTime();
        int status = 500;
        try {
            Map<String, String> params = params(ex);
            Object body = route(ex.getRequestMethod(), ex.getRequestURI().getPath(), params);
            status = ex.getRequestMethod().equals("POST") ? 201 : 200;
            if (ex.getRequestURI().getPath().matches(".*/(pay|cancel|deliver|return|complete|buy)/?$")) {
                status = 200;
            }
            send(ex, status, body);
        } catch (HobbyNestException e) {
            status = e.getHttpStatus();
            send(ex, status, Map.of("detail", e.getMessage(), "code", e.getCode()));
        } catch (IllegalArgumentException e) {
            status = 400;
            send(ex, status, Map.of("detail", e.getMessage() == null ? "Bad request." : e.getMessage(),
                    "code", "bad_request"));
        } catch (Exception e) {
            errors.incrementAndGet();
            status = 500;
            send(ex, status, Map.of("detail", "Internal server error.", "code", "server_error"));
        } finally {
            if (logRequests) System.out.printf("%s %s %d %.1fms%n", ex.getRequestMethod(), ex.getRequestURI().getPath(), status,
                    (System.nanoTime() - t0) / 1e6);
        }
    }

    private Object route(String method, String path, Map<String, String> p) throws HobbyNestException {
        Matcher m;
        if (method.equals("GET") && path.matches("^/api/health/?$")) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("status", "ok");
            h.put("service", "hobbynest-java-engine");
            h.put("uptime_s", (System.currentTimeMillis() - startedAt) / 1000);
            h.put("requests_served", requests.get());
            h.put("server_errors", errors.get());
            h.put("overdue_rentals", monitor == null ? 0 : monitor.getLastOverdueCount());
            h.putAll(service.stats());
            return h;
        }
        if (method.equals("GET") && path.matches("^/api/equipment/?$")) {
            return service.listEquipment().stream().map(ApiServer::equipmentJson).toList();
        }
        if (method.equals("GET") && (m = EQUIPMENT_ONE.matcher(path)).matches()) {
            return equipmentJson(service.getEquipment(Long.parseLong(m.group(1))));
        }
        if (method.equals("GET") && (m = EQUIPMENT_QUOTE.matcher(path)).matches()) {
            long id = Long.parseLong(m.group(1));
            LocalDate start = date(p, "start");
            LocalDate end = date(p, "end");
            Long renter = p.containsKey("renter") ? Long.valueOf(p.get("renter")) : null;
            Map<String, Object> q = quoteJson(service.quote(id, renter, start, end));
            q.put("available", service.isAvailable(id, start, end, null));
            return q;
        }
        if (method.equals("GET") && path.matches("^/api/bookings/?$")) {
            return service.bookingsForUser(requiredLong(p, "user")).stream().map(ApiServer::bookingJson).toList();
        }
        if (method.equals("POST") && path.matches("^/api/bookings/?$")) {
            return bookingJson(service.book(requiredLong(p, "renter"), requiredLong(p, "equipment"),
                    date(p, "start"), date(p, "end"), p.get("address")));
        }
        if (method.equals("POST") && (m = BOOKING_ACTION.matcher(path)).matches()) {
            long id = Long.parseLong(m.group(1));
            String action = m.group(2);
            long user = requiredLong(p, "user");
            if (action.equals("pay")) {
                PaymentGateway.PaymentMethod pm;
                try {
                    pm = PaymentGateway.PaymentMethod.valueOf(p.getOrDefault("method", "").toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("method must be 'upi' or 'card'.");
                }
                return bookingJson(service.pay(id, user, pm, p.get("details")));
            }
            if (action.equals("buy")) {
                return service.purchaseOffer(id, user);
            }
            return bookingJson(service.transition(id, user, action));
        }
        throw new NotFoundException("No route for " + method + " " + path);
    }

    // ---------- JSON views (snake_case, same field names as the Django API) ----------

    static Map<String, Object> equipmentJson(Equipment e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("category", e.getCategory().name().toLowerCase());
        m.put("owner", e.getOwnerId());
        m.put("condition", e.getCondition().name().toLowerCase());
        m.put("daily_rate", e.getDailyRate());
        m.put("weekly_rate", e.getWeeklyRate());
        m.put("monthly_rate", e.getMonthlyRate());
        m.put("market_value", e.getMarketValue());
        m.put("rent_to_buy", e.isRentToBuy());
        m.put("sale_price", e.getSalePrice());
        m.put("max_rental_days", e.maxRentalDays());
        m.put("id_check_required", e.requiresIdVerification());
        m.put("dispatch_checklist", e.dispatchChecklist());
        return m;
    }

    static Map<String, Object> quoteJson(Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", q.days());
        m.put("rental_amount", q.rentalAmount());
        m.put("discount_amount", q.discountAmount());
        m.put("discount_label", q.discountLabel());
        m.put("delivery_fee", q.deliveryFee());
        m.put("deposit", q.deposit());
        m.put("deposit_label", q.depositLabel());
        m.put("total_payable", q.totalPayable());
        m.put("commission", q.commission());
        m.put("owner_payout", q.ownerPayout());
        m.put("id_check_required", q.idCheckRequired());
        return m;
    }

    static Map<String, Object> bookingJson(Booking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("equipment", b.getEquipmentId());
        m.put("renter", b.getRenterId());
        m.put("start_date", b.getStartDate().toString());
        m.put("end_date", b.getEndDate().toString());
        m.put("status", b.getStatus().apiName());
        m.put("delivery_address", b.getDeliveryAddress());
        m.put("payment_reference", b.getPaymentReference());
        m.put("deposit_refunded", b.isDepositRefunded());
        m.putAll(quoteJson(b.getQuote()));
        return m;
    }

    // ---------- HTTP helpers ----------

    private static Map<String, String> params(HttpExchange ex) throws IOException {
        Map<String, String> out = new HashMap<>();
        parseInto(out, ex.getRequestURI().getRawQuery());
        if ("POST".equals(ex.getRequestMethod())) {
            byte[] body = ex.getRequestBody().readNBytes(64 * 1024);
            parseInto(out, new String(body, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void parseInto(Map<String, String> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
    }

    private static LocalDate date(Map<String, String> p, String key) throws InvalidRentalPeriodException {
        try {
            return LocalDate.parse(p.getOrDefault(key, ""));
        } catch (DateTimeParseException e) {
            throw new InvalidRentalPeriodException("'" + key + "' must be a date in YYYY-MM-DD format.");
        }
    }

    private static long requiredLong(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null || !v.matches("\\d{1,18}")) {
            throw new IllegalArgumentException("'" + key + "' is required and must be a number.");
        }
        return Long.parseLong(v);
    }

    private static void send(HttpExchange ex, int status, Object body) {
        try {
            byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length); // byte length, not String.length()
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            System.err.println("Could not send response: " + e.getMessage());
        } finally {
            ex.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdown();
        try {
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Exposed for tests. */
    List<Long> counters() {
        return List.of(requests.get(), errors.get());
    }
}
