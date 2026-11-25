package com.hobbynest.model;

import java.util.List;

/** Guitars, keyboards, ukuleles, DJ controllers. */
public class MusicalInstrument extends Equipment {

    public MusicalInstrument(long id, long ownerId, String title, Condition condition, int dailyRate, int weeklyRate,
            int monthlyRate, int marketValue, Integer salePrice) {
        super(id, ownerId, title, condition, dailyRate, weeklyRate, monthlyRate, marketValue, salePrice);
    }

    @Override
    public Category getCategory() {
        return Category.MUSIC;
    }

    @Override
    protected List<String> categorySpecificChecks() {
        return List.of("Tune the instrument and check strings or keys", "Include case, strap and any cables");
    }
}
