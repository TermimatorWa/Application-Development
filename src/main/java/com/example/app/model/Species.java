package com.example.app.model;

/** The three large herbivores in the model. The ordinal is the index used in all arrays. */
public enum Species {
    DEER("Red deer"),
    CATTLE("Heck cattle"),
    HORSES("Konik horses");

    public static final int COUNT = values().length;

    private final String displayName;

    Species(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
