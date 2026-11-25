package com.hobbynest.model;

/** Creates the right Equipment subclass for a category (factory method). */
public final class EquipmentFactory {

    private EquipmentFactory() {
    }

    public static Equipment create(Category category, long id, long ownerId, String title, Condition condition,
                                   int daily, int weekly, int monthly, int value, Integer salePrice) {
        return switch (category) {
            case MUSIC -> new MusicalInstrument(id, ownerId, title, condition, daily, weekly, monthly, value, salePrice);
            case PHOTOGRAPHY -> new CameraGear(id, ownerId, title, condition, daily, weekly, monthly, value, salePrice);
            case SPORTS -> new SportsKit(id, ownerId, title, condition, daily, weekly, monthly, value, salePrice);
            case ART -> new ArtTool(id, ownerId, title, condition, daily, weekly, monthly, value, salePrice);
            case GAMING -> new GamingDevice(id, ownerId, title, condition, daily, weekly, monthly, value, salePrice);
        };
    }
}
