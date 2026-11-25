package com.hobbynest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base class for every rentable item. Pricing fields are shared; each category subclass
 * adds its own inspection rules (polymorphism via {@link #categorySpecificChecks()}).
 */
public abstract class Equipment implements Rentable, Inspectable {

    /** Items worth at least this much need an ID check (matches the "high-value" deposit tier). */
    public static final int ID_CHECK_THRESHOLD = 15000;

    private final long id;
    private final long ownerId;
    private final String title;
    private final Condition condition;
    private final int dailyRate;
    private final int weeklyRate;
    private final int monthlyRate;
    private final int marketValue;
    private final Integer salePrice; // null when the owner has not enabled Rent-to-Buy
    private volatile boolean active = true;

    protected Equipment(long id, long ownerId, String title, Condition condition, int dailyRate,
                        int weeklyRate, int monthlyRate, int marketValue, Integer salePrice) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (dailyRate < 10) {
            throw new IllegalArgumentException("Daily rate must be at least Rs 10");
        }
        if (weeklyRate > dailyRate * 7) {
            throw new IllegalArgumentException("Weekly rate should not exceed 7 x daily rate");
        }
        if (monthlyRate > weeklyRate * 5) {
            throw new IllegalArgumentException("Monthly rate looks too high compared to weekly");
        }
        if (salePrice != null && salePrice <= 0) {
            throw new IllegalArgumentException("Sale price must be positive");
        }
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.condition = Objects.requireNonNull(condition);
        this.dailyRate = dailyRate;
        this.weeklyRate = weeklyRate;
        this.monthlyRate = monthlyRate;
        this.marketValue = marketValue;
        this.salePrice = salePrice;
    }

    public abstract Category getCategory();

    /** Checks specific to this kind of equipment. */
    protected abstract List<String> categorySpecificChecks();

    @Override
    public final List<String> dispatchChecklist() {
        List<String> checks = new ArrayList<>();
        checks.add("Photograph the item from all sides");
        checks.addAll(categorySpecificChecks());
        checks.add("Pack securely and hand over to the delivery partner");
        return checks;
    }

    @Override
    public boolean requiresIdVerification() {
        return marketValue >= ID_CHECK_THRESHOLD;
    }

    public boolean isRentToBuy() {
        return salePrice != null;
    }

    public long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public Condition getCondition() { return condition; }
    @Override public int getDailyRate() { return dailyRate; }
    @Override public int getWeeklyRate() { return weeklyRate; }
    @Override public int getMonthlyRate() { return monthlyRate; }
    @Override public int getMarketValue() { return marketValue; }
    public Integer getSalePrice() { return salePrice; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return getCategory().displayName() + ": " + title + " (Rs " + dailyRate + "/day)";
    }
}
