package com.hobbynest.model;

import java.util.Set;

/** Booking lifecycle. Same names as the Django API so both services agree. */
public enum BookingStatus {
    PENDING_PAYMENT, PAYMENT_FAILED, CONFIRMED, ACTIVE, RETURNED, COMPLETED, CANCELLED;

    /** Statuses that hold the item's calendar (PENDING_PAYMENT only for a short window). */
    public static final Set<BookingStatus> BLOCKING = Set.of(PENDING_PAYMENT, CONFIRMED, ACTIVE, RETURNED);

    public String apiName() {
        return name().toLowerCase();
    }
}
