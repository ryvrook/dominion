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

    public static final List<String> ALL = List.of(CLAIMS, UNCLAIMS, ENEMY_KILLS, ENEMY_LAND_DEATHS, SIEGES_WON,
            SIEGES_LOST, MISSIONS, BANK_DEPOSITED, FACTIONS_FOUNDED, INVITES);

    /** The keys TrackerAPI is asked to keep a leaderboard for. */
    public static final List<String> BOARDS = List.of(CLAIMS, ENEMY_KILLS, SIEGES_WON, MISSIONS, BANK_DEPOSITED);
}
