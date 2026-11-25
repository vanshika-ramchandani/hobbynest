package com.hobbynest.pricing;

import com.hobbynest.exception.InvalidRentalPeriodException;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.Quote;
import com.hobbynest.model.Renter;
import com.hobbynest.model.RewardTier;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Business pricing rules. Kept identical to backend/rentals/services.py. */
public class PricingEngine {

    public static final int COMMISSION_PERCENT = 15;
    public static final int SALE_COMMISSION_PERCENT = 8;
    public static final int DELIVERY_FEE = 99;
    public static final int STUDENT_DISCOUNT_PERCENT = 5;
    public static final int MAX_TOTAL_DISCOUNT_PERCENT = 20;

    /** Deposit tiers: market value below the limit pays this percent (rounded to nearest Rs 50). */
    private static final int[][] DEPOSIT_TIERS = {
            {3000, 0},
            {15000, 10},
            {50000, 20},
            {Integer.MAX_VALUE, 25},
    };
    private static final String[] DEPOSIT_LABELS = {
            "No deposit (low-value item)",
            "Small refundable deposit (medium-value item)",
            "Refundable deposit + ID check (high-value item)",
            "Refundable deposit + ID check (premium item)",
    };

    private final PricingStrategy strategy;

    public PricingEngine(PricingStrategy strategy) {
        this.strategy = strategy;
    }

    public PricingEngine() {
        this(new BestRatePricing());
    }

    public static int depositFor(int marketValue) {
        for (int[] tier : DEPOSIT_TIERS) {
            if (marketValue < tier[0]) {
                return Money.divHalfUp((long) marketValue * tier[1], 100L * 50) * 50;
            }
        }
        return 0;
    }

    public static String depositLabelFor(int marketValue) {
        for (int i = 0; i < DEPOSIT_TIERS.length; i++) {
            if (marketValue < DEPOSIT_TIERS[i][0]) {
                return DEPOSIT_LABELS[i];
            }
        }
        return "";
    }

    /** Validates dates and returns the number of rental days (inclusive). */
    public static int validatePeriod(Equipment item, LocalDate start, LocalDate end, LocalDate today)
            throws InvalidRentalPeriodException {
        if (start == null || end == null) {
            throw new InvalidRentalPeriodException("Start and end dates are required.");
        }
        if (start.isBefore(today)) {
            throw new InvalidRentalPeriodException("Start date cannot be in the past.");
        }
        if (end.isBefore(start)) {
            throw new InvalidRentalPeriodException("End date must be on or after the start date.");
        }
        int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
        if (days > item.maxRentalDays()) {
            throw new InvalidRentalPeriodException(
                    "Rentals of this item can be at most " + item.maxRentalDays() + " days.");
        }
        return days;
    }

    public Quote quote(Equipment item, LocalDate start, LocalDate end, Renter renter, LocalDate today)
            throws InvalidRentalPeriodException {
        int days = validatePeriod(item, start, end, today);
        int rent = strategy.rentFor(item, days);

        int discountPercent = 0;
        List<String> labels = new ArrayList<>();
        if (renter != null) {
            if (renter.isStudentVerified()) {
                discountPercent += STUDENT_DISCOUNT_PERCENT;
                labels.add("Student " + STUDENT_DISCOUNT_PERCENT + "%");
            }
            RewardTier tier = renter.getRewardTier();
            if (tier.discountPercent() > 0) {
                discountPercent += tier.discountPercent();
                labels.add(capitalise(tier.name()) + " badge " + tier.discountPercent() + "%");
            }
        }
        if (discountPercent > MAX_TOTAL_DISCOUNT_PERCENT) {
            discountPercent = MAX_TOTAL_DISCOUNT_PERCENT;
            labels.add("capped at " + MAX_TOTAL_DISCOUNT_PERCENT + "%");
        }

        int discount = Money.percentOf(rent, discountPercent);
        int rentAfter = rent - discount;
        int deposit = depositFor(item.getMarketValue());
        int commission = Money.percentOf(rentAfter, COMMISSION_PERCENT);

        return new Quote(days, rent, discount, String.join(", ", labels), DELIVERY_FEE, deposit,
                depositLabelFor(item.getMarketValue()), rentAfter + DELIVERY_FEE + deposit, commission,
                rentAfter - commission, item.requiresIdVerification());
    }

    /** Rent-to-Buy: 50% of rent actually paid is credited, capped at half the sale price. */
    public static int rentalCredit(Quote paidQuote, int salePrice) {
        int paidRent = paidQuote.rentalAmount() - paidQuote.discountAmount();
        return Math.min(paidRent / 2, salePrice / 2);
    }

    private static String capitalise(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
