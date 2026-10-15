package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Static help pages grouped by topic. */
final class HelpTopics {
    private HelpTopics() {}

    /** A topic holds message ids, read when the page is shown so a reload of messages.json applies. */
    private record Topic(String summary, String[][] lines) {}

    private static final Map<String, Topic> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("basics", new Topic(CommandText.HELP_STARTING_JOINING_AND_LEAVING, new String[][] {
                {"/f", CommandText.HELP_OPEN_THE_FACTION_PANEL},
                {"/f create <tag> [name]", CommandText.HELP_FOUND_A_FACTION},
                {"/f join <tag>", CommandText.HELP_JOIN_A_FACTION_THAT_INVITED_YOU},
                {"/f request <tag>", CommandText.HELP_ASK_TO_JOIN_A_FACTION},
                {"/f leave", CommandText.HELP_LEAVE_YOUR_FACTION},
                {"/f who [tag]", CommandText.HELP_SHOW_A_FACTION},
                {"/f list [page]", CommandText.HELP_LIST_FACTIONS},
                {"/f player [name]", CommandText.HELP_SHOW_A_PLAYER},
                {"/f tutorial", CommandText.HELP_OPEN_THE_TUTORIAL}}));
        TOPICS.put("members", new Topic(CommandText.HELP_RUNNING_YOUR_ROSTER, new String[][] {
                {"/f invite <player>", CommandText.HELP_INVITE_OR_ACCEPT_A_REQUEST},
                {"/f uninvite <player>", CommandText.HELP_WITHDRAW_AN_INVITE_OR_DECLINE_A},
                {"/f requests", CommandText.HELP_LIST_JOIN_REQUESTS},
                {"/f kick <player>", CommandText.HELP_REMOVE_A_MEMBER},
                {"/f ban <player>", CommandText.HELP_BAN_A_PLAYER},
                {"/f unban <player>", CommandText.HELP_LIFT_A_BAN},
                {"/f promote <player>", CommandText.HELP_RAISE_A_RANK},
                {"/f demote <player>", CommandText.HELP_LOWER_A_RANK},
                {"/f rank <player> <rank>", CommandText.HELP_SET_A_RANK},
                {"/f leader <player>", CommandText.HELP_HAND_OVER_LEADERSHIP},
                {"/f title <player> <title>", CommandText.HELP_GIVE_A_TITLE},
                {"/f rankname <rank> <name>", CommandText.HELP_RENAME_A_RANK_FOR_YOUR_FACTION},
                {"/f perms", CommandText.HELP_EDIT_PERMISSIONS},
                {"/f online", CommandText.HELP_LIST_ONLINE_MEMBERS},
                {"/f log [page]", CommandText.HELP_READ_THE_AUDIT_LOG}}));
        TOPICS.put("identity", new Topic(CommandText.HELP_TAG_NAME_AND_LOOK, new String[][] {
                {"/f tag <tag>", CommandText.HELP_CHANGE_THE_TAG},
                {"/f name <name>", CommandText.HELP_CHANGE_THE_DISPLAY_NAME},
                {"/f desc <text>", CommandText.HELP_SET_THE_DESCRIPTION},
                {"/f motd <text>", CommandText.HELP_SET_THE_LOGIN_MESSAGE},
                {"/f color <hex>", CommandText.HELP_SET_THE_COLOUR},
                {"/f open", CommandText.HELP_LET_ANYONE_JOIN},
                {"/f close", CommandText.HELP_REQUIRE_INVITES},
                {"/f settings", CommandText.HELP_OPEN_FACTION_SETTINGS},
                {"/f disband", CommandText.HELP_DISBAND_THE_FACTION}}));
        TOPICS.put("land", new Topic(CommandText.HELP_CLAIMING_AND_PROTECTING_LAND, new String[][] {
                {"/f claim", CommandText.HELP_CLAIM_THE_CHUNK_YOU_STAND_IN},
                {"/f claim auto", CommandText.HELP_CLAIM_AS_YOU_WALK},
                {"/f claim radius <n>", CommandText.HELP_CLAIM_A_SQUARE},
                {"/f claim line <n>", CommandText.HELP_CLAIM_A_LINE_AHEAD_OF_YOU},
                {"/f unclaim", CommandText.HELP_RELEASE_THIS_CHUNK},
                {"/f unclaim radius <n>", CommandText.HELP_RELEASE_A_SQUARE},
                {"/f unclaim all", CommandText.HELP_RELEASE_EVERYTHING},
                {"/f map [size]", CommandText.HELP_SHOW_NEARBY_LAND},
                {"/f map hide", CommandText.HELP_HIDE_YOURSELF_FROM_THE_WEB_MAP},
                {"/f map show", CommandText.HELP_SHOW_YOURSELF_ON_THE_WEB_MAP},
                {"/f power [player]", CommandText.HELP_SHOW_POWER}}));
        TOPICS.put("relations", new Topic(CommandText.HELP_ALLIES_TRUCES_AND_ENEMIES, new String[][] {
                {"/f ally <tag>", CommandText.HELP_ASK_FOR_AN_ALLIANCE},
                {"/f truce <tag>", CommandText.HELP_ASK_FOR_A_TRUCE},
                {"/f neutral <tag>", CommandText.HELP_GO_NEUTRAL},
                {"/f enemy <tag>", CommandText.HELP_DECLARE_AN_ENEMY},
                {"/f accept <tag>", CommandText.HELP_ACCEPT_A_REQUEST},
                {"/f decline <tag>", CommandText.HELP_DECLINE_A_REQUEST},
                {"/f relations", CommandText.HELP_LIST_RELATIONS}}));
        TOPICS.put("nation", new Topic(CommandText.HELP_NATIONS_OF_ALLIED_FACTIONS, new String[][] {
                {"/f nation", CommandText.HELP_SHOW_YOUR_NATION},
                {"/f nation info [tag]", CommandText.HELP_SHOW_A_NATION},
                {"/f nation create <tag> [name]", CommandText.HELP_FOUND_A_NATION_LED_BY_YOUR},
                {"/f nation invite <tag>", CommandText.HELP_INVITE_A_FACTION},
                {"/f nation join <tag>", CommandText.HELP_JOIN_A_NATION_THAT_INVITED_YOU},
                {"/f nation leave", CommandText.HELP_TAKE_YOUR_FACTION_OUT_OF_ITS},
                {"/f nation kick <tag>", CommandText.HELP_REMOVE_A_FACTION},
                {"/f nation leader <tag>", CommandText.HELP_HAND_THE_NATION_TO_ANOTHER_MEMBER},
                {"/f nation disband", CommandText.HELP_DISBAND_THE_NATION},
                {"/f chat nation", CommandText.HELP_TALK_TO_EVERY_FACTION_IN_YOUR}}));
        TOPICS.put("zones", new Topic(CommandText.HELP_ZONES_WITH_THEIR_OWN_PERMISSIONS, new String[][] {
                {"/f zone list", CommandText.HELP_LIST_YOUR_ZONES},
                {"/f zone create <name>", CommandText.HELP_CREATE_A_ZONE},
                {"/f zone delete <name>", CommandText.HELP_DELETE_A_ZONE},
                {"/f zone assign <name>", CommandText.HELP_PUT_THE_CHUNK_YOU_STAND_IN},
                {"/f zone clear", CommandText.HELP_TAKE_THE_CHUNK_YOU_STAND_IN},
                {"/f settings", CommandText.HELP_EDIT_OR_RESET_ZONE_PERMISSIONS_FROM}}));
        TOPICS.put("travel", new Topic(CommandText.HELP_HOME_WARPS_AND_ESCAPING, new String[][] {
                {"/f home", CommandText.HELP_TELEPORT_HOME},
                {"/f sethome", CommandText.HELP_SET_THE_HOME},
                {"/f delhome", CommandText.HELP_REMOVE_THE_HOME},
                {"/f allyhome <tag>", CommandText.HELP_TELEPORT_TO_AN_ALLY_HOME_THAT},
                {"/f warp <name> [password]", CommandText.HELP_USE_A_WARP},
                {"/f setwarp <name> [password]", CommandText.HELP_CREATE_A_WARP},
                {"/f delwarp <name>", CommandText.HELP_DELETE_A_WARP},
                {"/f warps", CommandText.HELP_LIST_WARPS},
                {"/f stuck", CommandText.HELP_ESCAPE_TO_THE_WILDERNESS},
                {"/f near [radius]", CommandText.HELP_LIST_NEARBY_PLAYERS},
                {"/f coords", CommandText.HELP_SHARE_YOUR_POSITION}}));
        TOPICS.put("chat", new Topic(CommandText.HELP_CHANNELS_AND_ANNOUNCEMENTS, new String[][] {
                {"/f chat [channel]", CommandText.HELP_SWITCH_CHANNEL_BETWEEN_PUBLIC_FACTION_ALLY},
                {"/f announce <text>", CommandText.HELP_POST_AN_ANNOUNCEMENT},
                {"/f alerts", CommandText.HELP_TOGGLE_ALERTS},
                {"/f hud", CommandText.HELP_TOGGLE_THE_SIDEBAR}}));
        TOPICS.put("economy", new Topic(CommandText.HELP_MONEY_AND_THE_FACTION_BANK, new String[][] {
                {"/f bank", CommandText.HELP_SHOW_THE_BANK},
                {"/f deposit <amount>", CommandText.HELP_DEPOSIT},
                {"/f withdraw <amount>", CommandText.HELP_WITHDRAW},
                {"/f pay <tag> <amount>", CommandText.HELP_PAY_A_FACTION},
                {"/balance [player]", CommandText.HELP_SHOW_A_BALANCE},
                {"/pay <player> <amount>", CommandText.HELP_PAY_A_PLAYER},
                {"/baltop", CommandText.HELP_RICHEST_PLAYERS},
                {"/f stats [tag]", CommandText.HELP_FACTION_STATISTICS}}));
        TOPICS.put("siege", new Topic(CommandText.HELP_SIEGES_RAIDS_AND_SHIELDS, new String[][] {
                {"/f siege", CommandText.HELP_PLANT_A_BANNER_ON_THE_ENEMY},
                {"/f siege cancel", CommandText.HELP_CALL_OFF_YOUR_SIEGE},
                {"/f sieges", CommandText.HELP_LIST_SIEGES_IN_PROGRESS},
                {"/f raids [tag]", CommandText.HELP_RAID_HISTORY_AND_RAID_POINTS},
                {"/f shield", CommandText.HELP_SHOW_YOUR_SHIELD_WINDOW},
                {"/f shield set <hour>", CommandText.HELP_MOVE_THE_DAILY_SHIELD_WINDOW},
                {"/f shield clear", CommandText.HELP_REMOVE_THE_DAILY_SHIELD_WINDOW}}));
        TOPICS.put("market", new Topic(CommandText.HELP_TRADING_AUCTIONS_AND_THE_MAILBOX, new String[][] {
                {"/f market", CommandText.HELP_OPEN_THE_MARKET},
                {"/f market sell <price> [amount]", CommandText.HELP_LIST_THE_STACK_IN_YOUR_HAND},
                {"/f market buy <id> [amount]", CommandText.HELP_BUY_A_LISTING},
                {"/f market cancel <id>", CommandText.HELP_TAKE_DOWN_YOUR_LISTING},
                {"/f market order <itemId> <price> <amount>", CommandText.HELP_PLACE_A_BUY_ORDER},
                {"/f market fulfil <id> [amount]", CommandText.HELP_SELL_INTO_A_BUY_ORDER},
                {"/f market mine", CommandText.HELP_LIST_YOUR_ENTRIES_WITH_IDS_SIX},
                {"/f market mailbox", CommandText.HELP_SEE_WHAT_IS_WAITING_FOR_YOU},
                {"/f market collect", CommandText.HELP_TAKE_EVERYTHING_FROM_THE_MAILBOX},
                {"/f auction sell <start> [buyout] [hours]", CommandText.HELP_AUCTION_THE_STACK_IN_YOUR_HAND},
                {"/f auction bid <id> <amount>", CommandText.HELP_BID_ON_AN_AUCTION},
                {"/f storefront", CommandText.HELP_SHOW_YOUR_FACTION_STOREFRONT},
                {"/f storefront pin <id>", CommandText.HELP_PIN_A_LISTING},
                {"/f storefront unpin <id>", CommandText.HELP_UNPIN_A_LISTING},
                {"/f shop [tag]", CommandText.HELP_OPEN_YOUR_FACTION_SHOP_OR_ANOTHER},
                {"/f shop manage", CommandText.HELP_STOCK_AND_PRICE_YOUR_FACTION_SHOP}}));
        TOPICS.put("progression", new Topic(CommandText.HELP_VALUE_UPGRADES_AND_MISSIONS, new String[][] {
                {"/f top", CommandText.HELP_FACTION_LEADERBOARD},
                {"/f value", CommandText.HELP_HOW_YOUR_VALUE_IS_WORKED_OUT},
                {"/f upgrades", CommandText.HELP_LIST_UPGRADES},
                {"/f upgrade <name>", CommandText.HELP_BUY_THE_NEXT_LEVEL},
                {"/f missions", CommandText.HELP_DAILY_AND_WEEKLY_MISSIONS},
                {"/f vault [page]", CommandText.HELP_OPEN_THE_FACTION_VAULT},
                {"/f stats [tag]", CommandText.HELP_FACTION_STATISTICS}}));
    }

    static void show(Call c, String topic) {
        if (topic == null) {
            c.send(Msg.info(Messages.get(CommandText.HELP_TOPICS)));
            for (Map.Entry<String, Topic> e : TOPICS.entrySet()) {
                c.send(Message.join(Msg.colored(" " + e.getKey(), Msg.GOLD), Msg.muted("  " + Messages.get(e.getValue().summary()))));
            }
            return;
        }
        Topic t = TOPICS.get(topic.toLowerCase(Locale.ROOT));
        if (t == null) {
            c.send(Msg.bad(Messages.get(CommandText.NO_HELP_TOPIC, "topic", topic)));
            return;
        }
        c.send(Msg.info(Messages.get(t.summary())));
        for (String[] line : t.lines()) {
            c.send(Message.join(Msg.colored(" " + line[0], Msg.GOLD), Msg.muted("  " + Messages.get(line[1]))));
        }
    }
}
