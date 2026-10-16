# Changelog

## 0.7.2 - 2026-10-16

### With the other mods
- `dominion:rank_changes` is sent now. Each promote or demote counts once for the member whose rank moved, not for
  the member who made the change, which is what the New Rank feat in Feats 1.7.3 reads. It has a TrackerAPI
  leaderboard like the other counters.
- With Feats 1.7.0 or newer Dominion links and unlinks through the Feats enable and disable hooks, the way it does
  with TrackerAPI, Atlas and Coinage. An older Feats is still found by the watch every 5 seconds.
- During a `/plugin reload` of TrackerAPI, Coinage, Feats or Atlas, Dominion logs one INFO line saying the sibling
  brings it back by itself, instead of a warning asking for `/plugin reload Ryvrook:Dominion`. The warning stays
  for a sibling that is still gone after 30 seconds.
- A server stop or a `/plugin reload` no longer makes a Dominion timer wait for the plugin manager. The sibling watch,
  the VaultUnlocked lookup and the reload threads only try the manager's lock and use the last answer while it is
  held. The IO thread gets 1 s at stop plus 0.5 s after an interrupt instead of 15 s. With all nine suite jars a stop
  after reloading TrackerAPI and Atlas took 0.55 s.

### For mod authors
- New bus event `MemberRankChanged(factionId, member, actor, fromRank, toRank)`, published after every promote,
  demote and set rank, with the ranks as `Rank` ordinals. `DominionApi.API_VERSION` is 3.

## 0.7.1 - 2026-10-16

### With the other mods
- Fifteen more counters per player go to TrackerAPI and Feats under `dominion:`, read by the 14 feats Feats 1.7.2
  adds: `joined`, `kicked`, `wars_declared`, `alliances`, `upgrades`, `upgrade_level_max`, `market_trades`,
  `foreign_entries`, `sieges_defended`, `breaches`, `hill_wins`, `outposts_held`, `power_max`, `rank_changes` and
  `tutorial_done`. War, alliance, upgrade, breach, hill and outpost credit goes to every member, online or not.
  Foreign entries count each faction once, `upgrade_level_max` only rises, and `power_max` and `tutorial_done` are
  sent once. `rank_changes` is listed and never sent, since the rank log line does not name the member by id.
- A Feats older than 1.7.2 keeps only the original ten counters, so only those are sent to it.
- TrackerAPI gets a leaderboard for every counter that can grow past one.

### For admins
- Player records keep the counter memories under `Counters` in the player file.

## 0.7.0 - 2026-10-16

### For admins
- After a `/plugin reload` of Dominion, every enabled plugin that lists `Ryvrook:Dominion` in its manifest is
  reloaded two seconds later, one at a time and under the same lock TrackerAPI and Coinage use, so SocialBridge and
  RestAPI link to the new instance. A plugin someone else already reloaded is left alone.
- Staff actions taken through the API (disband, set leader, set power, unclaim) write one server log line with the
  caller's name, such as `RestAPI:ops force disbanded NRT through the Dominion API`.

### For mod authors
- `DominionApi.API_VERSION` is 2. `DominionApi.onEnable(Consumer)` runs at once when Dominion is up and after every
  start, `onDisable(Runnable)` runs first thing on shutdown, `removeHooks` drops both. `DominionApi.find()` and
  `isPresent()` answer without throwing.
- `factions()` lists every faction with safe and war zones, sorted by tag, and `factionById(UUID)` finds one.
- `sieges()` and `siegeHistory(factionId, limit)` return `SiegeInfo` records with both sides, their tags, the
  phase, when it ends, the banner position and the counts.
- A `DominionEvent.ChatLine` is published for every faction, ally, officer and nation line, with the faction id and
  tag, the channel, the sender and name, the text and the time. `sendFactionChat(factionId, name, text)` sends a line
  from outside the game to the faction's online members and publishes it with a null sender. Lines are cut at 256
  characters. Private lines still never reach `export/snapshot.json`.
- Staff actions `disband`, `setLeader`, `setPower`, `unclaim(world, chunkX, chunkZ)` and `unclaimAll` return a
  `Result` with the reason. `setPower` refuses a player Dominion has never seen.
- Methods added after 0.6.2 have default bodies, so a stand in API written against the older jar still compiles.
- The build compiles against TrackerAPI 1.1.0, Coinage api 1.2.0, Feats 1.6.0 and Atlas 1.7.0.

## 0.6.2 - 2026-10-16

### Changed
- Faction and nation tags read like clan tags. Labels show `[TST] Testers` in chat, on the panel, the sidebar, the territory title and the map, and the tag is left out when it only repeats the name.
- The sidebar title shows the bracketed tag in the faction colour and the name in the text colour in its own case, instead of both in capitals.
- The default tag length is 4, so raise `faction.tagMax` before importing from a factions mod whose tags were longer, or the importer cuts them to fit.
- `faction.tagMax` and `nation.tagMax` default to 4 (was 16). Existing config.json files keep their own values.
- New `faction.tagUppercase`, on by default, stores new and renamed faction tags in upper case. Existing tags are not rewritten until the faction renames itself.
- The panel's create form has separate tag and name fields with hints that follow the live limits, and `/f create` replies with the label as it will show, for example "Created [TST] Testers".
### For players
- The tutorial is a guided page with ten steps, each with a dot row, the commands of that step in code rows and a
  button that does the step, such as creating the faction, claiming the chunk you stand in, showing the chat map,
  setting the home, sending an invite or depositing into the bank. A step that is already done says so in green.
  The first join opener brings it back at the step reached until it is finished or skipped, and the Help tab of
  the panel has a Tutorial button. `/f tutorial` opens it from the start and `/f tutorial reset <player>` lets an
  admin show it again. The new `misc.tutorialSteps` setting trims the steps, and steps for features that are off
  are left out on their own.

## 0.6.1 - 2026-10-16

### Changed
- The faction name label on the world map is text only, the square icon under it is gone, and `map.factionLabels` turns the label off.
- `/f claim 5` claims every chunk within five of you that the rules and your power allow, the short form of `/f claim radius 5`, and `/f unclaim 5` releases the same square.
- Looser defaults for faction and nation names. Tags may be 2 to 16 characters (was 3 to 10), names up to 48 (was 32) and custom rank titles up to 32 (was 24). Existing config.json files keep their own values.

## 0.6.0 - 2026-10-15

### For players
- With TrackerAPI bound, the mine, chop, farm and fish missions count from its events. The counts are the same
  either way, both go through Dominion's own anti farm memory.
- Coinage dues and upkeep round up to the smallest unit of the currency, so an upkeep of 0.4 on a whole coin
  currency costs one coin instead of nothing. Interest still rounds down.
- `/f stats` no longer lists the per player withdraw allowance and counter bookkeeping.

### For admins
- After a `/plugin reload` of Feats, Atlas or Coinage, or when one is installed while the server runs, Dominion
  reloads itself within about 8 seconds and links to the new instance. TrackerAPI 1.0.3 and newer and Coinage
  1.1.0 already reload Dominion, and Dominion then skips its own pass, so it starts once.
- A runtime reload drops the stale asset pack registration the way Feats and Coinage do, so a reload with a new jar
  name no longer refuses the UI pack.
- The Atlas siege layer is drawn only while `export.enabled` is off, since the snapshot already gives Atlas every
  siege. New key `integrations.atlasSiegeTier` sets who sees it.
- `dominion:invites` counts once per invited player and faction, and `dominion:bank_deposited` counts a player's
  net deposits of the day, so invite loops and deposit and withdraw loops count once. TrackerAPI keeps a
  leaderboard for all ten `dominion:` counters.

### Upgrading
- Swap the jar. One key is added with its default, `integrations.atlasSiegeTier` (`public`). Set it to match
  Atlas's `visibility.territories` if you changed that.

### Other changes
- Dominion's own mission systems take over again within 5 seconds when TrackerAPI stops, pauses the listener or
  stops tracking one of those kinds. Deaths, kills and land protection stay on Dominion's own hooks.
- The balance move into Coinage takes the whole coins of a balance on a currency with 0 or 1 decimals and leaves
  the fraction, instead of failing every day.
- The claims overlay paints a chunk image in 2.9 us instead of about 7 us and keeps less memory per chunk.
- The sidebar sends only the values that changed since the last refresh.
- The map snapshot is rewritten every two seconds only while it carries player positions, otherwise when something
  changed, and the first one is written right after start.
- Dominion removes its Atlas hooks on stop when Atlas has `removeHooks`.
- The daily withdraw allowance is booked when Coinage asks Dominion about a withdrawal from its pages, so two
  withdrawals at once can no longer both fit under it. A booking whose move failed is given back within 10
  seconds, and at shutdown.
- A faction account deleted by an admin in Coinage no longer leaves the bank refusing every move. Dominion opens a
  new empty account under the same handle and logs one warning.

## 0.5.1 - 2026-10-15

### Changed
- Dominion markers stay off the compass. The client shows every world map marker on the compass unless a player toggles it by hand, so claim labels, homes and sieges crowded it. New setting `map.compass` with `none` (default, no Dominion marker on the compass), `events` (homes, sieges, hills, outposts and envoys stay, claim labels and chunk icons go) and `all` (as before). Hidden markers carry a transparent compass image, the map itself is unchanged.

## 0.5.0 - 2026-10-15

Dominion Factions 0.5.0 puts the faction economy on Coinage when it is installed. Wallets, faction banks, upkeep,
dues, interest and the market hand off to Coinage, faction banks are Coinage shared accounts the members can see
in Coinage's own pages, and the faction bank share counts in net worth. Without Coinage nothing changes.

### Upgrading
- Replace `DominionFactions-0.4.0.jar` with `DominionFactions-0.5.0.jar`. The data folder and every existing setting carry over.
- `eco.provider` now defaults to `auto`, which picks Coinage when it is bound, then VaultUnlocked, then the internal ledger. Set it to `internal` to keep Dominion's own ledger next to Coinage.
- New keys `eco.coinageCurrency`, `eco.coinageBanks`, `eco.coinageDisbandTo`, `market.useCoinage`, `shop.useCoinage` (reserved) and `integrations.coinage`, see docs/SETTINGS.md.
- On the first start with Coinage bound, internal balances that were touched move into Coinage once, and each faction bank moves into a shared account `dominion:<id>`.

### Coinage
- Coinage is a new optional link, `integrations.coinage`. With `eco.provider` on `coinage`, or on `auto`, the new default that picks Coinage, then VaultUnlocked, then the built in ledger, wallets live in Coinage and every move shows in its history under Dominion. Amounts convert through the decimals of `eco.coinageCurrency`, blank for the primary currency, and balances left in the built in ledger move over once. Dominion's own starting balance is only granted while the built in ledger holds the wallets, and the move leaves out balances that never changed from it. Existing servers keep the provider they had.
- Faction banks become Coinage shared accounts `dominion:<faction id>` while `eco.coinageBanks` is on. The old balance moves in once, the leader owns the account, members get the rights their rank gives, Dominion's rank, hold and daily limit checks also guard moves made from the Coinage pages, and moves made in Coinage are written to the faction log. Disband pays the account to the leader or, with `eco.coinageDisbandTo` on `server`, to the treasury and deletes it.
- The `faction` net worth part counts a member's share of the faction bank in `/networth` and Chronicle's Money tab.
- With `market.useCoinage` on, the panel Market tab opens Coinage's market. `shop.useCoinage` is reserved and off until Coinage shops can belong to a faction account.

## 0.4.0 - 2026-10-15

Dominion Factions 0.4.0 makes everything a server owner might want to change a setting or a sentence. Every player
facing line lives in messages.json, every rule, limit, timer, colour, command name and permission node is a key in
config.json, and `/fa config` and `/fa messages` show and change them in game.

### Upgrading
- Replace `DominionFactions-0.3.0.jar` with `DominionFactions-0.4.0.jar`. The data folder and every existing setting carry over.
- On the first start `messages.json` is written next to `config.json` with every sentence. Edit it and run `/fa reload`. Command and argument descriptions are read when the commands register, so rewording those needs a restart or a plugin reload.
- New settings are added to config.json with their defaults, see docs/SETTINGS.md for every key. The Colours, Commands, Interface and Permissions sections are new, and the former fixed values keep their old numbers as defaults.

### Messages
- Every player sentence of the faction, land, home, relation, economy and nation packages and the time words players see are now messages.json ids that a server can reword or translate without a rebuild, including audit log lines, bank ledger reasons and the reasons sent with payments to the server economy.
- Every player sentence of the command, admin, chat, ui, combat, shield, progress and integration packages is read from messages.json by id, including command and argument descriptions, help pages, panel and sidebar labels, mission names and audit lines, so a server can reword or translate them without a rebuild.
- Every player sentence of the shop, events, market, siege, vault, importer, web map feed and map markers now comes from messages.json, including page labels, command help, audit lines and payment notes.
### Settings
- Every remaining rule that was a constant is a setting now, read live. `land.reclaimWindowDays`, `land.territoryMessageMillis`, `land.nearDefault`, `land.nearMax`, `power.dtrBase`, `power.dtrPerMember`, `power.dtrCap`, `power.dtrFloor`, `power.dtrLossPerDeath`, `power.dtrRegenPerOnline`, `power.dtrRegenCap`, `power.regenStep`, `power.upgradeStep`, `combat.homeRespawnDelayMillis`, `combat.homeRespawnAttempts`, `combat.safeZoneMaxStep`, `home.stuckSearchRadius`, `home.warmupMoveTolerance`, `missions.farmGuardMaxPlaced` (read at start), `missions.farmGuardMaxDrops`, `missions.farmGuardDropMemoryMinutes`, `integrations.foundedWindowSeconds`, `market.auctionHours`, `raid.upkeepUnpaidDays`, `vault.memberPages`, `siege.holdOffSeconds`, `siege.resumeGraceSeconds` and `commands.confirmSeconds`. Every default is the old constant.
- A new Interface section with `ui.marketPageSize`, `ui.logPageSize`, `ui.gridRadius` (2 to 7), `ui.topLimit`, `ui.eventRows`, `ui.siegeHistory`, `ui.vaultMaxPages`, `ui.confirmLockMillis`, `ui.maxDots` and `ui.bannerIcon`.
- A new Colours section with fifteen `colors.*` keys for the relations, the zones, wilderness, the gold prefix and the message tones, each a `#rrggbb` colour. Chat, relation colours, land banners, the chat map, the sidebar and the world map overlay follow a change at once.
- A new Commands section. `commands.root`, `commands.rootAliases`, `commands.adminRoot`, `commands.adminAliases`, `commands.eventsRoot`, `commands.eventsAliases` and `commands.shopEditRoot` rename the commands, and `commands.disabled` leaves sub commands such as `map,top` or the top level `balance`, `pay` and `baltop` out, by name or by alias, hiding their panel tabs too. They apply at the next start or `/plugin reload`, and help text and hints print the names in use.
- A new Permissions section. `permissions.admin` and `permissions.adminPayouts` rename the two nodes Dominion checks.
- New feature switches, all on by default. `vault.enabled`, `missions.enabled`, `upgrades.enabled`, `home.enabled`, `chat.enabled`, `hud.enabled`, `map.enabled`, `top.enabled` and `integrations.enabled`. A switch that is off hides the panel tab, refuses the commands with the feature off message and pauses the timers. The sieges, shield and nation parts of the panel now also follow `raid.modeSiege`, `shield.enabled` and `nation.enabled`.
- `/fa config` lists the sections with their key counts, pages through a section, shows or changes one key and writes every key to `config-dump.txt` with `/fa config dump`.
- `/fa messages` counts the message ids and the customised ones, shows one id, searches the wording and resets one id to its default.
- `docs/SETTINGS.md` lists every key with its default, limits and description. It is generated from the defaults and a test keeps it and the README count current.

## 0.3.0 - 2026-10-15

Dominion Factions 0.3.0 is the first release shaped by a client session. Faction names show once, the sidebar,
the chat map, `/f who` and `/f top` follow the classic Factions look, claims are painted on the world map the way
Simple Claims paints them, and Dominion links to TrackerAPI, Feats and Atlas when they are installed.

### Upgrading
- Replace `DominionFactions-0.2.1.jar` with `DominionFactions-0.3.0.jar`. The data folder and every existing setting carry over.
- New settings are added to config.json with their defaults on the first start. `hud.side`, `hud.top`, `map.overlay`, `map.overlayOpacity`, `map.overlayBorder`, `map.claimMarkers`, `top.pageSize`, `chat.showTag`, `chat.nameplateTag`, `chat.announceRenames`, `integrations.tracker`, `integrations.feats`, `integrations.atlas` and `integrations.featsAnnounce`.
- TrackerAPI, Feats and Atlas are optional dependencies. Without them Dominion runs as before.

### Changed
- A faction or nation whose tag matches its name is shown by its name once everywhere, in the panel header, the sidebar, the land owner row, world map markers, nation rows, top lists and info pages.
- The sidebar is redesigned with a title bar in the faction colour, one row per value, a location row with a relation colour bar and status rows that only show when they have something to say. New settings `hud.side` (right or left) and `hud.top` (default 400) place it clear of the Coinage and Chronicle boxes.
- The `/f map` chat map follows the classic Factions layout with a `____[ (x, z) Owner ]____` header and a legend that names every faction on the map, while drawing each chunk with the same glyph so columns stay straight in the chat font. Claims take the faction's own colour, wilderness is dim grey, zones show in a lighter shade and safe and war zones keep gold and red. The panel's Nearby chunks grid now draws each chunk as a filled square in its owner colour with the first letter of the tag on top.
- Claimed land is drawn on the in game world map the way Simple Claims draws it. Each claimed chunk gets a translucent fill in the owner's colour and a darker outline on the edges that face other land, so joined claims read as one region. SafeZones are gold and WarZones red. Dominion wraps the world's own map generator instead of replacing it, leaves a map another plugin draws alone and puts the generator back when it shuts down.
- Changed chunks and their neighbours are drawn again within about a second of a claim, unclaim, colour change or disband, for the players who can see them.
- Claims now show one label per faction and world in the middle of its largest region instead of an icon on every chunk. The per chunk icons come back with `map.claimMarkers`, and are used anyway in a world the overlay does not draw.
- New settings `map.overlay` (default `true`), `map.overlayOpacity` (percent, default 55, 10 to 90), `map.overlayBorder` (pixels, default 2, 0 to 4) and `map.claimMarkers` (default `false`).

### Integrations
- Dominion links to TrackerAPI, Feats and Atlas when they are installed and runs as before when they are not. Each link has a setting, `integrations.tracker`, `integrations.feats` and `integrations.atlas`, with `auto` (the default), `always` and `never`, and the manifest lists the three mods as optional dependencies.
- With TrackerAPI, ten per player counters under `dominion:` for claims, unclaims, enemy kills, deaths in enemy land, sieges won and lost, missions, bank deposits, factions founded and invites, with leaderboards for five of them.
- With Feats, the same counters are mirrored into Feats, and feat unlocks are posted to the player's faction chat while `integrations.featsAnnounce` is on.
- With Atlas, a live Sieges layer shows each besieged chunk with its phase and end time, and `/f map hide` and `/f map show` switch the Atlas hide flag as well.
- `/fa integrations` lists each mod as bound, missing or off, and the start log has one line per mod.
- New bus events PlayerDied, BankDeposited, MemberInvited and AuditLogged for other mods to read.
- 26 new tests for the switch, the event to counter mapping against the real faction and land services, the siege popup and the snapshot fields Chronicle Stats reads.

### Chat and commands
- `/f who` prints the classic layout. A `____[ Tag Name ]____` header in your relation colour, then description, age, open or invite only, Land / Power / Maxpower, raidable, bank, nation, truces, allies and enemies as coloured lists, and the online and offline members with `**` for the leader, `*` for officers and custom rank names.
- `/f who <player>` finds the faction of any known player when no faction uses that tag.
- `/f top [page]` pages the value leaderboard with a `____[ Faction Top (Value) 1/4 ]____` header, rows such as `1. Tag (3/12) 48,500`, your own standing when it is off the page and a hint for the next page. New setting `top.pageSize`, default 10, from 5 to 25.
- New setting `chat.showTag`, default true. When off, public chat shows no faction tag in either public format. Faction, ally, officer and nation channels keep their prefixes.
- New experimental setting `chat.nameplateTag`, default false. When on, the bracketed faction tag shows before the name above each player's head. It updates on join, world change, faction join and leave and rename, and the plain name is put back on quit and when the plugin stops.
- Faction renames are announced to every online player as The faction Old is now New, with the tags in each viewer's relation colour, and written to the faction log. New setting `chat.announceRenames`, default true.
- New bus event `DominionEvent.FactionRenamed` with the faction id and the old and new tag and name, published after every successful rename, including admin renames.

### Fixed during review
- `/f who` lists a faction that declared war one sided and the factions of a shared nation under Enemies and Allies.
- Rename announcements name a faction with the same label as everywhere else, so a tag that repeats the name shows once.
- A faction tag saved into a nameplate is taken off at the next join even after the player left the faction or it was renamed.
- Open sidebars move as soon as `hud.side` or `hud.top` changes.
- Renewing an invite that is still open no longer adds to the invites counter.
- `ClaimChanged` carries the actor and a `counts` flag, and the claims and unclaims counters only move for land the faction had not released within the mission window, so claiming and releasing the same chunk over and over counts once each way. Admin moves and decay never count.

## 0.2.1 - 2026-10-14

Dominion Factions 0.2.1 works with the CombatLog mod by Devify. When CombatLog is installed its combat tags drive
Dominion's combat rules next to Dominion's own tag. Punishing players who log out in combat stays with CombatLog,
Dominion only reads whether a player is tagged.

### Upgrading
- Replace `DominionFactions-0.2.0.jar` with `DominionFactions-0.2.1.jar`. The data folder and every existing setting carry over.
- Three new settings are added to config.json with their defaults on the first start. `combat.useCombatLog` is `auto`, `combat.blockSafeZoneEntry` is `true` and `combat.blockClaimsWhileTagged` is `true`.
- CombatLog 1.7.0 or newer is optional. The manifest lists `Devify:CombatLog` as an optional dependency so the server loads it before Dominion when it is present. Without it Dominion runs on its own combat tag as before.

### Changed
- New CombatTagService answers whether a player is combat tagged and for how long, as the union of Dominion's own tag and CombatLog's tag. The remaining time is the longer of the two.
- CombatLog is reached through reflection in combat/CombatLogBridge, resolved once at start. A missing mod, an API of another shape or a call that throws answers not tagged and never reaches Dominion as an error.
- New setting `combat.useCombatLog` with the values `auto` (use CombatLog when it is installed) and `off` (ignore it).
- `/f home`, ally homes, warps and `/f stuck` read the shared tag, and a running teleport warmup is cancelled within a second when either mod tags the player.
- `/f stuck` while tagged now says how long the tag has left, like the other teleports.
- New setting `combat.blockSafeZoneEntry`. A tagged player who walks into a SafeZone is moved back to the last spot they stood outside it and told You cannot enter a safe zone while in combat. Long jumps such as teleports and respawns are not pushed back.
- New setting `combat.blockClaimsWhileTagged`. Claiming, radius and line claims, auto claim, unclaiming and unclaim all are refused while tagged. Admin claims are not affected.
- Dying ends Dominion's own combat tag, the same way CombatLog ends its tag on death.
- The start log and `/fa diag` say which tag source is in use, for example Dominion uses CombatLog 1.7.0 for combat tags.
- Homes, warps, stuck and the SafeZone push back share one teleport path in util/Teleports.
- 30 new tests for the tag union, the auto and off setting, the bridge against fake APIs of the right and wrong shape, the SafeZone push back rule and claim refusal while tagged, for 461 in total.

## 0.2.0 - 2026-10-14

Dominion Factions 0.2.0. The mod is renamed Dominion Factions, the jar is `DominionFactions-0.2.0.jar`, and the
plugin id, data folder and commands are unchanged, so an existing `mods/Ryvrook_Dominion` folder keeps working.

### Storage, startup and settings
- Storage writes go to a unique temporary file, are forced to disk and moved over the target in one step, so two threads saving the same file can no longer corrupt it.
- Every saved file keeps the previous version as name.bak. A damaged file is moved aside as name.corrupt-<time> and the backup is read in its place, for factions, players, claims and every module file.
- A failed write leaves the faction or player dirty so the next autosave retries it, and the failure is logged once until a write succeeds again.
- The dirty flag is cleared before a record is serialised, so a change made during a save is no longer lost.
- A player record is saved and dropped atomically on logout, so a quick rejoin can no longer read the file before the last changes land.
- A disbanded faction can no longer be written back to disk by a save that was already queued.
- Claims whose faction file is missing are saved to claims/<world>.orphans-<time>.txt at start instead of vanishing on the next save.
- New Repository.replaceClaim(String world, long chunkIndex, ClaimEntry expected, ClaimEntry updated), a compare and set for one claim.
- Leftover temporary files from a crash are removed at start.
- Autosave, backups, imports and the map snapshot run on a dedicated Dominion-IO thread instead of the engine's shared scheduler.
- Players already online when the plugin is reloaded are picked up, so their records stay loaded and their sidebar returns.
- New players start with hud.enabledDefault, and a player's own sidebar choice now wins over the server default.
- Settings reads are lock free and never see half of a reload.
- Settings have ranges and allowed values. /fa set, the admin settings page and config.json refuse values such as a zero export interval, a tax above 100 percent, a NaN number or an unknown provider, and say what is allowed.
- /fa reload reports problems line by line, leaves a config.json with problems untouched, and changes nothing when the file has a syntax error instead of resetting every setting to its default.
- /fa get shows the allowed range or values of a setting.
- Every preset now sets the same keys, so switching from singleplayer back to classic restores raiding, events, shields and power loss. An unknown preset name is refused.
- Backups are written under a temporary name first and skip damaged and temporary files.
- /fa diag shows which modules really started, any that failed, and the snapshot path and interval.
- /fa power and /fa boost refuse NaN and infinite numbers.
- Quoted command arguments such as /fa zone safe "Spawn Town" no longer keep their quote marks.

### Land, factions and power
- Fixed dominance overclaims against a faction with negative power. The margin is now a share of the size of the defender's power, so a defender at minus 10 needs an attacker above minus 9 instead of above minus 11 (land/Raids.java, `dominates`).
- Fixed claim mission farming. Unclaiming and reclaiming the same chunk no longer advances the claim mission when the faction let go of that chunk within the last 7 days. New land still counts, and so does land another faction released (land/ClaimServiceImpl.java, `newLand` and `noteReleased`).
- Fixed kicked or departed roster players coming straight back. A kick, a ban or a leave now takes the player off the faction roster, so the login roster join and `/f join` no longer pull them back in (faction/Membership.java).
- Fixed zone permission overrides being a way around the rank limits. Editing a rank in a zone now follows the faction grid rules, so only ranks below your own and only permissions you hold yourself (faction/Zones.java, `setPerm`).
- Fixed a zone assign or zone delete undoing a claim made at the same moment on another thread. The write only lands when the chunk is still as it was read, otherwise the player is told the chunk changed hands a moment ago (faction/Zones.java).
- Fixed power above a lowered maximum staying forever. Online regen now pulls power down to the current maximum, for example after leaving a faction with the maxpower upgrade or after power.max is lowered (power/PowerTicker.java).
- A player file with a missing or non text name now loads with an empty name that the next login fills in, instead of being treated as damaged (model/PlayerData.java).
- Fixed one damaged entry in a faction file dropping the whole faction and, at the next claims save, all of its land. Members, relations, requests, invites, bans, roster entries, permission grids, zones and audit lines are now read one by one and a bad entry is skipped alone. An unknown member rank falls back to recruit (model/Faction.java `fromDocument`, model/Zone.java `fromDocument`).
- Fixed the nation file write racing between the minute save and the shutdown save, and a failed write now stays marked for the next minute (nation/NationServiceImpl.java `save`).
- Fixed claim mission farming. A chunk a faction lost within the last 7 days, by unclaim, decay, admin action or an enemy overclaim, earns no claim mission credit when that faction takes it again. Two cooperating factions trading a chunk get one credit each. The marks are stored in the faction file so a restart keeps them (land/ClaimServiceImpl.java `newLand` and `noteReleased`, model/Faction.java `noteReleased` and `releasedWithin`).
- Fixed kicked or departed roster players coming straight back. A kick, a ban or a leave now takes the player off the faction roster, so the login roster join and `/f join` no longer pull them back in. The faction remembers which roster entry each player joined through, so a rename does not keep the entry alive (faction/Membership.java, model/Faction.java `rosterBind` and `rosterRemovePlayer`).
- Fixed roster names being stored with the default locale and looked up with the root locale, which broke names with an i on a Turkish system. Both sides use the root locale now (model/Faction.java `rosterKey`).
- Fixed a zone assign or zone delete undoing a claim made at the same moment on another thread. Both now write through the atomic Repository.replaceClaim, so a chunk that changed hands is left alone (faction/Zones.java, `assignAt` and `delete`).
- Fixed player data loaded from a file with no name carrying an unusable name. The name is always text, empty until the next login (model/PlayerData.java `fromDocument`).
- Fixed one damaged entry in a faction file dropping the whole faction and, at the next claims save, all of its land. Members, relations, requests, invites, bans, roster entries, permission grids, zones and audit lines are now read one by one and a bad entry is skipped alone. The same goes for the type, leader, nation link, shield hour, home, warps, upgrades, stats and rank names. An unknown member rank falls back to recruit, a lost leader entry is rebuilt as the leader, and a permission grid with no usable entry falls back to the defaults instead of granting nothing (model/Faction.java `fromDocument`, model/Zone.java and model/Nation.java `fromDocument`).

### Economy, market, shops, vault, homes and the importer
- Economy money moves now live in one tested class, Books, and every transfer, deposit, withdrawal, payment, charge, credit and drain either moves all of the money or none of it.
- The VaultUnlocked balance migration no longer pays a balance into VaultUnlocked when the zeroed player file could not be saved, so a crash can never pay it twice, and a restored balance that could not be written stays loaded until the periodic save writes it.
- A withdrawal refund that lands after midnight no longer gives an officer extra daily allowance.
- The playtime stipend now pays whole cents into the internal ledger and keeps the fraction aside, so internal balances always hold whole cents.
- Daily dues are collected in whole cents, so what leaves the wallets is exactly what reaches the bank.
- The upkeep audit line now says how much a short bank gave when it could not pay in full.
- An item currency payment that rounds a fraction up to a whole item now owes the difference back with the next whole item.
- VaultUnlocked responses that report a type instead of transactionSuccess are now read correctly.
- Market taxes above 100 percent can no longer pay out more than the buyer paid. The seller, the faction bank and the burned server tax always add up to the sale.
- Expired and cancelled listings, expired auctions and lots that cannot be built right now go to the mailbox as stored documents, so no part of a lot is dropped.
- Trades between faction mates, repeat trades with the same partner within an hour, trades under 10 and more than 10 trades an hour per buyer no longer count toward trade missions, in the market and in faction shops.
- The admin shop caps the outpost sell boost at 10 times, so stacked outposts cannot make sell only items worth any amount.
- Taking someone else's entry out of the faction shop now needs the vault permission and a finished member hold, the same as stocking it from the vault.
- A faction shop purchase whose leftover items could not be refunded keeps those items in stock and tells the buyer, instead of destroying them.
- Disbanded faction shop stock that cannot be rebuilt is kept on disk for an admin instead of being dropped.
- Vault items that cannot be read back are kept in the vault file instead of being deleted by the next save, and a vault save that fails is retried a minute later.
- A teleport warmup can no longer complete after damage cancelled it on another thread.
- A teleport warmup is cancelled when the player becomes combat tagged.
- The teleport cooldown only starts when a teleport actually happens.
- A warp password set or changed during the warmup now applies to that teleport.
- An import run is refused when the source data could only be read in part, and when the import record file cannot be read, so a damaged file can no longer import factions without their land or import a faction twice.
- Importers skip claims with damaged or out of range coordinates instead of claiming the wrong chunk, and an Ultimate Factions claim with a missing world says so.
- The built in SQLite reader turns any damaged file into a readable reason instead of an internal error.
- The VaultUnlocked balance migration saves through the repository and no longer pays a balance into VaultUnlocked when the zeroed player file could not be saved, and a restored balance that could not be written stays loaded until the periodic save writes it.
- Internal wallet changes always land on the record the repository holds, so a player record unloaded in the middle of a payment, a bank withdrawal or the dues run can no longer lose or create money.
- Money that a provider refuses to refund, or a bank withdrawal for a faction disbanded during the payment, now goes to the player's market mailbox instead of being lost.
- The market no longer holds its lock while the economy is paying, so a slow item currency or VaultUnlocked can no longer stall a world. A purchase or bid reserves its listing, pays, then commits or releases it.
- Market listings and mailboxes are saved before items or coins leave for good, a failed save is retried every minute, an auction that cannot be saved as closed is not paid out, and a cancel that cannot be saved is refused.
- Collecting mailbox coins empties the box on disk before paying, so a restart can never pay the same coins twice.
- Faction shop purchases reserve their stock and pay outside the shop lock, and refunds that the economy refuses go to the market mailbox.
- Item currency deliveries reserve the owed items before handing them out, so two deliveries can never give the same items.
- Faction upkeep no longer takes anything from a bank at or below zero.
- Disbanded faction shop stock that cannot be rebuilt is kept on disk for an admin instead of being dropped, and a disband without the market module no longer throws.

### Sieges, shields, combat, events and progression
- Sieges pause their timers while the server is down. sieges.json now records when it was saved, a capture or breach resumes with the time it had left, and a capture gets two minutes of grace after a restart so neither side wins or loses by absence while players rejoin.
- A running capture saves every minute, so a crash loses at most a minute of siege time.
- A siege can no longer pay out twice when an admin cancel lands at the same moment as a defence or a win. The phase change happens first and the immunity, stats and mission credit follow only when it succeeds.
- Calling off a siege or an admin cancel on a siege that already ended now says so instead of reporting success.
- The siege mission credit only goes to the starter while they are still in the attacking faction.
- Siege failure messages now say why the siege failed, such as no attacker staying at the banner or an admin ending it.
- The siege status line no longer says "The walls fall in now" when the capture time is up but no attacker holds the banner.
- Siege start messages use the right plural for one member.
- The shield status explains that an open window covers no land while the faction has no home.
- NPCs, merchants and mounts in a SafeZone can be used again. Using an entity in a SafeZone was treated as hurting it.
- Dying in a SafeZone no longer costs power, since no player can cause a death there.
- A scheduled hill that ran past its next slot no longer reopens the moment it closes.
- A scheduled hill opening missed by more than five minutes, for example during downtime, is skipped instead of opening at boot.
- The hill capture broadcast now says the coins went to the winner's bank instead of "your faction bank".
- Season payouts look factions up by id at payout time. A faction that renamed after the season ended is still paid, a faction that took a freed tag is not, a disbanded faction is skipped, and a tag that is not plain letters and digits never reaches a console command.
- Ending a season from the tick and from an admin at the same moment can no longer freeze the board twice and reopen payouts.
- A bounty of infinity or NaN is refused before any money moves.
- Hill and envoy schedule changes write their file outside the events lock.
- Outpost messages read "an xp boost" instead of "a xp boost".
- The envoy timer refusal now reads "The shortest envoy timer is 5 minutes."
- Two faction members can no longer farm the crop and fish missions by passing the same items back and forth, and logging out no longer clears the drop memory.
- Buying an upgrade with faction points can no longer lose points that a hill or mission adds at the same moment.
- An upgrade cost setting that is NaN or infinite makes the upgrade unaffordable instead of free.
- A running capture or breach saves every minute, so a crash loses at most a minute of siege time. A crash late in a breach no longer restarts the whole breach.
- The restart grace now belongs to each siege resumed from disk and is only given after a boot with nobody online. Sieges started after boot can be defended or failed at once, and a plugin reload adds no grace.
- Siege, event and bounty files are written in snapshot order, so an older snapshot can no longer land over a newer one, and a failed write stays pending and is retried.
- Season payouts only run once their record is saved. If events.json cannot be written no command runs, so a restart can never pay the same board twice.
- Two faction members can no longer farm the crop and fish missions by passing the same items back and forth, and logging out no longer clears the drop memory. Drops by players of other factions never withhold credit.

### Chat, maps and the web map snapshot
- /f create <tag> <name> takes a display name of several words, like /f name.
- Durations such as 1d12 without a final unit and overflowing numbers are refused instead of throwing.
- Public chat is formatted last, after title and rank plugins, and keeps their prefix. Private channels are still routed at normal priority so relays never see them.
- New setting chat.publicFormat. passthrough keeps public chat events alive so Discord relays and other chat plugins see them, relation (the default) keeps relation coloured tags per viewer.
- The map snapshot is written on the export interval on the IO thread and always on /fa export.
- Snapshot territory type is FACTION for player factions, so the web map no longer shows a "normal" badge on every faction. SAFEZONE and WARZONE are unchanged.
- Snapshot relations are the resolved ones. An unaccepted ally request no longer shows as an alliance, an enemy declaration from either side counts, and factions in one nation are allies.
- The snapshot leaves out events when the events module is missing instead of writing null.
- Feed lines have strictly increasing times like chat lines, so the web map no longer drops a line logged in the same millisecond.
- Naming an offline player in the feed no longer creates a player file for them.
- World map marker ids follow one rule, letters, digits and _ . : - within 64 characters, so hill names and world names with spaces make valid ids.
- A player who hid from the web map no longer loses every claim marker on their own in game map.

### Panel and commands
- New `/f map hide` and `/f map show`, and a Web map button on the Overview tab under Quick actions. A hidden player's position is never written to the map snapshot, even when the server shares player positions.
- Settings with a minimum and a maximum, faction and nation tag length and power, refuse a minimum above the maximum with a reason, go back to their defaults when config.json has them out of order at start, and keep their current values on reload. Problems found in config.json at start are now logged as warnings.
- The panel only shows buttons the viewer's rank may use. Promote, demote and kick need the right, invite and join requests need invite, claim, unclaim and set home need theirs, relations, bank deposit and withdraw, settings toggles and identity fields need settings, unclaim all and disband need disband, and transfer is leader only.
- Renaming the faction from the settings tab no longer resends the current tag.
- Only the toggles the settings tab shows can be switched, whatever a client sends.
- The permissions page hides Reset for ranks that cannot edit permissions.
- Player lookups return null instead of throwing while the universe is not running.

### Tests
- A JUnit suite of 431 tests that runs without a server, over money moves, storage, settings, parsers, claims, power, membership, relations, nations, sieges, shields, the PvP table, events, missions, the market, shops, homes, the importer and the web map snapshot contract.

## 0.1.0 - 2026-10-12

First build. Factions, ranks, permissions, claims, zones, power, relations, nations, homes, warps, bank, market,
auctions, shops, vault, chat, sieges, shields, grace, upgrades, missions, events, an importer and the web map export.
