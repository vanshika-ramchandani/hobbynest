package com.hobbynest.model;

/**
 * Full price breakdown shown to the customer before payment.
 * The deposit is refundable and is never counted as revenue.
 */
public record Quote(
        int days,
        int rentalAmount,
        int discountAmount,
        String discountLabel,
        int deliveryFee,
        int deposit,
        String depositLabel,
        int totalPayable,
        int commission,
        int ownerPayout,
        boolean idCheckRequired) {
}
