package com.hobbynest.model;

public enum Category {
    MUSIC("Musical Instruments"),
    PHOTOGRAPHY("Photography"),
    SPORTS("Sports"),
    ART("Art & Creative"),
    GAMING("Gaming & Tech");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
