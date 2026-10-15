# Dominion Factions

Dominion Factions is a factions plugin for Hytale servers. Players found factions, claim chunks, protect
them with the combined power of their members, make allies and enemies, join nations, trade on a
player market and lay siege to each other's land. Almost everything runs from an in game panel
opened with `/f`, a sidebar keeps the important numbers on screen and the world map shows who owns
what. Server owners get a live settings page, four presets, a full admin command tree and an
importer for land from other claim and faction mods. Dominion is a single jar with no required
dependencies and is released under the MIT License.

| | |
| --- | --- |
| Version | 0.1.0 |
| Hytale server | 0.7.1 or 0.7.0 (Chapter 1), 0.6.8 still compiles |
| Java | 25 |
| Build | Gradle |
| License | MIT |

## Features

### Factions and ranks

- Found a faction with a short tag and an optional display name, then set a description, a message of the day and a colour.
- Four ranks (recruit, member, officer and leader). Each faction can rename any rank.
- Invites, join requests, open or invite only joining, kicks, bans, promotions, demotions, member titles and leadership transfer.
- A permission grid per rank and per relation covering building, containers, doors, homes, warps, the bank, the vault, relations, sieges and more.
- A faction audit log, faction statistics and player profiles.
- Optional cleanup of inactive members and factions, and optional roster rotation when a faction is full.

### Claims and zones

- Claim the chunk you stand in, a square around you, a line in the direction you face, or turn on auto claim.
- Claims can be required to touch, kept a buffer away from other factions and kept away from spawn. Claiming can be limited to chosen worlds.
- Optional claim costs that grow with each claim, and refunds when land is released.
- Zones split faction land into named areas with their own permission grid.
- Server SafeZones and WarZones created by admins.
- A chat map, territory banners at borders and land decay for abandoned factions.

### Power and raiding modes

- Every player has power that regenerates while online and drops on death. Faction power sets how much land a faction can hold.
- An alternative deaths pool mode in the classic DTR style.
- Raiding modes that switch on and off one by one (overextended, dominance by a stronger enemy, sieges, low power ratio and unpaid upkeep). Any one mode or all of them together can be required.
- Minimum defenders online, attacker cooldowns and protection of the home chunk until it is the last one left.

### Relations and nations

- Ally, truce, neutral and enemy. Alliances and truces need both sides to agree, with limits on allies and truces, request expiry and PvP rules by relation with friendly fire, ally fire and truce fire switches.
- A combat tag and an optional bank reward for kills. Tagged players cannot use homes, warps or stuck, cannot claim or unclaim land, and are moved back out of SafeZones they walk into.
- Allied factions can form a nation under one tag with a leading faction, with invites, kicks, leadership transfer and a nation chat channel.

### CombatLog integration

Dominion reads combat tags from [CombatLog](https://www.curseforge.com/hytale/mods/combatlog) by Devify when that mod is installed. A player counts as tagged while either Dominion's own tag or CombatLog's tag runs, and the remaining time shown is the longer of the two. Every combat rule in Dominion uses this shared tag.

- `/f home`, ally homes, warps and `/f stuck` are refused while tagged, and a running teleport warmup is cancelled when the player becomes tagged.
- A tagged player who walks into a SafeZone is moved back to where they stood and told so (`combat.blockSafeZoneEntry`).
- Claiming and unclaiming are refused while tagged (`combat.blockClaimsWhileTagged`).

Punishing players who log out in combat stays with CombatLog. Dominion only reads its tag state, through reflection, so it has no compile time dependency on the mod and runs the same without it. Set `combat.useCombatLog` to `off` to ignore CombatLog and use Dominion's own tag only. The start log and `/fa diag` say which tag source is in use.

### Homes and warps

- A faction home with a warmup, a cooldown, an enemy nearby check and an option to respawn at home.
- Ally homes that allies can open to each other.
- Named warps with optional passwords, and `/f stuck` to escape to the nearest wilderness.

### Economy, bank, shops, market and auctions

- A built in coin ledger, an item currency that uses a chosen item, or an optional bridge to VaultUnlocked.
- A faction bank with deposits, withdrawals, payments to other factions and a daily withdraw limit for officers.
- Optional daily upkeep per claim, member dues, playtime stipends and bank interest.
- A server shop with categories and an in game editor for admins, plus faction shops stocked by officers from the hand or from the faction vault, with sales paid into the faction bank.
- A player market with listings, buy orders, auctions with a buyout price and anti snipe extensions, a faction storefront and a mailbox for earnings and returned items.

### Vault

- A shared faction vault with pages that open as a chest window. Extra pages come from the vault upgrade, and the vault is sealed while faction land is breached by a siege.

### Chat

- Public, faction, ally, officer and nation channels, faction tags in chat, announcements, login alerts and a server wide line when a faction is renamed. Switch with `/f chat` or send one line with a prefix (`!` faction, `@` ally and `#` nation by default).

### Sieges, shields and grace

- Plant a siege banner on enemy land next to you and hold it until the capture timer runs out to breach the chunk for a while. Defenders near the banner hold up the capture, and a siege fails when the attackers leave.
- Raid history with raid points, immunity after a successful defence and a daily shield window chosen by each faction that protects the core land around its home.
- A server grace period and an end of the world mode for the close of a season.

### Upgrades and missions

- Seven upgrades (members, regen, maxpower, warps, vault, shield and immunity) paid from the bank or with faction points.
- Daily and weekly missions such as mining, chopping, farming, fishing, market trades, claiming and winning sieges.
- A faction value leaderboard built from land, members, power, kills, sieges, bank and outpost time, and admin strikes that lower a faction's value.

### Events

- **KOTH.** Hills open on demand or on a schedule, and the holding faction wins coins and faction points.
- **Outposts.** Controlled outposts grant an xp, missions, tax or sell boost. The xp boost is offered to other mods through the API.
- **Envoys.** Loot chests dropped in WarZones on demand or on a timer.
- **Bounties.** Any player can put money on another player's head.
- **Season clock.** Season start and end, an end of the world finale, a frozen leaderboard and payout commands for the top factions.

### Interface and admin

- A faction panel with tabs for every enabled feature, a permission grid page, zone permissions, faction info pages, a top factions page, a tutorial and confirmation pages before anything destructive.
- A sidebar HUD and world map markers for factions, homes, sieges, hills, outposts and envoys.
- Claimed land on the in game world map is tinted in the owner's colour with a darker outline around each
  region, so neighbouring claims of one faction read as one area and the terrain stays visible underneath.
  Each faction gets one label in the middle of its largest region. `map.claimMarkers` brings back one icon
  per claimed chunk.
- An admin settings page with every setting grouped by topic. Changes save at once and apply live.
- Classic, hardcore, peaceful and singleplayer presets. Singleplayer worlds get the singleplayer preset automatically on first run, which turns off raiding, sieges, shields, events and power loss.
- An importer for Simple Claims, HyFactions, HyperFactions and Ultimate Factions data, with a required preview.

## Requirements

- A Hytale server on 0.7.0 or 0.6.8, dedicated or a singleplayer world.
- No other mods. VaultUnlocked and CombatLog 1.7.0 or newer are optional.
- JDK 25 only if you build from source.

## Install

1. Get `DominionFactions-0.4.0.jar` from a release or build it from source and drop it into the Mods folder.

| Setup | Mods folder |
| --- | --- |
| Windows client | `%APPDATA%\Hytale\UserData\Mods` |
| macOS client | `~/Library/Application Support/Hytale/UserData/Mods` |
| Dedicated server | the server's `mods` directory |

2. Start the server once. Dominion writes `config.json` with every setting at its default.
3. Look for `Dominion started with 23 modules` in the server log.

## Quick start

### Players

1. Type `/f` to open the panel, or `/f create <tag> [name]` to found a faction.
2. Stand in a chunk and run `/f claim`.
3. Bring friends in with `/f invite <player>`. They join with `/f join <tag>`.
4. Set a home with `/f sethome` and return with `/f home`.
5. Run `/f help` for every command, or `/f help <topic>` for one area.

### Admins

1. Grant the `dominion.admin` permission to staff. Players need no permission for `/f`.
2. Pick a profile with `/fa preset classic|hardcore|peaceful|singleplayer`.
3. Open `/fa settings` to adjust individual rules.
4. Create a spawn SafeZone with `/fa zone safe <name>` and WarZones with `/fa zone war <name>`. Run `/fa` alone for the full admin command list.

## Commands

| Command | Description |
| --- | --- |
| `/f` | Open the faction panel. Aliases `/faction` and `/factions` |
| `/f create <tag> [name]` | Found a faction |
| `/f invite <player>`, `/f join <tag>`, `/f leave` | Manage membership |
| `/f promote`, `/f demote`, `/f kick`, `/f ban <player>` | Manage members |
| `/f perms`, `/f rankname` | Edit the permission grid and rename ranks |
| `/f claim`, `/f claim radius <r>`, `/f claim line <n>`, `/f claim auto` | Claim land |
| `/f unclaim`, `/f unclaim radius <r>`, `/f unclaim all` | Release land |
| `/f zone create <name>`, `/f zone assign <name>` | Manage zones |
| `/f map`, `/f power [player]`, `/f who [tag or player]` | Show land, power and faction info. `/f who` prints the classic layout with age, land, power, relations and the roster |
| `/f map hide`, `/f map show` | Hide yourself from the web map or show yourself again. Also a button on the Overview tab |
| `/f ally`, `/f truce`, `/f neutral`, `/f enemy <tag>` | Set relations. Answer with `/f accept` or `/f decline` |
| `/f home`, `/f sethome`, `/f warp <name> [password]`, `/f stuck` | Homes and warps |
| `/f bank`, `/f deposit <amount>`, `/f withdraw <amount>` | Faction bank |
| `/f market`, `/f market sell <price> [amount]` | Player market |
| `/f auction sell <start> [buyout] [hours]` | Auction the stack in your hand |
| `/f vault [page]`, `/f upgrades`, `/f missions`, `/f top [page]` | Vault and progression. `/f top` pages the value leaderboard |
| `/f siege`, `/f shield set <hour>`, `/f raids` | Sieges and shields |
| `/f nation create <tag> [name]`, `/f nation invite <tag>` | Nations |
| `/f chat [channel]`, `/f hud`, `/f tutorial` | Chat channel, sidebar and tutorial |
| `/balance [player]`, `/pay <player> <amount>`, `/baltop` | Personal money |
| `/shop`, `/fshop [tag]`, `/fshop manage` | Server shop and faction shops |
| `/fe`, `/fe panel`, `/fe bounty place <player> <amount>` | Events |
| `/fa` | Admin tools. Requires `dominion.admin` |
| `/fa settings`, `/fa set <key> <value>`, `/fa get <key>` | Settings |
| `/fa preset <name>`, `/fa reload` | Presets and reloading the file |
| `/fa config [section\|key\|dump] [value]`, `/fa messages [id\|search\|reset]` | Browse and dump settings, look up and reset messages |
| `/fa claim <tag> [radius]`, `/fa unclaim [radius]`, `/fa bypass` | Land overrides |
| `/fa force join\|kick\|leader\|disband\|rename` | Membership overrides |
| `/fa grace on <duration>`, `/fa eotw on\|off` | Grace and end of the world |
| `/fa import preview <source>`, `/fa import <source>` | Import from another mod |
| `/fa backup`, `/fa diag`, `/fa export` | Backups, diagnostics and a manual snapshot |
| `/fashop edit` | Server shop editor. Requires `dominion.admin` |
| `/fe koth`, `/fe outpost`, `/fe envoy`, `/fe season`, `/fe payouts` | Event management. Admin sub commands require `dominion.admin` |

## Configuration

Settings live in `config.json` inside the plugin data folder. There are 248 keys grouped into
Factions, Power, Land, Raiding, Relations, Combat, Homes, Chat, Economy, Market, Sieges, Shields, Events, Progression, Storage, Map, Integrations, Commands, Interface, Colours and Permissions. Every key is listed with its default, its limits and a description in
[docs/SETTINGS.md](docs/SETTINGS.md), which is generated from
`src/main/java/dev/ryvrook/dominion/config/Defaults.java`.

Admins rarely need to touch the file.

- `/fa settings` opens the settings page. Each group is a tab, and changes save and apply live.
- `/fa set <key> <value>` and `/fa get <key>` work from chat or the server console.
- `/fa reload` reads the file again after a manual edit.
- `/fa preset <name>` applies a preset. A preset only changes the keys it names.
- `/fa config` lists the sections, `/fa config <section>` pages through one, `/fa config <key> <value>` changes a key
  and `/fa config dump` writes every key with its description, value, default and limits to `config-dump.txt`.
- `/fa messages` counts the message ids in `messages.json`, `/fa messages <id>` shows one, `/fa messages search <text>`
  finds ids and `/fa messages reset <id>` puts the default wording back.
- Colours, command names and aliases, the disabled sub commands, permission nodes and a switch for every feature
  are settings too. Command names and permission nodes on commands apply at the next start or `/plugin reload`.

A few keys worth knowing

| Key | Default | Meaning |
| --- | --- | --- |
| `faction.maxMembers` | `20` | Members per faction |
| `power.max` | `20.0` | Maximum power per player |
| `power.lossPerDeath` | `4.0` | Power lost per death |
| `power.dtrMode` | `false` | Use the deaths pool mode instead of power |
| `land.claimsPerPower` | `1.0` | Claims allowed per point of faction power |
| `land.bufferChunks` | `1` | Empty chunks required between factions |
| `raid.enabled` | `true` | Raiding enabled at all |
| `raid.combineAny` | `true` | Any raid mode makes a faction raidable, otherwise all |
| `combat.useCombatLog` | `auto` | `auto` reads combat tags from CombatLog when it is installed, `off` ignores it |
| `combat.blockSafeZoneEntry` | `true` | Move combat tagged players back out of SafeZones |
| `combat.blockClaimsWhileTagged` | `true` | Refuse claiming and unclaiming while combat tagged |
| `chat.showTag` | `true` | Show the faction tag before player names in public chat |
| `chat.nameplateTag` | `false` | Experimental. Show the faction tag before the name above each player's head |
| `chat.announceRenames` | `true` | Tell every online player when a faction changes its tag or name |
| `top.pageSize` | `10` | Factions per page of `/f top`, 5 to 25 |
| `eco.provider` | `internal` | `internal`, `vaultunlocked` or `item` |
| `market.taxPercent` | `5.0` | Faction tax on market sales |
| `siege.captureMinutes` | `15` | Siege capture time |
| `events.enabled` | `true` | Events layer enabled |
| `export.enabled` | `true` | Write the web map snapshot file |
| `map.overlay` | `true` | Tint claimed land on the in game world map |
| `map.overlayOpacity` | `55` | Overlay opacity in percent, 10 to 90 |
| `map.overlayBorder` | `2` | Overlay outline width in pixels, 0 to 4 |
| `map.claimMarkers` | `false` | One icon per claimed chunk instead of one label per faction |
| `misc.singleplayerAutoPreset` | `true` | Apply the singleplayer preset automatically |

## Integrations

Dominion works on its own and works better next to the other Ryvrook mods. Each link is optional. When a mod is
missing Dominion behaves exactly as before, logs one line that it runs without it and never fails. The manifest lists
TrackerAPI, Feats and Atlas as optional dependencies, so the server loads them first when they are installed.

| Mod | What it adds | Setting |
| --- | --- | --- |
| [TrackerAPI](https://www.curseforge.com/hytale/mods/trackerapi) 1.0.0 or newer | Dominion publishes ten per player counters under `dominion:`, with leaderboards for claims, enemy kills, sieges won, missions and bank deposits | `integrations.tracker` |
| Feats 1.5.0 or newer | The same counters are mirrored into Feats, and every feat a player unlocks is posted to their faction chat | `integrations.feats`, `integrations.featsAnnounce` |
| Atlas 1.6.0 or newer | A live Sieges layer with the phase and end time of every siege, and `/f map hide` hides the player in Atlas as well | `integrations.atlas` |
| Chronicle Stats | Reads factions, members, kills and deaths from the map snapshot. Nothing to set up | none |

The three link settings take `auto` (link when the mod is installed and new enough, the default), `always` (link to
any installed version and warn at start when the mod is missing) or `never` (ignore the mod). They are read when the
server starts. `integrations.featsAnnounce` is `true` by default and can be changed at any time.
`/fa integrations` lists each mod as bound, missing or off, and the start log has one line per mod, for example
`Dominion is bound to TrackerAPI 1.0.3`.

The counters are `dominion:claims` and `dominion:unclaims` (a chunk the faction released within the last seven days counts for neither when it is claimed or released again), `dominion:enemy_kills` (a kill of a member of a faction at
war with yours), `dominion:enemy_land_deaths` (deaths on the land of a faction at war with yours),
`dominion:sieges_won`, `dominion:sieges_lost` (every member of both factions when a siege ends), `dominion:missions`
(every member when the faction completes a mission), `dominion:bank_deposited` (whole coins),
`dominion:factions_founded` and `dominion:invites`. TrackerAPI and Feats only take counters for players who are
online, which is everyone involved except for siege and mission credit to offline members.

Feats has no file for server defined feats yet, so the counters show in its stats and wait there for feats that read
them. Atlas draws claims, homes, warps, hills, outposts and envoys from the map snapshot whether or not it is linked,
so the link only adds what the snapshot cannot carry. After a `/plugin reload` of Feats or Atlas, run
`/plugin reload Ryvrook:Dominion` too, Dominion logs a reminder. TrackerAPI 1.0.3 reloads Dominion by itself.

Dominion also uses [CombatLog](https://www.curseforge.com/hytale/mods/combatlog) when it is installed, see CombatLog
integration above. Money from Coinage reaches Dominion through VaultUnlocked with `eco.provider` set to
`vaultunlocked`.

## Web map export

Dominion writes `export/snapshot.json` in its data folder every few seconds while something
changes (`export.intervalSeconds`, default 2). The snapshot holds factions with their claims and
homes, nations, sieges, the grace and end of the world state, events, an activity feed and recent
public chat. Private channels are never written.

The snapshot is meant for **Atlas**, a separate live web map plugin that reads the file and shows
the server's factions on a map. Dominion does not serve the map itself. Other tools can read the
file too. The format carries a `version` field, currently `1`. Turn the export off with
`/fa set export.enabled false`.

## Data layout

Everything lives in the plugin data folder as JSON.

```text
config.json              settings
factions/<id>.json       one file per faction
players/<uuid>.json      one file per player
claims/<world>.json      claims per world
market/                  listings, history and mailboxes
vault/  shops/  progress/  economy/  imports/
nations.json  sieges.json  events.json  bounties.json  strikes.json
export/snapshot.json     web map snapshot
backups/                 daily zipped backups (storage.backupKeep, default 7)
```

Data autosaves every 30 seconds by default (`storage.autosaveSeconds`).

## Building from source

Requires JDK 25. The build compiles against the Hytale server artifact from Hytale's Maven
repository. The channel and version come from `gradle.properties` and can be overridden on the
command line.

```bash
git clone https://github.com/ryvrook/dominion-factions.git
cd dominion
./gradlew build
./gradlew build -Phytale_channel=release -Phytale_version=0.6.8
```

The jar lands in `build/libs/DominionFactions-0.4.0.jar`. To copy it straight into your local Mods folder
run the `installMod` task, optionally with `-PmodsDir=<path>`.

```bash
./gradlew installMod
```

## Project layout

```text
src/main/java/dev/ryvrook/dominion/
  DominionPlugin, DominionContext, Wiring   plugin entry and module loading
  model, storage, config, service, util, text   shared skeleton
  api/                                      public API for other mods
  economy, faction, power, relation, land, combat, chat, home, shield, siege,
  market, vault, progress, nation, shop, events, importer, ui, map,
  command, admin, export                    one package per feature module
src/main/resources/                         manifest, UI markup and assets
docs/                                      DEVELOPMENT.md conventions and STATUS.md
```

- Each feature module implements `DominionModule` and is loaded by class name from `Wiring.MODULES`. A missing module is logged and skipped.
- Modules register services with the context and look up other services lazily. Mutations go through services and return a `Result` with a readable reason.
- The `api` package is the read API for other mods. Call `DominionApi.get()` after Dominion has started to query factions, claims, relations, power, raidable state, permission checks, outpost boosts and the event bus.
- `docs/DEVELOPMENT.md` is the conventions doc. Read it before opening a pull request.

## Status

Dominion Factions 0.4.0 builds against server 0.7.1 and still compiles on 0.7.0 and 0.6.8, boots headless with all
23 modules next to Atlas and next to CombatLog, and has a JUnit suite of 600 tests over money moves, storage, settings, parsers, claims,
power, membership, relations, nations, sieges, shields, the PvP table, events, missions, the market, shops, homes,
the importer and the web map snapshot contract. **It has not been played in a game client yet.** The following
are unverified until someone does.

- Rendering of the panel, every page, the HUD sidebar, shop rows and vault stocking, item icons and the hour and quantity pickers.
- Map marker images and tints, vault chest windows, container change events and windows closing on disconnect or breach.
- Cross world teleports, home respawn, warmup cancellation and command parsing with apostrophes, multiword zone names and custom rank names.
- Item currency counting, VaultUnlocked class names, owed item delivery, envoy chest placement and console command payouts.
- Nation chat delivery and importer results against real data from the other mods.
- Live CombatLog tags blocking homes, claims and SafeZone entry, and the SafeZone push back teleport itself.
- The tutorial on first join, the first daily backup, truce door refusal and public chat reaching Atlas.

Tab list integration is not possible server side and is out of scope.

## Contributing

Bug reports and pull requests are welcome. Please test in a client if you can and say which server
version you used. Keep changes inside the module you are touching, follow `docs/DEVELOPMENT.md`, and
make sure `./gradlew build` passes on both 0.7.0 and 0.6.8. For larger changes open an issue first.

## License

Dominion is released under the MIT License. Copyright (c) 2026 ryvrook. See [LICENSE](LICENSE).
