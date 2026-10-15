# Changelog

## Unreleased

### Changed
- A faction or nation whose tag matches its name is shown by its name once everywhere, in the panel header, the sidebar, the land owner row, world map markers, nation rows, top lists and info pages.
- The sidebar is redesigned with a title bar in the faction colour, one row per value, a location row with a relation colour bar and status rows that only show when they have something to say. New settings `hud.side` (right or left) and `hud.top` (default 400) place it clear of the Coinage and Chronicle boxes.
- The `/f map` chat map follows the classic Factions layout with a `____[ (x, z) Owner ]____` header and a legend that names every faction on the map, while drawing each chunk with the same glyph so columns stay straight in the chat font. Claims take the faction's own colour, wilderness is dim grey, zones show in a lighter shade and safe and war zones keep gold and red. The panel's Nearby chunks grid now draws each chunk as a filled square in its owner colour with the first letter of the tag on top.
- Claimed land is drawn on the in game world map the way Simple Claims draws it. Each claimed chunk gets a translucent fill in the owner's colour and a darker outline on the edges that face other land, so joined claims read as one region. SafeZones are gold and WarZones red. Dominion wraps the world's own map generator instead of replacing it, leaves a map another plugin draws alone and puts the generator back when it shuts down.
- Changed chunks and their neighbours are drawn again within about a second of a claim, unclaim, colour change or disband, for the players who can see them.
- Claims now show one label per faction and world in the middle of its largest region instead of an icon on every chunk. The per chunk icons come back with `map.claimMarkers`, and are used anyway in a world the overlay does not draw.
- New settings `map.overlay` (default `true`), `map.overlayOpacity` (percent, default 55, 10 to 90), `map.overlayBorder` (pixels, default 2, 0 to 4) and `map.claimMarkers` (default `false`).

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
