package com.hobbynest;

import com.hobbynest.api.Json;
import com.hobbynest.exception.PaymentFailedException;
import com.hobbynest.payment.MockPaymentGateway;
import com.hobbynest.payment.PaymentGateway.PaymentMethod;
import com.hobbynest.storage.CsvCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentAndCodecTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void validPaymentsSucceed() throws Exception {
        assertTrue(gateway.charge(PaymentMethod.UPI, "aarav@okaxis", 1499).startsWith("TEST-"));
        assertTrue(gateway.charge(PaymentMethod.CARD, "4111 1111 1111 1111", 1499).startsWith("TEST-"));
    }

    @ParameterizedTest(name = "{0} '{1}' is rejected")
    @CsvSource({
            "UPI, fail@test",            // deterministic decline
            "UPI, FAIL@TEST",            // case-insensitive
            "UPI, noatsign",             // invalid format
            "UPI, a@b",                  // too short
            "CARD, 4111111111110002",    // decline suffix
            "CARD, 12345",               // too short
            "CARD, 41111111111111111111",// 20 digits, too long
    })
    void invalidOrDeclinedPaymentsThrow(PaymentMethod method, String details) {
        assertThrows(PaymentFailedException.class, () -> gateway.charge(method, details, 100));
    }

    @Test
    void paymentEdgeCases() {
        assertThrows(PaymentFailedException.class, () -> gateway.charge(null, "a@okaxis", 100));
        assertThrows(PaymentFailedException.class, () -> gateway.charge(PaymentMethod.UPI, "a@okaxis", 0));
        PaymentFailedException e = assertThrows(PaymentFailedException.class,
                () -> gateway.charge(PaymentMethod.UPI, null, 100));
        assertEquals(402, e.getHttpStatus());
        assertEquals("payment_failed", e.getCode());
    }

    @Test
    void csvRoundTripsCommasQuotesAndNewlines() {
        List<String> row = Arrays.asList("1", "B-12 Sunrise Hostel, Navrangpura", "He said \"hi\"", "line1\nline2", "", null);
        String encoded = CsvCodec.encode(row);
        List<List<String>> parsed = CsvCodec.parse("h1,h2\n" + encoded + "\n");
        assertEquals(2, parsed.size());
        assertEquals(List.of("1", "B-12 Sunrise Hostel, Navrangpura", "He said \"hi\"", "line1\nline2", "", ""),
                parsed.get(1));
    }

    @Test
    void csvHandlesWindowsLineEndingsAndBlankLines() {
        List<List<String>> parsed = CsvCodec.parse("a,b\r\n\r\nc,d");
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), parsed);
    }

    @Test
    void jsonEscapesSpecialCharacters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", "quote\" slash\\ tab\t nl\n ctrl\u0001 rupee₹");
        m.put("n", 5);
        m.put("ok", true);
        m.put("none", null);
        m.put("list", List.of(1, "two"));
        assertEquals("{\"text\":\"quote\\\" slash\\\\ tab\\t nl\\n ctrl\\u0001 rupee₹\",\"n\":5,\"ok\":true,"
                + "\"none\":null,\"list\":[1,\"two\"]}", Json.write(m));
    }
}
