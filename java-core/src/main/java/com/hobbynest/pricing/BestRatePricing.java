package com.hobbynest.pricing;

import com.hobbynest.model.Rentable;

/**
 * Cheapest combination of daily, weekly and monthly rates covering the days.
 * May "overshoot": 6 days can cost one week if that is cheaper than 6 daily rates.
 * Dynamic programming: cost[d] = min(cost[d-1] + daily, cost[d-7] + weekly, cost[d-30] + monthly).
 */
public class BestRatePricing implements PricingStrategy {

    @Override
    public int rentFor(Rentable item, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Days must be at least 1");
        }
        int[] cost = new int[days + 1];
        for (int d = 1; d <= days; d++) {
            int byDay = cost[d - 1] + item.getDailyRate();
            int byWeek = cost[Math.max(0, d - 7)] + item.getWeeklyRate();
            int byMonth = cost[Math.max(0, d - 30)] + item.getMonthlyRate();
            cost[d] = Math.min(byDay, Math.min(byWeek, byMonth));
        }
        return cost[days];
    }
}
