package dev.ryvrook.dominion.model;

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

    public String color() {
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
