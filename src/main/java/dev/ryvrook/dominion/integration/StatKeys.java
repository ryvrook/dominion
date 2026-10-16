package dev.ryvrook.dominion.integration;

import java.util.List;

/**
 * The per player counters Dominion publishes to TrackerAPI and mirrors to Feats. TrackerAPI lets another plugin
 * write keys under its own prefix and a colon, so every key starts with {@code dominion:}. The names follow the
 * TrackerAPI scheme, lower case words joined by underscores.
 */
public final class StatKeys {
    private StatKeys() {}

    public static final String PREFIX = "dominion:";

    /** Chunks the player claimed for their faction, taken chunks included. */
    public static final String CLAIMS = PREFIX + "claims";
    /** Chunks the player released. */
    public static final String UNCLAIMS = PREFIX + "unclaims";
    /** Players killed who were in a faction at war with the killer's faction. */
    public static final String ENEMY_KILLS = PREFIX + "enemy_kills";
    /** Deaths on land owned by a faction at war with the player's faction. */
    public static final String ENEMY_LAND_DEATHS = PREFIX + "enemy_land_deaths";
    /** Sieges the player's faction won while the player was a member. */
    public static final String SIEGES_WON = PREFIX + "sieges_won";
    /** Sieges the player's faction lost while the player was a member. */
    public static final String SIEGES_LOST = PREFIX + "sieges_lost";
    /** Faction missions completed while the player was a member. */
    public static final String MISSIONS = PREFIX + "missions";
    /** Whole coins the player put into the faction bank. */
    public static final String BANK_DEPOSITED = PREFIX + "bank_deposited";
    /** Factions the player founded. */
    public static final String FACTIONS_FOUNDED = PREFIX + "factions_founded";
    /** Players the player invited to their faction. */
    public static final String INVITES = PREFIX + "invites";
    /** Factions the player joined, one per join, imports included. */
    public static final String JOINED = PREFIX + "joined";
    /** Times the player was kicked out of a faction, a kick with a ban included. */
    public static final String KICKED = PREFIX + "kicked";
    /** Wars the player's faction declared while the player was a member. */
    public static final String WARS_DECLARED = PREFIX + "wars_declared";
    /** Alliances the player's faction formed while the player was a member, both sides count. */
    public static final String ALLIANCES = PREFIX + "alliances";
    /** Upgrades the player's faction bought while the player was a member. */
    public static final String UPGRADES = PREFIX + "upgrades";
    /**
     * The highest upgrade level of any faction the player was a member of when it was bought. Feats and TrackerAPI
     * only add, so the mapper sends the difference from the last level the player was credited, which
     * {@code ContextFacts} remembers on the player.
     */
    public static final String UPGRADE_LEVEL_MAX = PREFIX + "upgrade_level_max";
    /** Market and shop trades the player made, as buyer or as seller. */
    public static final String MARKET_TRADES = PREFIX + "market_trades";
    /** Other factions whose land the player entered, each faction once. */
    public static final String FOREIGN_ENTRIES = PREFIX + "foreign_entries";
    /** Sieges the player's faction held off while the player was a member. */
    public static final String SIEGES_DEFENDED = PREFIX + "sieges_defended";
    /** Sieges in which the player's faction breached the defender while the player was a member. */
    public static final String BREACHES = PREFIX + "breaches";
    /** Hill events the player's faction captured while the player was a member. */
    public static final String HILL_WINS = PREFIX + "hill_wins";
    /** Outposts the player's faction took control of while the player was a member. */
    public static final String OUTPOSTS_HELD = PREFIX + "outposts_held";
    /** 1 once the player's power first reached their maximum. */
    public static final String POWER_MAX = PREFIX + "power_max";
    /** Times another member promoted or demoted the player, from the MemberRankChanged event. Sent since 0.7.2. */
    public static final String RANK_CHANGES = PREFIX + "rank_changes";
    /** 1 once the player finished or skipped the tutorial. */
    public static final String TUTORIAL_DONE = PREFIX + "tutorial_done";

    public static final List<String> ALL = List.of(CLAIMS, UNCLAIMS, ENEMY_KILLS, ENEMY_LAND_DEATHS, SIEGES_WON,
            SIEGES_LOST, MISSIONS, BANK_DEPOSITED, FACTIONS_FOUNDED, INVITES, JOINED, KICKED, WARS_DECLARED, ALLIANCES,
            UPGRADES, UPGRADE_LEVEL_MAX, MARKET_TRADES, FOREIGN_ENTRIES, SIEGES_DEFENDED, BREACHES, HILL_WINS,
            OUTPOSTS_HELD, POWER_MAX, RANK_CHANGES, TUTORIAL_DONE);

    /** The ten keys Dominion sent before 0.7.1. A Feats older than 1.7.2 keeps only these. */
    public static final List<String> ORIGINAL = ALL.subList(0, 10);

    /**
     * The keys TrackerAPI is asked to keep a leaderboard for, every key that can grow past one, so every reader of
     * the boards reads one source. power_max and tutorial_done stop at 1 and get no board.
     */
    public static final List<String> BOARDS = ALL.stream().filter(k -> !k.equals(POWER_MAX) && !k.equals(TUTORIAL_DONE)).toList();
}
