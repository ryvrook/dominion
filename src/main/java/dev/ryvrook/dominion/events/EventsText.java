package dev.ryvrook.dominion.events;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the events, their panel and commands, editable in messages.json. */
public final class EventsText {
    private EventsText() {}

    // ---- shared pieces
    public static final String WHERE = Messages.define("events.where", "{x}, {y}, {z} in {world}");
    public static final String COORDS = Messages.define("events.coords", "{x}, {y}, {z}");
    public static final String DISBANDED_FACTION = Messages.define("events.disbandedFaction", "a disbanded faction");
    public static final String SERVER = Messages.define("events.server", "Server");
    public static final String TURNED_OFF = Messages.define("events.turnedOff", "Events are turned off on this server.");
    public static final String POSITION_UNKNOWN = Messages.define("events.positionUnknown", "Your position is not known yet. Try again in a moment.");
    public static final String LIST_AND = Messages.define("events.listAnd", "{list} and {last}");
    public static final String SCHEDULE_EVERY = Messages.define("events.scheduleEvery", "every {minutes} minutes");
    public static final String SCHEDULE_DAILY = Messages.define("events.scheduleDaily", "daily at {times}");
    public static final String BOOST_XP = Messages.define("events.boostXp", "an xp boost");
    public static final String BOOST_MISSIONS = Messages.define("events.boostMissions", "a missions boost");
    public static final String BOOST_TAX = Messages.define("events.boostTax", "a tax boost");
    public static final String BOOST_SELL = Messages.define("events.boostSell", "a sell boost");
    public static final String BOOST_OTHER = Messages.define("events.boostOther", "a {kind} boost");
    public static final String BOOST_OTHER_VOWEL = Messages.define("events.boostOtherVowel", "an {kind} boost");
    public static final String DAYS_ONE = Messages.define("events.daysOne", "{count} day");
    public static final String DAYS_MANY = Messages.define("events.daysMany", "{count} days");
    public static final String FACTIONS_ONE = Messages.define("events.factionsOne", "{count} faction");
    public static final String FACTIONS_MANY = Messages.define("events.factionsMany", "{count} factions");
    public static final String COMMANDS_ONE = Messages.define("events.commandsOne", "{count} payout command");
    public static final String COMMANDS_MANY = Messages.define("events.commandsMany", "{count} payout commands");

    // ---- hills
    public static final String HILL_NAME_RULES = Messages.define("events.hillNameRules", "Hill names use letters, numbers, dashes and underscores, up to 24 characters.");
    public static final String HILL_RADIUS_MAX = Messages.define("events.hillRadiusMax", "A hill radius can be at most {max} blocks.");
    public static final String HILL_EXISTS = Messages.define("events.hillExists", "A hill named {hill} already exists.");
    public static final String HILL_SET = Messages.define("events.hillSet", "Hill {hill} is set at {where} with a radius of {radius} blocks.");
    public static final String NO_SUCH_HILL = Messages.define("events.noSuchHill", "No hill is named {hill}.");
    public static final String HILL_REMOVED = Messages.define("events.hillRemoved", "Hill {hill} was removed.");
    public static final String HILL_ALREADY_OPEN = Messages.define("events.hillAlreadyOpen", "Hill {hill} is already open.");
    public static final String HILL_OPENED = Messages.define("events.hillOpened", "Hill {hill} is open.");
    public static final String HILL_NOT_OPEN = Messages.define("events.hillNotOpen", "Hill {hill} is not open.");
    public static final String HILL_CLOSED_BY_ADMIN = Messages.define("events.hillClosedByAdmin", "Hill {hill} was closed by an admin with no winner.");
    public static final String HILL_CLOSED = Messages.define("events.hillClosed", "Hill {hill} is closed.");
    public static final String HILL_UNSCHEDULED = Messages.define("events.hillUnscheduled", "Hill {hill} no longer opens on a schedule.");
    public static final String SCHEDULE_USAGE = Messages.define("events.scheduleUsage", "Use every and a number of minutes such as every 30m, or at and times such as at 18:00,21:30.");
    public static final String HILL_SCHEDULED = Messages.define("events.hillScheduled", "Hill {hill} opens {schedule}. The next opening is in {time}.");
    public static final String HILL_STATUS_CONTESTED = Messages.define("events.hillStatusContested", "Hill {hill} at {where} is contested. It closes in {time}.");
    public static final String HILL_STATUS_FREE = Messages.define("events.hillStatusFree", "Hill {hill} at {where} is open and nobody holds it. It closes in {time}.");
    public static final String HILL_STATUS_HELD = Messages.define("events.hillStatusHeld", "Hill {hill} at {where} is open and {faction} has held it for {held} of {cap}. It closes in {time}.");
    public static final String HILL_STATUS_SCHEDULED = Messages.define("events.hillStatusScheduled", "Hill {hill} at {where} is closed and opens in {time}, {schedule}.");
    public static final String HILL_STATUS_UNSCHEDULED = Messages.define("events.hillStatusUnscheduled", "Hill {hill} at {where} is closed with no schedule.");
    public static final String HILL_LAST_WON = Messages.define("events.hillLastWon", "Last won by {faction} {ago}.");
    public static final String HILL_OPEN_ANNOUNCE = Messages.define("events.hillOpenAnnounce", "Hill {hill} is open at {where}. Hold it for {time} to win {reward}.");
    public static final String REWARD_COINS_YOURS = Messages.define("events.rewardCoinsYours", "{amount} for your faction bank");
    public static final String REWARD_COINS_ITS = Messages.define("events.rewardCoinsIts", "{amount} for its bank");
    public static final String REWARD_POINTS = Messages.define("events.rewardPoints", "{points} faction points");
    public static final String REWARD_BOTH = Messages.define("events.rewardBoth", "{coins} and {points}");
    public static final String REWARD_NONE = Messages.define("events.rewardNone", "the hill");
    public static final String HILL_OPENS_ONE_MINUTE = Messages.define("events.hillOpensOneMinute", "Hill {hill} opens in one minute at {where}.");
    public static final String HILL_OPENS_TEN_MINUTES = Messages.define("events.hillOpensTenMinutes", "Hill {hill} opens in ten minutes at {where}.");
    public static final String HILL_TIMED_OUT = Messages.define("events.hillTimedOut", "Hill {hill} closed after an hour with no winner.");
    public static final String HILL_CONTESTED = Messages.define("events.hillContested", "Hill {hill} is contested.");
    public static final String HILL_TAKING = Messages.define("events.hillTaking", "{faction} is taking hill {hill}.");
    public static final String HILL_HOLDS = Messages.define("events.hillHolds", "{faction} holds hill {hill} and wins in {time}.");
    public static final String MEMO_HILL = Messages.define("events.memoHill", "capturing hill {hill}");
    public static final String AUDIT_HILL = Messages.define("events.auditHill", "Captured hill {hill}");
    public static final String HILL_CAPTURED = Messages.define("events.hillCaptured", "{faction} captured hill {hill} and won {reward}.");

    // ---- outposts
    public static final String OUTPOST_NAME_RULES = Messages.define("events.outpostNameRules", "Outpost names use letters, numbers, dashes and underscores, up to 24 characters.");
    public static final String BOOST_KINDS = Messages.define("events.boostKinds", "The boost must be xp, missions, tax or sell.");
    public static final String MULTIPLIER_RANGE = Messages.define("events.multiplierRange", "The multiplier must be above 0 and at most 10.");
    public static final String OUTPOST_RADIUS_MAX = Messages.define("events.outpostRadiusMax", "An outpost radius can be at most {max} blocks.");
    public static final String OUTPOST_EXISTS = Messages.define("events.outpostExists", "An outpost named {outpost} already exists.");
    public static final String OUTPOST_SET = Messages.define("events.outpostSet", "Outpost {outpost} is set at {where} with a radius of {radius} blocks. Holding it gives {boost} of {multiplier} times.");
    public static final String NO_SUCH_OUTPOST = Messages.define("events.noSuchOutpost", "No outpost is named {outpost}.");
    public static final String OUTPOST_REMOVED = Messages.define("events.outpostRemoved", "Outpost {outpost} was removed.");
    public static final String OUTPOST_RESET_ALERT = Messages.define("events.outpostResetAlert", "An admin reset outpost {outpost}. Your faction no longer holds it.");
    public static final String OUTPOST_RESET = Messages.define("events.outpostReset", "Outpost {outpost} was reset and nobody holds it.");
    public static final String OUTPOST_STATUS = Messages.define("events.outpostStatus", "Outpost {outpost} at {where} gives {boost} of {multiplier} times");
    public static final String OUTPOST_STATUS_UNHELD = Messages.define("events.outpostStatusUnheld", ". Nobody holds it");
    public static final String OUTPOST_STATUS_HELD = Messages.define("events.outpostStatusHeld", ". {faction} holds it with {control} percent control");
    public static final String OUTPOST_STATUS_CHALLENGER = Messages.define("events.outpostStatusChallenger", " and {faction} is at {control} percent");
    public static final String OUTPOST_STATUS_END = Messages.define("events.outpostStatusEnd", ".");
    public static final String OUTPOST_STATUS_CONTESTED = Messages.define("events.outpostStatusContested", ". It is contested.");
    public static final String OUTPOST_TAKEN = Messages.define("events.outpostTaken", "{faction} took outpost {outpost}.");
    public static final String OUTPOST_TAKEN_FROM = Messages.define("events.outpostTakenFrom", "{faction} took outpost {outpost} from {loser}.");
    public static final String OUTPOST_WON = Messages.define("events.outpostWon", "Your faction took outpost {outpost}. You now get {boost} of {multiplier} times while you hold it.");
    public static final String AUDIT_OUTPOST_TOOK = Messages.define("events.auditOutpostTook", "Took outpost {outpost}");
    public static final String OUTPOST_LOST = Messages.define("events.outpostLost", "{faction} took outpost {outpost} from your faction.");
    public static final String AUDIT_OUTPOST_LOST = Messages.define("events.auditOutpostLost", "Lost outpost {outpost} to {faction}");

    // ---- envoys
    public static final String NO_SUCH_ZONE = Messages.define("events.noSuchZone", "No zone has the tag {tag}.");
    public static final String NOT_WAR_ZONE = Messages.define("events.notWarZone", "{zone} is not a war zone.");
    public static final String ZONE_NO_LAND = Messages.define("events.zoneNoLand", "{zone} has no claimed land for an envoy to land on.");
    public static final String ENVOY_NO_WORLD = Messages.define("events.envoyNoWorld", "An envoy dropped in {zone} at {where}. Its world is not loaded so no chest was placed.");
    public static final String ENVOY_DROPPING = Messages.define("events.envoyDropping", "An envoy is dropping in {zone}.");
    public static final String ENVOY_LANDED = Messages.define("events.envoyLanded", "An envoy landed in {zone} at {where}. Stand next to it and run /fe envoy claim within {time} to take the loot.");
    public static final String ENVOY_TIMER_MIN = Messages.define("events.envoyTimerMin", "The shortest envoy timer is 5 minutes.");
    public static final String ENVOY_TIMER_OFF = Messages.define("events.envoyTimerOff", "Envoys no longer drop in {zone} on a timer.");
    public static final String ENVOY_TIMER_SET = Messages.define("events.envoyTimerSet", "An envoy drops in {zone} every {minutes} minutes.");
    public static final String ENVOY_LINE = Messages.define("events.envoyLine", "Envoy in {zone} at {where} vanishes in {time}.");
    public static final String ENVOY_LINE_NO_CHEST = Messages.define("events.envoyLineNoChest", "Envoy in {zone} at {where} vanishes in {time}. No chest could be placed so it is marked by coordinates only.");
    public static final String ENVOY_TIMER_LINE = Messages.define("events.envoyTimerLine", "Envoys drop in {zone} every {minutes} minutes. The next one lands in {time}.");
    public static final String ENVOY_NEEDS_MARKET = Messages.define("events.envoyNeedsMarket", "Envoy loot is delivered through the market mailbox, which is not installed.");
    public static final String NO_ENVOY_NEAR = Messages.define("events.noEnvoyNear", "No envoy is within {distance} blocks of you.");
    public static final String ENVOY_CLAIMED = Messages.define("events.envoyClaimed", "{player} claimed the envoy in {zone}.");
    public static final String ENVOY_LOOT_MAILED = Messages.define("events.envoyLootMailed", "The envoy loot is in your market mailbox. Collect it with /f market collect.");
    public static final String ENVOY_VANISHED = Messages.define("events.envoyVanished", "The envoy in {zone} at {where} vanished unclaimed.");

    // ---- bounties
    public static final String PLAYER_NOT_FOUND = Messages.define("events.playerNotFound", "That player could not be found.");
    public static final String BOUNTY_SELF = Messages.define("events.bountySelf", "You cannot place a bounty on yourself.");
    public static final String BOUNTY_NO_ECONOMY = Messages.define("events.bountyNoEconomy", "Bounties need the economy, which is turned off.");
    public static final String BOUNTY_MIN = Messages.define("events.bountyMin", "The smallest bounty is {amount}.");
    public static final String BOUNTY_WHOLE = Messages.define("events.bountyWhole", "Bounties must be whole amounts.");
    public static final String MEMO_BOUNTY = Messages.define("events.memoBounty", "bounty on {player}");
    public static final String BOUNTY_PLACED_ANNOUNCE = Messages.define("events.bountyPlacedAnnounce", "{placer} put {amount} on the head of {player}. The bounty is now {total}.");
    public static final String BOUNTY_PLACED = Messages.define("events.bountyPlaced", "Your bounty of {amount} on {player} is placed.");
    public static final String BOUNTY_LINE_ONE = Messages.define("events.bountyLineOne", "{player} is worth {amount} from {count} placer.");
    public static final String BOUNTY_LINE_MANY = Messages.define("events.bountyLineMany", "{player} is worth {amount} from {count} placers.");
    public static final String BOUNTY_COLLECTED = Messages.define("events.bountyCollected", "{killer} collected the bounty of {amount} on {player}.");

    // ---- season
    public static final String DAYS_NEGATIVE = Messages.define("events.daysNegative", "Days cannot be negative.");
    public static final String SEASON_RUNNING = Messages.define("events.seasonRunning", "A season is already running. End it first with /fe season end.");
    public static final String GRACE_NEEDS_SHIELD = Messages.define("events.graceNeedsShield", "Grace needs the shield module, which is not installed.");
    public static final String SEASON_BEGUN = Messages.define("events.seasonBegun", "A new season has begun.");
    public static final String SEASON_BEGUN_GRACE = Messages.define("events.seasonBegunGrace", "Grace lasts {days}.");
    public static final String SEASON_BEGUN_EOTW = Messages.define("events.seasonBegunEotw", "The end of the world comes in {days}.");
    public static final String SEASON_STARTED = Messages.define("events.seasonStarted", "The season started.");
    public static final String SEASON_ALREADY_ENDED = Messages.define("events.seasonAlreadyEnded", "The season already ended {ago}. Start a new one first.");
    public static final String SEASON_OVER = Messages.define("events.seasonOver", "The season is over and the end of the world has begun.");
    public static final String SEASON_TOP_ONE = Messages.define("events.seasonTopOne", "The top faction is {factions}.");
    public static final String SEASON_TOP_MANY = Messages.define("events.seasonTopMany", "The top factions are {factions}.");
    public static final String SEASON_ENDED = Messages.define("events.seasonEnded", "The season ended and the leaderboard is frozen.");
    public static final String STATUS_STARTED = Messages.define("events.statusStarted", "The season started {ago}.");
    public static final String STATUS_GRACE_ENDS = Messages.define("events.statusGraceEnds", "Grace ends in {time}.");
    public static final String STATUS_GRACE_ON = Messages.define("events.statusGraceOn", "Grace is on.");
    public static final String STATUS_EOTW_IN = Messages.define("events.statusEotwIn", "The end of the world begins in {time}.");
    public static final String STATUS_EOTW_RUNNING = Messages.define("events.statusEotwRunning", "The season is over and the end of the world is running.");
    public static final String STATUS_NO_SEASON = Messages.define("events.statusNoSeason", "No season is running.");
    public static final String STATUS_FROZEN = Messages.define("events.statusFrozen", "The leaderboard was frozen {ago}.");
    public static final String STATUS_PAYOUTS_RAN = Messages.define("events.statusPayoutsRan", "Payouts ran {ago}.");

    // ---- payouts
    public static final String NO_QUALIFIERS = Messages.define("events.noQualifiers", "No factions qualify for payouts yet.");
    public static final String NO_PAYOUT_COMMANDS_HINT = Messages.define("events.noPayoutCommandsHint", "No payout commands are set. Add them to events.payoutCommands in config.json, separated by pipes.");
    public static final String PAYOUTS_FROZEN = Messages.define("events.payoutsFrozen", "Payouts use the leaderboard frozen at the end of the season.");
    public static final String PAYOUTS_LIVE = Messages.define("events.payoutsLive", "No season has ended so payouts use the live leaderboard.");
    public static final String PAYOUT_LINE = Messages.define("events.payoutLine", "Rank {rank} {faction} runs /{command}");
    public static final String PAYOUTS_PERMISSION = Messages.define("events.payoutsPermission", "Running payouts needs the {permission} permission.");
    public static final String NO_PAYOUT_COMMANDS = Messages.define("events.noPayoutCommands", "No payout commands are set in events.payoutCommands in config.json.");
    public static final String PAYOUTS_ALREADY_RAN = Messages.define("events.payoutsAlreadyRan", "Payouts already ran for this season {ago}.");
    public static final String PAYOUT_RECORD_FAILED = Messages.define("events.payoutRecordFailed", "The payout record could not be saved, so no payout commands ran. Check the log.");
    public static final String PAYOUTS_RAN_FEED = Messages.define("events.payoutsRanFeed", "Season payouts ran for {factions}.");
    public static final String NO_COMMAND_MANAGER = Messages.define("events.noCommandManager", "The command manager is not available, so the payout commands were only written to the log.");
    public static final String PAYOUTS_SENT = Messages.define("events.payoutsSent", "Sent {commands} to the console.");
    public static final String PAYOUTS_SENT_FAILED = Messages.define("events.payoutsSentFailed", "Sent {commands} to the console and {failed} failed. Check the log.");

    // ---- map points and panel rows
    public static final String POI_HILL = Messages.define("events.poiHill", "Hill {hill}");
    public static final String POI_HILL_OPEN = Messages.define("events.poiHillOpen", "Hill {hill} is open");
    public static final String POI_OUTPOST = Messages.define("events.poiOutpost", "Outpost {outpost}");
    public static final String POI_OUTPOST_HELD = Messages.define("events.poiOutpostHeld", "Outpost {outpost} held by {faction}");
    public static final String POI_ENVOY = Messages.define("events.poiEnvoy", "Envoy in {zone}");
    public static final String ROW_CONTESTED = Messages.define("events.rowContested", "Contested");
    public static final String ROW_OPEN = Messages.define("events.rowOpen", "Open");
    public static final String ROW_HELD = Messages.define("events.rowHeld", "{faction} {percent}%");
    public static final String ROW_CLOSES_IN = Messages.define("events.rowClosesIn", "closes in {time}");
    public static final String ROW_OPENS_IN = Messages.define("events.rowOpensIn", "opens in {time}");
    public static final String ROW_NO_SCHEDULE = Messages.define("events.rowNoSchedule", "no schedule");
    public static final String ROW_CLOSED = Messages.define("events.rowClosed", "Closed");
    public static final String ROW_WON_BY = Messages.define("events.rowWonBy", "Won by {faction}");
    public static final String ROW_UNHELD = Messages.define("events.rowUnheld", "Unheld");
    public static final String ROW_BOOST = Messages.define("events.rowBoost", "{boost} x{multiplier}");
    public static final String ROW_BOOST_CONTESTED = Messages.define("events.rowBoostContested", "{boost} x{multiplier}, contested");
    public static final String ROW_VANISHES_IN = Messages.define("events.rowVanishesIn", "vanishes in {time}");
    public static final String ROW_DROPS_IN = Messages.define("events.rowDropsIn", "Drops in {zone}");
    public static final String ROW_EVERY = Messages.define("events.rowEvery", "every {minutes}m");
    public static final String ROW_NEXT_IN = Messages.define("events.rowNextIn", "next in {time}");
    public static final String ROW_PLACER = Messages.define("events.rowPlacer", "{count} placer");
    public static final String ROW_PLACERS = Messages.define("events.rowPlacers", "{count} placers");

    // ---- panel
    public static final String PAGE_FAILED = Messages.define("events.pageFailed", "Part of this page could not be shown. Try again in a moment.");
    public static final String PANEL_TITLE = Messages.define("events.panelTitle", "Events");
    public static final String PANEL_HEADING = Messages.define("events.panelHeading", "Faction events");
    public static final String PANEL_SUBHEADING = Messages.define("events.panelSubheading", "Hills, outposts, envoys and bounties");
    public static final String PANEL_OFF = Messages.define("events.panelOff", "Events are turned off");
    public static final String BUTTON_REFRESH = Messages.define("events.buttonRefresh", "Refresh");
    public static final String BUTTON_CLAIM = Messages.define("events.buttonClaim", "Claim envoy");
    public static final String SECTION_HILLS = Messages.define("events.sectionHills", "Hills");
    public static final String SECTION_OUTPOSTS = Messages.define("events.sectionOutposts", "Outposts");
    public static final String SECTION_ENVOYS = Messages.define("events.sectionEnvoys", "Envoys");
    public static final String SECTION_BOUNTIES = Messages.define("events.sectionBounties", "Bounties");
    public static final String SECTION_SEASON = Messages.define("events.sectionSeason", "Season");
    public static final String FINAL_STANDINGS = Messages.define("events.finalStandings", "Final standings");
    public static final String NO_HILLS = Messages.define("events.noHills", "No hills are set.");
    public static final String NO_OUTPOSTS = Messages.define("events.noOutposts", "No outposts are set.");
    public static final String NO_ENVOYS_NOW = Messages.define("events.noEnvoysNow", "No envoys are out right now.");
    public static final String NO_ENVOYS = Messages.define("events.noEnvoys", "No envoys are out.");
    public static final String NO_ENVOYS_OR_TIMERS = Messages.define("events.noEnvoysOrTimers", "No envoys are out and none are scheduled.");
    public static final String NO_BOUNTIES = Messages.define("events.noBounties", "No bounties are out.");
    public static final String NOTHING_TO_PAY = Messages.define("events.nothingToPay", "Nothing to pay out.");
    public static final String PANEL_IN_GAME = Messages.define("events.panelInGame", "The panel can only be opened in game.");
    public static final String NO_PLAYER_NAMED = Messages.define("events.noPlayerNamed", "No player named {player} was found.");

    // ---- commands
    public static final String CMD_FE = Messages.define("events.cmdFe", "Faction events. KOTH hills, outposts, envoys, bounties and the season.");
    public static final String CMD_LIST = Messages.define("events.cmdList", "Show every event with its status.");
    public static final String CMD_PANEL = Messages.define("events.cmdPanel", "Open the events panel.");
    public static final String CMD_KOTH = Messages.define("events.cmdKoth", "List KOTH hills.");
    public static final String CMD_KOTH_CREATE = Messages.define("events.cmdKothCreate", "Create a hill where you stand.");
    public static final String ARG_HILL_NAME = Messages.define("events.argHillName", "Hill name");
    public static final String CMD_KOTH_CREATE_RADIUS = Messages.define("events.cmdKothCreateRadius", "Create a hill with a radius in blocks.");
    public static final String ARG_RADIUS = Messages.define("events.argRadius", "Radius in blocks");
    public static final String CMD_KOTH_DELETE = Messages.define("events.cmdKothDelete", "Delete a hill.");
    public static final String CMD_KOTH_START = Messages.define("events.cmdKothStart", "Open a hill now.");
    public static final String CMD_KOTH_STOP = Messages.define("events.cmdKothStop", "Close a hill with no winner.");
    public static final String CMD_KOTH_SCHEDULE = Messages.define("events.cmdKothSchedule", "Schedule a hill with every 30m or at 18:00,21:30.");
    public static final String ARG_MODE = Messages.define("events.argMode", "every or at");
    public static final String ARG_WHEN = Messages.define("events.argWhen", "Minutes such as 30m, or times such as 18:00,21:30");
    public static final String CMD_KOTH_SCHEDULE_OFF = Messages.define("events.cmdKothScheduleOff", "Clear a hill schedule with off.");
    public static final String ARG_OFF = Messages.define("events.argOff", "off");
    public static final String CMD_OUTPOST = Messages.define("events.cmdOutpost", "List outposts.");
    public static final String CMD_OUTPOST_CREATE = Messages.define("events.cmdOutpostCreate", "Create an outpost where you stand. Boosts are xp, missions, tax or sell.");
    public static final String ARG_OUTPOST_NAME = Messages.define("events.argOutpostName", "Outpost name");
    public static final String ARG_BOOST = Messages.define("events.argBoost", "xp, missions, tax or sell");
    public static final String ARG_MULTIPLIER = Messages.define("events.argMultiplier", "Boost multiplier such as 1.25");
    public static final String CMD_OUTPOST_CREATE_RADIUS = Messages.define("events.cmdOutpostCreateRadius", "Create an outpost with a radius in blocks.");
    public static final String CMD_OUTPOST_DELETE = Messages.define("events.cmdOutpostDelete", "Delete an outpost.");
    public static final String CMD_OUTPOST_RESET = Messages.define("events.cmdOutpostReset", "Clear the holder and control of an outpost.");
    public static final String CMD_ENVOY = Messages.define("events.cmdEnvoy", "List envoys and envoy timers.");
    public static final String CMD_ENVOY_SPAWN = Messages.define("events.cmdEnvoySpawn", "Drop an envoy in a war zone now.");
    public static final String ARG_ZONE = Messages.define("events.argZone", "War zone tag");
    public static final String CMD_ENVOY_SCHEDULE = Messages.define("events.cmdEnvoySchedule", "Drop envoys in a war zone every few minutes, or 0 to stop.");
    public static final String ARG_MINUTES = Messages.define("events.argMinutes", "Minutes between drops, 0 to stop");
    public static final String CMD_ENVOY_CLAIM = Messages.define("events.cmdEnvoyClaim", "Take the loot of an envoy next to you.");
    public static final String CMD_SEASON = Messages.define("events.cmdSeason", "Show the season status.");
    public static final String CMD_SEASON_START = Messages.define("events.cmdSeasonStart", "Start a season with no grace and no end date.");
    public static final String CMD_SEASON_START_GRACE = Messages.define("events.cmdSeasonStartGrace", "Start a season with grace days.");
    public static final String ARG_GRACE_DAYS = Messages.define("events.argGraceDays", "Days of grace");
    public static final String CMD_SEASON_START_BOTH = Messages.define("events.cmdSeasonStartBoth", "Start a season with grace days and raid days before the end of the world.");
    public static final String ARG_RAID_DAYS = Messages.define("events.argRaidDays", "Days of raiding after grace");
    public static final String CMD_SEASON_END = Messages.define("events.cmdSeasonEnd", "End the season, start the end of the world and freeze the leaderboard.");
    public static final String CMD_BOUNTY = Messages.define("events.cmdBounty", "List bounties.");
    public static final String CMD_BOUNTY_PLACE = Messages.define("events.cmdBountyPlace", "Put money on a player's head.");
    public static final String ARG_PLAYER = Messages.define("events.argPlayer", "Player name");
    public static final String ARG_AMOUNT = Messages.define("events.argAmount", "Amount to put up");
    public static final String CMD_PAYOUTS = Messages.define("events.cmdPayouts", "Preview the season payout commands.");
    public static final String CMD_PAYOUTS_RUN = Messages.define("events.cmdPayoutsRun", "Run the season payout commands from the console.");
    public static final String ARG_NAME = Messages.define("events.argName", "Name");
}
