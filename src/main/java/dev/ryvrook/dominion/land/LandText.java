package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the land package. */
public final class LandText {
    private LandText() {}

    // Claim checks
    public static final String CLAIM_NEED_FACTION = Messages.define("land.claimNeedFaction", "You need to be in a faction to claim land.");
    public static final String CLAIM_RANK = Messages.define("land.claimRank", "Your rank cannot claim land.");
    public static final String CLAIM_IN_COMBAT = Messages.define("land.claimInCombat", "You cannot claim land while in combat.");
    public static final String CLAIM_WORLD_BLOCKED = Messages.define("land.claimWorldBlocked", "Land cannot be claimed in this world.");
    public static final String CLAIM_NEAR_SPAWN = Messages.define("land.claimNearSpawn", "This land is too close to spawn to claim.");
    public static final String CLAIM_SERVER_LAND = Messages.define("land.claimServerLand", "This land belongs to the server.");
    public static final String CLAIM_ALREADY_OWN = Messages.define("land.claimAlreadyOwn", "Your faction already owns this land.");
    public static final String CLAIM_NOT_CONNECTED = Messages.define("land.claimNotConnected", "New claims must touch land your faction already owns.");
    public static final String CLAIM_BUFFER = Messages.define("land.claimBuffer", "This land is too close to {faction}.");
    public static final String CLAIM_NO_POWER = Messages.define("land.claimNoPower", "Your faction has no claim power left. It holds {count} of {max} claims.");
    public static final String CLAIM_TOO_EXPENSIVE = Messages.define("land.claimTooExpensive", "This claim costs {cost} and the faction bank only holds {bank}.");
    public static final String CLAIM_NOT_ENEMY = Messages.define("land.claimNotEnemy", "{faction} owns this land. Only enemy land can be taken.");
    public static final String CLAIM_EDGE_ONLY = Messages.define("land.claimEdgeOnly", "You can only take chunks at the edge of {faction} land.");
    public static final String BANK_CLAIM_REASON = Messages.define("land.bankClaimReason", "a land claim by {name}");
    public static final String BANK_UNCLAIM_REASON = Messages.define("land.bankUnclaimReason", "an unclaim refund for {name}");
    public static final String THE_SERVER = Messages.define("land.theServer", "the server");

    // Claim results
    public static final String AUDIT_CLAIMED = Messages.define("land.auditClaimed", "Claimed chunk {chunk} in {world}");
    public static final String AUDIT_TOOK = Messages.define("land.auditTook", "Took chunk {chunk} in {world} from {faction}");
    public static final String AUDIT_TAKEN_BY = Messages.define("land.auditTakenBy", "{faction} took chunk {chunk} in {world}");
    public static final String BANK_PAID = Messages.define("land.bankPaid", "The faction bank paid {amount}.");
    public static final String TOOK = Messages.define("land.took", "Took chunk {chunk} from {faction}.");
    public static final String CLAIMED = Messages.define("land.claimed", "Claimed chunk {chunk} for {faction}.");
    public static final String RADIUS_NEGATIVE = Messages.define("land.radiusNegative", "The radius cannot be negative.");
    public static final String CLAIM_RADIUS_MAX = Messages.define("land.claimRadiusMax", "The largest radius you can claim is {max}.");
    public static final String LINE_EMPTY = Messages.define("land.lineEmpty", "A line needs at least one chunk.");
    public static final String LINE_MAX = Messages.define("land.lineMax", "The longest line you can claim is {max} chunks.");
    public static final String LINE_DIRECTION = Messages.define("land.lineDirection", "Pick a direction for the line.");
    public static final String AREA_ALREADY_OWN = Messages.define("land.areaAlreadyOwn", "Your faction already owns all of that land.");
    public static final String AREA_NONE_CLAIMED = Messages.define("land.areaNoneClaimed", "No land could be claimed.");
    public static final String AREA_CLAIMED = Messages.define("land.areaClaimed", "Claimed {claimed} of {total} chunks.");

    // Unclaim
    public static final String UNCLAIM_NOT_OWN = Messages.define("land.unclaimNotOwn", "Your faction does not own this land.");
    public static final String RELEASED = Messages.define("land.released", "Released chunk {chunk}.");
    public static final String UNCLAIM_RADIUS_MAX = Messages.define("land.unclaimRadiusMax", "The largest radius you can unclaim is {max}.");
    public static final String UNCLAIM_AREA_NONE = Messages.define("land.unclaimAreaNone", "Your faction does not own any land in that area.");
    public static final String RELEASED_MANY = Messages.define("land.releasedMany", "Released {count} chunks.");
    public static final String UNCLAIM_ALL_RANK = Messages.define("land.unclaimAllRank", "Your rank cannot release all of your land.");
    public static final String UNCLAIM_IN_COMBAT = Messages.define("land.unclaimInCombat", "You cannot unclaim land while in combat.");
    public static final String UNCLAIM_NO_LAND = Messages.define("land.unclaimNoLand", "Your faction does not own any land.");
    public static final String UNCLAIM_ALL_CONFIRM = Messages.define("land.unclaimAllConfirm", "This releases all {count} chunks your faction owns. Confirm to continue.");
    public static final String RELEASED_ALL = Messages.define("land.releasedAll", "Released all {count} chunks.");
    public static final String UNCLAIM_RANK = Messages.define("land.unclaimRank", "Your rank cannot unclaim land.");
    public static final String BANK_REFUND = Messages.define("land.bankRefund", "The faction bank got {amount} back.");
    public static final String AUDIT_RELEASED = Messages.define("land.auditReleased", "Released chunk {chunk} in {world}");
    public static final String AUDIT_DECAYED = Messages.define("land.auditDecayed", "Lost chunk {chunk} in {world} after {days} days with no member online");

    // Admin and zones
    public static final String ADMIN_ALREADY_OWNS = Messages.define("land.adminAlreadyOwns", "{faction} already owns this land.");
    public static final String AUDIT_ADMIN_GAVE_US = Messages.define("land.auditAdminGaveUs", "An admin gave chunk {chunk} in {world} to the faction");
    public static final String AUDIT_ADMIN_GAVE_OTHER = Messages.define("land.auditAdminGaveOther", "An admin gave chunk {chunk} in {world} to {faction}");
    public static final String ADMIN_GAVE = Messages.define("land.adminGave", "Chunk {chunk} now belongs to {faction}.");
    public static final String ADMIN_NOBODY_OWNS = Messages.define("land.adminNobodyOwns", "Nobody owns this land.");
    public static final String AUDIT_ADMIN_RELEASED = Messages.define("land.auditAdminReleased", "An admin released chunk {chunk} in {world}");
    public static final String ADMIN_RELEASED = Messages.define("land.adminReleased", "Released chunk {chunk} from {faction}.");
    public static final String ZONE_TYPE = Messages.define("land.zoneType", "A zone must be a SafeZone or a WarZone.");
    public static final String ZONE_NAME = Messages.define("land.zoneName", "Give the zone a name with no spaces.");
    public static final String ZONE_TAKEN = Messages.define("land.zoneTaken", "A faction or zone named {name} already exists.");
    public static final String AUDIT_SAFEZONE_CREATED = Messages.define("land.auditSafezoneCreated", "Created the SafeZone {name}");
    public static final String AUDIT_WARZONE_CREATED = Messages.define("land.auditWarzoneCreated", "Created the WarZone {name}");
    public static final String SAFEZONE_CREATED = Messages.define("land.safezoneCreated", "Created the SafeZone {name}.");
    public static final String WARZONE_CREATED = Messages.define("land.warzoneCreated", "Created the WarZone {name}.");
    public static final String SAFEZONE_CREATED_CLAIMED = Messages.define("land.safezoneCreatedClaimed", "Created the SafeZone {name} and claimed your chunk.");
    public static final String WARZONE_CREATED_CLAIMED = Messages.define("land.warzoneCreatedClaimed", "Created the WarZone {name} and claimed your chunk.");

    // Raids
    public static final String RAID_NO_FACTION = Messages.define("land.raidNoFaction", "There is no faction to raid here.");
    public static final String RAID_OWN = Messages.define("land.raidOwn", "You cannot raid your own faction.");
    public static final String RAID_HOME_LAST = Messages.define("land.raidHomeLast", "The home chunk of {faction} can only be taken last.");
    public static final String RAID_SHIELD = Messages.define("land.raidShield", "The shield of {faction} protects this land right now.");
    public static final String RAID_OFF = Messages.define("land.raidOff", "Raiding is turned off on this server.");
    public static final String RAID_GRACE = Messages.define("land.raidGrace", "Raiding is paused during the grace period.");
    public static final String RAID_SERVER_ZONE = Messages.define("land.raidServerZone", "Server zones cannot be raided.");
    public static final String RAID_MIN_ONLINE = Messages.define("land.raidMinOnline", "{faction} needs {min} members online before it can be raided.");
    public static final String RAID_NOT_RAIDABLE = Messages.define("land.raidNotRaidable", "{faction} is not raidable right now.");
    public static final String RAID_MARGIN = Messages.define("land.raidMargin", "Your faction needs {margin} percent more power than {faction} to take its land.");
    public static final String RAID_RATIO = Messages.define("land.raidRatio", "Your power ratio must be higher than the ratio of {faction} to take its land.");
    public static final String RAID_COOLDOWN = Messages.define("land.raidCooldown", "You can take land from {faction} again in {time}.");

    // Map, territory banner and safe zone entry
    public static final String WILDERNESS = Messages.define("land.wilderness", "Wilderness");
    public static final String LEGEND_YOU = Messages.define("land.legendYou", "You");
    public static final String LEGEND_SAFE_ZONE = Messages.define("land.legendSafeZone", "Safe zone");
    public static final String LEGEND_WAR_ZONE = Messages.define("land.legendWarZone", "War zone");
    public static final String LEGEND_NORTH = Messages.define("land.legendNorth", "North is up");
    public static final String SAFEZONE_COMBAT = Messages.define("land.safezoneCombat", "You cannot enter a safe zone while in combat.");
    public static final String BANNER_UNCLAIMED = Messages.define("land.bannerUnclaimed", "Unclaimed land");
    public static final String FLAG_SAFEZONE = Messages.define("land.flagSafezone", "SafeZone");
    public static final String FLAG_WARZONE = Messages.define("land.flagWarzone", "WarZone");
    public static final String FLAG_RAIDABLE = Messages.define("land.flagRaidable", "Raidable");
    public static final String FLAG_BREACHED = Messages.define("land.flagBreached", "Breached");
}
