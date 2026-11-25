package com.hobbynest.payment;

import com.hobbynest.exception.PaymentFailedException;

/** Abstraction over a payment provider so a real test-mode gateway can replace the mock later. */
public interface PaymentGateway {

    /**
     * Charges the amount and returns a transaction reference.
     *
     * @throws PaymentFailedException when details are invalid or the charge is declined
     */
    String charge(PaymentMethod method, String details, int amount) throws PaymentFailedException;

    enum PaymentMethod { UPI, CARD }
}
