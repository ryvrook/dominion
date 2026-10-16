package dev.ryvrook.dominion.chat;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the chat package, editable in messages.json. */
public final class ChatText {
    private ChatText() {}

    public static final String NOT_DELIVERED = Messages.define("chat.notDelivered", "Your message could not be delivered. Please try again.");
    public static final String LABEL_ALLY = Messages.define("chat.labelAlly", "Ally");
    public static final String LABEL_OFFICER = Messages.define("chat.labelOfficer", "Officer");
    public static final String LABEL_FACTION = Messages.define("chat.labelFaction", "Faction");
    public static final String LABEL_NATION = Messages.define("chat.labelNation", "Nation {tag}");
    public static final String CHANNELS = Messages.define("chat.channels", "Channels are public, faction, ally, nation and officer.");
    public static final String CHANNEL_NEEDS_FACTION = Messages.define("chat.channelNeedsFaction", "You need a faction to use {channel}.");
    public static final String OFFICER_CHAT_ONLY = Messages.define("chat.officerChatOnly", "Only officers and the leader can use officer chat.");
    public static final String NATIONS_OFF = Messages.define("chat.nationsOff", "Nations are not installed on this server.");
    public static final String NOT_IN_NATION = Messages.define("chat.notInNation", "Your faction is not in a nation.");
    public static final String CHANNEL_SET = Messages.define("chat.channelSet", "You are now talking in {channel}.");
    public static final String CHANNEL_PUBLIC = Messages.define("chat.channelPublic", "public chat");
    public static final String CHANNEL_FACTION = Messages.define("chat.channelFaction", "faction chat");
    public static final String CHANNEL_ALLY = Messages.define("chat.channelAlly", "ally chat");
    public static final String CHANNEL_NATION = Messages.define("chat.channelNation", "nation chat");
    public static final String CHANNEL_OFFICER = Messages.define("chat.channelOfficer", "officer chat");
    public static final String ANNOUNCE_NO_PERMISSION = Messages.define("chat.announceNoPermission", "You do not have permission to post announcements.");
    public static final String ANNOUNCEMENT_EMPTY = Messages.define("chat.announcementEmpty", "The announcement is empty.");
    public static final String LINE_EMPTY = Messages.define("chat.lineEmpty", "There is nothing to send.");
    public static final String AN_OFFICER = Messages.define("chat.anOfficer", "An officer");
    public static final String AUDIT_ANNOUNCEMENT = Messages.define("chat.auditAnnouncement", "Posted an announcement");
    public static final String ANNOUNCEMENT_FROM = Messages.define("chat.announcementFrom", "{tag} announcement from {author}");
    public static final String ANNOUNCEMENT_POSTED = Messages.define("chat.announcementPosted", "Your announcement was posted and is now the message of the day.");
    public static final String MOTD_TITLE = Messages.define("chat.motdTitle", "{tag} message of the day");
    public static final String RENAMED = Messages.define("chat.renamed", "The faction {before} is now {after}.");
}
