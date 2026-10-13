package dev.ryvrook.dominion.model;

/**
 * A mission template. Kind names the counter the mission tracks, for example mine, chop, farm,
 * kill, fish, trade, claim, siege or outpost. Progress is stored per faction in faction stats
 * under the key "mission.<id>".
 */
public record Mission(String id, String name, String description, String kind, long target, boolean weekly,
                      int rewardPoints, double rewardCoins, String icon) {}
