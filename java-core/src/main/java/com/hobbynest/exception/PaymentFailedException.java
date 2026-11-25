package com.hobbynest.exception;

/** Thrown when the payment gateway declines a charge or payment details are invalid. */
public class PaymentFailedException extends HobbyNestException {

    public PaymentFailedException(String message) {
        super(message, 402, "payment_failed");
    }
}
