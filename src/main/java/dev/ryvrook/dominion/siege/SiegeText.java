package dev.ryvrook.dominion.siege;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of sieges, editable in messages.json. */
public final class SiegeText {
    private SiegeText() {}

    public static final String SERVER = Messages.define("siege.server", "Server");
    public static final String MEMBERS_ONE = Messages.define("siege.membersOne", "{count} member");
    public static final String MEMBERS_MANY = Messages.define("siege.membersMany", "{count} members");
    public static final String ATTACKERS_ONE = Messages.define("siege.attackersOne", "{count} attacker");
    public static final String ATTACKERS_MANY = Messages.define("siege.attackersMany", "{count} attackers");
    public static final String DEFENDERS_ONE = Messages.define("siege.defendersOne", "{count} defender");
    public static final String DEFENDERS_MANY = Messages.define("siege.defendersMany", "{count} defenders");
    public static final String HOURS_ONE = Messages.define("siege.hoursOne", "{count} hour");
    public static final String HOURS_MANY = Messages.define("siege.hoursMany", "{count} hours");

    public static final String SYSTEM_CANNOT_START = Messages.define("siege.systemCannotStart", "Server zones cannot start sieges.");
    public static final String RANK_CANNOT_START = Messages.define("siege.rankCannotStart", "Your rank cannot start sieges.");
    public static final String TURNED_OFF = Messages.define("siege.turnedOff", "Sieges are turned off on this server.");
    public static final String GRACE = Messages.define("siege.grace", "Sieges are paused during the grace period.");
    public static final String TOO_FEW_ATTACKERS = Messages.define("siege.tooFewAttackers", "Your faction needs {members} online to start a siege.");
    public static final String POSITION_UNKNOWN = Messages.define("siege.positionUnknown", "Your position is not known yet. Try again in a moment.");
    public static final String WRONG_GROUND = Messages.define("siege.wrongGround", "Stand in the wilderness or your own land next to enemy land to start a siege.");
    public static final String NO_ENEMY_LAND = Messages.define("siege.noEnemyLand", "There is no enemy land next to you. Stand beside a chunk owned by an enemy faction.");
    public static final String TOO_FEW_DEFENDERS = Messages.define("siege.tooFewDefenders", "{faction} needs {members} online before it can be sieged.");
    public static final String SHIELDED = Messages.define("siege.shielded", "That land of {faction} is under its shield right now.");
    public static final String IMMUNE = Messages.define("siege.immune", "{faction} held off a siege recently and is safe for {time}.");
    public static final String ALREADY_IN_SIEGE = Messages.define("siege.alreadyInSiege", "Your faction is already in a siege.");
    public static final String TARGET_IN_SIEGE = Messages.define("siege.targetInSiege", "{faction} is already in a siege.");
    public static final String COOLDOWN = Messages.define("siege.cooldown", "You can siege {faction} again in {time}.");
    public static final String AUDIT_STARTED = Messages.define("siege.auditStarted", "Started a siege against {faction} at chunk {chunk} in {world}");
    public static final String AUDIT_STARTED_AGAINST_US = Messages.define("siege.auditStartedAgainstUs", "{faction} started a siege at chunk {chunk} in {world}");
    public static final String BEGUN = Messages.define("siege.begun", "Your siege against {faction} has begun. Hold the banner for {time}.");
    public static final String NO_SUCH_SIEGE = Messages.define("siege.noSuchSiege", "No active siege has that id.");
    public static final String REASON_ADMIN = Messages.define("siege.reasonAdmin", "An admin ended it.");
    public static final String ALREADY_ENDED = Messages.define("siege.alreadyEnded", "That siege has already ended.");
    public static final String UNKNOWN_FACTION = Messages.define("siege.unknownFaction", "an unknown faction");
    public static final String AUDIT_ADMIN_ENDED_AGAINST = Messages.define("siege.auditAdminEndedAgainst", "An admin ended the siege against {faction}");
    public static final String AUDIT_ADMIN_ENDED_BY = Messages.define("siege.auditAdminEndedBy", "An admin ended the siege by {faction}");
    public static final String ENDED = Messages.define("siege.ended", "The siege was ended.");
    public static final String RANK_CANNOT_CANCEL = Messages.define("siege.rankCannotCancel", "Your rank cannot call off sieges.");
    public static final String NOT_RUNNING = Messages.define("siege.notRunning", "Your faction is not running a siege.");
    public static final String ALREADY_BREACHED = Messages.define("siege.alreadyBreached", "The walls are already breached so the siege cannot be called off.");
    public static final String THE_ATTACKERS = Messages.define("siege.theAttackers", "the attackers");
    public static final String THE_DEFENDERS = Messages.define("siege.theDefenders", "the defenders");
    public static final String REASON_CALLED_OFF = Messages.define("siege.reasonCalledOff", "The attackers called it off.");
    public static final String AUDIT_CALLED_OFF = Messages.define("siege.auditCalledOff", "Called off the siege against {faction}");
    public static final String CALLED_OFF = Messages.define("siege.calledOff", "You called off the siege against {faction}.");
    public static final String REASON_FACTION_GONE = Messages.define("siege.reasonFactionGone", "A faction in it no longer exists.");
    public static final String REASON_ABANDONED = Messages.define("siege.reasonAbandoned", "No attacker stayed at the banner.");
    public static final String CROWD = Messages.define("siege.crowd", "{attackers} and {defenders} are at the banner on chunk {chunk}.");
    public static final String STATUS_ATTACKERS = Messages.define("siege.statusAttackers", "Siege on {faction} continues. {crowd} The walls fall in {time}.");
    public static final String STATUS_ATTACKERS_NOW = Messages.define("siege.statusAttackersNow", "Siege on {faction} continues. {crowd} The walls fall as soon as one of you stands at the banner.");
    public static final String STATUS_DEFENDERS = Messages.define("siege.statusDefenders", "The siege by {faction} continues. {crowd} Your walls fall in {time} unless you clear the banner.");
    public static final String STATUS_DEFENDERS_NOW = Messages.define("siege.statusDefendersNow", "The siege by {faction} continues. {crowd} Your walls fall as soon as an attacker reaches the banner, so keep it clear.");
    public static final String AUDIT_BREACHED = Messages.define("siege.auditBreached", "Breached {faction} at chunk {chunk}");
    public static final String AUDIT_WE_WERE_BREACHED = Messages.define("siege.auditWeWereBreached", "{faction} breached the land at chunk {chunk}");
    public static final String AUDIT_HELD_OFF = Messages.define("siege.auditHeldOff", "Held off the siege by {faction}");
    public static final String AUDIT_THEY_HELD_OFF = Messages.define("siege.auditTheyHeldOff", "{faction} held off the siege");
    public static final String AUDIT_WON = Messages.define("siege.auditWon", "Won the siege against {faction} with {blocks} blocks broken and {containers} containers opened");
    public static final String AUDIT_LOST = Messages.define("siege.auditLost", "Lost the siege to {faction}");
    public static final String WHERE = Messages.define("siege.where", "chunk {chunk} in {world}");
    public static final String RAISED_ATTACKER = Messages.define("siege.raisedAttacker", "Your faction raised a siege banner against {faction} at {where}. Stay near the banner for {time} to breach their land.");
    public static final String RAISED_DEFENDER = Messages.define("siege.raisedDefender", "{faction} raised a siege banner against your land at {where}. Stand by the banner to break the siege.");
    public static final String BREACHED_ATTACKER = Messages.define("siege.breachedAttacker", "The land of {faction} is breached at {where}. You can raid it for {time}.");
    public static final String BREACHED_DEFENDER = Messages.define("siege.breachedDefender", "{faction} breached your land at {where}. The breach lasts {time}.");
    public static final String FAILED_ATTACKER = Messages.define("siege.failedAttacker", "Your siege against {faction} at {where} failed.{reason}");
    public static final String FAILED_DEFENDER = Messages.define("siege.failedDefender", "The siege by {faction} at {where} failed.{reason}");
    public static final String DEFENDED_ATTACKER = Messages.define("siege.defendedAttacker", "{faction} held off your siege at {where}.");
    public static final String DEFENDED_DEFENDER = Messages.define("siege.defendedDefender", "Your faction held off the siege by {faction} at {where}.");
    public static final String DEFENDED_DEFENDER_IMMUNE = Messages.define("siege.defendedDefenderImmune", "Your faction held off the siege by {faction} at {where}. You are safe from sieges for {hours}.");
    public static final String WON_ATTACKER = Messages.define("siege.wonAttacker", "The breach of {faction} at {where} has ended. The siege is won.");
    public static final String WON_DEFENDER = Messages.define("siege.wonDefender", "The breach by {faction} at {where} has ended.");
}
