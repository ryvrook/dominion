package dev.ryvrook.dominion.relation;

import dev.ryvrook.dominion.text.Messages;

/** Player facing sentences of the relation package. */
public final class RelationText {
    private RelationText() {}

    // Parts that fill the sentences below.
    public static final String STATE_ALLY = Messages.define("relation.stateAlly", "allied with");
    public static final String STATE_TRUCE = Messages.define("relation.stateTruce", "in a truce with");
    public static final String STATE_ENEMY = Messages.define("relation.stateEnemy", "an enemy of");
    public static final String STATE_NEUTRAL = Messages.define("relation.stateNeutral", "neutral with");
    public static final String AN_ALLY = Messages.define("relation.anAlly", "an ally");
    public static final String A_TRUCE = Messages.define("relation.aTruce", "a truce");
    public static final String NOUN_ALLY = Messages.define("relation.nounAlly", "ally");
    public static final String NOUN_TRUCE = Messages.define("relation.nounTruce", "truce");
    public static final String YOUR_FACTION = Messages.define("relation.yourFaction", "Your faction");

    // Announcements and audit log lines.
    public static final String AUDIT_SET = Messages.define("relation.auditSet", "Set {faction} to {relation}");
    public static final String AUDIT_SET_BY = Messages.define("relation.auditSetBy", "{faction} set the relation to {relation}");
    public static final String NOW_OURS = Messages.define("relation.nowOurs", "Your faction is now {state} {faction}");
    public static final String NOW_THEIRS = Messages.define("relation.nowTheirs", "{faction} is now {state} your faction");
    public static final String AUDIT_ASKED = Messages.define("relation.auditAsked", "Asked {faction} for {request}");
    public static final String AUDIT_ASKED_BY = Messages.define("relation.auditAskedBy", "{faction} asked for {request}");
    public static final String ASKED_NOTICE = Messages.define("relation.askedNotice", "Your faction asked {faction} for {request}");
    public static final String WANTS_NOTICE = Messages.define("relation.wantsNotice", "{faction} wants {request}. Use /f relation accept {faction}");
    public static final String AUDIT_DECLINED = Messages.define("relation.auditDeclined", "Declined the {noun} request from {faction}");
    public static final String AUDIT_DECLINED_BY = Messages.define("relation.auditDeclinedBy", "{faction} declined the {noun} request");
    public static final String DECLINED_NOTICE = Messages.define("relation.declinedNotice", "Your faction declined the {noun} request from {faction}");
    public static final String DECLINED_YOURS = Messages.define("relation.declinedYours", "{faction} declined your {noun} request");
    public static final String AUDIT_EXPIRED_FROM = Messages.define("relation.auditExpiredFrom", "The {noun} request from {faction} expired");
    public static final String AUDIT_EXPIRED_TO = Messages.define("relation.auditExpiredTo", "The {noun} request to {faction} expired");

    // Command results.
    public static final String NOT_SETTABLE = Messages.define("relation.notSettable", "That is not a relation you can set");
    public static final String NOT_ASKED = Messages.define("relation.notAsked", "{faction} has not asked your faction for anything");
    public static final String REQUEST_EXPIRED = Messages.define("relation.requestExpired", "That request has expired");
    public static final String DECLINED = Messages.define("relation.declined", "Declined the request from {faction}");
    public static final String ALREADY_ENEMIES = Messages.define("relation.alreadyEnemies", "You are already enemies with {faction}");
    public static final String NOW_ENEMY = Messages.define("relation.nowEnemy", "{faction} is now your enemy");
    public static final String ALREADY_NEUTRAL = Messages.define("relation.alreadyNeutral", "You are already neutral with {faction}");
    public static final String WITHDREW = Messages.define("relation.withdrew", "Withdrew your request to {faction}");
    public static final String NOW_NEUTRAL = Messages.define("relation.nowNeutral", "You are now neutral with {faction}");
    public static final String ALREADY_ALLIED = Messages.define("relation.alreadyAllied", "You are already allied with {faction}");
    public static final String ALREADY_TRUCE = Messages.define("relation.alreadyTruce", "You are already in a truce with {faction}");
    public static final String ALREADY_ASKED = Messages.define("relation.alreadyAsked", "You already asked {faction} and are waiting for an answer");
    public static final String ASKED_HOUR = Messages.define("relation.askedHour", "Asked {faction}. The request lasts {hours} hour");
    public static final String ASKED_HOURS = Messages.define("relation.askedHours", "Asked {faction}. The request lasts {hours} hours");
    public static final String NOW_ALLIED = Messages.define("relation.nowAllied", "You are now allied with {faction}");
    public static final String NOW_TRUCE = Messages.define("relation.nowTruce", "You are now in a truce with {faction}");
    public static final String MAX_ALLY = Messages.define("relation.maxAlly", "{who} already has the maximum of {max} ally");
    public static final String MAX_ALLIES = Messages.define("relation.maxAllies", "{who} already has the maximum of {max} allies");
    public static final String MAX_TRUCE = Messages.define("relation.maxTruce", "{who} already has the maximum of {max} truce");
    public static final String MAX_TRUCES = Messages.define("relation.maxTruces", "{who} already has the maximum of {max} truces");
    public static final String RANK_DENIED = Messages.define("relation.rankDenied", "Your rank does not allow you to change relations");
    public static final String NO_FACTION_WITH_TAG = Messages.define("relation.noFactionWithTag", "No faction has that tag");
    public static final String OWN_FACTION = Messages.define("relation.ownFaction", "You cannot set a relation with your own faction");
    public static final String ZONES_NO_DIPLOMACY = Messages.define("relation.zonesNoDiplomacy", "Server zones do not take part in diplomacy");
    public static final String SAME_NATION = Messages.define("relation.sameNation", "You share a nation with {faction} so you are always allies");
}
