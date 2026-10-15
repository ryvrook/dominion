package dev.ryvrook.dominion.nation;

import dev.ryvrook.dominion.text.Messages;

/** Player facing sentences of the nation package. */
public final class NationText {
    private NationText() {}

    public static final String DISABLED = Messages.define("nation.disabled", "Nations are turned off on this server");
    public static final String NO_FACTION_WITH_TAG = Messages.define("nation.noFactionWithTag", "No faction has that tag");
    public static final String NO_NATION_WITH_TAG = Messages.define("nation.noNationWithTag", "No nation has that tag");
    public static final String NATION_GONE = Messages.define("nation.nationGone", "That nation no longer exists");
    public static final String ALREADY_IN_NATION = Messages.define("nation.alreadyInNation", "Your faction is already in a nation");
    public static final String NOT_IN_NATION = Messages.define("nation.notInNation", "Your faction is not in a nation");
    public static final String FULL = Messages.define("nation.full", "{nation} already has the most factions allowed");
    public static final String TARGET_NOT_MEMBER = Messages.define("nation.targetNotMember", "{faction} is not in your nation");

    // Founding and tags.
    public static final String FOUND_LEADER_ONLY = Messages.define("nation.foundLeaderOnly", "Only a faction leader can found a nation");
    public static final String NAME_TOO_LONG = Messages.define("nation.nameTooLong", "Nation names can be at most {max} characters long");
    public static final String NAME_SPECIAL = Messages.define("nation.nameSpecial", "Nation names cannot contain special characters");
    public static final String TAG_TAKEN = Messages.define("nation.tagTaken", "A nation with that tag already exists");
    public static final String AUDIT_FOUNDED = Messages.define("nation.auditFounded", "Founded the nation {nation}");
    public static final String FOUNDED = Messages.define("nation.founded", "Founded the nation {nation}");
    public static final String TAG_MISSING = Messages.define("nation.tagMissing", "Please choose a nation tag");
    public static final String TAG_TOO_SHORT = Messages.define("nation.tagTooShort", "Nation tags must be at least {min} characters long");
    public static final String TAG_TOO_LONG = Messages.define("nation.tagTooLong", "Nation tags can be at most {max} characters long");
    public static final String TAG_CHARACTERS = Messages.define("nation.tagCharacters", "Nation tags may only use letters and digits");

    // Invites and joining.
    public static final String INVITE_LEADER_ONLY = Messages.define("nation.inviteLeaderOnly", "Only the leader of the leading faction can invite to a nation");
    public static final String ALREADY_IN_YOURS = Messages.define("nation.alreadyInYours", "{faction} is already in your nation");
    public static final String ALREADY_IN_ANOTHER = Messages.define("nation.alreadyInAnother", "{faction} is already in another nation");
    public static final String ALREADY_INVITED = Messages.define("nation.alreadyInvited", "{faction} already has an invite");
    public static final String AUDIT_INVITED = Messages.define("nation.auditInvited", "Invited {faction} to the nation {nation}");
    public static final String INVITE_NOTICE = Messages.define("nation.inviteNotice", "{faction} invited your faction to the nation {nation}. Use the nation join command with the tag {nation} to accept");
    public static final String INVITED = Messages.define("nation.invited", "Invited {faction} to {nation}");
    public static final String JOIN_LEADER_ONLY = Messages.define("nation.joinLeaderOnly", "Only a faction leader can join a nation");
    public static final String NOT_INVITED = Messages.define("nation.notInvited", "{nation} has not invited your faction");
    public static final String AUDIT_JOINED = Messages.define("nation.auditJoined", "Joined the nation {nation}");
    public static final String JOINED_NOTICE = Messages.define("nation.joinedNotice", "{faction} joined the nation {nation}");
    public static final String JOINED = Messages.define("nation.joined", "Your faction joined {nation}");

    // Leaving and removal.
    public static final String LEAVE_LEADER_ONLY = Messages.define("nation.leaveLeaderOnly", "Only a faction leader can leave a nation");
    public static final String LEAVE_HAND_OVER = Messages.define("nation.leaveHandOver", "Hand the nation to another faction first, or disband it");
    public static final String LEFT_NOTICE = Messages.define("nation.leftNotice", "{faction} left the nation {nation}");
    public static final String AUDIT_LEFT = Messages.define("nation.auditLeft", "Left the nation {nation}");
    public static final String LEFT = Messages.define("nation.left", "Your faction left {nation}");
    public static final String DISBANDED_MEMBER_LEFT = Messages.define("nation.disbandedMemberLeft", "{faction} was disbanded and left the nation {nation}");
    public static final String KICK_LEADER_ONLY = Messages.define("nation.kickLeaderOnly", "Only the leader of the leading faction can remove factions from a nation");
    public static final String KICK_OWN = Messages.define("nation.kickOwn", "You cannot remove your own faction. Disband the nation instead");
    public static final String AUDIT_INVITE_WITHDRAWN = Messages.define("nation.auditInviteWithdrawn", "Withdrew the nation invite for {faction}");
    public static final String INVITE_WITHDRAWN = Messages.define("nation.inviteWithdrawn", "Withdrew the invite for {faction}");
    public static final String REMOVED_NOTICE = Messages.define("nation.removedNotice", "{faction} was removed from the nation {nation}");
    public static final String AUDIT_REMOVED = Messages.define("nation.auditRemoved", "Removed {faction} from the nation {nation}");
    public static final String AUDIT_WAS_REMOVED = Messages.define("nation.auditWasRemoved", "Was removed from the nation {nation} by {faction}");
    public static final String WAS_REMOVED_NOTICE = Messages.define("nation.wasRemovedNotice", "Your faction was removed from the nation {nation}");
    public static final String REMOVED = Messages.define("nation.removed", "Removed {faction} from {nation}");

    // Disbanding and leadership.
    public static final String DISBAND_LEADER_ONLY = Messages.define("nation.disbandLeaderOnly", "Only the leader of the leading faction can disband a nation");
    public static final String DISBAND_CONFIRM = Messages.define("nation.disbandConfirm", "Please confirm that you want to disband the nation {nation}");
    public static final String AUDIT_DISBANDED = Messages.define("nation.auditDisbanded", "The nation {nation} was disbanded");
    public static final String DISBANDED_NOTICE = Messages.define("nation.disbandedNotice", "The nation {nation} was disbanded");
    public static final String DISBANDED = Messages.define("nation.disbanded", "Disbanded the nation {nation}");
    public static final String HANDOVER_LEADER_ONLY = Messages.define("nation.handoverLeaderOnly", "Only the leader of the leading faction can hand over the nation");
    public static final String ALREADY_LEADS = Messages.define("nation.alreadyLeads", "Your faction already leads {nation}");
    public static final String AUDIT_HANDED = Messages.define("nation.auditHanded", "Handed the nation {nation} to {faction}");
    public static final String AUDIT_NOW_LEADS = Messages.define("nation.auditNowLeads", "Now leads the nation {nation}");
    public static final String NOW_LEADS_NOTICE = Messages.define("nation.nowLeadsNotice", "{faction} now leads the nation {nation}");
    public static final String NOW_LEADS = Messages.define("nation.nowLeads", "{faction} now leads {nation}");
    public static final String AUDIT_NOW_LEADS_AFTER = Messages.define("nation.auditNowLeadsAfter", "Now leads the nation {nation} after {reason}");
}
