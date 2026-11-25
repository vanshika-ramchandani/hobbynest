package com.hobbynest.model;

/** Anything that can be rented on a daily, weekly or monthly rate. */
public interface Rentable {

    int MAX_RENTAL_DAYS = 90;

    int getDailyRate();

    int getWeeklyRate();

    int getMonthlyRate();

    /** Value used to pick the refundable deposit tier. */
    int getMarketValue();

    /** Longest rental allowed for this item. Categories may tighten it. */
    default int maxRentalDays() {
        return MAX_RENTAL_DAYS;
    }
}
