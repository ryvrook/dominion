package dev.ryvrook.dominion.model;

/** Built-in ranks, ordered from lowest to highest authority. */
public enum Rank {
    RECRUIT("Recruit", "-"),
    MEMBER("Member", "+"),
    OFFICER("Officer", "*"),
    LEADER("Leader", "**");

    private final String displayName;
    private final String prefix;

    Rank(String displayName, String prefix) {
        this.displayName = displayName;
        this.prefix = prefix;
    }

    public String displayName() {
        return displayName;
    }

    public String prefix() {
        return prefix;
    }

    public boolean atLeast(Rank other) {
        return ordinal() >= other.ordinal();
    }

    public Rank next() {
        return ordinal() < values().length - 2 ? values()[ordinal() + 1] : this;
    }

    public Rank previous() {
        return ordinal() > 0 ? values()[ordinal() - 1] : this;
    }

    public static Rank parse(String text) {
        for (Rank r : values()) {
            if (r.name().equalsIgnoreCase(text) || r.displayName.equalsIgnoreCase(text)) {
                return r;
            }
        }
        return null;
    }
}
