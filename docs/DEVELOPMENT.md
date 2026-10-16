# Dominion development guide

Dominion is a factions server plugin for Hytale (server 0.7.0, also compiles on 0.6.8).
This guide describes the conventions the code follows and the server API signatures that were verified with javap.
Please read it before opening a pull request.

## Conventions

- The plugin targets Java 25 and has no runtime dependencies beyond the Hytale server jar and the JDK.
  It compiles against the TrackerAPI, Feats, Atlas and Coinage api jars for the optional integrations, see Integrations below.
  Set JAVA_HOME to a JDK 25 and build with `./gradlew build`. To check the older server, build with
  `./gradlew build -Phytale_channel=release -Phytale_version=0.6.8`.
- Player facing text, log lines and comments are written as plain sentences. Avoid em dashes,
  semicolons in prose and "Label: value" colons. Code syntax is fine.
- Every module lives in its own package and implements `dev.ryvrook.dominion.DominionModule`
  with a public no-arg constructor. `init` registers services into the context with
  `ctx.register(ServiceInterface.class, impl)`. `start` registers commands, ECS systems,
  event listeners and scheduled tasks. Modules are loaded by class name from `Wiring.MODULES`.
- A change to a module stays inside its package, plus `src/main/resources` for the markup it owns.
  The shared core (model, storage, config, api, service, util, text, DominionPlugin, DominionContext
  and Wiring) changes rarely. When an interface has to change, change it in the core first and update
  every implementation in the same change.
- Look services up lazily with `ctx.service(X.class)` inside methods, not in constructors or `init`,
  because init order is economy, faction, power, relation, land, combat, chat, home, ui, map, command, admin.
- Thread rules. World threads run ECS systems and events. `HytaleServer.SCHEDULED_EXECUTOR`
  runs the context timers. Repository, Faction and PlayerData are thread safe. Anything that
  touches a Store, Ref or component runs on that world's thread (`world.execute`).
- All mutations go through services. Every service call returns a `Result` with a reason the
  player can read, and every important change calls `FactionService.audit` and publishes a
  `DominionEvent` on `ctx.bus()`.
- Settings are read live with `ctx.settings().bool/integer/decimal/text(key)`. Keys are in
  `config/Defaults.java`, and a setting is never cached across calls. A key defined with
  `defineFileOnly` can only be changed in config.json, never through /fa set or the settings page.
- Messages use the `dev.ryvrook.dominion.text.Msg` helpers and the colours defined there. The colours come from the
  `colors.*` settings through `text/Palette`, which `Settings` refreshes after every load, reload, set and preset, so the
  `Msg` colour fields are not final and must never be copied into a `static final` field. A class that keeps a colour in
  a field registers with `Palette.onChange`.
- Command names come from `config/CommandNames` and permission nodes from `config/Perms`. Write `/f`, `/fa`, `/fe` and
  `/fashop` in sentences as usual, the `Msg` helpers and the panel text rewrite them to the names registered at start.
  A command that runs another command, such as a map context menu entry, builds it with `CommandNames.root()`.
- A new key goes in `config/Defaults.java` with a range, choices or pattern. `docs/SETTINGS.md` and the key count in
  README.md are generated, run the tests with `DOMINION_WRITE_DOCS=1` to write them again.
- A sub command of `/f` that belongs to a feature with an `enabled` switch is gated in `CommandModule.gates`, and its
  panel tab is hidden in `DominionPanel.hiddenTabs`.

## Verified Hytale API facts (javap against 0.7.0, first checked on 0.7.0-pre.5.1)

- Plugin base: `com.hypixel.hytale.server.core.plugin.JavaPlugin` gives `getCommandRegistry()`,
  `getEventRegistry()`, `getEntityStoreRegistry()`, `getChunkStoreRegistry()`, `getDataDirectory()`,
  `getLogger()` (flogger style, `logger.at(Level.INFO).log("x %s", y)`). Access via `ctx.plugin()`.
- Commands: extend `com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand`
  with constructor `(String name, String description)` and
  `protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world)`.
  `addSubCommand(AbstractCommand)`, `addAliases(String...)`, `requirePermission(String)`.
  Arguments: `withRequiredArg(name, desc, ArgTypes.STRING)` returns `RequiredArg<T>`, read with `arg.get(context)`.
  Also `withOptionalArg`, `withDefaultArg(name, desc, type, default, desc)`. `ArgTypes` is in
  `com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes` (STRING, INTEGER, FLOAT, DOUBLE, BOOLEAN, PLAYER_REF and more, check with javap).
  Send text with `context.sendMessage(Message)`. Register with `ctx.plugin().getCommandRegistry().registerCommand(cmd)`.
- Messages: `Message.raw(String)`, `.color("#hex")`, `.bold(true)`, `Message.join(Message...)`,
  `playerRef.sendMessage(Message)`.
- Player lookup: `Universe.get().getPlayer(UUID)` returns `PlayerRef` or null. `PlayerRef` has
  `getUuid()`, `getUsername()`, `getTransform()` (position is `getTransform().getPosition()` as joml Vector3d),
  `getWorldUuid()`, `getPacketHandler()`, `getReference()` (entity Ref), `hasPermission(String)`.
  `Universe.get().getWorld(UUID)` returns `World`. `World` has `getName()`, `execute(Runnable)`,
  `scheduleAfter(Runnable, long, TimeUnit)`, `getEntityStore()`, `getWorldMapManager()`, `getPlayerRefs()`.
  Helper `dev.ryvrook.dominion.util.Players` wraps these.
- Chunks: `dev.ryvrook.dominion.util.Keys` wraps `com.hypixel.hytale.math.util.ChunkUtil`.
  Chunk size is `ChunkUtil.SIZE` blocks.
- ECS event systems: extend `com.hypixel.hytale.component.system.EntityEventSystem<EntityStore, E>`
  with constructor `super(E.class)`, implement
  `handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> cb, E event)`
  and `Query<EntityStore> getQuery()` (return `Player.getComponentType()` for player entities,
  `Archetype.empty()` for all). Get the acting entity with `chunk.getReferenceTo(index)` and the player with
  `store.getComponent(ref, PlayerRef.getComponentType())`. Register with
  `ctx.plugin().getEntityStoreRegistry().registerSystem(system)`.
  Cancellable events (package `com.hypixel.hytale.server.core.event.events.ecs`): `BreakBlockEvent`
  (`getTargetBlock()` Vector3i, `getBlockType().getId()`), `PlaceBlockEvent` (`getItemInHand()`, `getTargetBlock()`),
  `DamageBlockEvent`, `UseBlockEvent.Pre` (`getTargetBlock()`, `getBlockType()`), `UseEntityEvent.Pre` (`getTargetEntity()`),
  all with `setCancelled(true)`.
- Damage: `com.hypixel.hytale.server.core.modules.entity.damage.Damage` is a cancellable ECS event on the victim.
  `getSource()` may be `Damage.EntitySource` (`getRef()` is the attacker, `Damage.ProjectileSource` extends it).
  Extend `com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem` and override
  `public SystemGroup<EntityStore> getGroup()` returning `DamageModule.get().getFilterDamageGroup()`.
  Deaths: extend `com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.OnDeathSystem`, implement
  `getQuery()` and `onComponentAdded(Ref, DeathComponent, Store, CommandBuffer)`.
- Chat: `ctx.plugin().getEventRegistry().registerAsyncGlobal(PlayerChatEvent.class, future -> future.thenApply(e -> { ...; return e; }))`.
  `PlayerChatEvent` has `getSender()`, `getTargets()`/`setTargets(List<PlayerRef>)`, `getContent()`/`setContent`,
  `setFormatter((sender, content) -> Message)`, `setCancelled(boolean)`.
- Regular events: `getEventRegistry().registerGlobal(PlayerReadyEvent.class, consumer)`. The plugin already handles
  ready and disconnect and exposes `ctx.onPlayerReady(Consumer<PlayerRef>)` and `ctx.onPlayerQuit(Consumer<UUID>)`.
- Teleport: `Teleport.createForPlayer(world, new Transform(x, y, z, pitch, yaw, 0))` then
  `store.putComponent(ref, Teleport.getComponentType(), teleport)` on the world thread, where
  `store = world.getEntityStore().getStore()` and `ref = playerRef.getReference()`.
- Notifications: `NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message title, Message body, new ItemStack("Item_Id", 1).toPacket(), NotificationStyle.Default)`.
  Sound: `SoundUtil.playSoundEvent2dToPlayer(playerRef, SoundEvent.getAssetMap().getIndex("SFX_Memories_Unlock_Local"), SoundCategory.UI)`.
- Custom pages: extend `InteractiveCustomUIPage<T>` with `super(playerRef, CustomPageLifetime.CanDismiss, T.CODEC)`,
  implement `build(Ref, UICommandBuilder, UIEventBuilder, Store)` and `handleDataEvent(Ref, Store, T)`, call `rebuild()` to refresh.
  `UICommandBuilder.append("Pages/File.ui")`, `.append("#List", "Pages/Row.ui")`, `.set("#List[0] #Name.Text", "x")`,
  `.set("#Bar.Value", 0.5f)`, `.set("#Thing.Visible", false)`, `.clear("#List")`, `.appendInline(selector, markup)`.
  `UIEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#List[0] #Button", EventData.of("Action", "x").append("Value", "y"), false)`.
  Event data class uses `BuilderCodec` with `KeyedCodec("Action", Codec.STRING)` setter `(e, v, extra) -> e.action = v` getter `(e, extra) -> e.action`.
  Open with `player.getPageManager().openCustomPage(ref, store, page)` where `player = store.getComponent(ref, Player.getComponentType())`.
  `.ui` markup lives under `src/main/resources/Common/UI/Custom/Pages/` and imports `$C = "../Common.ui";` for vanilla styles.
  Vanilla markup examples can be extracted from Assets.zip under Common/UI/Custom.
  `TextField` elements exist with a `Value` property and a `ValueChanged` binding type. `ItemIcon` has `ItemId`. `ProgressBar` has `Value`.
- HUD: extend `CustomUIHud(playerRef, key)`, implement `build(UICommandBuilder)`, add with
  `player.getHudManager().addCustomHud(playerRef, hud)`, update with `hud.update(false, builder)`, remove with `removeCustomHud(playerRef, key)`.
- World map: `world.getWorldMapManager().addMarkerProvider(id, (world, player, collector) -> collector.add(marker))`.
  Marker via `new MapMarkerBuilder(id, "MapMarkers/Image.png" style path as vanilla uses, transform).withName(Message).withComponent(new TintComponent(new Color(...))).withContextMenuItem(new ContextMenuItem("Label", "/f claim")).build()`.
  Vanilla marker images are at `Common/UI/WorldMap/MapMarkers/*.png` (Home, Warp, Player, UserA to UserF). Check `MapMarkerBuilder` and `MarkersCollector` with javap for exact signatures before use.
- World map images (checked on 0.7.1 with javap -c). `WorldMapManager.getGenerator()` returns the world's
  `IWorldMap`, normally `ChunkWorldMap.INSTANCE`. `IWorldMap` has `getWorldMapSettings()`,
  `generate(World, int width, int height, LongSet chunks)` returning `CompletableFuture<WorldMap>`,
  `generatePointsOfInterest(World)` and a default no-op `shutdown()`. The manager's own ticking thread calls
  `generate` with a batch of chunk indexes and a size of `floor(32 * imageScale)` for both sides, then reads
  each image from `WorldMap.getChunks()` (a `Long2ObjectMap<MapImage>` keyed by chunk index) and caches it.
  A generated default world uses `com.hypixel.hytale.server.worldgen.map.GeneratorChunkWorldMap`, which extends
  `ChunkWorldMap` and keeps its `generate` and the no-op `shutdown`. A headless 0.7.1 boot on 2026-10-15 logged the swap
  to `DominionWorldMap(...GeneratorChunkWorldMap@...)`, `/worldmap reload` replaced it and the minute scan wrapped the
  new generator again, and `/plugin reload` handed the same generator back before the new instance wrapped it once.
- `MapImage` (package `com.hypixel.hytale.protocol.packets.worldmap`) has public fields `width`, `height`,
  `int[] palette`, `byte bitsPerIndex` and `byte[] packedIndices`, and a constructor taking all five. Palette
  entries are RGBA packed as `r << 24 | g << 16 | b << 8 | a`. Pixels are row major from the north west
  corner, pixel `y * width + x`, with x towards +X and y towards +Z. Indexes are packed by the engine
  `BitFieldArr` little end first, index i starting at bit `i * bits`, and may straddle bytes. The engine
  picks 4 bits for up to 16 colours, 8 up to 256, 12 up to 4096 and 16 above.
- `WorldMapManager.setGenerator(IWorldMap)` calls `shutdown()` on the old generator, stores the new one, logs
  "Initializing world map generator: " plus its `toString()`, completes and replaces `generatorLoaded`, applies
  the view radius overrides, clears every cached and generating image, cancels the queue, calls
  `getWorldMapTracker().clear()` on every player of the world (a full map resend), updates the ticking state,
  sends the settings and generates the points of interest again. A null generator disables the map.
  The world calls it during init, and `/worldmap reload` and `/worldgen reload` call it again with a fresh
  generator. It has no thread assertion, Dominion calls it on the world thread.
- `isWorldMapEnabled()` reads the settings packet's `enabled` flag. `WorldMapSettings.DISABLED` is the
  settings of a disabled map.
- `WorldMapManager.clearImagesInChunks(LongSet)` drops the cached image, the running generation and any
  queued request of each chunk. `WorldMapTracker` (package `com.hypixel.hytale.server.core.universe.world`,
  from `Player.getWorldMapTracker()`) has `clearChunks(LongSet)`, which under its own lock forgets the
  chunks and queues them for a reload that generates and sends them whether or not they are in view, so
  Dominion only passes chunks for which `shouldBeVisible(getEffectiveViewRadius(world), index)` is true.
  `World.getPlayers()` is deprecated for removal, so the Player component is read from each
  `getPlayerRefs()` entry on the world thread.

- Permissions: `PermissionsModule.get().hasPermission(UUID, String)`, commands `requirePermission("dominion.admin")`.
- Item stacks: `new ItemStack(itemId, quantity)`, `ItemStack.CODEC` for persistence.
- Nameplates (checked with javap on 0.7.1). The text above a player's head is the
  `com.hypixel.hytale.server.core.entity.nameplate.Nameplate` component with `getText()` and `setText(String)`.
  `setText` marks it network outdated and `NameplateSystems.EntityTrackerUpdate` sends a `NameplateUpdate` to viewers.
  `PlayerSystems.NameplateRefSystem.onEntityAdded` creates it from `DisplayNameComponent.getDisplayName().getAnsiMessage()`
  only when the entity has no Nameplate yet, and `NameplateRefChangeSystem` rewrites it whenever `DisplayNameComponent`
  is added, set or removed. The component is registered with a codec, so it is saved with the player.
  `Store.removeEntity` calls every `RefSystem.onEntityRemove` before it copies the components into the saved holder,
  which is where `chat/Nameplates` puts the plain name back. Set the text on the world thread only.
  The tab list is not reachable this way and stays out of scope.

Javap any class before relying on it:
`javap -cp <path to the Server jar from maven.hytale.com> -p <class>`.
A class list comes from `unzip -l` on the same jar.

## Verified CombatLog API facts (javap -public on CombatLog 1.7.0 by Devify, api package only)

CombatLog (CurseForge `combatlog`, plugin id `Devify:CombatLog`, MIT) is an optional dependency. Dominion never
compiles against it and the jar is never added to the repository. `combat/CombatLogBridge` resolves the class with
`Class.forName` across Dominion's loader, the thread loader and every plugin loader, then looks up method handles
with `MethodHandles.publicLookup().findStatic`. Only the public API package below was inspected.

- `org.combatlog.plugin.api.CombatLogApi` is a final class with static methods only.
  `public static boolean isAvailable()`, `public static boolean isTagged(UUID)`,
  `public static long getRemainingMillis(UUID)`, `public static CombatTagInfo getTagInfo(UUID)`.
  The listing says they answer safe defaults when the mod is not running.
- `org.combatlog.plugin.api.CombatTagInfo` is a final class with `static CombatTagInfo empty(UUID)`,
  `getPlayerUuid()`, `isTagged()`, `getRemainingMillis()` (long), `getCause()`, `getAttackerName()` and `isAttackerPlayer()`.
- `org.combatlog.plugin.api.CombatTagCause` is an enum with PVP, PVE, ENVIRONMENT and UNKNOWN.
- `org.combatlog.plugin.api.CombatTagEndReason` is an enum with EXPIRED, DEATH, DISCONNECT, CLEARED, MOD_DISABLED and MANUAL.
- `org.combatlog.plugin.api.event.CombatTagStartedEvent` implements `com.hypixel.hytale.event.IEvent<Void>` with
  `getPlayerUuid()`, `getCause()`, `getAttackerName()` and `isAttackerPlayer()`.
- `org.combatlog.plugin.api.event.CombatTagEndedEvent` implements `IEvent<Void>` with `getPlayerUuid()`, `getReason()`,
  `getLastCause()`, `getLastAttackerName()` and `isLastAttackerPlayer()`.
- Dominion uses only `isAvailable`, `isTagged` and `getRemainingMillis`. Warmups and the SafeZone guard poll the shared
  tag, so the events are not subscribed to.
- The installed version comes from `PluginManager.get().getPlugin(new PluginIdentifier("Devify", "CombatLog")).getManifest().getVersion()`.
  The manifest lists `"OptionalDependencies": { "Devify:CombatLog": ">=1.7.0" }` so the engine sets CombatLog up first.
- Every combat rule reads `CombatTagService` (tagged and remainingMillis). `PlayerData.combatTagged` is only read
  as a fallback when the combat module is not running. `combat.useCombatLog` is `auto` or `off` and is read on every call.

## Integrations

TrackerAPI, Feats, Atlas and Coinage are optional. Dominion compiles against their jars with `compileOnly`, lists them under
`OptionalDependencies` in the manifest and never ships them. The engine resolves the classes of a listed optional
dependency through the plugin bridge loader, so the bridges can name the sibling types directly.

- Jars. `-PtrackerApiJar=<path>`, `-PfeatsJar=<path>`, `-PatlasJar=<path>` and `-PcoinageJar=<path>` name them.
  Without a property the build takes `../trackerapi/build/libs/TrackerAPI-1.1.0.jar`, `../feats/build/libs/Feats-1.6.0.jar`,
  `../atlas/build/libs/Atlas-1.7.0.jar` and `../coinage/build/libs/Coinage-api-1.2.0.jar` (the api packages only), then the
  same paths under `~/Projects`. The tests have the Coinage api, Atlas and TrackerAPI jars on their classpath. The
  Coinage link runs over a stand in Coinage API (`economy/FakeCoinage`), the Atlas bridge over a stand in `AtlasApi`
  with the jar's real static hooks, and the gather test builds TrackerAPI's event records.
- Package `integration`. `IntegrationModule` runs last in `Wiring.MODULES`. For each sibling it reads the setting
  (`integrations.tracker`, `integrations.feats`, `integrations.atlas`, `integrations.coinage` with `auto`, `always` or `never`), looks the
  plugin up in `PluginManager` (`Presence`), decides with `Choice` and only then loads the bridge class by name and
  calls its static `install(Host)`. `Binding` keeps the link and polls the plugin object every 5 seconds. A sibling that was
  reloaded or installed late has classes in a new loader the bridge cannot follow, so once it is running Dominion
  logs one line and reloads itself through `PluginManager.reload` on a thread of its own 3 seconds later, which
  links to the new instance. TrackerAPI 1.0.3 and newer reload Dominion themselves, so that case stays quiet.
  Coinage 1.1.0 reloads the plugins that list it too, so `SelfReload` takes the lock TrackerAPI and Coinage hold for
  their reload pass (a `ReentrantLock` under the system property `ryvrook.dependents.lock`, waited on for up to 30
  seconds) and only reloads when the plugin manager still holds the Dominion instance that asked. The relink thread
  gets every class it needs before it sleeps, because after a reload the old plugin loader hands out the new
  instance's classes and the JVM refuses them. A headless run on 2026-10-15 reloaded Feats, Coinage, Atlas, TrackerAPI
  and Dominion in turn and logged "Enabled plugin Ryvrook:Dominion" once for each. A
  sibling that was unloaded logs one warning. `Integrations` is registered as a service under its own
  class and feeds `/fa integrations`.
- Bridges. `integration.tracker.TrackerBridge`, `integration.feats.FeatsBridge`, `integration.atlas.AtlasBridge` and
  `integration.coinage.CoinageLink` are the only classes that name sibling types. Every call into a sibling is guarded, and stop sets a stopped flag
  before removing hooks. TrackerAPI and Coinage have `removeHooks`. Atlas got it late in 1.6.0, so the bridge looks it up by reflection.
  Feats publishes `FeatsApi` in its own setup with no enable hook, so the Feats bridge links on install or on a later
  poll. Feats 1.5.1 answers false from `addStat` on an API object left from before a Feats reload, so on false the
  bridge reads `FeatsApi.get()` again and, when that is a new object, relinks and tries the add once more.
- Counters. `StatFanout` is one bus listener. It maps each `DominionEvent` with `StatMapper` (pure, facts behind the
  `StatMapper.Facts` interface) and hands the credits to every linked `StatSink`, which is `Counters.add` for
  TrackerAPI and `FeatsApi.addStat` for Feats. TrackerAPI accepts keys under another plugin's own prefix, so every key
  is `dominion:<name>`, listed in `StatKeys`, and TrackerAPI is asked for a leaderboard of every key but the two that
  stop at one (`StatKeys.BOARDS`). Claims and unclaims read the actor and the
  `counts` flag that `ClaimChanged` carries (false for admin moves, decay and any chunk the faction released within
  the seven day mission window, so a chunk claimed, released and claimed again counts once each way), and kills, deposits and invites from the `PlayerDied`,
  `BankDeposited` and `MemberInvited` events. An invite counts once per invited player and faction. A deposit counts
  the whole coins of the player's net deposits that day, deposits minus the `BankWithdrawn` withdrawals and never
  below zero, less what was already credited, so a deposit and withdraw loop counts once. `ContextFacts` keeps both
  memories in the faction stats under `counter.`, which `/f stats` leaves out. Siege and mission credit goes to every
  member, online or not, TrackerAPI and Feats queue it for offline players.
- The 0.7.1 keys. `joined` reads `MemberJoined`, `kicked` reads `MemberLeft` with reason `kick` or `ban`,
  `wars_declared` credits the members of the first faction of a `RelationChanged` to `ENEMY` (the relation service
  publishes the faction that declared first), `alliances` credits both factions of an `ALLY` change, `upgrades` and
  `upgrade_level_max` read `UpgradeBought` for every member, `market_trades` credits the buyer and the seller of a
  `MarketTrade`, `foreign_entries` reads `PlayerEnteredTerritory` into land of another, non system faction,
  `sieges_defended` reads `SiegePhase` `DEFENDED` for the defender, `breaches` reads `BreachChanged` with
  `breached` true for the attacker, `hill_wins` reads `GameEvent` kind `koth` phase `captured`, `outposts_held` reads
  kind `outpost` phase `captured`, both for the faction the event names, and `power_max` reads `PowerChanged` at or
  above `PowerService.maxPowerOf`. `tutorial_done` has no event, `TutorialCredit.finished` sets the flag and sends
  the credit through `StatFanout.credit`, which is why the fanout is registered as a service. `rank_changes` is listed
  and never sent, because `AuditLogged` carries the actor and names the member in text only. Feats and TrackerAPI
  only add, so `upgrade_level_max` is sent as the difference from the highest level the player was credited, and
  that memory, the once per faction foreign entry memory (capped at 128 factions) and the power maximum flag live
  on `PlayerData` under `Counters`, since they follow the player across factions. The siege service publishes one
  `BreachChanged` per breached chunk, so `ContextFacts` counts events of the same attacker and defender within ten
  seconds as one breach. `FeatsBridge.keeps` holds the new keys back from a Feats older than 1.7.2, which would log
  a warning a minute for a key it does not keep.
- Gather missions from TrackerAPI. While TrackerAPI is bound and tracks `block_broken`, `block_placed`,
  `item_picked_up` and `item_dropped`, the bridge subscribes to those four kinds (ASYNC) and hands them to
  `progress.GatherFeed`, and Dominion's own four `GatherSystems` return at once. The systems stay registered, so a
  TrackerAPI stop, a kind turned off in its config or a listener it suspended for running over budget hands the
  counting back within the 5 second poll. Both feeds go through the same `GatherFeed` methods and Dominion's own
  `FarmGuard`, not TrackerAPI's `playerPlaced` and `fresh` flags, because Dominion's guard is wider (a block any
  player placed, a crop a faction mate dropped, both memories configurable), so the counts are the same either way.
  `GatherEquivalenceTest` runs one session through both paths and compares the credits. Drops on death are skipped,
  as the engine drop event Dominion hooks never sees them. While the switch is on Dominion's own systems, events that
  still arrive for the other kinds are ignored, or one break would count twice. The listener runs on
  TrackerAPI-dispatch and only reads memory, the farm guard, the claims and the mission stats under the faction lock.
  A finished mission pays the bank and sends chat from that thread, as it does from the scheduler. TrackerAPI drops
  events when its async queue is full and logs a warning, and Dominion does not count those.
- Hooks that stay Dominion's own. `combat/DeathTrackingSystem` applies the power loss, pays kill rewards from bank to
  bank, informs sieges and bounties and needs the victim's chunk and the killer's ref in the tick of the death. A
  TrackerAPI listener can be suspended or turned off, which would silently stop the power loss, and TrackerAPI also
  credits kills through an effect, so the counts would change. The protection guards cancel the engine event and
  must run on it. `PvpFilterSystem` and `WarmupDamageSystem` cancel or react to damage in the same tick,
  `HomeRespawnSystem` moves the respawn, and chat, nameplates and the map react to engine events TrackerAPI does not
  publish.
- Atlas. The snapshot already carries claims, homes, warps, siege pins with the phase and end time, hills, outposts
  and envoys. Only while `export.enabled` is off does the bridge push a `Ryvrook:Dominion:sieges` layer of circles
  whose popups name the phase and the end time, at the tier `integrations.atlasSiegeTier` names (set it to Atlas's
  `visibility.territories`, the API does not expose it), redrawn on `SiegePhase`, `BreachChanged`,
  `FactionDisbanded` and when the export switch flips. Stop calls `AtlasApi.removeHooks` by reflection, and on an
  older Atlas build without it the hooks stay and do nothing. `PlayerToggles.onWebMap` tells it about `/f map hide`
  from the command and the panel, and it passes hidden players on to `AtlasApi.setHidden` at bind and on join,
  since Atlas does not save API hides.
- Chronicle Stats has no link. It reads `tag`, `name`, `color`, `nation`, `claims`, `power`, `maxPower`, `level`,
  `kills`, `deaths`, `createdAt` and `memberIds` of each faction from the snapshot, which `SnapshotContractTest` checks.

### Coinage

Coinage is linked like the others, `Ryvrook:Coinage` under `OptionalDependencies`, `integrations.coinage` and one
bridge, `integration.coinage.CoinageLink`, loaded by name after the presence check. It needs Coinage API 3 for the
service facades and uses the net worth parts of API 4. The version is read from `CoinageApi.API_VERSION` by
reflection at bind, since the compiled constant would be the one built against. Facts checked with javap on
`Coinage-api-1.0.0.jar` on 2026-10-15, API version 4.

- `CoinageApi.onEnable(Consumer)` runs at once when Coinage is up and on every start, `onDisable(Runnable)` on every
  stop, `removeHooks(enable, disable)`. `currencies().resolve(id)` gives the primary currency for a null id,
  `Currency.decimals()` is 0 to 4. `ledger().deposit/withdraw/transfer/set(Request)` return a `Result` with `ok`,
  `failure` (`Failure`), `message`, `fromAfter` and `toAfter`. `Request.deposit/withdraw/transfer/set(...)` take minor
  units, `.plugin("Dominion").reason(text).by(Initiator.plugin("Dominion"))`. A player initiator moving money out of
  a shared account needs its WITHDRAW permission, a plugin initiator does not.
- `accounts().createShared(handle, name, owner, plugin)` returns the existing account for a known handle, handles
  match `[a-z0-9_:.-]{1,64}`. `delete(id, by, plugin)` refuses an account with money unless `by` is the server.
  `setOwner` empties the new owner's member entry. `setPermissions(id, member, set, by)` with an empty set removes
  the member. `get(id)`, `byHandle`, `player(uuid)` (creates the account with the starting balance), `rename`.
- `subscribe(owner, EconomyListener)`. `beforeTransaction(TransactionPre)` runs outside the ledger lock before every
  move and may `cancel(reason)`, Coinage warns about listeners slower than 5 ms. `afterTransaction(Transaction)` runs
  after the move on the same thread. `netWorth().add/remove(NetWorthPart)`, parts are read on the board timer and must
  answer from memory. `service(MarketService.class)` is empty while the market module is off,
  `MarketService.openPage(UUID)` opens the market page. `ShopService.createShop(owner, ...)` takes a player owner and
  charges them, so a faction account cannot own a Coinage shop yet.

How Dominion uses it.

- Seam. The economy package never names a Coinage type. `economy.CoinagePort` is the interface it needs, in minor
  units with Dominion's own copy of the failure cases. `CoinageLink` implements it and attaches itself to
  `economy.CoinageSeam`, a service registered under its own class, while Coinage is up. The seam forwards the
  listener calls and the net worth part to the economy service.
- Wallets. `economy.CoinageBridge` is the `coinage` `BalanceProvider`. `EconomyServiceImpl.select` picks the provider,
  `auto` takes Coinage when linked, else VaultUnlocked when found, else internal. `CoinageAmounts` converts both ways,
  refusing amounts with more decimals than the currency has and rounding minor units down to cents. `LedgerMigration`
  runs into Coinage the same way it runs into VaultUnlocked, with its own summary file `economy/coinage-migrated.json`.
  On a currency with 0 or 1 decimals it moves the part of a balance the currency can hold and leaves the fraction in
  the internal ledger, so a balance of 12.34 moves 12 coins instead of failing every day.
  Both skip an untouched balance. `PlayerData` persists the flag as `BalanceTouched`, false for a record made by
  this version that never moved money, which is skipped whatever `eco.startingBalance` says now, and absent in older
  files, which are skipped only while they still equal `eco.startingBalance`. `DominionContext.newPlayer` grants the
  starting balance only while the provider is not coinage or vaultunlocked.
- Banks. `economy.CoinageBanks` keeps each faction bank in the shared account `dominion:<faction id>` while the
  provider is coinage and `eco.coinageBanks` is on. `Faction.coinageAccount` holds the handle once the old balance
  moved in, persisted as `CoinageAccount` with a null default. An account that saw any transaction (`Account.seq` above
  zero) while the faction has no handle was seeded before a crash and is not seeded again, even when it was spent
  back to zero. Moving a bank back takes deposits that land before the delete, and returns the money to the account
  when the delete still fails, so no balance is left behind for a later seed to miss. `Faction.bank` mirrors the account after every
  move, from the listener and on the minute tick, so every reader of `bank()` stays right. `EconomyServiceImpl.route`
  sends each bank move to `Books`, to `CoinageBanks`, or refuses it while a bank that lives in Coinage cannot be
  reached. The minute tick also sets the owner to the leader, gives members the rights their rank and hold allow,
  and moves banks back when Coinage banks are turned off. The listener vetoes player withdrawals from a faction
  account that `/f withdraw` would refuse and writes moves made outside Dominion to the faction log. The veto books
  the amount against the daily allowance under the ledger lock, so two withdrawals at once cannot both fit, and
  remembers the booking by thread. Coinage runs the veto and the after listener on the same thread, so the after
  listener settles the booking. A booking whose move failed is given back on the next veto of that thread, or once
  it is 10 seconds old on the next veto from any thread or the minute tick. Shutdown gives every booking back before
  the factions are saved and books nothing after, so a move in flight then is counted once by the after listener.
  Dues and upkeep round up to the smallest unit of the currency, the same as the whole unit VaultUnlocked path, so
  an upkeep of 0.4 on a whole coin currency costs one coin instead of nothing. Interest rounds down.
  When Coinage answers but no longer knows a faction's handle, an admin deleted the account in Coinage. Coinage only
  deletes an account with money when the server asks, so Dominion sets the bank to zero, logs one warning and opens a
  new empty account under the same handle on the next move or minute tick, instead of refusing every move.
- Disband. `bankDrain` transfers the account to the faction's leader, not the account owner, which follows a new
  leader only on the minute tick, or to `Accounts.SERVER` with `eco.coinageDisbandTo` on `server`, deletes it and
  returns zero, so `FactionSupport` pays nothing twice. Only when Coinage refuses the transfer to the leader is the
  money withdrawn and returned for the usual payout.
- Net worth. The part `faction` answers a member's bank divided by the member count, from a member index rebuilt on
  the minute tick, in the Coinage currency only.
- Market. `market.MarketHandoff` is a service the link attaches a target to. `ui.MarketTab` opens Coinage's market on
  a tab click and shows a button and Dominion's mailbox instead of its listing tabs while `market.useCoinage` is on.
  `shop.useCoinage` is defined but off and unused until Coinage shops can have a shared account owner.

## The API for other mods

`api.DominionApi` is the contract SocialBridge, RestAPI and any other mod compile against. Methods, events and record
components are only ever added, and `API_VERSION` goes up with each addition (2 since 0.7.0). A method added after
0.6.2 has a default body so a stand in written against an older jar still compiles. Values handed out are Dominion's
own model types or plain records such as `SiegeInfo` and `DominionEvent.ChatLine`, never an engine object.

- Lifecycle. `export.ExportModule` sets the instance in `ApiHolder` during setup, so `get()` answers while the other
  modules start. `DominionPlugin.start` runs the `onEnable` hooks once every module started, and `shutdown` runs the
  `onDisable` hooks before any module stops. `onEnable` runs at once when Dominion is already up.
- Dependents. `Dependents` is the class TrackerAPI and Coinage use, renamed. At start it lists the enabled plugins
  whose manifest names `Ryvrook:Dominion`. On a fresh boot there are none, since the dependency orders the load.
  After a reload it reloads them two seconds later on the `Dominion-dependents` thread, under the `ReentrantLock` in
  the system property `ryvrook.dependents.lock`, in dependency order, and leaves alone any plugin whose instance
  changed since Dominion started.
- Chat. `ChatServiceImpl` publishes a `ChatLine` after it delivered a faction, ally, officer or nation line.
  `sendFactionChat` goes through `ChatService.sendExternal`, which publishes with a null sender so a relay can skip
  its own lines. The export module ignores `ChatLine`, private channels never reach the snapshot.
- Staff actions call the same service methods as `/fa force disband`, `/fa force leader`, `/fa power set` and
  `/fa unclaim` with a null admin id and the caller's name, and log one INFO line each. `setPower` checks
  `Repository.knows` first, because `ctx.player` would create a record for any id.
- A stop path never loads a sibling class for the first time. The plugin manager holds its write lock during
  shutdown and the bridge class loader needs the read lock, so a first load there hangs for 5 seconds. Every class
  shutdown touches is loaded during start, and a bridge that never bound is never touched on stop.

## Hot paths

- Claims overlay. `OverlayPainter.paint` works out a fill and an outline colour per palette entry and only moves
  indexes per pixel. It took about 7 us for a 32 by 32 chunk image with 40 colours when it blended every pixel and
  takes 2.9 us now (Apple M series, JDK 25). `DOMINION_BENCH=1 ./gradlew test --tests '*PaintBench*' --rerun -i`
  prints the numbers, and `PaintBench` also checks the result is the image the per pixel version drew, for palettes
  with repeated and unused entries too, and that an index past the palette still throws so the chunk is left as the
  engine drew it. `MapOverlay` keeps what
  it painted in one primitive map per world, locked on itself, with one `Painted` per faction and colour.
- Sidebar. `DominionHud.refresh` fills the values into a map and sends only those that changed since the last push,
  nothing at all when none did, and everything again every 30 seconds in case the client dropped an update.
- Snapshot. `export/snapshot.json` is rebuilt every two seconds only while `export.playerPositions` is on and players
  are online. Otherwise only bus events and a change of the online roster (who, which world, hidden) rebuild it. The
  first snapshot is written on the IO thread right after the export module starts.

## Headless boot test

A short headless boot catches wiring and startup errors without a game client.

1. Build the jar with `./gradlew build`.
2. Make an empty scratch directory outside the repository with a `mods` folder inside it and copy the
   jar from `build/libs` into `mods`.
3. Add a `permissions.json` containing `{"users":{},"groups":{}}` to the scratch directory so the headless
   server stays up.
4. From the scratch directory run the server jar of a Hytale install with
   `java -jar <path to HytaleServer.jar> --assets <path to Assets.zip> --auth-mode offline --disable-sentry --bind 127.0.0.1:<port>`. Do not pass `--bare`, since server 0.7.1 stops with "Listeners is empty after starting ServerManager" without a bound listener.
   Assets.zip sits one level above the Server folder of the install.
5. Expect "Dominion started with 23 modules" and "Enabled plugin Ryvrook:Dominion", and no stack trace
   from `dev.ryvrook`. Stop the server when done.

With Atlas in the same mods folder, `curl http://127.0.0.1:8123/api/health` checks the web map.
Without `` the server generates a world, and piping `chunk load X Z` commands into its console
loads chunks so Atlas tiles render.

Vanilla markup extracted from Assets.zip is for reference only. Keep it outside the repository and
never commit it.

## Before opening a pull request

1. `./gradlew build` passes on 0.7.0 and on 0.6.8 with the module present.
2. No TODOs are left in scope. Features that wait on a later change are stubbed with a clear `Result.fail("Not available yet")`.
3. The pull request lists every public class added, every interface change, and anything that could not be verified without a client.
