package dev.ryvrook.dominion.shield;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the shield package, editable in messages.json. */
public final class ShieldText {
    private ShieldText() {}

    public static final String RANK_CANNOT = Messages.define("shield.rankCannot", "Your rank cannot change the shield window.");
    public static final String SHIELDS_OFF = Messages.define("shield.shieldsOff", "Shields are turned off on this server.");
    public static final String PICK_HOUR = Messages.define("shield.pickHour", "Pick an hour from 0 to 23, or none to remove the shield.");
    public static final String ALREADY_NONE = Messages.define("shield.alreadyNone", "Your faction has no shield window already.");
    public static final String ALREADY_STARTS = Messages.define("shield.alreadyStarts", "Your shield already starts at {hour}.");
    public static final String AUDIT_PENDING_CANCELLED = Messages.define("shield.auditPendingCancelled", "Cancelled the pending shield change");
    public static final String PENDING_CANCELLED = Messages.define("shield.pendingCancelled", "The pending shield change was cancelled.");
    public static final String AUDIT_ASK_REMOVE = Messages.define("shield.auditAskRemove", "Asked to remove the shield window in {delay}");
    public static final String AUDIT_ASK_MOVE = Messages.define("shield.auditAskMove", "Asked to move the shield window to {hour} in {delay}");
    public static final String AUDIT_REMOVED = Messages.define("shield.auditRemoved", "The shield window was removed");
    public static final String AUDIT_STARTS = Messages.define("shield.auditStarts", "The shield window now starts at {hour}");
    public static final String REMOVED = Messages.define("shield.removed", "Your shield window was removed.");
    public static final String WILL_BE_REMOVED = Messages.define("shield.willBeRemoved", "Your shield window will be removed in {delay}.");
    public static final String NOW_STARTS = Messages.define("shield.nowStarts", "Your shield now starts at {hour}.");
    public static final String WILL_START = Messages.define("shield.willStart", "Your shield will start at {hour} in {delay}.");
    public static final String STATUS_NO_FACTION = Messages.define("shield.statusNoFaction", "No faction.");
    public static final String STATUS_EOTW = Messages.define("shield.statusEotw", "Shields are down for the end of the world.");
    public static final String STATUS_NONE = Messages.define("shield.statusNone", "No shield window is set.");
    public static final String STATUS_RAIDABLE = Messages.define("shield.statusRaidable", "Shield is down because the faction is raidable.");
    public static final String STATUS_NO_HOME = Messages.define("shield.statusNoHome", "The shield window is open but covers no land until the faction sets a home.");
    public static final String STATUS_ACTIVE = Messages.define("shield.statusActive", "Shield active for {time}.");
    public static final String STATUS_STARTS = Messages.define("shield.statusStarts", "Shield starts at {hour}.");
    public static final String WHEN_SOON = Messages.define("shield.whenSoon", "within a minute");
    public static final String WHEN_IN = Messages.define("shield.whenIn", "in {time}");
    public static final String PENDING_REMOVE = Messages.define("shield.pendingRemove", "The shield window will be removed {when}.");
    public static final String PENDING_MOVE = Messages.define("shield.pendingMove", "The shield window moves to {hour} {when}.");
    public static final String GRACE_FUTURE = Messages.define("shield.graceFuture", "The grace end must be in the future.");
    public static final String GRACE_FOR = Messages.define("shield.graceFor", "Grace is active for {time}.");
    public static final String GRACE_UNTIL_ENDED = Messages.define("shield.graceUntilEnded", "Grace is active until an admin ends it.");
    public static final String GRACE_NOT_ACTIVE = Messages.define("shield.graceNotActive", "Grace is not active.");
    public static final String GRACE_ENDED = Messages.define("shield.graceEnded", "Grace has ended.");
    public static final String EOTW_ALREADY_ON = Messages.define("shield.eotwAlreadyOn", "End of the world is already on.");
    public static final String EOTW_ALREADY_OFF = Messages.define("shield.eotwAlreadyOff", "End of the world is already off.");
    public static final String EOTW_ON = Messages.define("shield.eotwOn", "End of the world is on. Shields and siege protections are down.");
    public static final String EOTW_OFF = Messages.define("shield.eotwOff", "End of the world is off.");
}
