package com.hobbynest.model;

import java.util.List;

/** Cameras, lenses, gimbals and lights. Cameras always need an ID check because they are the most-stolen category. */
public class CameraGear extends Equipment {

    public CameraGear(long id, long ownerId, String title, Condition condition, int dailyRate, int weeklyRate,
            int monthlyRate, int marketValue, Integer salePrice) {
        super(id, ownerId, title, condition, dailyRate, weeklyRate, monthlyRate, marketValue, salePrice);
    }

    @Override
    public Category getCategory() {
        return Category.PHOTOGRAPHY;
    }

    @Override
    protected List<String> categorySpecificChecks() {
        return List.of("Check the sensor and lens for scratches or dust", "Charge all batteries and format the memory card");
    }

    /** Cameras are high theft risk, so an ID check applies above Rs 10,000 instead of Rs 15,000. */
    @Override
    public boolean requiresIdVerification() {
        return getMarketValue() >= 10000;
    }
}
