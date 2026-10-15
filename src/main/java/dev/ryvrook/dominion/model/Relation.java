package dev.ryvrook.dominion.model;

import dev.ryvrook.dominion.text.Palette;

/** Relationship between two factions, or between a player and a faction. */
public enum Relation {
    OWN("Own", "#5fd37a"),
    ALLY("Ally", "#d99cff"),
    TRUCE("Truce", "#7fd6ff"),
    NEUTRAL("Neutral", "#d3d6db"),
    ENEMY("Enemy", "#ff6b6b");

    private final String displayName;
    private final String color;

    Relation(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** The colour from the colors settings, see Palette. */
    public String color() {
        return switch (this) {
            case OWN -> Palette.own();
            case ALLY -> Palette.ally();
            case TRUCE -> Palette.truce();
            case NEUTRAL -> Palette.neutral();
            case ENEMY -> Palette.enemy();
        };
    }

    /** The shipped colour, before any setting changed it. */
    public String defaultColor() {
        return color;
    }

    /** True for states that other factions can be set to. */
    public boolean isDiplomatic() {
        return this != OWN;
    }

    public static Relation parse(String text) {
        for (Relation r : values()) {
            if (r.isDiplomatic() && (r.name().equalsIgnoreCase(text) || r.displayName.equalsIgnoreCase(text))) {
                return r;
            }
        }
        return null;
    }
}
