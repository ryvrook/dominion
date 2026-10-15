package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.text.Messages;

/** Player facing sentences of the progress package, upgrades, missions and strikes. */
public final class ProgressText {
    private ProgressText() {}

    // Upgrade effects, one per upgrade key
    public static final String EFFECT_MEMBERS = Messages.define("progress.effectMembers", "Members plus 5 per level");
    public static final String EFFECT_REGEN = Messages.define("progress.effectRegen",
            "Power regenerates 25 percent faster for every member per level");
    public static final String EFFECT_MAXPOWER = Messages.define("progress.effectMaxpower", "Maximum power plus 2 per member per level");
    public static final String EFFECT_WARPS = Messages.define("progress.effectWarps", "Warps plus 2 per level");
    public static final String EFFECT_VAULT = Messages.define("progress.effectVault", "Vault plus 1 page per level");
    public static final String EFFECT_SHIELD = Messages.define("progress.effectShield",
            "The shield covers one more ring of chunks around the home per level");
    public static final String EFFECT_IMMUNITY = Messages.define("progress.effectImmunity",
            "One more hour of siege immunity after a defence per level");
    public static final String UNKNOWN_UPGRADE = Messages.define("progress.unknownUpgrade", "Unknown upgrade.");
    public static final String UPGRADE_DESCRIPTION = Messages.define("progress.upgradeDescription", "{effect}, level {level} of {max}");

    // Buying upgrades
    public static final String ZONES_CANNOT_UPGRADE = Messages.define("progress.zonesCannotUpgrade", "Server zones cannot buy upgrades.");
    public static final String NO_SUCH_UPGRADE = Messages.define("progress.noSuchUpgrade",
            "There is no upgrade called {key}. Choose one of {choices}.");
    public static final String RANK_CANNOT_UPGRADE = Messages.define("progress.rankCannotUpgrade", "Your rank cannot buy upgrades.");
    public static final String UPGRADE_MAXED = Messages.define("progress.upgradeMaxed", "This upgrade is already at its highest level.");
    public static final String UPGRADE_LABEL = Messages.define("progress.upgradeLabel", "{upgrade} upgrade level {level}");
    public static final String UPGRADE_NO_ECONOMY = Messages.define("progress.upgradeNoEconomy",
            "The economy is turned off so upgrades cannot be bought with coins.");
    public static final String UPGRADE_CHANGED = Messages.define("progress.upgradeChanged",
            "The upgrade changed while you were buying it. Try again.");
    public static final String UPGRADE_BANK_SHORT = Messages.define("progress.upgradeBankShort",
            "The faction bank needs {cost} for this upgrade.");
    public static final String UPGRADE_POINTS_SHORT = Messages.define("progress.upgradePointsShort",
            "Your faction needs {cost} points for this upgrade and has {points}.");
    public static final String POINTS = Messages.define("progress.points", "{points} points");
    public static final String UPGRADE_AUDIT = Messages.define("progress.upgradeAudit", "{player} bought the {upgrade} for {paid}.");
    public static final String UPGRADE_BOUGHT = Messages.define("progress.upgradeBought", "Your faction bought the {upgrade} for {paid}.");

    // Missions
    public static final String MISSION_BANK_REASON = Messages.define("progress.missionBankReason", "Mission {mission}");
    public static final String REWARD_COINS = Messages.define("progress.rewardCoins", "{coins} for the bank");
    public static final String REWARD_COINS_AND_POINTS = Messages.define("progress.rewardCoinsAndPoints",
            "{coins} for the bank and {points} points");
    public static final String DAILY_DONE = Messages.define("progress.dailyDone", "Your faction completed the daily mission {mission}.");
    public static final String DAILY_DONE_REWARD = Messages.define("progress.dailyDoneReward",
            "Your faction completed the daily mission {mission} and earned {reward}.");
    public static final String WEEKLY_DONE = Messages.define("progress.weeklyDone", "Your faction completed the weekly mission {mission}.");
    public static final String WEEKLY_DONE_REWARD = Messages.define("progress.weeklyDoneReward",
            "Your faction completed the weekly mission {mission} and earned {reward}.");
    public static final String MISSION_AUDIT = Messages.define("progress.missionAudit", "The faction completed the mission {mission}.");
    public static final String MISSION_AUDIT_REWARD = Messages.define("progress.missionAuditReward",
            "The faction completed the mission {mission} and earned {reward}.");

    // Strikes
    public static final String STRIKE_TOO_SMALL = Messages.define("progress.strikeTooSmall", "A strike must be worth at least one point.");
    public static final String STRIKE_NO_REASON = Messages.define("progress.strikeNoReason", "Give a reason for the strike.");
    public static final String AN_ADMIN = Messages.define("progress.anAdmin", "An admin");
    public static final String CONSOLE = Messages.define("progress.console", "Console");
    public static final String SOMEONE = Messages.define("progress.someone", "Someone");
    public static final String STRIKE_AUDIT_ONE = Messages.define("progress.strikeAuditOne",
            "{admin} gave the faction a strike worth {points} point because {reason}");
    public static final String STRIKE_AUDIT_MANY = Messages.define("progress.strikeAuditMany",
            "{admin} gave the faction a strike worth {points} points because {reason}");
    public static final String STRIKE_GIVEN_ONE = Messages.define("progress.strikeGivenOne",
            "{faction} received strike number {number} worth {points} point. Its value is now {value}.");
    public static final String STRIKE_GIVEN_MANY = Messages.define("progress.strikeGivenMany",
            "{faction} received strike number {number} worth {points} points. Its value is now {value}.");
    public static final String NO_STRIKE_NUMBER = Messages.define("progress.noStrikeNumber", "{faction} has no strike number {index}.");
    public static final String UNSTRIKE_AUDIT = Messages.define("progress.unstrikeAudit", "{admin} removed the strike given for {reason}");
    public static final String UNSTRUCK = Messages.define("progress.unstruck",
            "Removed strike number {index} from {faction}. Its value is now {value}.");

    // Mission names and descriptions, by template id
    public static final String PROSPECTORS_NAME = Messages.define("progress.missionProspectorsName", "Prospectors");
    public static final String PROSPECTORS_DESC = Messages.define("progress.missionProspectorsDescription", "Mine 48 ore blocks.");
    public static final String ORE_RUSH_NAME = Messages.define("progress.missionOreRushName", "Ore Rush");
    public static final String ORE_RUSH_DESC = Messages.define("progress.missionOreRushDescription", "Mine 128 ore blocks.");
    public static final String TIMBER_NAME = Messages.define("progress.missionTimberName", "Timber");
    public static final String TIMBER_DESC = Messages.define("progress.missionTimberDescription", "Chop 64 tree trunks.");
    public static final String CLEAR_CUT_NAME = Messages.define("progress.missionClearCutName", "Clear Cut");
    public static final String CLEAR_CUT_DESC = Messages.define("progress.missionClearCutDescription", "Chop 160 tree trunks.");
    public static final String HARVEST_DAY_NAME = Messages.define("progress.missionHarvestDayName", "Harvest Day");
    public static final String HARVEST_DAY_DESC = Messages.define("progress.missionHarvestDayDescription", "Gather 64 crops.");
    public static final String FULL_GRANARY_NAME = Messages.define("progress.missionFullGranaryName", "Full Granary");
    public static final String FULL_GRANARY_DESC = Messages.define("progress.missionFullGranaryDescription", "Gather 200 crops.");
    public static final String CATCH_OF_THE_DAY_NAME = Messages.define("progress.missionCatchOfTheDayName", "Catch of the Day");
    public static final String CATCH_OF_THE_DAY_DESC = Messages.define("progress.missionCatchOfTheDayDescription", "Catch 12 fish.");
    public static final String SKIRMISH_NAME = Messages.define("progress.missionSkirmishName", "Skirmish");
    public static final String SKIRMISH_DESC = Messages.define("progress.missionSkirmishDescription",
            "Defeat 3 players from other factions.");
    public static final String MARKET_DAY_NAME = Messages.define("progress.missionMarketDayName", "Market Day");
    public static final String MARKET_DAY_DESC = Messages.define("progress.missionMarketDayDescription", "Complete 5 trades on the market.");
    public static final String PATHFINDERS_NAME = Messages.define("progress.missionPathfindersName", "Pathfinders");
    public static final String PATHFINDERS_DESC = Messages.define("progress.missionPathfindersDescription", "Claim 4 chunks of land.");
    public static final String MOTHER_LODE_NAME = Messages.define("progress.missionMotherLodeName", "Mother Lode");
    public static final String MOTHER_LODE_DESC = Messages.define("progress.missionMotherLodeDescription", "Mine 1,200 ore blocks.");
    public static final String LUMBER_BARON_NAME = Messages.define("progress.missionLumberBaronName", "Lumber Baron");
    public static final String LUMBER_BARON_DESC = Messages.define("progress.missionLumberBaronDescription", "Chop 1,500 tree trunks.");
    public static final String BREADBASKET_NAME = Messages.define("progress.missionBreadbasketName", "Breadbasket");
    public static final String BREADBASKET_DESC = Messages.define("progress.missionBreadbasketDescription", "Gather 2,000 crops.");
    public static final String DEEP_WATERS_NAME = Messages.define("progress.missionDeepWatersName", "Deep Waters");
    public static final String DEEP_WATERS_DESC = Messages.define("progress.missionDeepWatersDescription", "Catch 120 fish.");
    public static final String WAR_PATH_NAME = Messages.define("progress.missionWarPathName", "War Path");
    public static final String WAR_PATH_DESC = Messages.define("progress.missionWarPathDescription",
            "Defeat 25 players from other factions.");
    public static final String MERCHANT_GUILD_NAME = Messages.define("progress.missionMerchantGuildName", "Merchant Guild");
    public static final String MERCHANT_GUILD_DESC = Messages.define("progress.missionMerchantGuildDescription",
            "Complete 40 trades on the market.");
    public static final String EXPANSION_NAME = Messages.define("progress.missionExpansionName", "Expansion");
    public static final String EXPANSION_DESC = Messages.define("progress.missionExpansionDescription", "Claim 24 chunks of land.");
    public static final String CONQUEST_NAME = Messages.define("progress.missionConquestName", "Conquest");
    public static final String CONQUEST_DESC = Messages.define("progress.missionConquestDescription",
            "Win a siege against an enemy faction.");
    public static final String SIEGE_MASTERS_NAME = Messages.define("progress.missionSiegeMastersName", "Siege Masters");
    public static final String SIEGE_MASTERS_DESC = Messages.define("progress.missionSiegeMastersDescription",
            "Win 3 sieges against enemy factions.");
}
