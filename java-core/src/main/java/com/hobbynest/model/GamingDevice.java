package com.hobbynest.model;

import java.util.List;

/** Consoles, VR headsets. */
public class GamingDevice extends Equipment {

    public GamingDevice(long id, long ownerId, String title, Condition condition, int dailyRate, int weeklyRate,
            int monthlyRate, int marketValue, Integer salePrice) {
        super(id, ownerId, title, condition, dailyRate, weeklyRate, monthlyRate, marketValue, salePrice);
    }

    @Override
    public Category getCategory() {
        return Category.GAMING;
    }

    @Override
    protected List<String> categorySpecificChecks() {
        return List.of("Sign out of all personal accounts and factory-reset profiles", "Check controllers pair and are charged");
    }
}
