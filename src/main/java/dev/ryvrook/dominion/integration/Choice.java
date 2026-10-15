package dev.ryvrook.dominion.integration;

/**
 * The start time decision for one sibling, made from the setting and the plugin manager only, so it never loads a
 * sibling class. Pure, so the switch logic is tested without an engine.
 */
public record Choice(Sibling sibling, Mode mode, Presence.Found found, Outcome outcome) {

    /** What Dominion does about the sibling. */
    public enum Outcome {
        /** The bridge is loaded. */
        BIND,
        /** The setting is never. */
        OFF,
        /** The sibling is not installed or not running. */
        MISSING,
        /** The sibling is installed but older than the minimum and the setting is auto. */
        TOO_OLD
    }

    public static Choice decide(Sibling sibling, Mode mode, Presence.Found found) {
        Presence.Found f = found == null ? Presence.Found.MISSING : found;
        Outcome outcome;
        if (mode == Mode.NEVER) {
            outcome = Outcome.OFF;
        } else if (!f.present() || !f.running()) {
            outcome = Outcome.MISSING;
        } else if (mode == Mode.AUTO && VersionCompare.below(f.version(), sibling.minimum())) {
            outcome = Outcome.TOO_OLD;
        } else {
            outcome = Outcome.BIND;
        }
        return new Choice(sibling, mode, f, outcome);
    }

    public boolean bind() {
        return outcome == Outcome.BIND;
    }

    /** True when the start line is a warning, which is only when the setting asks for a sibling that is missing. */
    public boolean warn() {
        return outcome == Outcome.MISSING && mode == Mode.ALWAYS;
    }

    /** The one line logged at start. For a bound sibling the bridge logs its own line once it is linked. */
    public String startLine() {
        String name = sibling.pluginName();
        return switch (outcome) {
            case BIND -> "Dominion found " + name + " " + found.version() + " and links to it";
            case OFF -> "Dominion leaves " + name + " alone because " + sibling.settingKey() + " is never";
            case MISSING -> mode == Mode.ALWAYS
                    ? "Dominion is set to use " + name + " with " + sibling.settingKey() + " always, but " + name
                    + " is " + (found.present() ? "not running" : "not installed") + ", so it runs without it"
                    : "Dominion runs without " + name + " because it is " + (found.present() ? "not running" : "not installed");
            case TOO_OLD -> "Dominion runs without " + name + " because " + name + " " + found.version()
                    + " is older than " + sibling.minimum();
        };
    }
}
