package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.text.Messages;

/** Every sentence the pages, the sidebar and the territory banners show, editable in messages.json. */
public final class UiText {
    private UiText() {}

    // Shared page plumbing
    public static final String STATE_ON = Messages.define("ui.stateOn", "On");
    public static final String STATE_OFF = Messages.define("ui.stateOff", "Off");
    public static final String TURN_ON = Messages.define("ui.turnOn", "Turn on");
    public static final String TURN_OFF = Messages.define("ui.turnOff", "Turn off");
    public static final String PALETTE_NOW = Messages.define("ui.paletteNow", "Now {color}");
    public static final String PAGE_RENDER_FAILED = Messages.define("ui.pageRenderFailed", "Part of this page could not be shown. Try again in a moment.");
    public static final String BUTTON_FAILED = Messages.define("ui.buttonFailed", "Something went wrong with that button. Try again in a moment.");
    public static final String DONE = Messages.define("ui.done", "Done.");
    public static final String DID_NOT_WORK = Messages.define("ui.didNotWork", "That did not work.");

    // Confirmation page
    public static final String CONFIRM_DEFAULT_TITLE = Messages.define("ui.confirmDefaultTitle", "Are you sure");
    public static final String CONFIRM_WAIT = Messages.define("ui.confirmWait", "Read this first. Confirm unlocks in a few seconds.");

    // Admin settings page
    public static final String PRESETS_TAB = Messages.define("ui.presetsTab", "Presets");
    public static final String PRESET_CLASSIC = Messages.define("ui.presetClassic", "Raiding by power, dominance and sieges. The standard factions experience.");
    public static final String PRESET_HARDCORE = Messages.define("ui.presetHardcore", "Raids at any time, heavy death penalties and fast sieges.");
    public static final String PRESET_PEACEFUL = Messages.define("ui.presetPeaceful", "No raiding and no neutral PvP. Factions build and trade in peace.");
    public static final String PRESET_SINGLEPLAYER = Messages.define("ui.presetSingleplayer", "No raids, sieges or power loss. For solo worlds and small groups.");
    public static final String SETTINGS_TITLE = Messages.define("ui.settingsTitle", "Settings");
    public static final String SETTINGS_NEED_ADMIN = Messages.define("ui.settingsNeedAdmin", "You need the dominion.admin permission to change settings.");
    public static final String PRESET_ACTIVE_HINT = Messages.define("ui.presetActiveHint", "Active preset is {preset}");
    public static final String PRESETS_EXPLAINED = Messages.define("ui.presetsExplained", "A preset sets the raiding, PvP, power loss and event rules together. Every preset sets the same rules, "
            + "so switching back and forth never leaves one behind. Everything else stays as it is.");
    public static final String APPLY = Messages.define("ui.apply", "Apply");
    public static final String SETTINGS_LIVE_HINT = Messages.define("ui.settingsLiveHint", "Changes save at once and apply live");
    public static final String SETTING_FILE_ONLY_NOTE = Messages.define("ui.settingFileOnlyNote", "{key} runs console commands, so it can only be changed in config.json. "
            + "Running season payouts needs the dominion.admin.payouts permission.");
    public static final String SETTING_DEFAULT = Messages.define("ui.settingDefault", "default {value}");
    public static final String SETTING_RANGE_UP = Messages.define("ui.settingRangeUp", "from {min} up");
    public static final String SETTING_RANGE = Messages.define("ui.settingRange", "from {min} to {max}");
    public static final String SETTING_CHOICES = Messages.define("ui.settingChoices", "one of {choices}");
    public static final String SETTING_FILE_ONLY = Messages.define("ui.settingFileOnly", "{key} can only be changed in config.json.");
    public static final String SETTING_NOW_ON = Messages.define("ui.settingNowOn", "{key} is now on.");
    public static final String SETTING_NOW_OFF = Messages.define("ui.settingNowOff", "{key} is now off.");
    public static final String SETTING_NOW = Messages.define("ui.settingNow", "{key} is now {value}.");
    public static final String NO_SUCH_PRESET = Messages.define("ui.noSuchPreset", "There is no preset called {name}.");
    public static final String PRESET_CONFIRM_TITLE = Messages.define("ui.presetConfirmTitle", "Apply the {name} preset");
    public static final String PRESET_CONFIRM_BODY = Messages.define("ui.presetConfirmBody", "Many settings change at once and apply to every player right away. You can switch presets again later.");
    public static final String PRESET_APPLIED = Messages.define("ui.presetApplied", "The {name} preset is active.");

    // Tutorial
    public static final String TUTORIAL_TITLE = Messages.define("ui.tutorialTitle", "Tutorial");
    public static final String TUTORIAL_STEP = Messages.define("ui.tutorialStep", "Step {step} of {total}");
    public static final String TUTORIAL_1_TITLE = Messages.define("ui.tutorial1Title", "Welcome to Dominion");
    public static final String TUTORIAL_1_TEXT = Messages.define("ui.tutorial1Text", "Factions are groups of players who claim land together and protect it.");
    public static final String TUTORIAL_1_HINT = Messages.define("ui.tutorial1Hint", "Open the faction panel any time with /f. It has everything in this tutorial and more.");
    public static final String TUTORIAL_2_TITLE = Messages.define("ui.tutorial2Title", "Found or join a faction");
    public static final String TUTORIAL_2_TEXT = Messages.define("ui.tutorial2Text", "Type a short tag in the panel and press Create to found your own faction.");
    public static final String TUTORIAL_2_HINT = Messages.define("ui.tutorial2Hint", "Or join an open faction from the list, or accept an invite from a friend.");
    public static final String TUTORIAL_3_TITLE = Messages.define("ui.tutorial3Title", "Claim your land");
    public static final String TUTORIAL_3_TEXT = Messages.define("ui.tutorial3Text", "Stand where you want to build and press Claim on the land tab.");
    public static final String TUTORIAL_3_HINT = Messages.define("ui.tutorial3Hint", "Your power and your members' power decide how many chunks you can hold. Set a home so everyone can find base.");
    public static final String TUTORIAL_4_TITLE = Messages.define("ui.tutorial4Title", "Allies, enemies and raids");
    public static final String TUTORIAL_4_TEXT = Messages.define("ui.tutorial4Text", "Make allies to share access and set enemies to fight them.");
    public static final String TUTORIAL_4_HINT = Messages.define("ui.tutorial4Hint", "A faction that holds more land than its power can defend becomes raidable. Stay active and avoid needless deaths.");
    public static final String FINISH = Messages.define("ui.finish", "Finish");
    public static final String NEXT = Messages.define("ui.next", "Next");
    public static final String SKIP = Messages.define("ui.skip", "Skip");
    public static final String BACK = Messages.define("ui.back", "Back");

    // Labels shared by several pages
    public static final String LABEL_FOUNDED = Messages.define("ui.labelFounded", "Founded");
    public static final String LABEL_NATION = Messages.define("ui.labelNation", "Nation");
    public static final String LABEL_POWER = Messages.define("ui.labelPower", "Power");
    public static final String LABEL_LAND = Messages.define("ui.labelLand", "Land");
    public static final String LABEL_MEMBERS = Messages.define("ui.labelMembers", "Members");
    public static final String LABEL_JOINING = Messages.define("ui.labelJoining", "Joining");
    public static final String LABEL_STATUS = Messages.define("ui.labelStatus", "Status");
    public static final String LABEL_RELATIONS = Messages.define("ui.labelRelations", "Relations");
    public static final String LABEL_STOREFRONT = Messages.define("ui.labelStorefront", "Storefront");
    public static final String INFO = Messages.define("ui.info", "Info");
    public static final String BUY = Messages.define("ui.buy", "Buy");
    public static final String ONLINE = Messages.define("ui.online", "Online");
    public static final String RAIDABLE = Messages.define("ui.raidable", "Raidable");
    public static final String OPEN_TO_EVERYONE = Messages.define("ui.openToEveryone", "Open to everyone");
    public static final String INVITE_ONLY = Messages.define("ui.inviteOnly", "Invite only");
    public static final String POWER_OF_MAX = Messages.define("ui.powerOfMax", "{power} / {max}");
    public static final String CHUNKS = Messages.define("ui.chunks", "{count} chunks");
    public static final String ONLINE_OF = Messages.define("ui.onlineOf", "{online} online of {total}");
    public static final String POWER_AMOUNT = Messages.define("ui.powerAmount", "{power} power");
    public static final String PRICE_EACH = Messages.define("ui.priceEach", "{price} each");

    // Faction info page
    public static final String FACTION_INFO_TITLE = Messages.define("ui.factionInfoTitle", "Faction info");
    public static final String TOP_FACTIONS = Messages.define("ui.topFactions", "Top factions");
    public static final String YOUR_PANEL = Messages.define("ui.yourPanel", "Your panel");
    public static final String UNKNOWN_FACTION = Messages.define("ui.unknownFaction", "Unknown faction");
    public static final String NO_FACTION_WITH_TAG = Messages.define("ui.noFactionWithTag", "No faction uses the tag {tag}.");
    public static final String OPEN_SHOP = Messages.define("ui.openShop", "Open shop");
    public static final String NO_DESCRIPTION = Messages.define("ui.noDescription", "This faction has no description.");
    public static final String NEUTRAL_WITH_EVERYONE = Messages.define("ui.neutralWithEveryone", "Neutral with everyone.");
    public static final String STOREFRONT_HINT = Messages.define("ui.storefrontHint", "Type how many you want next to an item and press Buy. Leave it empty to buy them all.");
    public static final String SHOPS_UNAVAILABLE = Messages.define("ui.shopsUnavailable", "Shops are not available on this server.");
    public static final String FACTION_GONE = Messages.define("ui.factionGone", "That faction no longer exists.");
    public static final String ITEM_NOT_FOR_SALE = Messages.define("ui.itemNotForSale", "That item is no longer for sale.");
    public static final String WHOLE_NUMBER_TO_BUY = Messages.define("ui.wholeNumberToBuy", "Type a whole number of items to buy.");

    // Permissions page
    public static final String PERMS_CAN_EDIT = Messages.define("ui.permsCanEdit", "Click a cell to switch it. The leader can always do everything.");
    public static final String PERMS_READ_ONLY = Messages.define("ui.permsReadOnly", "You can look but only ranks with the permissions right can change these.");

    // Zone permissions page
    public static final String INHERIT = Messages.define("ui.inherit", "Inherit");
    public static final String ZONE_GONE = Messages.define("ui.zoneGone", "That zone no longer exists.");
    public static final String ZONE_OF = Messages.define("ui.zoneOf", "{zone} of {tag}");
    public static final String ZONE_PERMS_CAN_EDIT = Messages.define("ui.zonePermsCanEdit", "Inherit follows the faction grid. Clicking a cell copies that column for this zone.");
    public static final String ZONE_PERMS_NEED_RIGHT = Messages.define("ui.zonePermsNeedRight", "You need the permissions right to change zone permissions.");
    public static final String ZONE_PERMS_RESET = Messages.define("ui.zonePermsReset", "The zone follows the faction permissions again.");

    // Rank names and zones on the settings tab
    public static final String RANK_NAMES = Messages.define("ui.rankNames", "Rank names");
    public static final String RANK_NAMES_HINT = Messages.define("ui.rankNamesHint", "Give each rank your own name. Leave a field empty and save to go back to the default name.");
    public static final String RANK_NAME_FOR = Messages.define("ui.rankNameFor", "Name for {rank}");
    public static final String SAVE = Messages.define("ui.save", "Save");
    public static final String ZONES = Messages.define("ui.zones", "Zones");
    public static final String ZONES_HINT = Messages.define("ui.zonesHint", "A zone is a named part of your land with its own permissions. Stand in a chunk and press Assign here "
            + "to put it into a zone. Chunks outside any zone follow the faction permissions. "
            + "Changing the permissions of a zone needs the permissions right.");
    public static final String NO_ZONES = Messages.define("ui.noZones", "Your faction has no zones yet.");
    public static final String ZONE_HERE = Messages.define("ui.zoneHere", "{zone}  (here)");
    public static final String CHUNK_ONE = Messages.define("ui.chunkOne", "{count} chunk");
    public static final String ASSIGN_HERE = Messages.define("ui.assignHere", "Assign here");
    public static final String PERMISSIONS = Messages.define("ui.permissions", "Permissions");
    public static final String DELETE = Messages.define("ui.delete", "Delete");
    public static final String NEW_ZONE_NAME = Messages.define("ui.newZoneName", "New zone name");
    public static final String CREATE = Messages.define("ui.create", "Create");
    public static final String CLEAR_THIS_CHUNK = Messages.define("ui.clearThisChunk", "Clear this chunk");
    public static final String ZONE_NAME_FIRST = Messages.define("ui.zoneNameFirst", "Type a name for the zone first.");
    public static final String THIS_ZONE = Messages.define("ui.thisZone", "this zone");
    public static final String DELETE_NAMED = Messages.define("ui.deleteNamed", "Delete {name}");
    public static final String ZONE_DELETE_BODY = Messages.define("ui.zoneDeleteBody", "The zone and its permissions are removed. Its chunks stay claimed and follow the faction permissions again.");

    // Top factions page
    public static final String TOP_BY_VALUE = Messages.define("ui.topByValue", "Ranked by faction value");
    public static final String TOP_BY_SCORE = Messages.define("ui.topByScore", "Score blends land, members, power, kills and bank");
    public static final String COL_FACTION = Messages.define("ui.colFaction", "Faction");
    public static final String COL_VALUE = Messages.define("ui.colValue", "Value");
    public static final String COL_SCORE = Messages.define("ui.colScore", "Score");
    public static final String NO_FACTIONS_YET = Messages.define("ui.noFactionsYet", "No factions yet.");
    public static final String YOUR_FACTION = Messages.define("ui.yourFaction", "Your faction");
    public static final String VALUE_SOURCES = Messages.define("ui.valueSources", "Where your value comes from");
    public static final String FACTION_OF_NATION = Messages.define("ui.factionOfNation", "{faction}  of {nation}");
    public static final String VALUE_PART_OTHER = Messages.define("ui.valuePartOther", "Other");

    // Nation part of the relations tab
    public static final String NATION_NONE = Messages.define("ui.nationNone", "Your faction is not in a nation. A nation joins allied factions under one tag with a shared chat.");
    public static final String FACTIONS_COUNT = Messages.define("ui.factionsCount", "{count} factions");
    public static final String INVITED_YOU = Messages.define("ui.invitedYou", "Invited you");
    public static final String JOIN = Messages.define("ui.join", "Join");
    public static final String NATION_NO_INVITES = Messages.define("ui.nationNoInvites", "No nation has invited your faction.");
    public static final String FIELD_TAG = Messages.define("ui.fieldTag", "Tag");
    public static final String FIELD_NAME = Messages.define("ui.fieldName", "Name");
    public static final String NATION_LEADER_ONLY = Messages.define("ui.nationLeaderOnly", "Your faction leader can found or join a nation.");
    public static final String NATION_ROLE_LEADER = Messages.define("ui.nationRoleLeader", "Leader");
    public static final String NATION_ROLE_MEMBER = Messages.define("ui.nationRoleMember", "Member");
    public static final String MEMBERS_COUNT = Messages.define("ui.membersCount", "{count} members");
    public static final String KICK = Messages.define("ui.kick", "Kick");
    public static final String MAKE_LEADER = Messages.define("ui.makeLeader", "Make leader");
    public static final String NAME_YOU = Messages.define("ui.nameYou", "{name}  (you)");
    public static final String INVITED = Messages.define("ui.invited", "Invited");
    public static final String WITHDRAW = Messages.define("ui.withdraw", "Withdraw");
    public static final String INVITE_FACTION_TAG = Messages.define("ui.inviteFactionTag", "Invite faction tag");
    public static final String INVITE = Messages.define("ui.invite", "Invite");
    public static final String LEAVE = Messages.define("ui.leave", "Leave");
    public static final String DISBAND = Messages.define("ui.disband", "Disband");
    public static final String NATION_CHAT_HINT = Messages.define("ui.nationChatHint", "Talk to the whole nation with /f chat nation.");
    public static final String NATION_CHAT_HINT_PREFIX = Messages.define("ui.nationChatHintPrefix", "Talk to the whole nation with /f chat nation, or start a line with {prefix} to send only that line.");
    public static final String NATIONS_UNAVAILABLE = Messages.define("ui.nationsUnavailable", "Nations are not available on this server.");
    public static final String NATION_TAG_FIRST = Messages.define("ui.nationTagFirst", "Type a tag for the nation first.");
    public static final String FACTION_TAG_FIRST = Messages.define("ui.factionTagFirst", "Type a faction tag first.");
    public static final String NATION_DISBAND_TITLE = Messages.define("ui.nationDisbandTitle", "Disband the nation");
    public static final String NATION_DISBAND_BODY = Messages.define("ui.nationDisbandBody", "The nation ends and every member faction becomes independent again.");

    // Sieges tab
    public static final String SIEGES = Messages.define("ui.sieges", "Sieges");
    public static final String SHIELD = Messages.define("ui.shield", "Shield");
    public static final String ACTIVE_COUNT = Messages.define("ui.activeCount", "{count} active");
    public static final String NO_SIEGES_NOW = Messages.define("ui.noSiegesNow", "No sieges right now");
    public static final String IN_PROGRESS_COUNT = Messages.define("ui.inProgressCount", "{count} in progress");
    public static final String ACTIVE_SIEGES = Messages.define("ui.activeSieges", "Active sieges");
    public static final String NOT_IN_SIEGE = Messages.define("ui.notInSiege", "Your faction is not part of any siege right now.");
    public static final String SIEGE_PAIR = Messages.define("ui.siegePair", "{attacker} against {defender}");
    public static final String ENDS_IN = Messages.define("ui.endsIn", "Ends in {time}");
    public static final String YOU_ATTACK = Messages.define("ui.youAttack", "You attack");
    public static final String YOU_DEFEND = Messages.define("ui.youDefend", "You defend");
    public static final String START_SIEGE = Messages.define("ui.startSiege", "Start siege");
    public static final String CANCEL_SIEGE = Messages.define("ui.cancelSiege", "Cancel siege");
    public static final String SIEGE_HOWTO = Messages.define("ui.siegeHowto", "Stand next to enemy land and press Start siege to plant your banner on the nearest enemy chunk. "
            + "Your attackers must stay near the banner until the capture timer runs out. Cancel siege calls off "
            + "the siege your faction started.");
    public static final String RAID_HISTORY = Messages.define("ui.raidHistory", "Raid history");
    public static final String NO_SIEGES_YET = Messages.define("ui.noSiegesYet", "No sieges have been fought yet.");
    public static final String YOU_ATTACKED = Messages.define("ui.youAttacked", "You attacked");
    public static final String YOU_DEFENDED = Messages.define("ui.youDefended", "You defended");
    public static final String BLOCKS_COUNT = Messages.define("ui.blocksCount", "{count} blocks");
    public static final String DAILY_PROTECTION = Messages.define("ui.dailyProtection", "Daily protection window");
    public static final String NO_SHIELD_INFO = Messages.define("ui.noShieldInfo", "No shield information is available.");
    public static final String GRACE_PERIOD = Messages.define("ui.gracePeriod", "Grace period");
    public static final String GRACE_HINT = Messages.define("ui.graceHint", "Nobody can be sieged or raided until the grace period ends.");
    public static final String EOTW_ACTIVE = Messages.define("ui.eotwActive", "The end of the world is here. Shields are down and every faction can be raided.");
    public static final String SHIELD_PICK_HOUR = Messages.define("ui.shieldPickHour", "Pick the hour your daily shield starts. The hours use the server clock.");
    public static final String SHIELD_STARTS_AT = Messages.define("ui.shieldStartsAt", "Your shield starts at hour {hour} of the server clock. Pick another hour to move it, or remove the window below. "
            + "A change takes {delay} hours to apply.");
    public static final String REMOVE_WINDOW = Messages.define("ui.removeWindow", "Remove window");
    public static final String SIEGES_UNAVAILABLE = Messages.define("ui.siegesUnavailable", "Sieges are not available on this server.");
    public static final String SHIELDS_UNAVAILABLE = Messages.define("ui.shieldsUnavailable", "Shields are not available on this server.");
    public static final String HOUR_UNREADABLE = Messages.define("ui.hourUnreadable", "That hour could not be read.");
    public static final String SIEGE_BREACHED_STATE = Messages.define("ui.siegeBreachedState", "The land is open to the attackers. So far {blocks} blocks were broken and "
            + "{containers} containers were opened. Attackers lost {attackerDeaths} and defenders lost {defenderDeaths}.");
    public static final String SIEGE_DEATHS = Messages.define("ui.siegeDeaths", "Attackers lost {attackerDeaths} and defenders lost {defenderDeaths}.");
    public static final String SIEGE_NO_ATTACKERS = Messages.define("ui.siegeNoAttackers", "No attackers are near the banner. The siege fails in {time} unless they return.");
    public static final String SIEGE_DEFENDERS_HOLD = Messages.define("ui.siegeDefendersHold", "Defenders are standing at the banner and holding up the capture.");
    public static final String SIEGE_ATTACKERS_HOLD = Messages.define("ui.siegeAttackersHold", "The attackers hold the banner. The land is breached when the timer runs out.");
    public static final String PHASE_CAPTURING = Messages.define("ui.phaseCapturing", "Capturing");
    public static final String PHASE_BREACHED = Messages.define("ui.phaseBreached", "Breached");
    public static final String PHASE_FAILED = Messages.define("ui.phaseFailed", "Failed");
    public static final String PHASE_DEFENDED = Messages.define("ui.phaseDefended", "Defended");
    public static final String PHASE_EXPIRED = Messages.define("ui.phaseExpired", "Expired");
    public static final String OUTCOME_WON = Messages.define("ui.outcomeWon", "Won");
    public static final String OUTCOME_LOST = Messages.define("ui.outcomeLost", "Lost");
    public static final String OUTCOME_HELD = Messages.define("ui.outcomeHeld", "Held");
    public static final String OUTCOME_ONGOING = Messages.define("ui.outcomeOngoing", "Ongoing");
    public static final String FORMER_FACTION = Messages.define("ui.formerFaction", "A former faction");

    // Events tab
    public static final String BOUNTIES_COUNT = Messages.define("ui.bountiesCount", "{count} bounties");
    public static final String EVENTS = Messages.define("ui.events", "Events");
    public static final String EVENTS_NOT_RUNNING = Messages.define("ui.eventsNotRunning", "Events are not running");
    public static final String EVENTS_OFF = Messages.define("ui.eventsOff", "Events are turned off on this server.");
    public static final String EVENTS_SUBTITLE = Messages.define("ui.eventsSubtitle", "Hills, outposts, envoys and bounties");
    public static final String SEASON = Messages.define("ui.season", "Season");
    public static final String KING_OF_THE_HILL = Messages.define("ui.kingOfTheHill", "King of the hill");
    public static final String NO_HILLS = Messages.define("ui.noHills", "No hills are set up.");
    public static final String OUTPOSTS = Messages.define("ui.outposts", "Outposts");
    public static final String NO_OUTPOSTS = Messages.define("ui.noOutposts", "No outposts are set up.");
    public static final String OUTPOST_UNHELD = Messages.define("ui.outpostUnheld", "{name}  unheld");
    public static final String OUTPOST_HELD_BY = Messages.define("ui.outpostHeldBy", "{name}  held by {tag}");
    public static final String HOLDER = Messages.define("ui.holder", "Holder");
    public static final String ENVOYS = Messages.define("ui.envoys", "Envoys");
    public static final String NO_ENVOYS = Messages.define("ui.noEnvoys", "No envoy drops are scheduled.");
    public static final String BOUNTIES = Messages.define("ui.bounties", "Bounties");
    public static final String NO_BOUNTIES = Messages.define("ui.noBounties", "Nobody has a bounty on their head.");
    public static final String BOUNTY_HINT = Messages.define("ui.bountyHint", "Place a bounty on a player. Whoever kills them collects it. The smallest bounty is {min}.");
    public static final String FIELD_PLAYER = Messages.define("ui.fieldPlayer", "Player");
    public static final String FIELD_AMOUNT = Messages.define("ui.fieldAmount", "Amount");
    public static final String PLACE_BOUNTY = Messages.define("ui.placeBounty", "Place bounty");
    public static final String REFRESH = Messages.define("ui.refresh", "Refresh");
    public static final String EVENTS_UNAVAILABLE = Messages.define("ui.eventsUnavailable", "Events are not available on this server.");
    public static final String PLAYER_NAME_FIRST = Messages.define("ui.playerNameFirst", "Type the player's name first.");
    public static final String NO_PLAYER_NAMED = Messages.define("ui.noPlayerNamed", "No player named {name} is known.");
    public static final String BOUNTY_POSITIVE = Messages.define("ui.bountyPositive", "Type a positive amount for the bounty.");
    public static final String WAITING = Messages.define("ui.waiting", "Waiting.");

    // Vault, upgrades and missions tabs
    public static final String FACTION_VALUE = Messages.define("ui.factionValue", "Faction value");
    public static final String LEADERBOARD = Messages.define("ui.leaderboard", "Leaderboard");
    public static final String PLACE = Messages.define("ui.place", "Place {place}");
    public static final String NOT_RANKED = Messages.define("ui.notRanked", "Not ranked yet");
    public static final String LEVEL = Messages.define("ui.level", "Level");
    public static final String LEVEL_WITH_POINTS = Messages.define("ui.levelWithPoints", "Level {level} with {points} points");
    public static final String VAULT = Messages.define("ui.vault", "Vault");
    public static final String PAGE_ONE = Messages.define("ui.pageOne", "1 page");
    public static final String PAGES_COUNT = Messages.define("ui.pagesCount", "{count} pages");
    public static final String VAULT_EXPLAINED = Messages.define("ui.vaultExplained", "The vault is a shared chest for your faction. Pick a page to open it. "
            + "Opening the vault needs the vault permission.");
    public static final String VAULT_NO_PAGES = Messages.define("ui.vaultNoPages", "Your faction has no vault pages yet. Buy the vault upgrade to unlock more.");
    public static final String PAGE_BUTTON = Messages.define("ui.pageButton", "Page {page}");
    public static final String VAULT_SLOTS = Messages.define("ui.vaultSlots", "Each page holds {slots} slots.");
    public static final String UPGRADES = Messages.define("ui.upgrades", "Upgrades");
    public static final String BANK_AMOUNT = Messages.define("ui.bankAmount", "Bank {amount}");
    public static final String POINTS_TO_SPEND = Messages.define("ui.pointsToSpend", "{points} points to spend");
    public static final String UPGRADES_PAID_COINS = Messages.define("ui.upgradesPaidCoins", "Upgrades are paid from the faction bank and need the settings permission.");
    public static final String UPGRADES_PAID_POINTS = Messages.define("ui.upgradesPaidPoints", "Upgrades are paid with faction points and need the settings permission.");
    public static final String MAXED = Messages.define("ui.maxed", "Maxed");
    public static final String POINTS_AMOUNT = Messages.define("ui.pointsAmount", "{points} points");
    public static final String MISSIONS = Messages.define("ui.missions", "Missions");
    public static final String MISSIONS_COMPLETE = Messages.define("ui.missionsComplete", "{done} of {total} complete");
    public static final String DAILY_MISSIONS = Messages.define("ui.dailyMissions", "Daily missions");
    public static final String WEEKLY_MISSIONS = Messages.define("ui.weeklyMissions", "Weekly missions");
    public static final String NEW_MISSIONS_IN = Messages.define("ui.newMissionsIn", "New missions in {time}");
    public static final String MISSIONS_HINT = Messages.define("ui.missionsHint", "Every member helps. Progress counts while you play and rewards go to the faction when a mission is done.");
    public static final String NO_MISSIONS = Messages.define("ui.noMissions", "No missions are active right now.");
    public static final String COINS_AMOUNT = Messages.define("ui.coinsAmount", "{amount} coins");
    public static final String REWARD = Messages.define("ui.reward", "Reward {reward}");
    public static final String REWARD_BOTH = Messages.define("ui.rewardBoth", "Reward {coins} and {points}");
    public static final String COMPLETE = Messages.define("ui.complete", "Complete");
    public static final String PROGRESS_OF = Messages.define("ui.progressOf", "{progress} of {target}");
    public static final String VAULT_UNAVAILABLE = Messages.define("ui.vaultUnavailable", "The vault is not available on this server.");
    public static final String PAGE_UNREADABLE = Messages.define("ui.pageUnreadable", "That page could not be read.");
    public static final String UPGRADES_UNAVAILABLE = Messages.define("ui.upgradesUnavailable", "Upgrades are not available on this server.");
    public static final String UPGRADE_MEMBERS = Messages.define("ui.upgradeMembers", "Member slots");
    public static final String UPGRADE_REGEN = Messages.define("ui.upgradeRegen", "Power regeneration");
    public static final String UPGRADE_MAX_POWER = Messages.define("ui.upgradeMaxPower", "Max power");
    public static final String UPGRADE_WARPS = Messages.define("ui.upgradeWarps", "Warps");
    public static final String UPGRADE_VAULT = Messages.define("ui.upgradeVault", "Vault pages");
    public static final String UPGRADE_SHIELD = Messages.define("ui.upgradeShield", "Shield");
    public static final String UPGRADE_IMMUNITY = Messages.define("ui.upgradeImmunity", "Siege immunity");
    public static final String UPGRADE = Messages.define("ui.upgrade", "Upgrade");

    // Market tab
    public static final String SORT_NEWEST = Messages.define("ui.sortNewest", "Newest");
    public static final String SORT_CHEAPEST = Messages.define("ui.sortCheapest", "Cheapest");
    public static final String SORT_PRICIEST = Messages.define("ui.sortPriciest", "Priciest");
    public static final String SORT_ENDING = Messages.define("ui.sortEnding", "Ending soon");
    public static final String MAIL = Messages.define("ui.mail", "Mail");
    public static final String MARKET = Messages.define("ui.market", "Market");
    public static final String MARKET_CLOSED = Messages.define("ui.marketClosed", "The market is closed on this server.");
    public static final String TAB_BROWSE = Messages.define("ui.tabBrowse", "Browse");
    public static final String TAB_SELL = Messages.define("ui.tabSell", "Sell");
    public static final String TAB_ORDERS = Messages.define("ui.tabOrders", "Orders");
    public static final String TAB_MINE = Messages.define("ui.tabMine", "Mine");
    public static final String TAB_AUCTIONS = Messages.define("ui.tabAuctions", "Auctions");
    public static final String TAB_MAILBOX = Messages.define("ui.tabMailbox", "Mailbox");
    public static final String LISTINGS_PAGE = Messages.define("ui.listingsPage", "{total} listings, page {page} of {pages}");
    public static final String SEARCH_ITEMS = Messages.define("ui.searchItems", "Search items");
    public static final String SEARCH = Messages.define("ui.search", "Search");
    public static final String CLEAR = Messages.define("ui.clear", "Clear");
    public static final String COL_ITEM = Messages.define("ui.colItem", "Item");
    public static final String COL_AMOUNT = Messages.define("ui.colAmount", "Amount");
    public static final String COL_PRICE_EACH = Messages.define("ui.colPriceEach", "Price each");
    public static final String COL_SELLER = Messages.define("ui.colSeller", "Seller");
    public static final String NOTHING_FOR_SALE = Messages.define("ui.nothingForSale", "Nothing is for sale right now.");
    public static final String NOTHING_MATCHES = Messages.define("ui.nothingMatches", "Nothing matches your search.");
    public static final String BROWSE_HINT = Messages.define("ui.browseHint", "Type how many you want in the box next to an item and press Buy. Leave it empty to buy them all.");
    public static final String SELL_SUBTITLE = Messages.define("ui.sellSubtitle", "List the item in your hand");
    public static final String SELL_HINT = Messages.define("ui.sellHint", "Hold the item you want to sell in your hand, then type a price for each item and press Sell. "
            + "Leave the amount empty to sell the whole stack. Press Auction instead to put the whole stack up "
            + "for bids for {hours} hours. The price is then the starting bid for the whole stack.");
    public static final String FIELD_PRICE = Messages.define("ui.fieldPrice", "Price");
    public static final String AUCTION = Messages.define("ui.auction", "Auction");
    public static final String SALES_TAX = Messages.define("ui.salesTax", "Sales pay {percent} percent in tax.");
    public static final String LISTINGS_EXPIRE = Messages.define("ui.listingsExpire", "Listings expire after {hours} hours and unsold items come back through your mailbox.");
    public static final String BUY_ORDERS = Messages.define("ui.buyOrders", "Buy orders");
    public static final String ORDERS_PAGE = Messages.define("ui.ordersPage", "{total} open orders, page {page} of {pages}");
    public static final String PLACE_AN_ORDER = Messages.define("ui.placeAnOrder", "Place an order");
    public static final String ORDER_HINT = Messages.define("ui.orderHint", "Ask for an item by its id, set the price you pay for each one and how many you want. "
            + "The coins are held until sellers fill the order.");
    public static final String FIELD_ITEM_ID = Messages.define("ui.fieldItemId", "Item id");
    public static final String PLACE_BUTTON = Messages.define("ui.placeButton", "Place");
    public static final String OPEN_ORDERS = Messages.define("ui.openOrders", "Open orders");
    public static final String COL_WANTED = Messages.define("ui.colWanted", "Wanted");
    public static final String COL_PAYS_EACH = Messages.define("ui.colPaysEach", "Pays each");
    public static final String COL_BUYER = Messages.define("ui.colBuyer", "Buyer");
    public static final String FULFIL = Messages.define("ui.fulfil", "Fulfil");
    public static final String NO_ORDERS = Messages.define("ui.noOrders", "Nobody is asking for anything right now.");
    public static final String FULFIL_HINT = Messages.define("ui.fulfilHint", "Carry the items, type how many you can deliver and press Fulfil. Leave it empty to fill the whole order.");
    public static final String YOUR_LISTINGS = Messages.define("ui.yourListings", "Your listings");
    public static final String COUNT_OF_MAX = Messages.define("ui.countOfMax", "{count} of {max}");
    public static final String NOTHING_LISTED = Messages.define("ui.nothingListed", "You have nothing listed. Use the Sell tab to list the item in your hand.");
    public static final String COL_PRICE = Messages.define("ui.colPrice", "Price");
    public static final String COL_ENDS_IN = Messages.define("ui.colEndsIn", "Ends in");
    public static final String KIND_PINNED_SALE = Messages.define("ui.kindPinnedSale", "Pinned sale");
    public static final String KIND_SALE = Messages.define("ui.kindSale", "Sale");
    public static final String KIND_ORDER = Messages.define("ui.kindOrder", "Order");
    public static final String KIND_AUCTION = Messages.define("ui.kindAuction", "Auction");
    public static final String UNPIN = Messages.define("ui.unpin", "Unpin");
    public static final String PIN = Messages.define("ui.pin", "Pin");
    public static final String CANCEL = Messages.define("ui.cancel", "Cancel");
    public static final String PINNED_HINT = Messages.define("ui.pinnedHint", "Pinned sales show on your faction storefront, which visitors see on your faction page.");
    public static final String AUCTIONS = Messages.define("ui.auctions", "Auctions");
    public static final String AUCTIONS_PAGE = Messages.define("ui.auctionsPage", "{total} running, page {page} of {pages}");
    public static final String COL_TOP_BID = Messages.define("ui.colTopBid", "Top bid");
    public static final String AUCTION_LED_BY = Messages.define("ui.auctionLedBy", "{item}  led by {bidder}");
    public static final String BID = Messages.define("ui.bid", "Bid");
    public static final String NO_AUCTIONS = Messages.define("ui.noAuctions", "No auctions are running right now.");
    public static final String BID_HINT = Messages.define("ui.bidHint", "Type your bid in the box and press Bid. Each bid must beat the top bid by at least "
            + "{percent} percent. Your coins come back if someone outbids you.");
    public static final String MAILBOX = Messages.define("ui.mailbox", "Mailbox");
    public static final String STACKS_WAITING = Messages.define("ui.stacksWaiting", "{count} stacks waiting");
    public static final String COINS_WAITING = Messages.define("ui.coinsWaiting", "Coins waiting");
    public static final String COLLECT = Messages.define("ui.collect", "Collect");
    public static final String NO_ITEMS_WAITING = Messages.define("ui.noItemsWaiting", "No items are waiting for you.");
    public static final String MORE_ITEMS_WAITING = Messages.define("ui.moreItemsWaiting", "More items are waiting. Collect to see the rest.");
    public static final String MAILBOX_HINT = Messages.define("ui.mailboxHint", "Sold items pay out here, and expired or cancelled listings come back here. "
            + "Make room in your inventory before you collect.");
    public static final String PREVIOUS = Messages.define("ui.previous", "Previous");
    public static final String LISTING_GONE = Messages.define("ui.listingGone", "That listing is gone.");
    public static final String PRICE_ABOVE_ZERO = Messages.define("ui.priceAboveZero", "Type a price above zero first.");
    public static final String WHOLE_NUMBER_OR_EMPTY = Messages.define("ui.wholeNumberOrEmpty", "Type a whole number of items, or leave the amount empty.");
    public static final String ITEM_ID_FIRST = Messages.define("ui.itemIdFirst", "Type the id of the item you want.");
    public static final String ORDER_PRICE_FIRST = Messages.define("ui.orderPriceFirst", "Type the price you pay for each item.");
    public static final String ORDER_AMOUNT_FIRST = Messages.define("ui.orderAmountFirst", "Type how many items you want.");
    public static final String ORDER_GONE = Messages.define("ui.orderGone", "That order is gone.");
    public static final String WHOLE_NUMBER_TO_DELIVER = Messages.define("ui.wholeNumberToDeliver", "Type a whole number of items to deliver.");
    public static final String AUCTION_OVER = Messages.define("ui.auctionOver", "That auction is over.");
    public static final String BID_FIRST = Messages.define("ui.bidFirst", "Type your bid in the box first.");
    public static final String UNKNOWN_SELLER = Messages.define("ui.unknownSeller", "Unknown");

    // Territory banners
    public static final String BANNER_OWN = Messages.define("ui.bannerOwn", "Your land");
    public static final String BANNER_ALLY = Messages.define("ui.bannerAlly", "Ally land");
    public static final String BANNER_TRUCE = Messages.define("ui.bannerTruce", "Truce land");
    public static final String BANNER_ENEMY = Messages.define("ui.bannerEnemy", "Enemy land");
    public static final String BANNER_NEUTRAL = Messages.define("ui.bannerNeutral", "Neutral land");

    // Sidebar
    public static final String HUD_POWER = Messages.define("ui.hudPower", "Power");
    public static final String HUD_FACTION_POWER = Messages.define("ui.hudFactionPower", "Faction power");
    public static final String HUD_LAND = Messages.define("ui.hudLand", "Land");
    public static final String HUD_ONLINE = Messages.define("ui.hudOnline", "Online");
    public static final String HUD_NATION = Messages.define("ui.hudNation", "Nation");
    public static final String HUD_NO_FACTION = Messages.define("ui.hudNoFaction", "No faction");
    public static final String HUD_JOIN_HINT = Messages.define("ui.hudJoinHint", "Use /f to join or create one");
    public static final String HUD_LAND_VALUE = Messages.define("ui.hudLandValue", "{claims} / {max} chunks");
    public static final String HUD_WILDERNESS = Messages.define("ui.hudWilderness", "Wilderness");
    public static final String HUD_FLAG_SAFE = Messages.define("ui.hudFlagSafe", "safe");
    public static final String HUD_FLAG_WAR = Messages.define("ui.hudFlagWar", "war");
    public static final String HUD_FLAG_RAIDABLE = Messages.define("ui.hudFlagRaidable", "raidable");
    public static final String HUD_FLAG_BREACHED = Messages.define("ui.hudFlagBreached", "breached");
    public static final String HUD_GRACE_ENDS = Messages.define("ui.hudGraceEnds", "Grace ends in {time}, no raiding");
    public static final String HUD_GRACE = Messages.define("ui.hudGrace", "Grace period, no raiding");
    public static final String HUD_EOTW = Messages.define("ui.hudEotw", "End of the world, all land raidable");
    public static final String HUD_RAIDABLE = Messages.define("ui.hudRaidable", "Your faction is raidable");
    public static final String HUD_PROTECTED = Messages.define("ui.hudProtected", "Your land is protected");
    public static final String HUD_FORMER_FACTION = Messages.define("ui.hudFormerFaction", "a former faction");
    public static final String HUD_STAGE_BREACH = Messages.define("ui.hudStageBreach", "breach {time}");
    public static final String HUD_STAGE_CAPTURE = Messages.define("ui.hudStageCapture", "capture {time}");
    public static final String HUD_MORE_SIEGES = Messages.define("ui.hudMoreSieges", "+{count} more");
    public static final String HUD_SIEGING = Messages.define("ui.hudSieging", "Sieging {tag}");
    public static final String HUD_SIEGED_BY = Messages.define("ui.hudSiegedBy", "Sieged by {tag}");
    public static final String HUD_SHIELD_UP = Messages.define("ui.hudShieldUp", "Your core shield is up");
    public static final String HUD_GRACE_ENDS_SHORT = Messages.define("ui.hudGraceEndsShort", "Grace ends in {time}");

    // Faction panel
    public static final String TOGGLE_OPEN = Messages.define("ui.toggleOpen", "Anyone can join without an invite");
    public static final String TOGGLE_FRIENDLY_FIRE = Messages.define("ui.toggleFriendlyFire", "Members can hurt each other");
    public static final String TOGGLE_ALLY_BUILD = Messages.define("ui.toggleAllyBuild", "Allies can build in your land");
    public static final String TOGGLE_TRUCE_INTERACT = Messages.define("ui.toggleTruceInteract", "Truces can use doors in your land");
    public static final String TOGGLE_ALLY_HOME = Messages.define("ui.toggleAllyHome", "Allies can use your home");
    public static final String TOGGLE_HOME_ON_DEATH = Messages.define("ui.toggleHomeOnDeath", "Respawn at the faction home");
    public static final String TOGGLE_LOGIN_ALERTS = Messages.define("ui.toggleLoginAlerts", "Announce member logins");
    public static final String TAB_OVERVIEW = Messages.define("ui.tabOverview", "Overview");
    public static final String TAB_BANK = Messages.define("ui.tabBank", "Bank");
    public static final String TAB_LOG = Messages.define("ui.tabLog", "Log");
    public static final String TAB_HELP = Messages.define("ui.tabHelp", "Help");
    public static final String TAB_JOIN_OR_CREATE = Messages.define("ui.tabJoinOrCreate", "Join or create");
    public static final String NEW_COUNT = Messages.define("ui.newCount", "{count} new");
    public static final String HEADER_DOMINION = Messages.define("ui.headerDominion", "Dominion");
    public static final String HEADER_NO_FACTION = Messages.define("ui.headerNoFaction", "You are not in a faction yet");
    public static final String HEADER_YOUR_POWER = Messages.define("ui.headerYourPower", "Your power");
    public static final String RANK_NONE = Messages.define("ui.rankNone", "None");
    public static final String JOIN_OR_CREATE_HINT = Messages.define("ui.joinOrCreateHint", "Factions share land, power and a bank");
    public static final String JOIN_OR_CREATE_TEXT = Messages.define("ui.joinOrCreateText", "A faction is a group of players who claim land together. Claimed land is protected from outsiders, "
            + "and every member adds power that keeps the land safe. Type a short tag below to found your own faction, "
            + "or join one of the factions listed underneath.");
    public static final String NEW_FACTION_TAG = Messages.define("ui.newFactionTag", "New faction tag");
    public static final String TUTORIAL_BUTTON = Messages.define("ui.tutorialButton", "Tutorial");
    public static final String FACTIONS_YOU_CAN_JOIN = Messages.define("ui.factionsYouCanJoin", "Factions you can join");
    public static final String STATE_OPEN = Messages.define("ui.stateOpen", "Open");
    public static final String REQUEST = Messages.define("ui.request", "Request");
    public static final String NO_FACTIONS_EXIST = Messages.define("ui.noFactionsExist", "No factions exist yet. Be the first to found one.");
    public static final String CREATED_ON = Messages.define("ui.createdOn", "Created {date}");
    public static final String NOBODY = Messages.define("ui.nobody", "Nobody");
    public static final String LAND_OF_MAX = Messages.define("ui.landOfMax", "{claims} of {max} chunks");
    public static final String LABEL_ALLIES = Messages.define("ui.labelAllies", "Allies");
    public static final String LABEL_ENEMIES = Messages.define("ui.labelEnemies", "Enemies");
    public static final String LABEL_TRUCES = Messages.define("ui.labelTruces", "Truces");
    public static final String ANNOUNCEMENT = Messages.define("ui.announcement", "Announcement");
    public static final String NO_ANNOUNCEMENT = Messages.define("ui.noAnnouncement", "No announcement yet.");
    public static final String POSTED = Messages.define("ui.posted", "Posted {when}.");
    public static final String POSTED_BY = Messages.define("ui.postedBy", "Posted {when} by {name}.");
    public static final String NEW_ANNOUNCEMENT = Messages.define("ui.newAnnouncement", "New announcement");
    public static final String POST = Messages.define("ui.post", "Post");
    public static final String DESCRIPTION = Messages.define("ui.description", "Description");
    public static final String NO_DESCRIPTION_YET = Messages.define("ui.noDescriptionYet", "No description yet. Officers can write one on the settings tab.");
    public static final String QUICK_ACTIONS = Messages.define("ui.quickActions", "Quick actions");
    public static final String HOME = Messages.define("ui.home", "Home");
    public static final String MAP = Messages.define("ui.map", "Map");
    public static final String CHAT_CHANNEL = Messages.define("ui.chatChannel", "Chat {channel}");
    public static final String ALERTS_ON = Messages.define("ui.alertsOn", "Alerts on");
    public static final String ALERTS_OFF = Messages.define("ui.alertsOff", "Alerts off");
    public static final String SIDEBAR_ON = Messages.define("ui.sidebarOn", "Sidebar on");
    public static final String SIDEBAR_OFF = Messages.define("ui.sidebarOff", "Sidebar off");
    public static final String WEB_MAP_HIDDEN = Messages.define("ui.webMapHidden", "Web map hidden");
    public static final String WEB_MAP_SHOWN = Messages.define("ui.webMapShown", "Web map shown");
    public static final String COL_NAME = Messages.define("ui.colName", "Name");
    public static final String COL_RANK = Messages.define("ui.colRank", "Rank");
    public static final String COL_LAST_SEEN = Messages.define("ui.colLastSeen", "Last seen");
    public static final String COL_TITLE = Messages.define("ui.colTitle", "Title");
    public static final String PROMOTE = Messages.define("ui.promote", "Promote");
    public static final String DEMOTE = Messages.define("ui.demote", "Demote");
    public static final String INVITE_SECTION = Messages.define("ui.inviteSection", "Invite");
    public static final String PLAYER_NAME = Messages.define("ui.playerName", "Player name");
    public static final String JOIN_REQUESTS = Messages.define("ui.joinRequests", "Join requests");
    public static final String NO_PENDING_REQUESTS = Messages.define("ui.noPendingRequests", "No pending requests.");
    public static final String INVITE_RIGHT_HINT = Messages.define("ui.inviteRightHint", "Ranks with the invite right can accept or decline these.");
    public static final String WANTS_TO_JOIN = Messages.define("ui.wantsToJoin", "Wants to join");
    public static final String ACCEPT = Messages.define("ui.accept", "Accept");
    public static final String DECLINE = Messages.define("ui.decline", "Decline");
    public static final String LAND_CLAIMED_OF_MAX = Messages.define("ui.landClaimedOfMax", "{claims} of {max} chunks claimed");
    public static final String STANDING_IN = Messages.define("ui.standingIn", "You are standing in");
    public static final String CHUNK_OF_WORLD = Messages.define("ui.chunkOfWorld", "Chunk {chunk} of {world}");
    public static final String OWNER = Messages.define("ui.owner", "Owner");
    public static final String WILDERNESS = Messages.define("ui.wilderness", "Wilderness");
    public static final String CLAIMED = Messages.define("ui.claimed", "Claimed");
    public static final String CLAIMED_BY = Messages.define("ui.claimedBy", "{when} by {name}");
    public static final String CLAIM = Messages.define("ui.claim", "Claim");
    public static final String UNCLAIM = Messages.define("ui.unclaim", "Unclaim");
    public static final String AUTOCLAIM_ON = Messages.define("ui.autoclaimOn", "Autoclaim on");
    public static final String AUTOCLAIM_OFF = Messages.define("ui.autoclaimOff", "Autoclaim off");
    public static final String SET_HOME = Messages.define("ui.setHome", "Set home");
    public static final String CANNOT_CLAIM = Messages.define("ui.cannotClaim", "Your rank cannot claim land. Ask an officer to change the permissions.");
    public static final String NEARBY_CHUNKS = Messages.define("ui.nearbyChunks", "Nearby chunks");
    public static final String MAP_UNAVAILABLE = Messages.define("ui.mapUnavailable", "The map is not available here.");
    public static final String MAP_LEGEND = Messages.define("ui.mapLegend", "You are the @ in the middle and north is up. Each square takes the colour of its owner, "
            + "green yours, purple allies, blue truces, red enemies, gold safe zones. The letter starts "
            + "the owner's tag and dark squares are wilderness.");
    public static final String RELATIONS_COUNT = Messages.define("ui.relationsCount", "{allies} allies and {enemies} enemies");
    public static final String NONE_DOT = Messages.define("ui.noneDot", "None.");
    public static final String ENEMY = Messages.define("ui.enemy", "Enemy");
    public static final String NEUTRAL = Messages.define("ui.neutral", "Neutral");
    public static final String ALLY_HOMES = Messages.define("ui.allyHomes", "Ally homes");
    public static final String HOME_OPEN = Messages.define("ui.homeOpen", "Home open");
    public static final String VISIT = Messages.define("ui.visit", "Visit");
    public static final String REQUESTS = Messages.define("ui.requests", "Requests");
    public static final String OFFERS = Messages.define("ui.offers", "Offers {relation}");
    public static final String EXPIRES_IN = Messages.define("ui.expiresIn", "Expires in {time}");
    public static final String YOU_OFFERED = Messages.define("ui.youOffered", "You offered {relation}");
    public static final String NEW_RELATION = Messages.define("ui.newRelation", "New relation");
    public static final String FACTION_TAG = Messages.define("ui.factionTag", "Faction tag");
    public static final String ALLY = Messages.define("ui.ally", "Ally");
    public static final String TRUCE = Messages.define("ui.truce", "Truce");
    public static final String RELATIONS_RIGHT_HINT = Messages.define("ui.relationsRightHint", "Ranks with the relations right can change these.");
    public static final String BANK_HINT = Messages.define("ui.bankHint", "Shared funds for upkeep and upgrades");
    public static final String FACTION_BALANCE = Messages.define("ui.factionBalance", "Faction balance");
    public static final String YOUR_WALLET = Messages.define("ui.yourWallet", "Your wallet");
    public static final String DEPOSIT = Messages.define("ui.deposit", "Deposit");
    public static final String CANNOT_MOVE_MONEY = Messages.define("ui.cannotMoveMoney", "Your rank cannot move money in or out of the bank.");
    public static final String RECENT_BANK = Messages.define("ui.recentBank", "Recent bank activity");
    public static final String NO_BANK_ACTIVITY = Messages.define("ui.noBankActivity", "No bank activity yet.");
    public static final String SETTINGS_APPLY_AT_ONCE = Messages.define("ui.settingsApplyAtOnce", "Changes apply at once");
    public static final String SETTINGS_LOOK_ONLY = Messages.define("ui.settingsLookOnly", "Your rank can look but not change these");
    public static final String RULES = Messages.define("ui.rules", "Rules");
    public static final String IDENTITY = Messages.define("ui.identity", "Identity");
    public static final String MOTD = Messages.define("ui.motd", "Message of the day");
    public static final String DANGER_ZONE = Messages.define("ui.dangerZone", "Danger zone");
    public static final String CANNOT_BE_UNDONE = Messages.define("ui.cannotBeUndone", "These cannot be undone. Each one asks you to confirm first.");
    public static final String NEW_LEADER = Messages.define("ui.newLeader", "New leader");
    public static final String TRANSFER = Messages.define("ui.transfer", "Transfer");
    public static final String UNCLAIM_ALL = Messages.define("ui.unclaimAll", "Unclaim all");
    public static final String LOG_PAGE = Messages.define("ui.logPage", "{count} entries, page {page} of {pages}");
    public static final String LOG_ALL = Messages.define("ui.logAll", "All");
    public static final String NOTHING_LOGGED = Messages.define("ui.nothingLogged", "Nothing logged yet.");
    public static final String HELP_HINT = Messages.define("ui.helpHint", "How factions work");
    public static final String HELP_POWER = Messages.define("ui.helpPower", "Every player has power that grows while online and drops when they die. A faction's power is the sum "
            + "of its members. Power decides how much land the faction can hold.");
    public static final String HELP_CLAIMS_TITLE = Messages.define("ui.helpClaimsTitle", "Claims");
    public static final String HELP_CLAIMS = Messages.define("ui.helpClaims", "Stand in a chunk and press Claim on the land tab, or use /f claim. Claimed chunks are protected from "
            + "outsiders. You can hold more chunks as your power grows. Turn on auto claim to claim as you walk.");
    public static final String HELP_RELATIONS = Messages.define("ui.helpRelations", "Factions can be allies, have a truce, stay neutral or be enemies. Ally and truce need both sides to "
            + "agree. Enemy and neutral take effect at once. Relations decide who can build, fight and use your home.");
    public static final String HELP_RAIDING_TITLE = Messages.define("ui.helpRaidingTitle", "Raiding");
    public static final String HELP_RAIDING = Messages.define("ui.helpRaiding", "When a faction holds more land than its power can defend it becomes raidable, and enemies can take "
            + "its chunks. Keep members active and avoid needless deaths to stay safe. Watch the sidebar for warnings.");
    public static final String HELP_SIEGES_TITLE = Messages.define("ui.helpSiegesTitle", "Sieges and shields");
    public static final String HELP_SIEGES = Messages.define("ui.helpSieges", "A siege plants a banner on enemy land. If the attackers hold it until the timer ends, the chunk is "
            + "breached and opens to them for a while. Each faction picks a daily shield window that blocks sieges "
            + "on its core land.");
    public static final String HELP_MARKET = Messages.define("ui.helpMarket", "Sell the item in your hand, place buy orders, bid in auctions and collect your earnings from the "
            + "mailbox on the market tab.");
    public static final String HELP_NATIONS_TITLE = Messages.define("ui.helpNationsTitle", "Nations");
    public static final String HELP_NATIONS = Messages.define("ui.helpNations", "Allied factions can join a nation under one tag. Nation members share a chat channel. "
            + "Found or join a nation on the relations tab.");
    public static final String HELP_EVENTS = Messages.define("ui.helpEvents", "Hold a hill to capture it for your faction, keep outposts for their boosts, loot envoy chests "
            + "when they drop and claim bounties by defeating marked players. The events tab shows what is running.");
    public static final String HELP_COMMANDS_TITLE = Messages.define("ui.helpCommandsTitle", "Commands");
    public static final String HELP_COMMANDS = Messages.define("ui.helpCommands", "Open this panel with /f. Use /f help for the full command list.");
    public static final String FACTION_TAG_NEEDED = Messages.define("ui.factionTagNeeded", "Type a tag for your faction first.");
    public static final String CHANNEL_CHAT = Messages.define("ui.channelChat", "{channel} chat");
    public static final String TALK_IN = Messages.define("ui.talkIn", "You now talk in {channel}.");
    public static final String FACTION_ALERTS_ON = Messages.define("ui.factionAlertsOn", "Faction alerts are on.");
    public static final String FACTION_ALERTS_OFF = Messages.define("ui.factionAlertsOff", "Faction alerts are off.");
    public static final String SIDEBAR_NOW_ON = Messages.define("ui.sidebarNowOn", "The faction sidebar is on.");
    public static final String SIDEBAR_NOW_OFF = Messages.define("ui.sidebarNowOff", "The faction sidebar is off.");
    public static final String PLAYER_NAME_NEEDED = Messages.define("ui.playerNameNeeded", "Type a player name first.");
    public static final String POSITION_UNKNOWN_RETRY = Messages.define("ui.positionUnknownRetry", "Your position is not known yet. Try again in a moment.");
    public static final String AUTOCLAIM_NOW_ON = Messages.define("ui.autoclaimNowOn", "Auto claim is on. Walk into wilderness to claim it.");
    public static final String AUTOCLAIM_NOW_OFF = Messages.define("ui.autoclaimNowOff", "Auto claim is off.");
    public static final String ANNOUNCEMENT_FIRST = Messages.define("ui.announcementFirst", "Type the announcement first.");
    public static final String ECONOMY_OFF = Messages.define("ui.economyOff", "The economy is turned off on this server.");
    public static final String POSITIVE_AMOUNT_FIRST = Messages.define("ui.positiveAmountFirst", "Type a positive amount first.");
    public static final String NAME_FIRST = Messages.define("ui.nameFirst", "Type a name first.");
    public static final String NEW_LEADER_FIRST = Messages.define("ui.newLeaderFirst", "Type the new leader's name first.");
    public static final String TRANSFER_TITLE = Messages.define("ui.transferTitle", "Transfer leadership");
    public static final String TRANSFER_BODY = Messages.define("ui.transferBody", "You will hand the faction to {name} and become an officer. Only the new leader can give it back.");
    public static final String UNCLAIM_ALL_TITLE = Messages.define("ui.unclaimAllTitle", "Unclaim all land");
    public static final String UNCLAIM_ALL_BODY = Messages.define("ui.unclaimAllBody", "Every chunk your faction owns becomes wilderness. Anyone can build there or claim it.");
    public static final String DISBAND_TITLE = Messages.define("ui.disbandTitle", "Disband the faction");
    public static final String DISBAND_BODY = Messages.define("ui.disbandBody", "The faction, its land, its bank and its history are deleted for good. Every member becomes factionless.");
    public static final String LEAVE_TITLE = Messages.define("ui.leaveTitle", "Leave the faction");
    public static final String LEAVE_BODY = Messages.define("ui.leaveBody", "You lose access to the faction land, home and bank. You need a new invite to come back.");
    public static final String PLAYER_NOT_FOUND = Messages.define("ui.playerNotFound", "That player could not be found.");
    public static final String UNKNOWN_PLAYER = Messages.define("ui.unknownPlayer", "Unknown player");
}
