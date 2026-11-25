package com.hobbynest.model;

import java.util.List;

/** Rackets, cricket kits, table tennis sets. Shared gear must be sanitised between renters. */
public class SportsKit extends Equipment {

    public SportsKit(long id, long ownerId, String title, Condition condition, int dailyRate, int weeklyRate,
            int monthlyRate, int marketValue, Integer salePrice) {
        super(id, ownerId, title, condition, dailyRate, weeklyRate, monthlyRate, marketValue, salePrice);
    }

    @Override
    public Category getCategory() {
        return Category.SPORTS;
    }

    @Override
    protected List<String> categorySpecificChecks() {
        return List.of("Sanitise grips, pads and helmets", "Count balls or shuttles and note the number");
    }

    /** Sports kits are booked for short stints; long rentals block teams and tournaments, so cap at 30 days. */
    @Override
    public int maxRentalDays() {
        return 30;
    }
}
