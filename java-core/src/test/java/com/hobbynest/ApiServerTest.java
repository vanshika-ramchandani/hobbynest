package com.hobbynest;

import com.hobbynest.api.ApiServer;
import com.hobbynest.service.OverdueMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests: real HTTP server on a random port, real HttpClient, real file storage. */
class ApiServerTest {

    @TempDir
    Path dir;
    TestSupport.Fixture f;
    ApiServer api;
    OverdueMonitor monitor;
    final HttpClient http = HttpClient.newHttpClient();
    String base;

    @BeforeEach
    void start() throws Exception {
        f = new TestSupport.Fixture(dir);
        monitor = new OverdueMonitor(f.service, f.audit);
        api = new ApiServer(f.service, monitor, 0, 8);
        api.setLogRequests(false);
        api.start();
        base = "http://localhost:" + api.getPort();
    }

    @AfterEach
    void stop() {
        api.close();
        monitor.close();
        f.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String form) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static long id(String json) {
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertTrue(m.find(), json);
        return Long.parseLong(m.group(1));
    }

    private String bookingForm(long renter, int from, int to, String address) {
        return "renter=" + renter + "&equipment=" + f.guitar.getId() + "&start=" + f.day(from) + "&end=" + f.day(to)
                + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
    }

    @Test
    void healthAndCatalogue() throws Exception {
        HttpResponse<String> h = get("/api/health");
        assertEquals(200, h.statusCode());
        assertTrue(h.body().contains("\"status\":\"ok\""));
        assertTrue(h.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));

        HttpResponse<String> list = get("/api/equipment");
        assertTrue(list.body().contains("\"title\":\"Guitar\""));
        assertTrue(list.body().contains("\"dispatch_checklist\""));
        assertEquals(200, get("/api/equipment/" + f.guitar.getId()).statusCode());
    }

    @Test
    void quoteEndpointMatchesDjango() throws Exception {
        HttpResponse<String> q = get("/api/equipment/" + f.guitar.getId() + "/quote?start=" + f.day(2) + "&end=" + f.day(8));
        assertEquals(200, q.statusCode());
        assertTrue(q.body().contains("\"total_payable\":1499"), q.body());
        assertTrue(q.body().contains("\"available\":true"));
    }

    @Test
    void endToEndBookingFlowOverHttp() throws Exception {
        // Unicode in the address checks that Content-Length uses UTF-8 byte length
        HttpResponse<String> created = post("/api/bookings", bookingForm(f.renter.getId(), 2, 8, "Flat 4, Shanti Niwas, Paldi, Ahmedabad ₹"));
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.body().contains("₹"));
        long bookingId = id(created.body());

        HttpResponse<String> clash = post("/api/bookings", bookingForm(f.otherRenter.getId(), 3, 4, "Other address, Ahmedabad"));
        assertEquals(409, clash.statusCode());
        assertTrue(clash.body().contains("item_unavailable"));

        HttpResponse<String> fail = post("/api/bookings/" + bookingId + "/pay",
                "user=" + f.renter.getId() + "&method=upi&details=fail%40test");
        assertEquals(402, fail.statusCode());

        HttpResponse<String> ok = post("/api/bookings/" + bookingId + "/pay",
                "user=" + f.renter.getId() + "&method=upi&details=a%40okaxis");
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"status\":\"confirmed\""));

        assertEquals(409, post("/api/bookings/" + bookingId + "/deliver", "user=" + f.renter.getId()).statusCode());
        assertEquals(200, post("/api/bookings/" + bookingId + "/deliver", "user=" + f.owner.getId()).statusCode());
        assertEquals(200, post("/api/bookings/" + bookingId + "/buy", "user=" + f.renter.getId()).statusCode());

        HttpResponse<String> mine = get("/api/bookings?user=" + f.renter.getId());
        assertTrue(mine.body().contains("\"status\":\"active\""));
    }

    @Test
    void errorResponsesAreCleanJson() throws Exception {
        assertEquals(404, get("/api/nope").statusCode());
        assertEquals(404, get("/api/equipment/999").statusCode());
        HttpResponse<String> badDate = get("/api/equipment/" + f.guitar.getId() + "/quote?start=tomorrow&end=x");
        assertEquals(400, badDate.statusCode());
        assertTrue(badDate.body().contains("invalid_period"));
        assertEquals(400, post("/api/bookings", "renter=abc").statusCode());
        assertEquals(400, post("/api/bookings/1/pay", "user=" + f.renter.getId() + "&method=bitcoin").statusCode());
        // SQL-injection style input is just treated as data and rejected by validation
        assertEquals(400, get("/api/bookings?user=1%27%20OR%201%3D1").statusCode());
    }
}
