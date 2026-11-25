package com.hobbynest.model;

/** Loyalty badges earned by completed rentals (item returned and condition confirmed). */
public enum RewardTier {
    MEMBER(0, 0),
    BRONZE(3, 5),
    SILVER(7, 10),
    GOLD(15, 15);

    private final int rentalsNeeded;
    private final int discountPercent;

    RewardTier(int rentalsNeeded, int discountPercent) {
        this.rentalsNeeded = rentalsNeeded;
        this.discountPercent = discountPercent;
    }

    public int rentalsNeeded() { return rentalsNeeded; }
    public int discountPercent() { return discountPercent; }

    public static RewardTier forCompletedRentals(int completed) {
        RewardTier best = MEMBER;
        for (RewardTier tier : values()) {
            if (completed >= tier.rentalsNeeded) {
                best = tier;
            }
        }
        return best;
    }
}
