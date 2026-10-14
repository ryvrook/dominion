package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.text.Msg;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Static help pages grouped by topic. */
final class HelpTopics {
    private HelpTopics() {}

    private record Topic(String summary, String[][] lines) {}

    private static final Map<String, Topic> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("basics", new Topic("Starting, joining and leaving", new String[][] {
                {"/f", "Open the faction panel"},
                {"/f create <tag> [name]", "Found a faction"},
                {"/f join <tag>", "Join a faction that invited you or is open"},
                {"/f request <tag>", "Ask to join a faction"},
                {"/f leave", "Leave your faction"},
                {"/f who [tag]", "Show a faction"},
                {"/f list [page]", "List factions"},
                {"/f player [name]", "Show a player"},
                {"/f tutorial", "Open the tutorial"}}));
        TOPICS.put("members", new Topic("Running your roster", new String[][] {
                {"/f invite <player>", "Invite or accept a request"},
                {"/f uninvite <player>", "Withdraw an invite or decline a request"},
                {"/f requests", "List join requests"},
                {"/f kick <player>", "Remove a member"},
                {"/f ban <player>", "Ban a player"},
                {"/f unban <player>", "Lift a ban"},
                {"/f promote <player>", "Raise a rank"},
                {"/f demote <player>", "Lower a rank"},
                {"/f rank <player> <rank>", "Set a rank"},
                {"/f leader <player>", "Hand over leadership"},
                {"/f title <player> <title>", "Give a title"},
                {"/f rankname <rank> <name>", "Rename a rank for your faction, or type reset"},
                {"/f perms", "Edit permissions"},
                {"/f online", "List online members"},
                {"/f log [page]", "Read the audit log"}}));
        TOPICS.put("identity", new Topic("Tag, name and look", new String[][] {
                {"/f tag <tag>", "Change the tag"},
                {"/f name <name>", "Change the display name"},
                {"/f desc <text>", "Set the description"},
                {"/f motd <text>", "Set the login message"},
                {"/f color <hex>", "Set the colour"},
                {"/f open", "Let anyone join"},
                {"/f close", "Require invites"},
                {"/f settings", "Open faction settings"},
                {"/f disband", "Disband the faction"}}));
        TOPICS.put("land", new Topic("Claiming and protecting land", new String[][] {
                {"/f claim", "Claim the chunk you stand in"},
                {"/f claim auto", "Claim as you walk"},
                {"/f claim radius <n>", "Claim a square"},
                {"/f claim line <n>", "Claim a line ahead of you"},
                {"/f unclaim", "Release this chunk"},
                {"/f unclaim radius <n>", "Release a square"},
                {"/f unclaim all", "Release everything"},
                {"/f map [size]", "Show nearby land"},
                {"/f map hide", "Hide yourself from the web map"},
                {"/f map show", "Show yourself on the web map again"},
                {"/f power [player]", "Show power"}}));
        TOPICS.put("relations", new Topic("Allies, truces and enemies", new String[][] {
                {"/f ally <tag>", "Ask for an alliance"},
                {"/f truce <tag>", "Ask for a truce"},
                {"/f neutral <tag>", "Go neutral"},
                {"/f enemy <tag>", "Declare an enemy"},
                {"/f accept <tag>", "Accept a request"},
                {"/f decline <tag>", "Decline a request"},
                {"/f relations", "List relations"}}));
        TOPICS.put("nation", new Topic("Nations of allied factions", new String[][] {
                {"/f nation", "Show your nation"},
                {"/f nation info [tag]", "Show a nation"},
                {"/f nation create <tag> [name]", "Found a nation led by your faction"},
                {"/f nation invite <tag>", "Invite a faction"},
                {"/f nation join <tag>", "Join a nation that invited you"},
                {"/f nation leave", "Take your faction out of its nation"},
                {"/f nation kick <tag>", "Remove a faction"},
                {"/f nation leader <tag>", "Hand the nation to another member faction"},
                {"/f nation disband", "Disband the nation"},
                {"/f chat nation", "Talk to every faction in your nation. Start a line with the nation prefix, # by default, to send just that line"}}));
        TOPICS.put("zones", new Topic("Zones with their own permissions", new String[][] {
                {"/f zone list", "List your zones"},
                {"/f zone create <name>", "Create a zone"},
                {"/f zone delete <name>", "Delete a zone"},
                {"/f zone assign <name>", "Put the chunk you stand in into a zone"},
                {"/f zone clear", "Take the chunk you stand in out of its zone"},
                {"/f settings", "Edit or reset zone permissions from the zone list on the settings tab"}}));
        TOPICS.put("travel", new Topic("Home, warps and escaping", new String[][] {
                {"/f home", "Teleport home"},
                {"/f sethome", "Set the home"},
                {"/f delhome", "Remove the home"},
                {"/f allyhome <tag>", "Teleport to an ally home that is open to you"},
                {"/f warp <name> [password]", "Use a warp"},
                {"/f setwarp <name> [password]", "Create a warp"},
                {"/f delwarp <name>", "Delete a warp"},
                {"/f warps", "List warps"},
                {"/f stuck", "Escape to the wilderness"},
                {"/f near [radius]", "List nearby players"},
                {"/f coords", "Share your position"}}));
        TOPICS.put("chat", new Topic("Channels and announcements", new String[][] {
                {"/f chat [channel]", "Switch channel between public, faction, ally, officer and nation"},
                {"/f announce <text>", "Post an announcement"},
                {"/f alerts", "Toggle alerts"},
                {"/f hud", "Toggle the sidebar"}}));
        TOPICS.put("economy", new Topic("Money and the faction bank", new String[][] {
                {"/f bank", "Show the bank"},
                {"/f deposit <amount>", "Deposit"},
                {"/f withdraw <amount>", "Withdraw"},
                {"/f pay <tag> <amount>", "Pay a faction"},
                {"/balance [player]", "Show a balance"},
                {"/pay <player> <amount>", "Pay a player"},
                {"/baltop", "Richest players"},
                {"/f stats [tag]", "Faction statistics"}}));
        TOPICS.put("siege", new Topic("Sieges, raids and shields", new String[][] {
                {"/f siege", "Plant a banner on the enemy chunk next to you"},
                {"/f siege cancel", "Call off your siege"},
                {"/f sieges", "List sieges in progress"},
                {"/f raids [tag]", "Raid history and raid points"},
                {"/f shield", "Show your shield window"},
                {"/f shield set <hour>", "Move the daily shield window"},
                {"/f shield clear", "Remove the daily shield window"}}));
        TOPICS.put("market", new Topic("Trading, auctions and the mailbox", new String[][] {
                {"/f market", "Open the market"},
                {"/f market sell <price> [amount]", "List the stack in your hand"},
                {"/f market buy <id> [amount]", "Buy a listing"},
                {"/f market cancel <id>", "Take down your listing"},
                {"/f market order <itemId> <price> <amount>", "Place a buy order"},
                {"/f market fulfil <id> [amount]", "Sell into a buy order"},
                {"/f market mine", "List your entries with ids. Six characters of an id are enough"},
                {"/f market mailbox", "See what is waiting for you"},
                {"/f market collect", "Take everything from the mailbox"},
                {"/f auction sell <start> [buyout] [hours]", "Auction the stack in your hand for 1, 6, 12, 24 or 48 hours"},
                {"/f auction bid <id> <amount>", "Bid on an auction"},
                {"/f storefront", "Show your faction storefront"},
                {"/f storefront pin <id>", "Pin a listing"},
                {"/f storefront unpin <id>", "Unpin a listing"},
                {"/f shop [tag]", "Open your faction shop or another faction's"},
                {"/f shop manage", "Stock and price your faction shop"}}));
        TOPICS.put("progression", new Topic("Value, upgrades and missions", new String[][] {
                {"/f top", "Faction leaderboard"},
                {"/f value", "How your value is worked out"},
                {"/f upgrades", "List upgrades"},
                {"/f upgrade <name>", "Buy the next level"},
                {"/f missions", "Daily and weekly missions"},
                {"/f vault [page]", "Open the faction vault"},
                {"/f stats [tag]", "Faction statistics"}}));
    }

    static void show(Call c, String topic) {
        if (topic == null) {
            c.send(Msg.info("Help topics. Type /f help with a topic name."));
            for (Map.Entry<String, Topic> e : TOPICS.entrySet()) {
                c.send(Message.join(Msg.colored(" " + e.getKey(), Msg.GOLD), Msg.muted("  " + e.getValue().summary())));
            }
            return;
        }
        Topic t = TOPICS.get(topic.toLowerCase(Locale.ROOT));
        if (t == null) {
            c.send(Msg.bad("There is no help topic called " + topic + ". Try /f help."));
            return;
        }
        c.send(Msg.info(t.summary()));
        for (String[] line : t.lines()) {
            c.send(Message.join(Msg.colored(" " + line[0], Msg.GOLD), Msg.muted("  " + line[1])));
        }
    }
}
