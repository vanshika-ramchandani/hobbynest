package com.hobbynest.pricing;

import com.hobbynest.model.Rentable;

/** Simple alternative strategy: daily rate times days. Used to compare against best-rate pricing. */
public class DailyOnlyPricing implements PricingStrategy {

    @Override
    public int rentFor(Rentable item, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Days must be at least 1");
        }
        return item.getDailyRate() * days;
    }
}
