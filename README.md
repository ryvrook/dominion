# Dominion Factions

Factions for Hytale servers. Players found a faction, claim chunks, hold them with the power of their members,
pick allies and enemies, form nations and lay siege to each other's land. There is a faction bank, a vault, a
server shop, faction shops, a player market and events such as KOTH and outposts. Almost all of it runs from a
panel opened with `/f`, a sidebar keeps the key numbers on screen and the world map is tinted in each faction's
colour. One jar, no required dependencies.

## Requirements

Hytale server 0.7.0 or newer, built against 0.7.1, and Java 25. It still compiles against 0.6.8 and the
manifest allows it, but I only run it on 0.7. Coinage, VaultUnlocked, CombatLog 1.7.0 or newer, TrackerAPI,
Feats and Atlas are optional.

## Install

Drop `DominionFactions-0.6.1.jar` into the server's `mods` folder, or `UserData/Mods` for a singleplayer
world, and start the server. Dominion writes `config.json` with every setting and logs
`Dominion started with 23 modules`. A singleplayer world gets the singleplayer preset on first run, which turns
off raiding, sieges, shields, events and power loss.

For players, `/f` opens the panel, `/f create <tag>` founds a faction, `/f claim` takes the chunk you stand in and
`/f help` lists the rest. For admins, grant `dominion.admin` to staff, pick a preset with
`/fa preset classic|hardcore|peaceful|singleplayer`, open `/fa settings`, and make a spawn SafeZone with
`/fa zone safe <name>`.

## How it plays

Every player has power that regenerates while online and drops by 4 on death, up to 20. A faction can hold one
claim per point of power by default. Claims can be required to touch, kept a buffer chunk away from other
factions and kept off spawn, and can cost money that grows with each claim. Zones split faction land into named
areas with their own permissions. A permission grid per rank and per relation covers building, containers,
doors, homes, warps, the bank, the vault and sieges.

A faction becomes raidable through modes you switch on one by one, overextended, dominated by a stronger enemy,
under siege, low power ratio or unpaid upkeep, and any one or all of them can be required. A siege means planting a
banner on enemy land and holding it for 15 minutes to breach the chunk. Defenders near the banner hold up the
capture. Each faction can pick a daily shield window that protects the core land around its home, and a
successful defence buys immunity. There is also a DTR style deaths pool mode if you prefer it.

Relations are ally, truce, neutral and enemy, and alliances and truces need both sides. Allies can form a nation
under one tag with its own chat. A combat tag blocks homes, warps, `/f stuck` and claiming, and pushes tagged
players back out of SafeZones. With [CombatLog](https://www.curseforge.com/hytale/mods/combatlog) installed,
Dominion reads its tag too and takes the longer of the two. Punishing combat logging stays with CombatLog.

Progress comes from seven upgrades bought with the bank or faction points, daily and weekly missions such as
mining, chopping, farming and winning sieges, and a value leaderboard built from land, members, power, kills,
sieges, bank and outpost time. Events are KOTH hills, outposts that grant an xp, missions, tax or sell boost,
envoy chests in WarZones, bounties and a season clock with an end of the world finale and payouts.

The importer reads Simple Claims, HyFactions, HyperFactions and Ultimate Factions data, always with a preview
first.

## Commands

`/f` (aliases `/faction`, `/factions`) needs no permission. `/fa` and the admin parts of `/fe` and `/fashop`
need `dominion.admin`. `/f help <topic>` and `/fa` alone list everything.

| Command | What it does |
|---|---|
| `/f` | Open the panel |
| `/f create <tag> [name]` | Found a faction |
| `/f invite <player>`, `/f join <tag>`, `/f leave` | Membership |
| `/f promote`, `/f demote`, `/f kick`, `/f ban <player>` | Members |
| `/f perms`, `/f rankname` | Permission grid and rank names |
| `/f claim`, `claim <r>` or `claim radius <r>`, `claim line <n>`, `claim auto` | Claim land, a radius claims every chunk the rules and your power allow |
| `/f unclaim`, `unclaim <r>` or `unclaim radius <r>`, `unclaim all` | Release land |
| `/f map`, `/f power [player]`, `/f who [tag or player]` | Land, power and faction info |
| `/f map hide`, `/f map show` | Hide yourself from the web map |
| `/f ally`, `/f truce`, `/f neutral`, `/f enemy <tag>` | Relations, answered with `/f accept` or `/f decline` |
| `/f home`, `/f sethome`, `/f warp <name> [password]`, `/f stuck` | Homes and warps |
| `/f bank`, `/f deposit <amount>`, `/f withdraw <amount>` | Faction bank |
| `/f market`, `/f market sell <price> [amount]`, `/f auction sell <start> [buyout] [hours]` | Player market |
| `/f vault [page]`, `/f upgrades`, `/f missions`, `/f top [page]` | Vault and progression |
| `/f siege`, `/f shield set <hour>`, `/f raids` | Sieges and shields |
| `/f nation create <tag> [name]`, `/f nation invite <tag>` | Nations |
| `/f chat [channel]`, `/f hud`, `/f tutorial` | Chat channel, sidebar, tutorial |
| `/balance [player]`, `/pay <player> <amount>`, `/baltop` | Personal money |
| `/shop`, `/fshop [tag]`, `/fshop manage` | Server shop and faction shops |
| `/fe`, `/fe bounty place <player> <amount>` | Events |
| `/fa settings`, `/fa set <key> <value>`, `/fa get <key>` | Settings |
| `/fa preset <name>`, `/fa reload` | Presets and reloading the file |
| `/fa claim <tag> [radius]`, `/fa unclaim [radius]`, `/fa bypass` | Land overrides |
| `/fa force join\|kick\|leader\|disband\|rename` | Membership overrides |
| `/fa grace on <duration>`, `/fa eotw on\|off` | Grace period and end of the world |
| `/fa import preview <source>`, `/fa import <source>` | Import from another mod |
| `/fa backup`, `/fa diag`, `/fa export`, `/fa integrations` | Backups, diagnostics, a manual snapshot, link status |
| `/fashop edit` | Server shop editor |
| `/fe koth`, `/fe outpost`, `/fe envoy`, `/fe season`, `/fe payouts` | Event management |

Chat channels switch with `/f chat` or a prefix on one line, `!` for faction, `@` for ally and `#` for nation.

## Settings

`config.json` sits in the data folder. There are 258 keys grouped into
Factions, Power, Land, Raiding, Relations, Combat, Homes, Chat, Economy, Market, Sieges, Shields, Events, Progression, Storage, Map, Integrations, Commands, Interface, Colours and Permissions. Every key is listed with its default, limits and description in
[docs/SETTINGS.md](docs/SETTINGS.md), which is generated from `config/Defaults.java`. You rarely need the file.
`/fa settings` has a tab per group and applies changes live. `/fa set` and `/fa get` work from the console.
`/fa preset` only changes the keys the preset names. `/fa config dump` writes everything to `config-dump.txt`,
and `/fa messages` looks up and resets player messages in `messages.json`. Command names, aliases and permission
nodes on commands apply at the next start or `/plugin reload`.

| Key | Default | What it does |
|---|---|---|
| `faction.tagMax` | `4` | Longest faction tag, shown in brackets as `[TAG] Name`. Raise it up to 32 |
| `faction.tagUppercase` | `true` | New and renamed tags are stored in upper case. Existing tags stay as they are until renamed |
| `nation.tagMax` | `4` | Longest nation tag |
| `faction.maxMembers` | `20` | Members per faction |
| `power.max` | `20.0` | Maximum power per player |
| `power.lossPerDeath` | `4.0` | Power lost per death |
| `power.dtrMode` | `false` | Deaths pool instead of power |
| `land.claimsPerPower` | `1.0` | Claims per point of faction power |
| `land.bufferChunks` | `1` | Empty chunks required between factions |
| `raid.enabled` | `true` | Raiding at all |
| `raid.combineAny` | `true` | Any raid mode makes a faction raidable, otherwise all of them |
| `combat.useCombatLog` | `auto` | Read CombatLog's tag when installed, `off` ignores it |
| `chat.nameplateTag` | `false` | Experimental. Faction tag above each player's head |
| `eco.provider` | `auto` | `auto`, `internal`, `vaultunlocked`, `item` or `coinage` |
| `market.taxPercent` | `5.0` | Faction tax on market sales |
| `siege.captureMinutes` | `15` | Siege capture time |
| `export.enabled` | `true` | Write the web map snapshot |
| `map.overlay` | `true` | Tint claimed land on the in game map |
| `map.compass` | `none` | Which Dominion markers show on the compass, `none`, `events` or `all` |

## With the other mods

Every link is optional. A missing mod gets one log line and Dominion carries on. TrackerAPI, Feats, Atlas and
Coinage are listed as optional dependencies so they load first. Each link has a setting under `integrations.`
with `auto` (link when installed and new enough, the default), `always` or `never`, read at start.
`/fa integrations` shows each as bound, missing or off.

**TrackerAPI** 1.0.0 or newer. Dominion publishes ten counters per player, `dominion:claims`, `unclaims`,
`enemy_kills`, `enemy_land_deaths`, `sieges_won`, `sieges_lost`, `missions`, `bank_deposited`,
`factions_founded` and `invites`, each with a TrackerAPI leaderboard. Siege and mission credit goes to every
member, online or not. A chunk released within seven days counts for neither claim nor unclaim, invites count once
per player and faction, and bank deposits count net per day, so loops do not farm them. With TrackerAPI tracking
blocks and items, the mine, chop, farm and fish missions count from its events, and Dominion's own systems take
over again within 5 seconds if it stops.

**Feats** 1.5.0 or newer. The same counters go into Feats, where 15 faction feats read them, and every feat a
player unlocks is posted to their faction chat (`integrations.featsAnnounce`).

**Atlas** 1.6.0 or newer. Atlas reads `export/snapshot.json` for territories, factions, nations, sieges and
events whether or not it is linked. The link adds `/f map hide` on the map and a live sieges layer while
`export.enabled` is off (`integrations.atlasSiegeTier` sets who sees it).

**Coinage** 1.0.0 or newer. With `eco.provider` on `auto` or `coinage`, wallets live in Coinage and every move
shows in `/coinage history` under Dominion. Balances left in Dominion's own ledger move over once, each zeroed and
saved before the deposit. With `eco.coinageBanks` on, each faction bank becomes a Coinage shared account
`dominion:<faction id>` owned by the leader, with member rights by rank. Dominion's rules still sit in front of
every move, including moves from the Coinage pages, so a rank without withdraw rights or an officer past the daily
limit is refused there too. A member's share of the bank counts toward `/networth` as the `faction` part. With
`market.useCoinage` on, the Market tab opens Coinage's market. Faction shops stay Dominion's, since a Coinage shop
cannot be owned by a faction account yet. A disbanded faction's account goes to the leader, or the server with
`eco.coinageDisbandTo` set to `server`.

**Chronicle Stats** reads factions, members, kills and deaths from the snapshot, and the counters above through
TrackerAPI. Nothing to set up.

Lullaby does not touch factions.

After a `/plugin reload` of Feats, Atlas or Coinage, or when one is installed while the server runs, Dominion
notices within 5 seconds and reloads itself 3 seconds later. TrackerAPI 1.0.3 and newer and Coinage 1.1.0 reload
Dominion themselves, and Dominion then skips its own pass.

## Web map snapshot

Dominion writes `export/snapshot.json` in its data folder, every 2 seconds while it carries player positions and
otherwise when something changed. It holds factions with claims and homes, nations, sieges, the grace and end of
the world state, events, an activity feed and recent public chat. Private channels are never written. The format
has `"version": 1` and is documented in the Atlas README. Any tool can read it.

## Data

Everything is JSON in the data folder, `factions/<id>.json`, `players/<uuid>.json`, `claims/<world>.json`, the
market, vault, shops, progress, economy and imports folders, `nations.json`, `sieges.json`, `events.json`,
`bounties.json`, `strikes.json` and `export/snapshot.json`. It autosaves every 30 seconds
(`storage.autosaveSeconds`) and keeps 7 daily zipped backups under `backups/` (`storage.backupKeep`).

## Limits

Dominion boots headless with all 23 modules next to Atlas and CombatLog and has 659 JUnit tests over money, storage,
claims, power, relations, sieges, the market, the importer and the snapshot contract. I have not played it in a
game client yet, so these are unverified until someone does.

- How the panel, its pages, the sidebar, shop rows, item icons and the pickers render.
- Map marker images and tints, vault chest windows and windows closing on disconnect or breach.
- Cross world teleports, home respawn, warmup cancellation and command parsing with apostrophes, multi word zone
  names and custom rank names.
- Item currency counting, VaultUnlocked class names, envoy chest placement and console command payouts.
- Nation chat, the importer against real data, live CombatLog tags and the SafeZone push back.
- The tutorial on first join, the first daily backup and public chat reaching Atlas.

A faction tag in the tab list cannot be done server side.

## Building

JDK 25.

```bash
git clone https://github.com/ryvrook/dominion-factions.git
cd dominion-factions
./gradlew build
./gradlew installMod     # also copies the jar to your local Mods folder, -PmodsDir=<path> for elsewhere
```

The jar lands in `build/libs/DominionFactions-0.6.1.jar`. `-Phytale_channel` and `-Phytale_version` pick
another server artifact. Each feature is a `DominionModule` loaded by name from `Wiring.MODULES`, and
`DominionApi.get()` is the read API for other mods. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) has the conventions.
Read it before a pull request, keep changes inside one module, and say which server version you tested on.

## Licence

MIT, copyright (c) 2026 ryvrook. See [LICENSE](LICENSE).
