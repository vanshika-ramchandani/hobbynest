package com.hobbynest.pricing;

/**
 * Whole-rupee money helpers. All amounts are integers and round half up,
 * matching the Django backend (which avoids Python's round-half-to-even).
 */
public final class Money {

    private Money() {
    }

    /** numerator / denominator rounded half up, for non-negative values. */
    public static int divHalfUp(long numerator, long denominator) {
        if (numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("Money values must be non-negative");
        }
        return (int) ((2 * numerator + denominator) / (2 * denominator));
    }

    public static int percentOf(int amount, int percent) {
        return divHalfUp((long) amount * percent, 100);
    }
}
