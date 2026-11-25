package com.hobbynest.model;

import java.util.List;

/** Easels, pottery wheels, craft tools. */
public class ArtTool extends Equipment {

    public ArtTool(long id, long ownerId, String title, Condition condition, int dailyRate, int weeklyRate,
            int monthlyRate, int marketValue, Integer salePrice) {
        super(id, ownerId, title, condition, dailyRate, weeklyRate, monthlyRate, marketValue, salePrice);
    }

    @Override
    public Category getCategory() {
        return Category.ART;
    }

    @Override
    protected List<String> categorySpecificChecks() {
        return List.of("Clean off paint or clay residue", "Check moving parts and power cable");
    }
}
