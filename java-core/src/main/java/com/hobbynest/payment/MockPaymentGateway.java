package com.hobbynest.payment;

import com.hobbynest.exception.PaymentFailedException;

import java.util.UUID;

/**
 * Test-mode gateway. No money moves. Deterministic failures so error paths can be demoed and tested:
 * UPI ID "fail@test" is declined, and any card number ending in 0002 is declined.
 */
public class MockPaymentGateway implements PaymentGateway {

    public static final String FAIL_UPI = "fail@test";
    public static final String FAIL_CARD_SUFFIX = "0002";

    @Override
    public String charge(PaymentMethod method, String details, int amount) throws PaymentFailedException {
        if (method == null) {
            throw new PaymentFailedException("Unsupported payment method.");
        }
        if (amount <= 0) {
            throw new PaymentFailedException("Amount must be positive.");
        }
        String d = details == null ? "" : details.trim();
        switch (method) {
            case UPI -> {
                if (!d.contains("@") || d.length() < 5) {
                    throw new PaymentFailedException("Enter a valid UPI ID, for example name@okbank.");
                }
                if (d.equalsIgnoreCase(FAIL_UPI)) {
                    throw new PaymentFailedException("UPI request declined (test failure ID).");
                }
            }
            case CARD -> {
                String digits = d.replaceAll("\\D", "");
                if (digits.length() < 12 || digits.length() > 19) {
                    throw new PaymentFailedException("Enter a valid card number.");
                }
                if (digits.endsWith(FAIL_CARD_SUFFIX)) {
                    throw new PaymentFailedException("Card declined by issuer (test failure card).");
                }
            }
        }
        return "TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
