package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandTextTest {
    @Test
    void everyIdIsDefinedOnceWithThePlaceholdersTheCodeFills() throws Exception {
        TextClassCheck.check(CommandText.class, "command", Map.ofEntries(
                Map.entry(CommandText.NO_PLAYER_NAMED, Set.of("name")),
                Map.entry(CommandText.NO_FACTION_WITH_TAG, Set.of("tag")),
                Map.entry(CommandText.NO_HELP_TOPIC, Set.of("topic")),
                Map.entry(CommandText.ID_TOO_SHORT, Set.of("min", "noun")),
                Map.entry(CommandText.ID_NO_MATCH, Set.of("noun", "typed")),
                Map.entry(CommandText.ID_AMBIGUOUS, Set.of("count", "noun", "typed")),
                Map.entry(CommandText.RELATIONS_OF, Set.of("tag")),
                Map.entry(CommandText.RELATION_REQUEST_INCOMING, Set.of("relation", "tag")),
                Map.entry(CommandText.RELATION_REQUEST_OUTGOING, Set.of("relation", "tag", "time")),
                Map.entry(CommandText.BANK_BALANCE, Set.of("amount", "tag")),
                Map.entry(CommandText.YOUR_BALANCE, Set.of("amount")),
                Map.entry(CommandText.PLAYER_BALANCE, Set.of("amount", "name")),
                Map.entry(CommandText.WARPS_HEADER, Set.of("count", "tag")),
                Map.entry(CommandText.PLAYER_NOT_IN_A_FACTION, Set.of("name")),
                Map.entry(CommandText.NO_FACTION_OR_PLAYER, Set.of("text")),
                Map.entry(CommandText.MEMBERS_ONLINE, Set.of("members", "online", "tag")),
                Map.entry(CommandText.ROSTER_ONLINE, Set.of("count")),
                Map.entry(CommandText.ROSTER_OFFLINE, Set.of("count")),
                Map.entry(CommandText.AGE_DAY, Set.of("n")),
                Map.entry(CommandText.AGE_DAYS, Set.of("n")),
                Map.entry(CommandText.AGE_HOUR, Set.of("n")),
                Map.entry(CommandText.AGE_HOURS, Set.of("n")),
                Map.entry(CommandText.AGE_MINUTE, Set.of("n")),
                Map.entry(CommandText.AGE_MINUTES, Set.of("n")),
                Map.entry(CommandText.FACTIONS_PAGE, Set.of("page", "pages")),
                Map.entry(CommandText.LIST_ROW, Set.of("claims", "members", "online", "power")),
                Map.entry(CommandText.PLAYER_RANK_OF, Set.of("rank", "tag")),
                Map.entry(CommandText.PLAYER_POWER, Set.of("deaths", "kills", "max", "power")),
                Map.entry(CommandText.LAST_SEEN, Set.of("ago")),
                Map.entry(CommandText.AUDIT_PAGE, Set.of("page", "pages", "tag")),
                Map.entry(CommandText.STATS_KILLS, Set.of("deaths", "kills")),
                Map.entry(CommandText.STATS_POINTS, Set.of("date", "points")),
                Map.entry(CommandText.STATS_LEVEL, Set.of("level", "points", "standing", "value")),
                Map.entry(CommandText.STATS_FOUNDED, Set.of("date")),
                Map.entry(CommandText.STATS_RAIDS, Set.of("lost", "points", "won")),
                Map.entry(CommandText.STATS_RAIDS_RUNNING, Set.of("lost", "points", "running", "won")),
                Map.entry(CommandText.COORDS_AT, Set.of("x", "y", "z")),
                Map.entry(CommandText.COORDS_AT_IN, Set.of("world", "x", "y", "z")),
                Map.entry(CommandText.UNCLAIM_ALL_CONFIRM, Set.of("count", "tag")),
                Map.entry(CommandText.POWER_PLAYER, Set.of("max", "name", "power")),
                Map.entry(CommandText.POWER_FACTION, Set.of("claims", "max", "maxClaims", "power", "tag")),
                Map.entry(CommandText.NEAR_NOBODY, Set.of("radius")),
                Map.entry(CommandText.NEAR_HEADER, Set.of("count", "radius")),
                Map.entry(CommandText.NEAR_DISTANCE, Set.of("distance")),
                Map.entry(CommandText.STOREFRONT_EMPTY, Set.of("tag")),
                Map.entry(CommandText.STOREFRONT_OF, Set.of("tag")),
                Map.entry(CommandText.LISTING_SALE, Set.of("price", "seller", "what")),
                Map.entry(CommandText.LISTING_ORDER, Set.of("buyer", "price", "what")),
                Map.entry(CommandText.LISTING_AUCTION, Set.of("price", "what")),
                Map.entry(CommandText.AUCTION_BID_BY, Set.of("name")),
                Map.entry(CommandText.AUCTION_BUYOUT, Set.of("price")),
                Map.entry(CommandText.ENDS_IN, Set.of("time")),
                Map.entry(CommandText.DISBAND_CONFIRM, Set.of("tag")),
                Map.entry(CommandText.JOIN_REQUESTS_FOR, Set.of("tag")),
                Map.entry(CommandText.RANK_LIST, Set.of("member", "officer", "recruit")),
                Map.entry(CommandText.RANKS_ARE, Set.of("ranks")),
                Map.entry(CommandText.NOT_MEMBER_OF, Set.of("name", "tag")),
                Map.entry(CommandText.LEADER_CONFIRM, Set.of("name", "tag")),
                Map.entry(CommandText.NO_NATION_WITH_TAG, Set.of("tag")),
                Map.entry(CommandText.NATION_DISBAND_ONLY_LEADER, Set.of("tag")),
                Map.entry(CommandText.NATION_DISBAND_CONFIRM, Set.of("tag")),
                Map.entry(CommandText.NATION_INVITED_BY, Set.of("tags")),
                Map.entry(CommandText.NATION_LED_BY, Set.of("date", "leader")),
                Map.entry(CommandText.NO_UPGRADE_CALLED, Set.of("name", "upgrades")),
                Map.entry(CommandText.VALUE_WORTH, Set.of("standing", "value")),
                Map.entry(CommandText.TOP_TITLE, Set.of("page", "pages")),
                Map.entry(CommandText.TOP_MINE, Set.of("standing", "tag", "value")),
                Map.entry(CommandText.TOP_NEXT, Set.of("page")),
                Map.entry(CommandText.VAULT_PAGES, Set.of("pages")),
                Map.entry(CommandText.UPGRADES_FOR, Set.of("tag")),
                Map.entry(CommandText.NEXT_COST_POINTS, Set.of("points")),
                Map.entry(CommandText.NEXT_COST_COINS, Set.of("cost")),
                Map.entry(CommandText.UPGRADE_LEVEL, Set.of("level", "max")),
                Map.entry(CommandText.REWARD_POINTS, Set.of("points")),
                Map.entry(CommandText.REWARD_POINTS_AND_COINS, Set.of("coins", "points")),
                Map.entry(CommandText.MISSION_PROGRESS, Set.of("progress", "target")),
                Map.entry(CommandText.MISSION_REWARDS, Set.of("reward")),
                Map.entry(CommandText.RANKS_ORDINAL, Set.of("place")),
                Map.entry(CommandText.ORDINAL_ST, Set.of("n")),
                Map.entry(CommandText.ORDINAL_ND, Set.of("n")),
                Map.entry(CommandText.ORDINAL_RD, Set.of("n")),
                Map.entry(CommandText.ORDINAL_TH, Set.of("n")),
                Map.entry(CommandText.NO_SHOP, Set.of("tag")),
                Map.entry(CommandText.NOW_TALKING_IN, Set.of("channel")),
                Map.entry(CommandText.SHIELD_WINDOW, Set.of("hour", "hours")),
                Map.entry(CommandText.SIEGES_RUNNING, Set.of("count")),
                Map.entry(CommandText.SIEGE_ROW, Set.of("chunk", "phase", "time", "world")),
                Map.entry(CommandText.RAID_POINTS_HELD, Set.of("points")),
                Map.entry(CommandText.SIEGE_NOW, Set.of("phase", "siege")),
                Map.entry(CommandText.SIEGE_STATS, Set.of("attackers", "blocks", "defenders")),
                Map.entry(CommandText.SIEGE_ATTACKED, Set.of("tag")),
                Map.entry(CommandText.SIEGE_DEFENDED, Set.of("tag")),
                Map.entry(CommandText.NO_ZONE_CALLED, Set.of("name")),
                Map.entry(CommandText.ZONES_OF, Set.of("tag")),
                Map.entry(CommandText.ZONE_CHUNK, Set.of("count")),
                Map.entry(CommandText.ZONE_CHUNKS, Set.of("count"))));
    }

    @Test
    void assembledSentencesKeepTheirWording() {
        assertEquals("No listing has an id starting with abc123.",
                Messages.get(CommandText.ID_NO_MATCH, "noun", "listing", "typed", "abc123"));
        assertEquals("3rd", ProgressCommands.ordinal(3));
        assertEquals("11th", ProgressCommands.ordinal(11));
        assertEquals("ranks 22nd", ProgressCommands.standing(22));
        assertEquals("is not ranked yet", ProgressCommands.standing(0));
    }
}
