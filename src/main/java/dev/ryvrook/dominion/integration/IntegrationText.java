package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.text.Messages;

/** Player facing sentences of the integration package and its bridges. */
public final class IntegrationText {
    private IntegrationText() {}

    // /fa integrations status lines
    public static final String STATUS_OFF = Messages.define("integration.statusOff", "{plugin} is off, {setting} is {mode}");
    public static final String STATUS_MISSING = Messages.define("integration.statusMissing", "{plugin} is missing, {setting} is {mode}");
    public static final String STATUS_TOO_OLD = Messages.define("integration.statusTooOld",
            "{plugin} {version} is older than {minimum} and left alone, {setting} is {mode}");
    public static final String STATUS_BOUND = Messages.define("integration.statusBound", "{plugin} {version} is bound, {setting} is {mode}");
    public static final String STATUS_CHANGED = Messages.define("integration.statusChanged", "{plugin} changed since start, run {command}");
    public static final String STATUS_FAILED = Messages.define("integration.statusFailed", "{plugin} {version} failed to link, see the log");
    public static final String STATUS_WAITING = Messages.define("integration.statusWaiting",
            "{plugin} {version} is installed and Dominion waits for it to start");

    // Feats unlock announcement in faction chat, the name and feat are coloured pieces around this text
    public static final String FEAT_EARNED = Messages.define("integration.featEarned", " earned the feat ");

    // Atlas siege layer, label set when Atlas binds
    public static final String ATLAS_LAYER = Messages.define("integration.atlasLayer", "Live sieges");
    public static final String ATLAS_HIDE_REASON = Messages.define("integration.atlasHideReason", "Hidden with /f map hide in Dominion");
    public static final String SIEGE_UNKNOWN_ATTACKER = Messages.define("integration.siegeUnknownAttacker", "Unknown");
    public static final String SIEGE_UNKNOWN_DEFENDER = Messages.define("integration.siegeUnknownDefender", "unknown");
    public static final String SIEGE_LABEL = Messages.define("integration.siegeLabel", "{attacker} besieging {defender}");
    public static final String SIEGE_POPUP = Messages.define("integration.siegePopup",
            "{attacker} is besieging {defender} at chunk {x}, {z}. {state}");
    public static final String SIEGE_WHEN = Messages.define("integration.siegeWhen", "{time} server time");
    public static final String SIEGE_CAPTURING = Messages.define("integration.siegeCapturing", "The attackers are capturing the chunk.");
    public static final String SIEGE_CAPTURING_UNTIL = Messages.define("integration.siegeCapturingUntil",
            "The attackers are capturing the chunk until {when}.");
    public static final String SIEGE_BREACHED = Messages.define("integration.siegeBreached", "The land is breached and open to raids.");
    public static final String SIEGE_BREACHED_UNTIL = Messages.define("integration.siegeBreachedUntil",
            "The land is breached and open to raids until {when}.");
    public static final String SIEGE_PHASE = Messages.define("integration.siegePhase", "The siege is {phase}.");
}
