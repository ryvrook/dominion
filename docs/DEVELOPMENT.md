# Dominion development guide

Dominion is a factions server plugin for Hytale (server 0.7.0, also compiles on 0.6.8).
This guide describes the conventions the code follows and the server API signatures that were verified with javap.
Please read it before opening a pull request.

## Conventions

- The plugin targets Java 25 and has no dependencies beyond the Hytale server jar and the JDK.
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
- Messages use the `dev.ryvrook.dominion.text.Msg` helpers and the colours defined there.

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
5. Expect "Dominion started with 22 modules" and "Enabled plugin Ryvrook:Dominion", and no stack trace
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
