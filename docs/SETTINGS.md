# Dominion settings

Every key in `config.json`, generated from `config/Defaults.java`. There are 257 keys in 21 sections. Change them with `/fa set`, `/fa config` or the settings page, or edit the file and run `/fa reload`. A test fails when this file and the defaults disagree, so after adding a key run `DOMINION_WRITE_DOCS=1 ./gradlew test` to write it again.

## Factions

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `faction.tagMin` | `2` | 1 to 32 | Minimum tag length |
| `faction.tagMax` | `16` | 1 to 32 | Maximum tag length |
| `faction.nameMax` | `48` | 1 to 64 | Maximum name length |
| `faction.maxMembers` | `20` | 1 to 10000 | Maximum members |
| `faction.inviteHours` | `24` | 1 to 8760 | Invite expiry in hours |
| `faction.newMemberHoldHours` | `24` | 0 to 8760 | New member hold in hours |
| `faction.memberInactiveDays` | `30` | 0 to 3650 | Kick members after days offline |
| `faction.disbandInactiveDays` | `45` | 0 to 3650 | Disband after days with no logins |
| `faction.auditLimit` | `500` | 0 to 100000 | Audit entries kept per faction |
| `faction.tagBlacklist` | `admin,staff,server,mod` |  | Comma separated banned tags |
| `faction.rosterRotate` | `false` | true or false | Rotate the longest offline member out when the roster is full |

## Power

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `power.start` | `10.0` | -1000000 to 1000000 | Starting power per player |
| `power.max` | `20.0` | -1000000 to 1000000 | Maximum power per player |
| `power.min` | `-5.0` | -1000000 to 1000000 | Minimum power per player |
| `power.regenPerMinute` | `0.1` | 0 to 1000000 | Regen per minute while online |
| `power.lossPerDeath` | `4.0` | 0 to 1000000 | Power lost per death |
| `power.lossInWarzone` | `false` | true or false | Lose power when dying in a WarZone |
| `power.offlineDecayAfterDays` | `3` | 0 to 3650 | Days offline before decay starts |
| `power.offlineDecayPerDay` | `1.0` | 0 to 1000000 | Power lost per day while decaying |
| `power.dtrMode` | `false` | true or false | Use the deaths pool mode instead of power |
| `power.dtrBase` | `2.0` | 0 to 100000 | Deaths pool mode. Pool of a faction before members are counted |
| `power.dtrPerMember` | `1.0` | 0 to 100000 | Deaths pool mode. Pool added per member |
| `power.dtrCap` | `10.0` | 0 to 100000 | Deaths pool mode. Largest pool a faction can have |
| `power.dtrFloor` | `-3.0` | -100000 to 0 | Deaths pool mode. Lowest the pool can fall |
| `power.dtrLossPerDeath` | `1.0` | 0 to 100000 | Deaths pool mode. Pool lost per member death |
| `power.dtrRegenPerOnline` | `0.05` | 0 to 100000 | Deaths pool mode. Pool regained per minute for each online member |
| `power.dtrRegenCap` | `0.1` | 0 to 100000 | Deaths pool mode. Most pool regained per minute |
| `power.regenStep` | `0.25` | 0 to 100 | Extra share of the regen rate per level of the regen upgrade, 0.25 is a quarter |
| `power.upgradeStep` | `2.0` | 0 to 1000000 | Maximum power added per player for each level of the maxpower upgrade |

## Land

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `land.claimsPerPower` | `1.0` | 0 to 10000 | Claims allowed per point of faction power |
| `land.mustBeConnected` | `true` | true or false | Claims must touch existing claims |
| `land.bufferChunks` | `1` | 0 to 32 | Empty chunks required between factions |
| `land.spawnProtectRadius` | `64` | 0 to 1000000 | Blocks around spawn that cannot be claimed |
| `land.maxRadiusClaim` | `5` | 0 to 32 | Largest radius claim |
| `land.maxLineClaim` | `8` | 1 to 128 | Longest line claim |
| `land.decayAfterDays` | `14` | 0 to 3650 | Days with no member online before land decays |
| `land.decayChunksPerDay` | `1` | 0 to 100000 | Chunks lost per day while decaying |
| `land.allowedWorlds` | `""` |  | Comma separated worlds that allow claims, empty for all |
| `land.territoryBanners` | `true` | true or false | Show a banner when entering territory |
| `land.reclaimWindowDays` | `7` | 0 to 365 | Days a released chunk is remembered, so claiming it again earns no mission or counter credit |
| `land.territoryMessageMillis` | `2000` | 0 to 600000 | Milliseconds between two safe zone push back messages to one player |
| `land.nearDefault` | `64` | 1 to 4096 | Radius in blocks of /f near without a number |
| `land.nearMax` | `256` | 1 to 4096 | Largest radius in blocks /f near accepts |

## Raiding

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `raid.enabled` | `true` | true or false | Raiding enabled at all |
| `raid.modeOverextended` | `true` | true or false | Raidable when claims exceed power |
| `raid.modeDominance` | `true` | true or false | Raidable by stronger enemies |
| `raid.dominanceMarginPercent` | `10.0` | 0 to 10000 | Attacker power lead required in percent |
| `raid.dominanceCooldownMinutes` | `5` | 0 to 525600 | Minutes between dominance overclaims |
| `raid.modeSiege` | `true` | true or false | Sieges enabled |
| `raid.modeLowRatio` | `false` | true or false | Raidable below a power ratio |
| `raid.lowRatioPercent` | `25.0` | 0 to 100 | Power ratio threshold in percent |
| `raid.modeUpkeep` | `false` | true or false | Raidable when upkeep unpaid |
| `raid.combineAny` | `true` | true or false | Any mode makes a faction raidable, otherwise all |
| `raid.minDefendersOnline` | `1` | 0 to 10000 | Defenders online required to raid |
| `raid.minAttackersOnline` | `3` | 0 to 10000 | Attackers online required to siege |
| `raid.attackerCooldownMinutes` | `60` | 0 to 525600 | Minutes before one faction can siege the same defender again |
| `raid.protectHomeChunkUntilLast` | `true` | true or false | Home chunk can only be taken last |
| `raid.upkeepUnpaidDays` | `3` | 1 to 3650 | Days of unpaid upkeep that make a faction raidable in upkeep mode |

## Relations

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `relation.maxAllies` | `3` | 0 to 10000 | Maximum allies |
| `relation.maxTruces` | `5` | 0 to 10000 | Maximum truces |
| `relation.requestHours` | `48` | 1 to 8760 | Relation request expiry in hours |
| `relation.allyBuildDefault` | `false` | true or false | Allies may build by default |
| `relation.truceInteractDefault` | `true` | true or false | Truces may use doors by default |
| `nation.enabled` | `true` | true or false | Nations enabled |
| `nation.maxFactions` | `5` | 1 to 1000 | Factions per nation |
| `nation.tagMin` | `2` | 1 to 32 | Nation tag minimum length |
| `nation.tagMax` | `16` | 1 to 32 | Nation tag maximum length |

## Combat

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `combat.pvpNeutral` | `true` | true or false | PvP between neutral factions |
| `combat.pvpFactionless` | `true` | true or false | PvP for players without a faction |
| `combat.friendlyFireDefault` | `false` | true or false | Friendly fire default |
| `combat.allyFire` | `false` | true or false | Allies can hurt each other |
| `combat.truceFire` | `false` | true or false | Truces can hurt each other |
| `combat.tagSeconds` | `15` | 0 to 3600 | Combat tag duration in seconds |
| `combat.killRewardPercent` | `0.0` | 0 to 100 | Percent of victim bank paid to killer bank |
| `combat.useCombatLog` | `auto` | one of auto, off | Use combat tags from the CombatLog mod. auto uses them when the mod is installed, off ignores them |
| `combat.blockSafeZoneEntry` | `true` | true or false | Move combat tagged players back out of SafeZones |
| `combat.blockClaimsWhileTagged` | `true` | true or false | Refuse claiming and unclaiming while combat tagged |
| `combat.homeRespawnDelayMillis` | `1000` | 50 to 60000 | Milliseconds after a respawn before the move to the faction home |
| `combat.homeRespawnAttempts` | `5` | 1 to 100 | Tries to move a respawned player to the faction home |
| `combat.safeZoneMaxStep` | `64.0` | 1 to 10000 | Blocks moved in one tick above which a SafeZone entry counts as a teleport and is let through |

## Homes

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `home.warmupSeconds` | `5` | 0 to 3600 | Teleport warmup in seconds |
| `home.cooldownSeconds` | `60` | 0 to 86400 | Teleport cooldown in seconds |
| `home.enemyRadius` | `32` | 0 to 1024 | Enemy distance that blocks teleport |
| `home.maxWarps` | `3` | 0 to 1000 | Warps per faction |
| `home.stuckWarmupSeconds` | `20` | 0 to 3600 | Stuck escape warmup in seconds |
| `home.respawnAtHomeDefault` | `false` | true or false | Respawn at faction home by default |
| `home.stuckSearchRadius` | `32` | 1 to 256 | Chunks searched around a player for wilderness by /f stuck |
| `home.warmupMoveTolerance` | `1.0` | 0 to 1000 | Blocks a player may move during a teleport warmup |
| `home.enabled` | `true` | true or false | Faction homes, warps, ally homes, /f stuck and home respawns enabled |

## Chat

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `chat.factionPrefix` | `!` |  | Prefix character for faction chat |
| `chat.allyPrefix` | `@` |  | Prefix character for ally chat |
| `chat.nationPrefix` | `#` |  | Prefix character for nation chat |
| `chat.tagStyle` | `brackets` | one of brackets, plain | Tag style, brackets or plain |
| `chat.loginAlerts` | `true` | true or false | Announce member logins to the faction |
| `chat.logPrivate` | `false` | true or false | Log faction, ally and nation chat to the console at INFO instead of FINE |
| `chat.publicFormat` | `relation` | one of relation, passthrough | Public chat style. relation colours each tag for its viewer and delivers the line itself. passthrough adds the tag to the normal line so relays and other chat plugins still see it. In both, Dominion logs and maps only lines that were delivered |
| `chat.showTag` | `true` | true or false | Show the faction tag before player names in public chat |
| `chat.nameplateTag` | `false` | true or false | Experimental. Show the faction tag before the name above each player's head |
| `chat.announceRenames` | `true` | true or false | Tell every online player when a faction changes its tag or name |
| `chat.enabled` | `true` | true or false | Faction, ally and nation chat channels enabled |

## Economy

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `eco.enabled` | `true` | true or false | Economy enabled |
| `eco.provider` | `auto` | one of auto, internal, vaultunlocked, vault, item, items, coinage | Provider, auto, internal, vaultunlocked, item or coinage. auto picks Coinage, then VaultUnlocked, then internal |
| `eco.currencyName` | `Coins` |  | Currency name |
| `eco.currencySymbol` | `c` |  | Currency symbol |
| `eco.startingBalance` | `100.0` | 0 to any size | Starting player balance |
| `eco.itemId` | `Ingredient_Bar_Gold` |  | Item id used when provider is item |
| `eco.costCreate` | `0.0` | 0 to any size | Cost to create a faction |
| `eco.costClaim` | `0.0` | 0 to any size | Cost per claim |
| `eco.costClaimGrowthPercent` | `30.0` | 0 to 10000 | Claim cost growth per claim in percent |
| `eco.refundUnclaimPercent` | `70.0` | 0 to 100 | Refund on unclaim in percent |
| `eco.costWarp` | `0.0` | 0 to any size | Cost to set a warp |
| `eco.upkeepPerClaimPerDay` | `0.0` | 0 to any size | Daily upkeep per claim |
| `eco.duesPerMemberPerDay` | `0.0` | 0 to any size | Daily dues per member |
| `eco.playtimeStipendPerHour` | `0.0` | 0 to any size | Coins per hour of playtime |
| `eco.bankInterestPercentPerDay` | `0.0` | 0 to 100 | Bank interest per day in percent |
| `eco.withdrawDailyLimitOfficer` | `0.0` | 0 to any size | Daily withdraw limit for officers |
| `eco.coinageCurrency` | `""` | a Coinage currency id of lower case letters, digits or _, or blank | Coinage currency id for wallets and banks, blank for the primary currency |
| `eco.coinageBanks` | `true` | true or false | Keep faction banks in Coinage shared accounts while the provider is coinage |
| `eco.coinageDisbandTo` | `leader` | one of leader, server | Where a disbanded faction's Coinage bank goes, leader or server |

## Market

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `market.enabled` | `true` | true or false | Market and auctions enabled |
| `market.taxPercent` | `5.0` | 0 to 100 | Faction tax on sales in percent |
| `market.serverTaxPercent` | `0.0` | 0 to 100 | Server sink on sales in percent |
| `market.listingHours` | `48` | 1 to 8760 | Listing expiry in hours |
| `market.maxListingsPerPlayer` | `20` | 0 to 10000 | Listings per player |
| `market.auctionMinStepPercent` | `5.0` | 0 to 1000 | Minimum bid step in percent |
| `market.auctionAntiSnipeMinutes` | `2` | 0 to 1440 | Anti snipe extension in minutes |
| `vault.basePages` | `1` | 0 to 64 | Vault pages before upgrades |
| `vault.slotsPerPage` | `27` | 1 to 63 | Slots per vault page |
| `shop.adminEnabled` | `true` | true or false | Admin shop available |
| `shop.factionEnabled` | `true` | true or false | Faction shops available |
| `shop.factionMaxEntries` | `24` | 0 to 1000 | Entries per faction shop |
| `market.useCoinage` | `true` | true or false | Open Coinage's market from the panel Market tab while Coinage is linked |
| `shop.useCoinage` | `false` | true or false | Reserved. Faction shops stay Dominion's, since Coinage shops cannot be owned by a faction account yet |
| `market.auctionHours` | `24` | 1 to 8760 | Auction length in hours when the seller names none |
| `vault.memberPages` | `1` | 0 to 64 | Vault pages every member may open, later pages need an officer |
| `vault.enabled` | `true` | true or false | Faction vaults enabled |

## Sieges

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `siege.captureMinutes` | `15` | 1 to 1440 | Capture time in minutes |
| `siege.breachMinutes` | `30` | 1 to 1440 | Breach window in minutes |
| `siege.attackerRadius` | `8` | 1 to 256 | Attacker radius around the banner |
| `siege.defenderRadius` | `3` | 0 to 256 | Defender cancel radius |
| `siege.absentSecondsToFail` | `60` | 1 to 86400 | Seconds without attackers before failure |
| `siege.immunityHoursAfterDefence` | `2` | 0 to 8760 | Immunity after a successful defence in hours |
| `siege.holdOffSeconds` | `30` | 1 to 86400 | Seconds defenders must hold the banner alone to break a siege |
| `siege.resumeGraceSeconds` | `120` | 0 to 86400 | Seconds after a restart before absence can win or lose a capture |

## Shields

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `shield.enabled` | `true` | true or false | Shields enabled |
| `shield.hours` | `8` | 0 to 24 | Shield window length in hours |
| `shield.coreRadius` | `1` | 0 to 32 | Core radius in chunks around home |
| `shield.changeDelayHours` | `24` | 0 to 8760 | Hours before a window change applies |
| `grace.active` | `false` | true or false | Grace period active |
| `grace.endsAt` | `0` | 0 to any size | Grace end as epoch millis |
| `grace.noPowerLoss` | `true` | true or false | No power loss during grace |
| `eotw.active` | `false` | true or false | End of world mode |

## Events

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `events.enabled` | `true` | true or false | Events layer enabled |
| `events.kothCapSeconds` | `300` | 1 to 86400 | KOTH capture time in seconds |
| `events.outpostPercentPerPlayerPerSecond` | `1.0` | 0 to 100 | Outpost control gain per player per second |
| `events.envoyDespawnMinutes` | `10` | 1 to 1440 | Envoy lifetime in minutes |
| `events.kothRadius` | `8` | 1 to 256 | Default hill radius in blocks |
| `events.kothRewardCoins` | `500.0` | 0 to any size | Coins to the capturing faction bank |
| `events.kothRewardPoints` | `25` | 0 to 1000000 | Faction points to the capturer |
| `events.outpostDecayPerSecond` | `0.5` | 0 to 100 | Control lost per second when empty |
| `events.envoyChestItem` | `Furniture_Ancient_Chest_Small` |  | Block placed as an envoy chest |
| `events.bountyMin` | `50.0` | 0 to any size | Smallest bounty |
| `events.envoyLoot` | `Ingredient_Bar_Gold,4\|Ingredient_Bar_Iron,8\|Potion_Health,2` |  | Pipe separated ItemId,qty entries dropped by envoys |
| `events.payoutCommands` | `say Season payout to {tag} for rank {rank}` | config.json only | Pipe separated console commands with {tag} and {rank}, set in config.json only and run by players with dominion.admin.payouts |
| `events.payoutTop` | `3` | 0 to 1000 | Factions that receive payouts |
| `season.active` | `false` | true or false | A season is running |
| `season.startedAt` | `0` | 0 to any size | Season start as epoch millis |
| `season.raidEndsAt` | `0` | 0 to any size | Raid period end as epoch millis |

## Progression

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `value.weightClaims` | `10.0` | 0 to 1000000 | Value per claim |
| `value.weightMembers` | `5.0` | 0 to 1000000 | Value per member |
| `value.weightPower` | `1.0` | 0 to 1000000 | Value per point of power |
| `value.weightKills` | `2.0` | 0 to 1000000 | Value per kill |
| `value.weightSieges` | `25.0` | 0 to 1000000 | Value per siege won |
| `value.weightBankPer100` | `1.0` | 0 to 1000000 | Value per 100 coins in the bank |
| `value.weightOutpostHours` | `5.0` | 0 to 1000000 | Value per outpost hour |
| `value.strikePercent` | `10.0` | 0 to 100 | Value lost per strike in percent |
| `upgrades.useCoins` | `true` | true or false | Buy upgrades with coins instead of points |
| `upgrades.baseCost` | `500.0` | 0 to any size | Base upgrade cost |
| `upgrades.costGrowth` | `1.8` | 1 to 100 | Cost multiplier per level |
| `missions.daily` | `3` | 0 to 50 | Daily missions active |
| `missions.weekly` | `2` | 0 to 50 | Weekly missions active |
| `missions.rewardCoins` | `100.0` | 0 to any size | Coins per daily mission |
| `missions.rewardPoints` | `10` | 0 to 1000000 | Points per daily mission |
| `missions.placedBlockMemoryMinutes` | `60` | 0 to 10080 | Minutes a placed block stays excluded from mission credit |
| `top.pageSize` | `10` | 5 to 25 | Factions per page of /f top |
| `missions.farmGuardMaxPlaced` | `100000` | 0 to 10000000 | Placed blocks remembered across all worlds for mission credit, read at start |
| `missions.farmGuardMaxDrops` | `256` | 0 to 100000 | Dropped item stacks remembered per player for mission credit |
| `missions.farmGuardDropMemoryMinutes` | `5` | 0 to 10080 | Minutes a dropped item earns no mission credit for its dropper's faction |
| `missions.enabled` | `true` | true or false | Missions enabled, off also stops mission credit |
| `upgrades.enabled` | `true` | true or false | Upgrades can be bought, levels already bought keep working |
| `top.enabled` | `true` | true or false | The /f top leaderboard and top page |

## Storage

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `export.enabled` | `true` | true or false | Write the web map snapshot file |
| `export.intervalSeconds` | `2` | 1 to 3600 | Snapshot interval in seconds |
| `export.playerPositions` | `false` | true or false | Write online player positions to the web map snapshot |
| `storage.autosaveSeconds` | `30` | 10 to 3600 | Autosave interval in seconds |
| `storage.backupKeep` | `7` | 0 to 365 | Daily backups to keep |
| `misc.preset` | `classic` | one of classic, singleplayer, hardcore, peaceful | Active preset name |
| `misc.tutorial` | `true` | true or false | Show the tutorial on first join |
| `misc.singleplayerAutoPreset` | `true` | true or false | Apply the singleplayer preset automatically |

## Map

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `map.markers` | `true` | true or false | Show claims on the world map |
| `map.borderOnlyAbove` | `64` | 0 to 1000000 | Show border chunks only above this many claims |
| `hud.enabledDefault` | `true` | true or false | Sidebar on by default |
| `hud.refreshSeconds` | `2` | 1 to 60 | Sidebar refresh in seconds |
| `map.overlay` | `true` | true or false | Tint claimed land on the world map in the owner's colour |
| `map.overlayOpacity` | `55` | 10 to 90 | Claims overlay opacity in percent |
| `map.overlayBorder` | `2` | 0 to 4 | Claims overlay border width in pixels |
| `map.claimMarkers` | `false` | true or false | Show an icon on every claimed chunk instead of one label per faction |
| `map.factionLabels` | `true` | true or false | Show one faction name label on the painted land of each faction |
| `map.compass` | `none` | one of none, events, all | Which Dominion markers the compass shows, none, events (homes, sieges, hills, outposts and envoys) or all |
| `hud.side` | `right` | one of right, left | Sidebar screen side, right or left |
| `hud.top` | `400` | 0 to 2000 | Sidebar distance from the top of the screen |
| `hud.enabled` | `true` | true or false | Sidebar available on this server |
| `map.enabled` | `true` | true or false | The /f map chat map and the world map claims, markers and overlay |

## Integrations

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `integrations.tracker` | `auto` | one of auto, always, never | Publish faction counters to TrackerAPI. auto when installed, always warns when missing, never ignores it |
| `integrations.feats` | `auto` | one of auto, always, never | Mirror faction counters to Feats. auto when installed, always warns when missing, never ignores it |
| `integrations.atlas` | `auto` | one of auto, always, never | Push the map hide switch, and sieges while the snapshot is off, to Atlas. auto when installed, always warns when missing, never ignores it |
| `integrations.atlasSiegeTier` | `public` | one of public, linked, faction, admin | Who sees the Atlas siege layer, set it to Atlas's visibility.territories. Only drawn while export.enabled is off, the snapshot draws sieges otherwise |
| `integrations.featsAnnounce` | `true` | true or false | Post Feats unlocks to the player's faction chat |
| `integrations.coinage` | `auto` | one of auto, always, never | Link to Coinage for wallets, faction banks, net worth and the market. auto when installed, always warns when missing, never ignores it |
| `integrations.foundedWindowSeconds` | `60` | 1 to 86400 | Seconds after a faction is created in which its leader is credited as founder |
| `integrations.enabled` | `true` | true or false | Links to TrackerAPI, Feats, Atlas and Coinage at all, read at start |

## Commands

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `commands.confirmSeconds` | `30` | 5 to 3600 | Seconds a dangerous command waits for /f confirm |
| `commands.root` | `f` | a single word of letters, digits, _ or - | Name of the player command, applied at the next start or /plugin reload |
| `commands.rootAliases` | `faction,factions` | a comma separated list of words of letters, digits, _ or - | Comma separated aliases of the player command, may be empty, applied at the next start or /plugin reload |
| `commands.adminRoot` | `fa` | a single word of letters, digits, _ or - | Name of the admin command, applied at the next start or /plugin reload |
| `commands.adminAliases` | `factionadmin` | a comma separated list of words of letters, digits, _ or - | Comma separated aliases of the admin command, may be empty, applied at the next start or /plugin reload |
| `commands.eventsRoot` | `fe` | a single word of letters, digits, _ or - | Name of the events command, applied at the next start or /plugin reload |
| `commands.eventsAliases` | `""` | a comma separated list of words of letters, digits, _ or - | Comma separated aliases of the events command, may be empty, applied at the next start or /plugin reload |
| `commands.shopEditRoot` | `fashop` | a single word of letters, digits, _ or - | Name of the server shop editor command, applied at the next start or /plugin reload |
| `commands.disabled` | `""` | a comma separated list of words of letters, digits, _ or - | Comma separated sub commands of the player command that are not registered, such as map,top, also balance, pay and baltop, and their panel tabs are hidden, applied at the next start or /plugin reload |

## Interface

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `ui.marketPageSize` | `10` | 1 to 50 | Listings per page in the panel market |
| `ui.logPageSize` | `25` | 1 to 100 | Audit entries per page in the panel log |
| `ui.gridRadius` | `7` | 2 to 7 | Chunks shown on each side of the player in the panel land grid |
| `ui.topLimit` | `30` | 1 to 500 | Factions listed on the top page |
| `ui.eventRows` | `20` | 1 to 100 | Rows per list on the panel events tab |
| `ui.siegeHistory` | `10` | 0 to 100 | Past sieges listed on the panel siege tab |
| `ui.vaultMaxPages` | `30` | 1 to 64 | Vault pages the panel offers buttons for |
| `ui.confirmLockMillis` | `3000` | 0 to 60000 | Milliseconds the confirm button stays locked on a confirm page |
| `ui.maxDots` | `10` | 1 to 10 | Most level dots drawn for an upgrade, the markup has ten |
| `ui.bannerIcon` | `Furniture_Flag_Small_Red` |  | Item whose icon decorates the territory banner |

## Colours

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `colors.own` | `#5fd37a` | a colour written as #rrggbb | Your own faction and its land, as #rrggbb |
| `colors.ally` | `#d99cff` | a colour written as #rrggbb | Allies, as #rrggbb |
| `colors.truce` | `#7fd6ff` | a colour written as #rrggbb | Truces, as #rrggbb |
| `colors.neutral` | `#d3d6db` | a colour written as #rrggbb | Neutral factions, as #rrggbb |
| `colors.enemy` | `#ff6b6b` | a colour written as #rrggbb | Enemies, as #rrggbb |
| `colors.safeZone` | `#E8A93B` | a colour written as #rrggbb | SafeZone land, as #rrggbb |
| `colors.warZone` | `#e0413c` | a colour written as #rrggbb | WarZone land, as #rrggbb |
| `colors.wilderness` | `#6b7280` | a colour written as #rrggbb | Unclaimed land, as #rrggbb |
| `colors.accent` | `#E8A93B` | a colour written as #rrggbb | The Dominion prefix in chat, as #rrggbb |
| `colors.text` | `#d3d6db` | a colour written as #rrggbb | Normal message text, as #rrggbb |
| `colors.muted` | `#96a9be` | a colour written as #rrggbb | Hints and secondary text, as #rrggbb |
| `colors.good` | `#5fd37a` | a colour written as #rrggbb | Success messages, as #rrggbb |
| `colors.bad` | `#ff6b6b` | a colour written as #rrggbb | Refusals and errors, as #rrggbb |
| `colors.warn` | `#ffb347` | a colour written as #rrggbb | Warnings, as #rrggbb |
| `colors.info` | `#7aa7e6` | a colour written as #rrggbb | Information, as #rrggbb |

## Permissions

| Key | Default | Accepts | Description |
| --- | --- | --- | --- |
| `permissions.admin` | `dominion.admin` | a permission node of letters, digits, dots, _, - or * | Node for every admin command, page and tool. Commands pick it up at the next start, checks in pages and services at once |
| `permissions.adminPayouts` | `dominion.admin.payouts` | a permission node of letters, digits, dots, _, - or * | Node that may run the season payout console commands, on top of the admin node. The command picks it up at the next start, the payout check at once |
