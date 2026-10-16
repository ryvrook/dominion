package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.text.Palette;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every setting the spec names, grouped the way the admin settings page shows them. */
public final class Defaults {
    private Defaults() {}

    public static void register(Settings s) {
        // Factions
        s.define("faction.tagMin", "Factions", "Minimum tag length", 2);
        s.define("faction.tagMax", "Factions", "Maximum tag length", 16);
        s.define("faction.nameMax", "Factions", "Maximum name length", 48);
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
        s.define("combat.useCombatLog", "Combat", "Use combat tags from the CombatLog mod. auto uses them when the mod is installed, off ignores them", "auto");
        s.define("combat.blockSafeZoneEntry", "Combat", "Move combat tagged players back out of SafeZones", true);
        s.define("combat.blockClaimsWhileTagged", "Combat", "Refuse claiming and unclaiming while combat tagged", true);

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
        s.define("eco.provider", "Economy", "Provider, auto, internal, vaultunlocked, item or coinage. auto picks Coinage, then VaultUnlocked, then internal", "auto");
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
        s.define("nation.tagMin", "Relations", "Nation tag minimum length", 2);
        s.define("nation.tagMax", "Relations", "Nation tag maximum length", 16);
        s.define("faction.rosterRotate", "Factions", "Rotate the longest offline member out when the roster is full", false);
        s.define("missions.placedBlockMemoryMinutes", "Progression", "Minutes a placed block stays excluded from mission credit", 60);

        // Map and HUD
        s.define("map.markers", "Map", "Show claims on the world map", true);
        s.define("map.borderOnlyAbove", "Map", "Show border chunks only above this many claims", 64);
        s.define("hud.enabledDefault", "Map", "Sidebar on by default", true);
        s.define("hud.refreshSeconds", "Map", "Sidebar refresh in seconds", 2);
        // Map overlay brief. Claims drawn on the world map images
        s.define("map.overlay", "Map", "Tint claimed land on the world map in the owner's colour", true);
        s.define("map.overlayOpacity", "Map", "Claims overlay opacity in percent", 55);
        s.define("map.overlayBorder", "Map", "Claims overlay border width in pixels", 2);
        s.define("map.claimMarkers", "Map", "Show an icon on every claimed chunk instead of one label per faction", false);
        s.define("map.factionLabels", "Map", "Show one faction name label on the painted land of each faction", true);
        s.define("map.compass", "Map", "Which Dominion markers the compass shows, none, events (homes, sieges, hills, outposts and envoys) or all", "none");

        // Storage and misc
        s.define("storage.autosaveSeconds", "Storage", "Autosave interval in seconds", 30);
        s.define("storage.backupKeep", "Storage", "Daily backups to keep", 7);
        s.define("misc.preset", "Storage", "Active preset name", "classic");
        s.define("misc.tutorial", "Storage", "Show the tutorial on first join", true);
        s.define("misc.singleplayerAutoPreset", "Storage", "Apply the singleplayer preset automatically", true);

        // UI brief, where the sidebar sits. The default top clears the Coinage and Chronicle boxes.
        s.define("hud.side", "Map", "Sidebar screen side, right or left", "right");
        s.define("hud.top", "Map", "Sidebar distance from the top of the screen", 400);
        s.choices("hud.side", "right", "left");
        s.range("hud.top", 0, 2000);
        // Chat commands brief. Classic /f top paging, the chat tag switch, nameplate tags and rename announcements.
        s.define("top.pageSize", "Progression", "Factions per page of /f top", 10);
        s.range("top.pageSize", 5, 25);
        s.define("chat.showTag", "Chat", "Show the faction tag before player names in public chat", true);
        s.define("chat.nameplateTag", "Chat", "Experimental. Show the faction tag before the name above each player's head", false);
        s.define("chat.announceRenames", "Chat", "Tell every online player when a faction changes its tag or name", true);
        // Tutorial brief. Which steps the guided tutorial shows, so a peaceful server can drop the raids step.
        s.define("misc.tutorialSteps", "Storage", "Comma separated tutorial steps to show, from found, claim, map, home, invite, "
                + "relations, raids, bank, chat and commands, in that order", "found,claim,map,home,invite,relations,raids,bank,chat,commands");
        String step = "(found|claim|map|home|invite|relations|raids|bank|chat|commands)";
        s.pattern("misc.tutorialSteps", "\\s*" + step + "(\\s*,\\s*" + step + ")*\\s*", "a comma separated list of found, claim, map, home, invite, relations, raids, bank, chat or commands");

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
        s.choices("combat.useCombatLog", "auto", "off");
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
        s.choices("eco.provider", "auto", "internal", "vaultunlocked", "vault", "item", "items", "coinage");
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
        // Map overlay brief
        s.range("map.overlayOpacity", 10, 90);
        s.range("map.overlayBorder", 0, 4);
        s.choices("map.compass", "none", "events", "all");
        s.range("storage.autosaveSeconds", 10, 3600);
        s.range("storage.backupKeep", 0, 365);
        s.choices("misc.preset", "classic", "singleplayer", "hardcore", "peaceful");

        // Minimum and maximum pairs. The minimum may equal the maximum but never pass it.
        s.ordered("faction.tagMin", "faction.tagMax");
        s.ordered("nation.tagMin", "nation.tagMax");
        s.ordered("power.min", "power.max");

        // Integrations brief. Optional links to the sibling mods, read once at start.
        s.define("integrations.tracker", "Integrations", "Publish faction counters to TrackerAPI. auto when installed, always warns when missing, never ignores it", "auto");
        s.define("integrations.feats", "Integrations", "Mirror faction counters to Feats. auto when installed, always warns when missing, never ignores it", "auto");
        s.define("integrations.atlas", "Integrations", "Push the map hide switch, and sieges while the snapshot is off, to Atlas. auto when installed, always warns when missing, never ignores it", "auto");
        s.define("integrations.atlasSiegeTier", "Integrations", "Who sees the Atlas siege layer, set it to Atlas's visibility.territories. Only drawn while export.enabled is off, the snapshot draws sieges otherwise", "public");
        s.choices("integrations.atlasSiegeTier", "public", "linked", "faction", "admin");
        s.define("integrations.featsAnnounce", "Integrations", "Post Feats unlocks to the player's faction chat", true);
        s.choices("integrations.tracker", "auto", "always", "never");
        s.choices("integrations.feats", "auto", "always", "never");
        s.choices("integrations.atlas", "auto", "always", "never");

        // Coinage brief. Wallets, faction banks and the market handed to Coinage while it is linked.
        s.define("integrations.coinage", "Integrations", "Link to Coinage for wallets, faction banks, net worth and the market. auto when installed, always warns when missing, never ignores it", "auto");
        s.choices("integrations.coinage", "auto", "always", "never");
        s.define("eco.coinageCurrency", "Economy", "Coinage currency id for wallets and banks, blank for the primary currency", "");
        s.pattern("eco.coinageCurrency", "[a-z0-9_]{0,16}", "a Coinage currency id of lower case letters, digits or _, or blank");
        s.define("eco.coinageBanks", "Economy", "Keep faction banks in Coinage shared accounts while the provider is coinage", true);
        s.define("eco.coinageDisbandTo", "Economy", "Where a disbanded faction's Coinage bank goes, leader or server", "leader");
        s.choices("eco.coinageDisbandTo", "leader", "server");
        s.define("market.useCoinage", "Market", "Open Coinage's market from the panel Market tab while Coinage is linked", true);
        s.define("shop.useCoinage", "Market", "Reserved. Faction shops stay Dominion's, since Coinage shops cannot be owned by a faction account yet", false);

        configKeys(s);
    }

    /**
     * Config keys brief. Every tunable that used to be a constant in the code, the colours, the command
     * names, the permission nodes and the feature switches. Read live unless the label says otherwise.
     */
    private static void configKeys(Settings s) {
        // Rules that were constants
        s.define("land.reclaimWindowDays", "Land", "Days a released chunk is remembered, so claiming it again earns no mission or counter credit", 7);
        s.range("land.reclaimWindowDays", 0, 365);
        s.define("land.territoryMessageMillis", "Land", "Milliseconds between two safe zone push back messages to one player", 2000);
        s.range("land.territoryMessageMillis", 0, 600_000);
        s.define("land.nearDefault", "Land", "Radius in blocks of /f near without a number", 64);
        s.range("land.nearDefault", 1, 4096);
        s.define("land.nearMax", "Land", "Largest radius in blocks /f near accepts", 256);
        s.range("land.nearMax", 1, 4096);
        s.ordered("land.nearDefault", "land.nearMax");
        s.define("power.dtrBase", "Power", "Deaths pool mode. Pool of a faction before members are counted", 2.0);
        s.range("power.dtrBase", 0, 100_000);
        s.define("power.dtrPerMember", "Power", "Deaths pool mode. Pool added per member", 1.0);
        s.range("power.dtrPerMember", 0, 100_000);
        s.define("power.dtrCap", "Power", "Deaths pool mode. Largest pool a faction can have", 10.0);
        s.range("power.dtrCap", 0, 100_000);
        s.define("power.dtrFloor", "Power", "Deaths pool mode. Lowest the pool can fall", -3.0);
        s.range("power.dtrFloor", -100_000, 0);
        s.define("power.dtrLossPerDeath", "Power", "Deaths pool mode. Pool lost per member death", 1.0);
        s.range("power.dtrLossPerDeath", 0, 100_000);
        s.define("power.dtrRegenPerOnline", "Power", "Deaths pool mode. Pool regained per minute for each online member", 0.05);
        s.range("power.dtrRegenPerOnline", 0, 100_000);
        s.define("power.dtrRegenCap", "Power", "Deaths pool mode. Most pool regained per minute", 0.1);
        s.range("power.dtrRegenCap", 0, 100_000);
        s.define("power.regenStep", "Power", "Extra share of the regen rate per level of the regen upgrade, 0.25 is a quarter", 0.25);
        s.range("power.regenStep", 0, 100);
        s.define("power.upgradeStep", "Power", "Maximum power added per player for each level of the maxpower upgrade", 2.0);
        s.range("power.upgradeStep", 0, 1_000_000);
        s.define("combat.homeRespawnDelayMillis", "Combat", "Milliseconds after a respawn before the move to the faction home", 1000);
        s.range("combat.homeRespawnDelayMillis", 50, 60_000);
        s.define("combat.homeRespawnAttempts", "Combat", "Tries to move a respawned player to the faction home", 5);
        s.range("combat.homeRespawnAttempts", 1, 100);
        s.define("combat.safeZoneMaxStep", "Combat", "Blocks moved in one tick above which a SafeZone entry counts as a teleport and is let through", 64.0);
        s.range("combat.safeZoneMaxStep", 1, 10_000);
        s.define("home.stuckSearchRadius", "Homes", "Chunks searched around a player for wilderness by /f stuck", 32);
        s.range("home.stuckSearchRadius", 1, 256);
        s.define("home.warmupMoveTolerance", "Homes", "Blocks a player may move during a teleport warmup", 1.0);
        s.range("home.warmupMoveTolerance", 0, 1000);
        s.define("missions.farmGuardMaxPlaced", "Progression", "Placed blocks remembered across all worlds for mission credit, read at start", 100_000);
        s.range("missions.farmGuardMaxPlaced", 0, 10_000_000);
        s.define("missions.farmGuardMaxDrops", "Progression", "Dropped item stacks remembered per player for mission credit", 256);
        s.range("missions.farmGuardMaxDrops", 0, 100_000);
        s.define("missions.farmGuardDropMemoryMinutes", "Progression", "Minutes a dropped item earns no mission credit for its dropper's faction", 5);
        s.range("missions.farmGuardDropMemoryMinutes", 0, 10_080);
        s.define("integrations.foundedWindowSeconds", "Integrations", "Seconds after a faction is created in which its leader is credited as founder", 60);
        s.range("integrations.foundedWindowSeconds", 1, 86_400);
        s.define("market.auctionHours", "Market", "Auction length in hours when the seller names none", 24);
        s.range("market.auctionHours", 1, 8760);
        s.define("raid.upkeepUnpaidDays", "Raiding", "Days of unpaid upkeep that make a faction raidable in upkeep mode", 3);
        s.range("raid.upkeepUnpaidDays", 1, 3650);
        s.define("vault.memberPages", "Market", "Vault pages every member may open, later pages need an officer", 1);
        s.range("vault.memberPages", 0, 64);
        s.define("siege.holdOffSeconds", "Sieges", "Seconds defenders must hold the banner alone to break a siege", 30);
        s.range("siege.holdOffSeconds", 1, 86_400);
        s.define("siege.resumeGraceSeconds", "Sieges", "Seconds after a restart before absence can win or lose a capture", 120);
        s.range("siege.resumeGraceSeconds", 0, 86_400);
        s.define("commands.confirmSeconds", "Commands", "Seconds a dangerous command waits for /f confirm", 30);
        s.range("commands.confirmSeconds", 5, 3600);

        // Interface
        s.define("ui.marketPageSize", "Interface", "Listings per page in the panel market", 10);
        s.range("ui.marketPageSize", 1, 50);
        s.define("ui.logPageSize", "Interface", "Audit entries per page in the panel log", 25);
        s.range("ui.logPageSize", 1, 100);
        s.define("ui.gridRadius", "Interface", "Chunks shown on each side of the player in the panel land grid", 7);
        s.range("ui.gridRadius", 2, 7);
        s.define("ui.topLimit", "Interface", "Factions listed on the top page", 30);
        s.range("ui.topLimit", 1, 500);
        s.define("ui.eventRows", "Interface", "Rows per list on the panel events tab", 20);
        s.range("ui.eventRows", 1, 100);
        s.define("ui.siegeHistory", "Interface", "Past sieges listed on the panel siege tab", 10);
        s.range("ui.siegeHistory", 0, 100);
        s.define("ui.vaultMaxPages", "Interface", "Vault pages the panel offers buttons for", 30);
        s.range("ui.vaultMaxPages", 1, 64);
        s.define("ui.confirmLockMillis", "Interface", "Milliseconds the confirm button stays locked on a confirm page", 3000);
        s.range("ui.confirmLockMillis", 0, 60_000);
        s.define("ui.maxDots", "Interface", "Most level dots drawn for an upgrade, the markup has ten", 10);
        s.range("ui.maxDots", 1, 10);
        s.define("ui.bannerIcon", "Interface", "Item whose icon decorates the territory banner", "Furniture_Flag_Small_Red");

        colors(s);
        commands(s);
        switches(s);
    }

    /**
     * Feature switches that were missing. Off hides the panel tab, refuses the commands with the feature
     * off sentence and skips the timers. Every one defaults to on.
     */
    private static void switches(Settings s) {
        s.define("vault.enabled", "Market", "Faction vaults enabled", true);
        s.define("missions.enabled", "Progression", "Missions enabled, off also stops mission credit", true);
        s.define("upgrades.enabled", "Progression", "Upgrades can be bought, levels already bought keep working", true);
        s.define("home.enabled", "Homes", "Faction homes, warps, ally homes, /f stuck and home respawns enabled", true);
        s.define("chat.enabled", "Chat", "Faction, ally and nation chat channels enabled", true);
        s.define("hud.enabled", "Map", "Sidebar available on this server", true);
        s.define("map.enabled", "Map", "The /f map chat map and the world map claims, markers and overlay", true);
        s.define("top.enabled", "Progression", "The /f top leaderboard and top page", true);
        s.define("integrations.enabled", "Integrations", "Links to TrackerAPI, Feats, Atlas and Coinage at all, read at start", true);
    }

    /** Command names and permission nodes. Both apply at the next start or /plugin reload. */
    private static void commands(Settings s) {
        String later = ", applied at the next start or /plugin reload";
        s.define("commands.root", "Commands", "Name of the player command" + later, CommandNames.DEFAULT_ROOT);
        s.define("commands.rootAliases", "Commands", "Comma separated aliases of the player command, may be empty" + later, "faction,factions");
        s.define("commands.adminRoot", "Commands", "Name of the admin command" + later, CommandNames.DEFAULT_ADMIN);
        s.define("commands.adminAliases", "Commands", "Comma separated aliases of the admin command, may be empty" + later, "factionadmin");
        s.define("commands.eventsRoot", "Commands", "Name of the events command" + later, CommandNames.DEFAULT_EVENTS);
        s.define("commands.eventsAliases", "Commands", "Comma separated aliases of the events command, may be empty" + later, "");
        s.define("commands.shopEditRoot", "Commands", "Name of the server shop editor command" + later, CommandNames.DEFAULT_SHOP_EDIT);
        s.define("commands.disabled", "Commands", "Comma separated sub commands of the player command that are not registered, "
                + "such as map,top, also balance, pay and baltop, and their panel tabs are hidden" + later, "");
        for (String key : List.of("commands.root", "commands.adminRoot", "commands.eventsRoot", "commands.shopEditRoot")) {
            s.pattern(key, CommandNames.NAME, "a single word of letters, digits, _ or -");
        }
        for (String key : List.of("commands.rootAliases", "commands.adminAliases", "commands.eventsAliases", "commands.disabled")) {
            s.pattern(key, CommandNames.NAME_LIST, "a comma separated list of words of letters, digits, _ or -");
        }

        s.define(Perms.ADMIN, "Permissions", "Node for every admin command, page and tool. Commands pick it up at the next start, "
                + "checks in pages and services at once", Perms.DEFAULTS.get(Perms.ADMIN));
        s.define(Perms.PAYOUTS, "Permissions", "Node that may run the season payout console commands, on top of the admin node. "
                + "The command picks it up at the next start, the payout check at once", Perms.DEFAULTS.get(Perms.PAYOUTS));
        for (String key : Perms.DEFAULTS.keySet()) {
            s.pattern(key, "[A-Za-z0-9_.*-]{1,128}", "a permission node of letters, digits, dots, _, - or *");
        }
    }

    /** Colours as #rrggbb. Chat, the panel, the sidebar, banners and the world map overlay read them live. */
    private static void colors(Settings s) {
        String[][] colors = {
                {"colors.own", "Your own faction and its land"},
                {"colors.ally", "Allies"},
                {"colors.truce", "Truces"},
                {"colors.neutral", "Neutral factions"},
                {"colors.enemy", "Enemies"},
                {"colors.safeZone", "SafeZone land"},
                {"colors.warZone", "WarZone land"},
                {"colors.wilderness", "Unclaimed land"},
                {"colors.accent", "The Dominion prefix in chat"},
                {"colors.text", "Normal message text"},
                {"colors.muted", "Hints and secondary text"},
                {"colors.good", "Success messages"},
                {"colors.bad", "Refusals and errors"},
                {"colors.warn", "Warnings"},
                {"colors.info", "Information"},
        };
        for (String[] c : colors) {
            s.define(c[0], "Colours", c[1] + ", as #rrggbb", Palette.DEFAULTS.get(c[0]));
            s.pattern(c[0], "#[0-9a-fA-F]{6}", "a colour written as #rrggbb");
        }
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
