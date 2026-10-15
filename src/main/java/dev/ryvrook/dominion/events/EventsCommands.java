package dev.ryvrook.dominion.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.command.Call;
import dev.ryvrook.dominion.command.PlayerCmd;
import dev.ryvrook.dominion.command.ServerCmd;
import dev.ryvrook.dominion.command.Targets;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.config.Perms;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import org.joml.Vector3d;

import java.util.List;
import java.util.logging.Level;

/**
 * Builds /fe. The engine checks permissions on each command and never the parent, so every
 * admin sub command and variant requires the admin permission on its own. Children attach
 * bottom up in seal before the root is registered.
 */
final class EventsCommands {
    /** The shipped nodes. The nodes in use come from Perms, set by permissions.admin and permissions.adminPayouts. */
    static final String ADMIN = "dominion.admin";
    /** Payouts run console commands, so they need their own node on top of dominion.admin. */
    static final String PAYOUTS = "dominion.admin.payouts";

    private final DominionContext ctx;
    private final EventsServiceImpl events;
    private final Targets targets;

    EventsCommands(DominionContext ctx, EventsServiceImpl events) {
        this.ctx = ctx;
        this.events = events;
        this.targets = new Targets(ctx);
    }

    private static ServerCmd admin(String name, String description) {
        return new ServerCmd(name, description).permission(Perms.admin());
    }

    private static ServerCmd adminVariant(String description) {
        return new ServerCmd(description).permission(Perms.admin());
    }

    private static PlayerCmd adminHere(String name, String description) {
        return new PlayerCmd(name, description).permission(Perms.admin());
    }

    private static PlayerCmd adminHereVariant(String description) {
        return new PlayerCmd(description).permission(Perms.admin());
    }

    ServerCmd build() {
        // Read once at registration, so a change applies at the next start or /plugin reload.
        String rootName = CommandNames.name(ctx.settings().text("commands.eventsRoot"), CommandNames.DEFAULT_EVENTS);
        ServerCmd root = new ServerCmd(rootName, "Faction events. KOTH hills, outposts, envoys, bounties and the season.");
        List<String> aliases = CommandNames.aliases(ctx.settings().text("commands.eventsAliases"), rootName);
        if (!aliases.isEmpty()) {
            root.addAliases(aliases.toArray(String[]::new));
        }
        CommandNames.registeredEvents(rootName);
        root.handler(this::list);
        root.sub(new ServerCmd("list", "Show every event with its status.")).handler(this::list);
        root.sub(new PlayerCmd("panel", "Open the events panel.")).handler(this::panel);
        koth(root);
        outpost(root);
        envoy(root);
        season(root);
        bounty(root);
        payouts(root);
        root.seal();
        return root;
    }

    // ---- koth --------------------------------------------------------------------

    private void koth(ServerCmd root) {
        ServerCmd koth = root.sub(new ServerCmd("koth", "List KOTH hills."));
        koth.handler(c -> lines(c, "No hills are set.", events.koths()));

        PlayerCmd create = koth.sub(adminHere("create", "Create a hill where you stand."));
        RequiredArg<String> createName = create.withRequiredArg("name", "Hill name", ArgTypes.STRING);
        create.handler(c -> createHill(c, c.arg(createName), 0));
        PlayerCmd createRadius = adminHereVariant("Create a hill with a radius in blocks.");
        RequiredArg<String> crName = createRadius.withRequiredArg("name", "Hill name", ArgTypes.STRING);
        RequiredArg<Integer> crRadius = createRadius.withRequiredArg("radius", "Radius in blocks", ArgTypes.INTEGER);
        createRadius.handler(c -> createHill(c, c.arg(crName), c.arg(crRadius)));
        create.variant(createRadius);

        named(koth, "delete", "Delete a hill.", (c, n) -> c.reply(events.kothDelete(c.uuid(), n)));
        named(koth, "start", "Open a hill now.", (c, n) -> c.reply(events.kothStart(c.uuid(), n)));
        named(koth, "stop", "Close a hill with no winner.", (c, n) -> c.reply(events.kothStop(c.uuid(), n)));

        ServerCmd schedule = koth.sub(admin("schedule", "Schedule a hill with every 30m or at 18:00,21:30."));
        RequiredArg<String> sName = schedule.withRequiredArg("name", "Hill name", ArgTypes.STRING);
        RequiredArg<String> sMode = schedule.withRequiredArg("mode", "every or at", ArgTypes.STRING);
        RequiredArg<String> sWhen = schedule.withRequiredArg("when", "Minutes such as 30m, or times such as 18:00,21:30", ArgTypes.STRING);
        schedule.handler(c -> c.reply(events.kothSchedule(c.uuid(), c.arg(sName), c.arg(sMode) + " " + c.arg(sWhen))));
        ServerCmd scheduleOff = adminVariant("Clear a hill schedule with off.");
        RequiredArg<String> soName = scheduleOff.withRequiredArg("name", "Hill name", ArgTypes.STRING);
        RequiredArg<String> soMode = scheduleOff.withRequiredArg("mode", "off", ArgTypes.STRING);
        scheduleOff.handler(c -> c.reply(events.kothSchedule(c.uuid(), c.arg(soName), c.arg(soMode))));
        schedule.variant(scheduleOff);
    }

    private void createHill(Call c, String name, int radius) {
        Vector3d pos = Players.position(c.player());
        String world = Players.worldName(c.player());
        if (pos == null || world == null) {
            c.bad("Your position is not known yet. Try again in a moment.");
            return;
        }
        c.reply(events.kothCreate(c.uuid(), name, world, pos.x, pos.y, pos.z, radius));
    }

    // ---- outposts ----------------------------------------------------------------

    private void outpost(ServerCmd root) {
        ServerCmd outpost = root.sub(new ServerCmd("outpost", "List outposts."));
        outpost.handler(c -> lines(c, "No outposts are set.", events.outposts()));

        PlayerCmd create = outpost.sub(adminHere("create", "Create an outpost where you stand. Boosts are xp, missions, tax or sell."));
        RequiredArg<String> name = create.withRequiredArg("name", "Outpost name", ArgTypes.STRING);
        RequiredArg<String> boost = create.withRequiredArg("boost", "xp, missions, tax or sell", ArgTypes.STRING);
        RequiredArg<Double> mult = create.withRequiredArg("multiplier", "Boost multiplier such as 1.25", ArgTypes.DOUBLE);
        create.handler(c -> createOutpost(c, c.arg(name), c.arg(boost), c.arg(mult), 0));
        PlayerCmd createRadius = adminHereVariant("Create an outpost with a radius in blocks.");
        RequiredArg<String> rName = createRadius.withRequiredArg("name", "Outpost name", ArgTypes.STRING);
        RequiredArg<String> rBoost = createRadius.withRequiredArg("boost", "xp, missions, tax or sell", ArgTypes.STRING);
        RequiredArg<Double> rMult = createRadius.withRequiredArg("multiplier", "Boost multiplier such as 1.25", ArgTypes.DOUBLE);
        RequiredArg<Integer> rRadius = createRadius.withRequiredArg("radius", "Radius in blocks", ArgTypes.INTEGER);
        createRadius.handler(c -> createOutpost(c, c.arg(rName), c.arg(rBoost), c.arg(rMult), c.arg(rRadius)));
        create.variant(createRadius);

        named(outpost, "delete", "Delete an outpost.", (c, n) -> c.reply(events.outpostDelete(c.uuid(), n)));
        named(outpost, "reset", "Clear the holder and control of an outpost.", (c, n) -> c.reply(events.outpostReset(c.uuid(), n)));
    }

    private void createOutpost(Call c, String name, String boost, double multiplier, int radius) {
        Vector3d pos = Players.position(c.player());
        String world = Players.worldName(c.player());
        if (pos == null || world == null) {
            c.bad("Your position is not known yet. Try again in a moment.");
            return;
        }
        c.reply(events.outpostCreate(c.uuid(), name, world, pos.x, pos.y, pos.z, radius, boost, multiplier));
    }

    // ---- envoys ------------------------------------------------------------------

    private void envoy(ServerCmd root) {
        ServerCmd envoy = root.sub(new ServerCmd("envoy", "List envoys and envoy timers."));
        envoy.handler(c -> lines(c, "No envoys are out and none are scheduled.", events.envoys()));

        ServerCmd spawn = envoy.sub(admin("spawn", "Drop an envoy in a war zone now."));
        RequiredArg<String> zone = spawn.withRequiredArg("zoneTag", "War zone tag", ArgTypes.STRING);
        spawn.handler(c -> c.reply(events.envoySpawn(c.uuid(), c.arg(zone))));

        ServerCmd schedule = envoy.sub(admin("schedule", "Drop envoys in a war zone every few minutes, or 0 to stop."));
        RequiredArg<String> sZone = schedule.withRequiredArg("zoneTag", "War zone tag", ArgTypes.STRING);
        RequiredArg<Integer> every = schedule.withRequiredArg("minutes", "Minutes between drops, 0 to stop", ArgTypes.INTEGER);
        schedule.handler(c -> c.reply(events.envoySchedule(c.uuid(), c.arg(sZone), c.arg(every))));

        envoy.sub(new PlayerCmd("claim", "Take the loot of an envoy next to you.")).handler(c -> {
            if (!events.enabled()) {
                c.bad("Events are turned off on this server.");
                return;
            }
            c.reply(events.envoyClaim(c.uuid()));
        });
    }

    // ---- season ------------------------------------------------------------------

    private void season(ServerCmd root) {
        ServerCmd season = root.sub(new ServerCmd("season", "Show the season status."));
        season.handler(c -> c.info(events.seasonStatus()));
        season.sub(new ServerCmd("status", "Show the season status.")).handler(c -> c.info(events.seasonStatus()));

        ServerCmd start = season.sub(admin("start", "Start a season with no grace and no end date."));
        start.handler(c -> c.reply(events.seasonStart(c.uuid(), 0, 0)));
        ServerCmd startGrace = adminVariant("Start a season with grace days.");
        RequiredArg<Integer> g1 = startGrace.withRequiredArg("graceDays", "Days of grace", ArgTypes.INTEGER);
        startGrace.handler(c -> c.reply(events.seasonStart(c.uuid(), c.arg(g1), 0)));
        start.variant(startGrace);
        ServerCmd startBoth = adminVariant("Start a season with grace days and raid days before the end of the world.");
        RequiredArg<Integer> g2 = startBoth.withRequiredArg("graceDays", "Days of grace", ArgTypes.INTEGER);
        RequiredArg<Integer> r2 = startBoth.withRequiredArg("raidDays", "Days of raiding after grace", ArgTypes.INTEGER);
        startBoth.handler(c -> c.reply(events.seasonStart(c.uuid(), c.arg(g2), c.arg(r2))));
        start.variant(startBoth);

        season.sub(admin("end", "End the season, start the end of the world and freeze the leaderboard."))
                .handler(c -> c.reply(events.seasonEnd(c.uuid())));
    }

    // ---- bounties ----------------------------------------------------------------

    private void bounty(ServerCmd root) {
        ServerCmd bounty = root.sub(new ServerCmd("bounty", "List bounties."));
        bounty.handler(this::showBounties);
        PlayerCmd place = bounty.sub(new PlayerCmd("place", "Put money on a player's head."));
        RequiredArg<String> who = place.withRequiredArg("player", "Player name", ArgTypes.STRING);
        RequiredArg<Double> amount = place.withRequiredArg("amount", "Amount to put up", ArgTypes.DOUBLE);
        place.handler(c -> {
            Targets.Target t = targets.resolveAnywhere(c.arg(who));
            if (t == null) {
                c.bad("No player named " + c.arg(who) + " was found.");
                return;
            }
            c.reply(events.bountyPlace(c.uuid(), t.uuid(), c.arg(amount)));
        });
        root.sub(new ServerCmd("bounties", "List bounties.")).handler(this::showBounties);
    }

    private void showBounties(Call c) {
        lines(c, "No bounties are out.", events.bounties());
    }

    // ---- payouts -----------------------------------------------------------------

    private void payouts(ServerCmd root) {
        ServerCmd payouts = root.sub(admin("payouts", "Preview the season payout commands."));
        payouts.handler(c -> lines(c, "Nothing to pay out.", events.payoutsPreview()));
        payouts.sub(admin("preview", "Preview the season payout commands."))
                .handler(c -> lines(c, "Nothing to pay out.", events.payoutsPreview()));
        payouts.sub(new ServerCmd("run", "Run the season payout commands from the console.").permission(Perms.payouts()))
                .handler(c -> c.reply(events.payoutsRun(c.uuid())));
    }

    // ---- list and panel ----------------------------------------------------------

    private void list(Call c) {
        if (!events.enabled()) {
            c.info("Events are turned off on this server.");
        }
        c.send(Msg.colored("Hills", Msg.GOLD).bold(true));
        sub(c, "No hills are set.", events.koths());
        c.send(Msg.colored("Outposts", Msg.GOLD).bold(true));
        sub(c, "No outposts are set.", events.outposts());
        c.send(Msg.colored("Envoys", Msg.GOLD).bold(true));
        sub(c, "No envoys are out.", events.envoys());
        c.send(Msg.colored("Bounties", Msg.GOLD).bold(true));
        sub(c, "No bounties are out.", events.bounties());
        c.send(Msg.colored("Season", Msg.GOLD).bold(true));
        c.send(Msg.colored(" " + events.seasonStatus(), Msg.TEXT));
    }

    private void panel(Call c) {
        PlayerRef player = c.player();
        World world = c.world() != null ? c.world() : Players.world(player);
        if (player == null || world == null) {
            c.bad("The panel can only be opened in game.");
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) {
                    p.getPageManager().openCustomPage(ref, store, new EventsPage(player, ctx, events));
                }
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion events panel failed to open");
            }
        });
    }

    // ---- helpers -----------------------------------------------------------------

    private interface NamedHandler {
        void run(Call c, String name);
    }

    private void named(ServerCmd parent, String name, String description, NamedHandler handler) {
        ServerCmd cmd = parent.sub(admin(name, description));
        RequiredArg<String> arg = cmd.withRequiredArg("name", "Name", ArgTypes.STRING);
        cmd.handler(c -> handler.run(c, c.arg(arg)));
    }

    private static void lines(Call c, String empty, List<String> lines) {
        if (lines.isEmpty()) {
            c.info(empty);
            return;
        }
        for (String l : lines) {
            c.send(Msg.colored(" " + l, Msg.TEXT));
        }
    }

    private static void sub(Call c, String empty, List<String> lines) {
        if (lines.isEmpty()) {
            c.send(Msg.muted(" " + empty));
            return;
        }
        for (String l : lines) {
            c.send(Msg.colored(" " + l, Msg.TEXT));
        }
    }
}
