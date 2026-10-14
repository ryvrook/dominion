package dev.ryvrook.dominion.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every setting the spec names, grouped the way the admin settings page shows them. */
public final class Defaults {
    private Defaults() {}

    public static void register(Settings s) {
        // Factions
        s.define("faction.tagMin", "Factions", "Minimum tag length", 3);
        s.define("faction.tagMax", "Factions", "Maximum tag length", 10);
        s.define("faction.nameMax", "Factions", "Maximum name length", 32);
        s.define("faction.maxMembers", "Factions", "Maximum members", 20);
        s.define("faction.inviteHours", "Factions", "Invite expiry in hours", 24);
        s.define("faction.newMemberHoldHours", "Factions", "New member hold in hours", 24);
        s.define("faction.memberInactiveDays", "Factions", "Kick members after days offline", 30);
        s.define("faction.disbandInactiveDays", "Factions", "Disband after days with no logins", 45);
        s.define("faction.auditLimit", "Factions", "Audit entries kept per faction", 500);
        s.define("faction.tagBlacklist", "Factions", "Comma separated banned tags", "admin,staff,server,mod");

        // Power
        s.define("power.start", "Power", "Starting power per player", 10.0);
        s.define("power.max", "Power", "Maximum power per player", 20.0);
        s.define("power.min", "Power", "Minimum power per player", -5.0);
        s.define("power.regenPerMinute", "Power", "Regen per minute while online", 0.1);
        s.define("power.lossPerDeath", "Power", "Power lost per death", 4.0);
        s.define("power.lossInWarzone", "Power", "Lose power when dying in a WarZone", false);
        s.define("power.offlineDecayAfterDays", "Power", "Days offline before decay starts", 3);
        s.define("power.offlineDecayPerDay", "Power", "Power lost per day while decaying", 1.0);
        s.define("power.dtrMode", "Power", "Use the deaths pool mode instead of power", false);

        // Land
        s.define("land.claimsPerPower", "Land", "Claims allowed per point of faction power", 1.0);
        s.define("land.mustBeConnected", "Land", "Claims must touch existing claims", true);
        s.define("land.bufferChunks", "Land", "Empty chunks required between factions", 1);
        s.define("land.spawnProtectRadius", "Land", "Blocks around spawn that cannot be claimed", 64);
        s.define("land.maxRadiusClaim", "Land", "Largest radius claim", 5);
        s.define("land.maxLineClaim", "Land", "Longest line claim", 8);
        s.define("land.decayAfterDays", "Land", "Days with no member online before land decays", 14);
        s.define("land.decayChunksPerDay", "Land", "Chunks lost per day while decaying", 1);
        s.define("land.allowedWorlds", "Land", "Comma separated worlds that allow claims, empty for all", "");
        s.define("land.territoryBanners", "Land", "Show a banner when entering territory", true);

        // Raiding
        s.define("raid.enabled", "Raiding", "Raiding enabled at all", true);
        s.define("raid.modeOverextended", "Raiding", "Raidable when claims exceed power", true);
        s.define("raid.modeDominance", "Raiding", "Raidable by stronger enemies", true);
        s.define("raid.dominanceMarginPercent", "Raiding", "Attacker power lead required in percent", 10.0);
        s.define("raid.dominanceCooldownMinutes", "Raiding", "Minutes between dominance overclaims", 5);
        s.define("raid.modeSiege", "Raiding", "Sieges enabled", true);
        s.define("raid.modeLowRatio", "Raiding", "Raidable below a power ratio", false);
        s.define("raid.lowRatioPercent", "Raiding", "Power ratio threshold in percent", 25.0);
        s.define("raid.modeUpkeep", "Raiding", "Raidable when upkeep unpaid", false);
        s.define("raid.combineAny", "Raiding", "Any mode makes a faction raidable, otherwise all", true);
        s.define("raid.minDefendersOnline", "Raiding", "Defenders online required to raid", 1);
        s.define("raid.minAttackersOnline", "Raiding", "Attackers online required to siege", 3);
        s.define("raid.attackerCooldownMinutes", "Raiding", "Minutes before one faction can siege the same defender again", 60);
        s.define("raid.protectHomeChunkUntilLast", "Raiding", "Home chunk can only be taken last", true);

        // Relations
        s.define("relation.maxAllies", "Relations", "Maximum allies", 3);
        s.define("relation.maxTruces", "Relations", "Maximum truces", 5);
        s.define("relation.requestHours", "Relations", "Relation request expiry in hours", 48);
        s.define("relation.allyBuildDefault", "Relations", "Allies may build by default", false);
        s.define("relation.truceInteractDefault", "Relations", "Truces may use doors by default", true);

        // Combat
        s.define("combat.pvpNeutral", "Combat", "PvP between neutral factions", true);
        s.define("combat.pvpFactionless", "Combat", "PvP for players without a faction", true);
        s.define("combat.friendlyFireDefault", "Combat", "Friendly fire default", false);
        s.define("combat.allyFire", "Combat", "Allies can hurt each other", false);
        s.define("combat.truceFire", "Combat", "Truces can hurt each other", false);
        s.define("combat.tagSeconds", "Combat", "Combat tag duration in seconds", 15);
        s.define("combat.killRewardPercent", "Combat", "Percent of victim bank paid to killer bank", 0.0);

        // Homes
        s.define("home.warmupSeconds", "Homes", "Teleport warmup in seconds", 5);
        s.define("home.cooldownSeconds", "Homes", "Teleport cooldown in seconds", 60);
        s.define("home.enemyRadius", "Homes", "Enemy distance that blocks teleport", 32);
        s.define("home.maxWarps", "Homes", "Warps per faction", 3);
        s.define("home.stuckWarmupSeconds", "Homes", "Stuck escape warmup in seconds", 20);
        s.define("home.respawnAtHomeDefault", "Homes", "Respawn at faction home by default", false);

        // Chat
        s.define("chat.factionPrefix", "Chat", "Prefix character for faction chat", "!");
        s.define("chat.allyPrefix", "Chat", "Prefix character for ally chat", "@");
        s.define("chat.nationPrefix", "Chat", "Prefix character for nation chat", "#");
        s.define("chat.tagStyle", "Chat", "Tag style, brackets or plain", "brackets");
        s.define("chat.loginAlerts", "Chat", "Announce member logins to the faction", true);
        s.define("chat.logPrivate", "Chat", "Log faction, ally and nation chat to the console at INFO instead of FINE", false);
        s.define("chat.publicFormat", "Chat", "Public chat style. relation colours each tag for its viewer and delivers the line itself. passthrough adds the tag to the normal line so relays and other chat plugins still see it. In both, Dominion logs and maps only lines that were delivered", "relation");

        // Economy
        s.define("eco.enabled", "Economy", "Economy enabled", true);
        s.define("eco.provider", "Economy", "Provider, internal or vaultunlocked or item", "internal");
        s.define("eco.currencyName", "Economy", "Currency name", "Coins");
        s.define("eco.currencySymbol", "Economy", "Currency symbol", "c");
        s.define("eco.startingBalance", "Economy", "Starting player balance", 100.0);
        s.define("eco.itemId", "Economy", "Item id used when provider is item", "Ingredient_Bar_Gold");
        s.define("eco.costCreate", "Economy", "Cost to create a faction", 0.0);
        s.define("eco.costClaim", "Economy", "Cost per claim", 0.0);
        s.define("eco.costClaimGrowthPercent", "Economy", "Claim cost growth per claim in percent", 30.0);
        s.define("eco.refundUnclaimPercent", "Economy", "Refund on unclaim in percent", 70.0);
        s.define("eco.costWarp", "Economy", "Cost to set a warp", 0.0);
        s.define("eco.upkeepPerClaimPerDay", "Economy", "Daily upkeep per claim", 0.0);
        s.define("eco.duesPerMemberPerDay", "Economy", "Daily dues per member", 0.0);
        s.define("eco.playtimeStipendPerHour", "Economy", "Coins per hour of playtime", 0.0);
        s.define("eco.bankInterestPercentPerDay", "Economy", "Bank interest per day in percent", 0.0);
        s.define("eco.withdrawDailyLimitOfficer", "Economy", "Daily withdraw limit for officers", 0.0);

        // Market
        s.define("market.enabled", "Market", "Market and auctions enabled", true);
        s.define("market.taxPercent", "Market", "Faction tax on sales in percent", 5.0);
        s.define("market.serverTaxPercent", "Market", "Server sink on sales in percent", 0.0);
        s.define("market.listingHours", "Market", "Listing expiry in hours", 48);
        s.define("market.maxListingsPerPlayer", "Market", "Listings per player", 20);
        s.define("market.auctionMinStepPercent", "Market", "Minimum bid step in percent", 5.0);
        s.define("market.auctionAntiSnipeMinutes", "Market", "Anti snipe extension in minutes", 2);

        // Sieges and shields
        s.define("siege.captureMinutes", "Sieges", "Capture time in minutes", 15);
        s.define("siege.breachMinutes", "Sieges", "Breach window in minutes", 30);
        s.define("siege.attackerRadius", "Sieges", "Attacker radius around the banner", 8);
        s.define("siege.defenderRadius", "Sieges", "Defender cancel radius", 3);
        s.define("siege.absentSecondsToFail", "Sieges", "Seconds without attackers before failure", 60);
        s.define("siege.immunityHoursAfterDefence", "Sieges", "Immunity after a successful defence in hours", 2);
        s.define("shield.enabled", "Shields", "Shields enabled", true);
        s.define("shield.hours", "Shields", "Shield window length in hours", 8);
        s.define("shield.coreRadius", "Shields", "Core radius in chunks around home", 1);
        s.define("shield.changeDelayHours", "Shields", "Hours before a window change applies", 24);
        s.define("grace.active", "Shields", "Grace period active", false);
        s.define("grace.endsAt", "Shields", "Grace end as epoch millis", 0L);
        s.define("grace.noPowerLoss", "Shields", "No power loss during grace", true);
        s.define("eotw.active", "Shields", "End of world mode", false);

        // Events
        s.define("events.enabled", "Events", "Events layer enabled", true);
        s.define("events.kothCapSeconds", "Events", "KOTH capture time in seconds", 300);
        s.define("events.outpostPercentPerPlayerPerSecond", "Events", "Outpost control gain per player per second", 1.0);
        s.define("events.envoyDespawnMinutes", "Events", "Envoy lifetime in minutes", 10);

        // Progression
        s.define("value.weightClaims", "Progression", "Value per claim", 10.0);
        s.define("value.weightMembers", "Progression", "Value per member", 5.0);
        s.define("value.weightPower", "Progression", "Value per point of power", 1.0);
        s.define("value.weightKills", "Progression", "Value per kill", 2.0);
        s.define("value.weightSieges", "Progression", "Value per siege won", 25.0);
        s.define("value.weightBankPer100", "Progression", "Value per 100 coins in the bank", 1.0);
        s.define("value.weightOutpostHours", "Progression", "Value per outpost hour", 5.0);
        s.define("value.strikePercent", "Progression", "Value lost per strike in percent", 10.0);
        s.define("upgrades.useCoins", "Progression", "Buy upgrades with coins instead of points", true);
        s.define("upgrades.baseCost", "Progression", "Base upgrade cost", 500.0);
        s.define("upgrades.costGrowth", "Progression", "Cost multiplier per level", 1.8);
        s.define("missions.daily", "Progression", "Daily missions active", 3);
        s.define("missions.weekly", "Progression", "Weekly missions active", 2);
        s.define("missions.rewardCoins", "Progression", "Coins per daily mission", 100.0);
        s.define("missions.rewardPoints", "Progression", "Points per daily mission", 10);

        // Vault and export
        s.define("vault.basePages", "Market", "Vault pages before upgrades", 1);
        s.define("vault.slotsPerPage", "Market", "Slots per vault page", 27);
        s.define("export.enabled", "Storage", "Write the web map snapshot file", true);
        s.define("export.intervalSeconds", "Storage", "Snapshot interval in seconds", 2);
        s.define("export.playerPositions", "Storage", "Write online player positions to the web map snapshot", false);

        // Events detail
        s.define("events.kothRadius", "Events", "Default hill radius in blocks", 8);
        s.define("events.kothRewardCoins", "Events", "Coins to the capturing faction bank", 500.0);
        s.define("events.kothRewardPoints", "Events", "Faction points to the capturer", 25);
        s.define("events.outpostDecayPerSecond", "Events", "Control lost per second when empty", 0.5);
        s.define("events.envoyChestItem", "Events", "Block placed as an envoy chest", "Furniture_Ancient_Chest_Small");
        s.define("events.bountyMin", "Events", "Smallest bounty", 50.0);
        s.define("events.envoyLoot", "Events", "Pipe separated ItemId,qty entries dropped by envoys", "Ingredient_Bar_Gold,4|Ingredient_Bar_Iron,8|Potion_Health,2");
        // Payout commands run as the console, so only the config file can change them.
        s.defineFileOnly("events.payoutCommands", "Events",
                "Pipe separated console commands with {tag} and {rank}, set in config.json only and run by players with dominion.admin.payouts",
                "say Season payout to {tag} for rank {rank}");
        s.define("events.payoutTop", "Events", "Factions that receive payouts", 3);
        s.define("season.active", "Events", "A season is running", false);
        s.define("season.startedAt", "Events", "Season start as epoch millis", 0L);
        s.define("season.raidEndsAt", "Events", "Raid period end as epoch millis", 0L);

        // Shops and nations
        s.define("shop.adminEnabled", "Market", "Admin shop available", true);
        s.define("shop.factionEnabled", "Market", "Faction shops available", true);
        s.define("shop.factionMaxEntries", "Market", "Entries per faction shop", 24);
        s.define("nation.enabled", "Relations", "Nations enabled", true);
        s.define("nation.maxFactions", "Relations", "Factions per nation", 5);
        s.define("nation.tagMin", "Relations", "Nation tag minimum length", 3);
        s.define("nation.tagMax", "Relations", "Nation tag maximum length", 10);
        s.define("faction.rosterRotate", "Factions", "Rotate the longest offline member out when the roster is full", false);
        s.define("missions.placedBlockMemoryMinutes", "Progression", "Minutes a placed block stays excluded from mission credit", 60);

        // Map and HUD
        s.define("map.markers", "Map", "Show claims on the world map", true);
        s.define("map.borderOnlyAbove", "Map", "Show border chunks only above this many claims", 64);
        s.define("hud.enabledDefault", "Map", "Sidebar on by default", true);
        s.define("hud.refreshSeconds", "Map", "Sidebar refresh in seconds", 2);

        // Storage and misc
        s.define("storage.autosaveSeconds", "Storage", "Autosave interval in seconds", 30);
        s.define("storage.backupKeep", "Storage", "Daily backups to keep", 7);
        s.define("misc.preset", "Storage", "Active preset name", "classic");
        s.define("misc.tutorial", "Storage", "Show the tutorial on first join", true);
        s.define("misc.singleplayerAutoPreset", "Storage", "Apply the singleplayer preset automatically", true);

        limits(s);
    }

    /** Upper bound for settings that only need to stay positive. */
    private static final double ANY = Long.MAX_VALUE;

    /**
     * Ranges and allowed values. A key missing here accepts any value of its type. Bounds are
     * generous on purpose, they only keep out values that break the code, such as a negative
     * interval or a percentage above one hundred.
     */
    private static void limits(Settings s) {
        // Factions
        s.range("faction.tagMin", 1, 32);
        s.range("faction.tagMax", 1, 32);
        s.range("faction.nameMax", 1, 64);
        s.range("faction.maxMembers", 1, 10_000);
        s.range("faction.inviteHours", 1, 8760);
        s.range("faction.newMemberHoldHours", 0, 8760);
        s.range("faction.memberInactiveDays", 0, 3650);
        s.range("faction.disbandInactiveDays", 0, 3650);
        s.range("faction.auditLimit", 0, 100_000);
        // Power
        s.range("power.start", -1_000_000, 1_000_000);
        s.range("power.max", -1_000_000, 1_000_000);
        s.range("power.min", -1_000_000, 1_000_000);
        s.range("power.regenPerMinute", 0, 1_000_000);
        s.range("power.lossPerDeath", 0, 1_000_000);
        s.range("power.offlineDecayAfterDays", 0, 3650);
        s.range("power.offlineDecayPerDay", 0, 1_000_000);
        // Land
        s.range("land.claimsPerPower", 0, 10_000);
        s.range("land.bufferChunks", 0, 32);
        s.range("land.spawnProtectRadius", 0, 1_000_000);
        s.range("land.maxRadiusClaim", 0, 32);
        s.range("land.maxLineClaim", 1, 128);
        s.range("land.decayAfterDays", 0, 3650);
        s.range("land.decayChunksPerDay", 0, 100_000);
        // Raiding
        s.range("raid.dominanceMarginPercent", 0, 10_000);
        s.range("raid.dominanceCooldownMinutes", 0, 525_600);
        s.range("raid.lowRatioPercent", 0, 100);
        s.range("raid.minDefendersOnline", 0, 10_000);
        s.range("raid.minAttackersOnline", 0, 10_000);
        s.range("raid.attackerCooldownMinutes", 0, 525_600);
        // Relations
        s.range("relation.maxAllies", 0, 10_000);
        s.range("relation.maxTruces", 0, 10_000);
        s.range("relation.requestHours", 1, 8760);
        // Combat
        s.range("combat.tagSeconds", 0, 3600);
        s.range("combat.killRewardPercent", 0, 100);
        // Homes
        s.range("home.warmupSeconds", 0, 3600);
        s.range("home.cooldownSeconds", 0, 86_400);
        s.range("home.enemyRadius", 0, 1024);
        s.range("home.maxWarps", 0, 1000);
        s.range("home.stuckWarmupSeconds", 0, 3600);
        // Chat
        s.choices("chat.tagStyle", "brackets", "plain");
        s.choices("chat.publicFormat", "relation", "passthrough");
        // Economy
        s.choices("eco.provider", "internal", "vaultunlocked", "vault", "item", "items");
        s.range("eco.startingBalance", 0, ANY);
        s.range("eco.costCreate", 0, ANY);
        s.range("eco.costClaim", 0, ANY);
        s.range("eco.costClaimGrowthPercent", 0, 10_000);
        s.range("eco.refundUnclaimPercent", 0, 100);
        s.range("eco.costWarp", 0, ANY);
        s.range("eco.upkeepPerClaimPerDay", 0, ANY);
        s.range("eco.duesPerMemberPerDay", 0, ANY);
        s.range("eco.playtimeStipendPerHour", 0, ANY);
        s.range("eco.bankInterestPercentPerDay", 0, 100);
        s.range("eco.withdrawDailyLimitOfficer", 0, ANY);
        // Market
        s.range("market.taxPercent", 0, 100);
        s.range("market.serverTaxPercent", 0, 100);
        s.range("market.listingHours", 1, 8760);
        s.range("market.maxListingsPerPlayer", 0, 10_000);
        s.range("market.auctionMinStepPercent", 0, 1000);
        s.range("market.auctionAntiSnipeMinutes", 0, 1440);
        // Sieges and shields
        s.range("siege.captureMinutes", 1, 1440);
        s.range("siege.breachMinutes", 1, 1440);
        s.range("siege.attackerRadius", 1, 256);
        s.range("siege.defenderRadius", 0, 256);
        s.range("siege.absentSecondsToFail", 1, 86_400);
        s.range("siege.immunityHoursAfterDefence", 0, 8760);
        s.range("shield.hours", 0, 24);
        s.range("shield.coreRadius", 0, 32);
        s.range("shield.changeDelayHours", 0, 8760);
        s.range("grace.endsAt", 0, ANY);
        // Events
        s.range("events.kothCapSeconds", 1, 86_400);
        s.range("events.outpostPercentPerPlayerPerSecond", 0, 100);
        s.range("events.envoyDespawnMinutes", 1, 1440);
        s.range("events.kothRadius", 1, 256);
        s.range("events.kothRewardCoins", 0, ANY);
        s.range("events.kothRewardPoints", 0, 1_000_000);
        s.range("events.outpostDecayPerSecond", 0, 100);
        s.range("events.bountyMin", 0, ANY);
        s.range("events.payoutTop", 0, 1000);
        s.range("season.startedAt", 0, ANY);
        s.range("season.raidEndsAt", 0, ANY);
        // Progression
        s.range("value.weightClaims", 0, 1_000_000);
        s.range("value.weightMembers", 0, 1_000_000);
        s.range("value.weightPower", 0, 1_000_000);
        s.range("value.weightKills", 0, 1_000_000);
        s.range("value.weightSieges", 0, 1_000_000);
        s.range("value.weightBankPer100", 0, 1_000_000);
        s.range("value.weightOutpostHours", 0, 1_000_000);
        s.range("value.strikePercent", 0, 100);
        s.range("upgrades.baseCost", 0, ANY);
        s.range("upgrades.costGrowth", 1, 100);
        s.range("missions.daily", 0, 50);
        s.range("missions.weekly", 0, 50);
        s.range("missions.rewardCoins", 0, ANY);
        s.range("missions.rewardPoints", 0, 1_000_000);
        s.range("missions.placedBlockMemoryMinutes", 0, 10_080);
        // Vault and export
        s.range("vault.basePages", 0, 64);
        s.range("vault.slotsPerPage", 1, 63);
        s.range("export.intervalSeconds", 1, 3600);
        // Shops and nations
        s.range("shop.factionMaxEntries", 0, 1000);
        s.range("nation.maxFactions", 1, 1000);
        s.range("nation.tagMin", 1, 32);
        s.range("nation.tagMax", 1, 32);
        // Map, HUD and storage
        s.range("map.borderOnlyAbove", 0, 1_000_000);
        s.range("hud.refreshSeconds", 1, 60);
        s.range("storage.autosaveSeconds", 10, 3600);
        s.range("storage.backupKeep", 0, 365);
        s.choices("misc.preset", "classic", "singleplayer", "hardcore", "peaceful");

        // Minimum and maximum pairs. The minimum may equal the maximum but never pass it.
        s.ordered("faction.tagMin", "faction.tagMax");
        s.ordered("nation.tagMin", "nation.tagMax");
        s.ordered("power.min", "power.max");
    }

    /** Names of the presets in the order the admin page lists them. */
    public static final List<String> PRESETS = List.of("classic", "hardcore", "peaceful", "singleplayer");

    public static boolean isPreset(String name) {
        return name != null && PRESETS.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The values a preset sets. Every preset names the same keys, so switching from one preset to
     * another never leaves a value behind from the first. Classic uses the shipped defaults.
     * Returns an empty map for an unknown name.
     */
    public static Map<String, Object> preset(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!isPreset(key)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        // Classic first, then each preset overrides its own differences.
        out.put("raid.enabled", true);
        out.put("raid.modeOverextended", true);
        out.put("raid.modeDominance", true);
        out.put("raid.modeSiege", true);
        out.put("raid.minDefendersOnline", 1);
        out.put("events.enabled", true);
        out.put("shield.enabled", true);
        out.put("land.mustBeConnected", true);
        out.put("power.lossPerDeath", 4.0);
        out.put("power.dtrMode", false);
        out.put("combat.pvpNeutral", true);
        out.put("combat.pvpFactionless", true);
        out.put("siege.captureMinutes", 15);
        switch (key) {
            case "singleplayer" -> {
                out.put("raid.enabled", false);
                out.put("raid.modeSiege", false);
                out.put("events.enabled", false);
                out.put("shield.enabled", false);
                out.put("land.mustBeConnected", false);
                out.put("power.lossPerDeath", 0.0);
                out.put("combat.pvpNeutral", false);
            }
            case "hardcore" -> {
                out.put("raid.minDefendersOnline", 0);
                out.put("power.lossPerDeath", 8.0);
                out.put("power.dtrMode", true);
                out.put("siege.captureMinutes", 10);
            }
            case "peaceful" -> {
                out.put("raid.enabled", false);
                out.put("combat.pvpNeutral", false);
                out.put("combat.pvpFactionless", false);
            }
            default -> { }
        }
        out.put("misc.preset", key);
        return out;
    }
}
