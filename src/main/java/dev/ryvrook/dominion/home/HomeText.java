package dev.ryvrook.dominion.home;

import dev.ryvrook.dominion.text.Messages;

/** Player facing sentences of the home package, faction home, warps, stuck and teleport warmups. */
public final class HomeText {
    private HomeText() {}

    public static final String POSITION_UNKNOWN = Messages.define("home.positionUnknown", "Your position is not known yet. Try again in a moment.");
    public static final String SERVER = Messages.define("home.server", "Server");

    // Faction home.
    public static final String NO_PERMISSION_SET_HOME = Messages.define("home.noPermissionSetHome", "You do not have permission to set the faction home.");
    public static final String HOME_OUTSIDE_LAND = Messages.define("home.homeOutsideLand", "The home must be inside your own territory.");
    public static final String AUDIT_HOME_SET = Messages.define("home.auditHomeSet", "Set the faction home at {coords}");
    public static final String HOME_SET = Messages.define("home.homeSet", "The faction home is set at {coords}.");
    public static final String NO_PERMISSION_CHANGE_HOME = Messages.define("home.noPermissionChangeHome", "You do not have permission to change the faction home.");
    public static final String NO_HOME_SET = Messages.define("home.noHomeSet", "Your faction has no home set.");
    public static final String AUDIT_HOME_REMOVED = Messages.define("home.auditHomeRemoved", "Removed the faction home");
    public static final String HOME_REMOVED = Messages.define("home.homeRemoved", "The faction home was removed.");
    public static final String NO_HOME = Messages.define("home.noHome", "Your faction has no home. Use /f sethome inside your land.");
    public static final String NO_PERMISSION_USE_HOME = Messages.define("home.noPermissionUseHome", "You do not have permission to use the faction home.");
    public static final String AUDIT_HOME_LOST = Messages.define("home.auditHomeLost", "The faction home was lost with its land");
    public static final String HOME_LOST = Messages.define("home.homeLost", "Your faction home was lost along with the land it stood on.");

    // Warps.
    public static final String NO_PERMISSION_CREATE_WARP = Messages.define("home.noPermissionCreateWarp", "You do not have permission to create warps.");
    public static final String WARP_NAME_RULES = Messages.define("home.warpNameRules", "Warp names use up to 16 letters, numbers, dashes or underscores.");
    public static final String WARP_LIMIT = Messages.define("home.warpLimit", "Your faction can hold {max} warps. Delete one or buy the warps upgrade.");
    public static final String WARP_OUTSIDE_LAND = Messages.define("home.warpOutsideLand", "Warps must be inside your own territory.");
    public static final String BANK_REASON_WARP = Messages.define("home.bankReasonWarp", "Warp {name}");
    public static final String WARP_COST = Messages.define("home.warpCost", "Setting a warp costs {cost} from the faction bank. {reason}");
    public static final String AUDIT_WARP_CREATED = Messages.define("home.auditWarpCreated", "Created warp {name}");
    public static final String AUDIT_WARP_CREATED_LOCKED = Messages.define("home.auditWarpCreatedLocked", "Created warp {name} with a password");
    public static final String AUDIT_WARP_MOVED = Messages.define("home.auditWarpMoved", "Moved warp {name}");
    public static final String AUDIT_WARP_MOVED_LOCKED = Messages.define("home.auditWarpMovedLocked", "Moved warp {name} with a password");
    public static final String WARP_CREATED = Messages.define("home.warpCreated", "Warp {name} created.");
    public static final String WARP_CREATED_LOCKED = Messages.define("home.warpCreatedLocked", "Warp {name} created with a password.");
    public static final String WARP_MOVED = Messages.define("home.warpMoved", "Warp {name} moved.");
    public static final String WARP_MOVED_LOCKED = Messages.define("home.warpMovedLocked", "Warp {name} moved with a password.");
    public static final String NO_PERMISSION_DELETE_WARP = Messages.define("home.noPermissionDeleteWarp", "You do not have permission to delete warps.");
    public static final String NO_SUCH_WARP = Messages.define("home.noSuchWarp", "Your faction has no warp called {name}.");
    public static final String AUDIT_WARP_DELETED = Messages.define("home.auditWarpDeleted", "Deleted warp {name}");
    public static final String WARP_DELETED = Messages.define("home.warpDeleted", "Warp {name} deleted.");
    public static final String NO_PERMISSION_USE_WARP = Messages.define("home.noPermissionUseWarp", "You do not have permission to use warps.");
    public static final String NO_SUCH_WARP_HINT = Messages.define("home.noSuchWarpHint", "Your faction has no warp called {name}. See /f warps.");
    public static final String WARP_LOCKED = Messages.define("home.warpLocked", "That warp is locked. Add the password after the name.");
    public static final String WARP_WRONG_PASSWORD = Messages.define("home.warpWrongPassword", "That password is wrong.");
    public static final String AUDIT_WARP_LOST = Messages.define("home.auditWarpLost", "Warp {name} was lost with its land");
    public static final String WARP_LOST = Messages.define("home.warpLost", "Warp {name} was lost along with the land it stood on.");

    // Stuck.
    public static final String STUCK_IN_COMBAT = Messages.define("home.stuckInCombat", "You cannot use stuck while in combat. Wait {time}.");
    public static final String ALREADY_WILDERNESS = Messages.define("home.alreadyWilderness", "You are already in the wilderness.");
    public static final String NO_WILDERNESS_NEAR = Messages.define("home.noWildernessNear", "There is no wilderness close enough to escape to.");

    // Teleports and warmups.
    public static final String LABEL_HOME = Messages.define("home.labelHome", "home");
    public static final String LABEL_WARP = Messages.define("home.labelWarp", "warp {name}");
    public static final String LABEL_WILDERNESS = Messages.define("home.labelWilderness", "the wilderness");
    public static final String LABEL_ALLY_HOME = Messages.define("home.labelAllyHome", "{faction} home");
    public static final String TELEPORT_IN_COMBAT = Messages.define("home.teleportInCombat", "You cannot teleport while in combat. Wait {time}.");
    public static final String TELEPORT_COOLDOWN = Messages.define("home.teleportCooldown", "You can teleport again in {time}.");
    public static final String TELEPORT_ENEMY_LAND = Messages.define("home.teleportEnemyLand", "You cannot teleport out of enemy territory.");
    public static final String TELEPORT_ENEMY_NEAR = Messages.define("home.teleportEnemyNear", "An enemy is within {radius} blocks, so you cannot teleport.");
    public static final String TELEPORTING_SECOND = Messages.define("home.teleportingSecond", "Teleporting to {destination} in {seconds} second. Do not move.");
    public static final String TELEPORTING_SECONDS = Messages.define("home.teleportingSeconds", "Teleporting to {destination} in {seconds} seconds. Do not move.");
    public static final String CANCELLED_MOVED = Messages.define("home.cancelledMoved", "Teleport cancelled because you moved.");
    public static final String CANCELLED_COMBAT = Messages.define("home.cancelledCombat", "Teleport cancelled because you are in combat.");
    public static final String COUNTDOWN = Messages.define("home.countdown", "Teleporting in {seconds}.");
    public static final String CANCELLED_DAMAGE = Messages.define("home.cancelledDamage", "Teleport cancelled because you took damage.");
    public static final String DESTINATION_GONE = Messages.define("home.destinationGone", "The destination no longer exists or is now locked.");
    public static final String DESTINATION_WORLD_UNLOADED = Messages.define("home.destinationWorldUnloaded", "The destination world is not loaded.");
    public static final String TELEPORTED = Messages.define("home.teleported", "Teleported to {destination}.");

    // Ally homes.
    public static final String NO_FACTION_CALLED = Messages.define("home.noFactionCalled", "There is no faction called {tag}.");
    public static final String ALLY_HOME_OWN = Messages.define("home.allyHomeOwn", "That is your own faction. Use /f home instead.");
    public static final String ALLY_HOME_NOT_ALLY = Messages.define("home.allyHomeNotAlly", "{faction} is not your ally.");
    public static final String ALLY_HOME_CLOSED = Messages.define("home.allyHomeClosed", "{faction} does not let allies use its home.");
    public static final String ALLY_HOME_NONE = Messages.define("home.allyHomeNone", "{faction} has no home set.");
}
