package dev.ryvrook.dominion.config;

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
        s.define("raid.attackerCooldownMinutes", "Raiding", "Minutes before the same attacker can raid again", 60);
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
    }

    public static Map<String, Object> preset(String name) {
        return switch (name.toLowerCase()) {
            case "singleplayer" -> Map.of(
                    "raid.enabled", false, "raid.modeSiege", false, "events.enabled", false,
                    "shield.enabled", false, "grace.active", false, "land.mustBeConnected", false,
                    "power.lossPerDeath", 0.0, "combat.pvpNeutral", false, "misc.preset", "singleplayer");
            case "hardcore" -> Map.of(
                    "raid.enabled", true, "raid.minDefendersOnline", 0, "power.lossPerDeath", 8.0,
                    "power.dtrMode", true, "combat.pvpNeutral", true, "combat.pvpFactionless", true,
                    "siege.captureMinutes", 10, "misc.preset", "hardcore");
            case "peaceful" -> Map.of(
                    "raid.enabled", false, "combat.pvpNeutral", false, "combat.pvpFactionless", false,
                    "events.enabled", true, "misc.preset", "peaceful");
            default -> Map.of(
                    "raid.enabled", true, "raid.modeOverextended", true, "raid.modeDominance", true,
                    "raid.modeSiege", true, "combat.pvpNeutral", true, "misc.preset", "classic");
        };
    }
}
