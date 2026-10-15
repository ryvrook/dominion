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
import dev.ryvrook.dominion.text.Messages;
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
        return new ServerCmd(name, description).permission(ADMIN);
    }

    private static ServerCmd adminVariant(String description) {
        return new ServerCmd(description).permission(ADMIN);
    }

    private static PlayerCmd adminHere(String name, String description) {
        return new PlayerCmd(name, description).permission(ADMIN);
    }

    private static PlayerCmd adminHereVariant(String description) {
        return new PlayerCmd(description).permission(ADMIN);
    }

    ServerCmd build() {
        ServerCmd root = new ServerCmd("fe", Messages.get(EventsText.CMD_FE));
        root.handler(this::list);
        root.sub(new ServerCmd("list", Messages.get(EventsText.CMD_LIST))).handler(this::list);
        root.sub(new PlayerCmd("panel", Messages.get(EventsText.CMD_PANEL))).handler(this::panel);
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
        ServerCmd koth = root.sub(new ServerCmd("koth", Messages.get(EventsText.CMD_KOTH)));
        koth.handler(c -> lines(c, Messages.get(EventsText.NO_HILLS), events.koths()));

        PlayerCmd create = koth.sub(adminHere("create", Messages.get(EventsText.CMD_KOTH_CREATE)));
        RequiredArg<String> createName = create.withRequiredArg("name", Messages.get(EventsText.ARG_HILL_NAME), ArgTypes.STRING);
        create.handler(c -> createHill(c, c.arg(createName), 0));
        PlayerCmd createRadius = adminHereVariant(Messages.get(EventsText.CMD_KOTH_CREATE_RADIUS));
        RequiredArg<String> crName = createRadius.withRequiredArg("name", Messages.get(EventsText.ARG_HILL_NAME), ArgTypes.STRING);
        RequiredArg<Integer> crRadius = createRadius.withRequiredArg("radius", Messages.get(EventsText.ARG_RADIUS), ArgTypes.INTEGER);
        createRadius.handler(c -> createHill(c, c.arg(crName), c.arg(crRadius)));
        create.variant(createRadius);

        named(koth, "delete", Messages.get(EventsText.CMD_KOTH_DELETE), (c, n) -> c.reply(events.kothDelete(c.uuid(), n)));
        named(koth, "start", Messages.get(EventsText.CMD_KOTH_START), (c, n) -> c.reply(events.kothStart(c.uuid(), n)));
        named(koth, "stop", Messages.get(EventsText.CMD_KOTH_STOP), (c, n) -> c.reply(events.kothStop(c.uuid(), n)));

        ServerCmd schedule = koth.sub(admin("schedule", Messages.get(EventsText.CMD_KOTH_SCHEDULE)));
        RequiredArg<String> sName = schedule.withRequiredArg("name", Messages.get(EventsText.ARG_HILL_NAME), ArgTypes.STRING);
        RequiredArg<String> sMode = schedule.withRequiredArg("mode", Messages.get(EventsText.ARG_MODE), ArgTypes.STRING);
        RequiredArg<String> sWhen = schedule.withRequiredArg("when", Messages.get(EventsText.ARG_WHEN), ArgTypes.STRING);
        schedule.handler(c -> c.reply(events.kothSchedule(c.uuid(), c.arg(sName), c.arg(sMode) + " " + c.arg(sWhen))));
        ServerCmd scheduleOff = adminVariant(Messages.get(EventsText.CMD_KOTH_SCHEDULE_OFF));
        RequiredArg<String> soName = scheduleOff.withRequiredArg("name", Messages.get(EventsText.ARG_HILL_NAME), ArgTypes.STRING);
        RequiredArg<String> soMode = scheduleOff.withRequiredArg("mode", Messages.get(EventsText.ARG_OFF), ArgTypes.STRING);
        scheduleOff.handler(c -> c.reply(events.kothSchedule(c.uuid(), c.arg(soName), c.arg(soMode))));
        schedule.variant(scheduleOff);
    }

    private void createHill(Call c, String name, int radius) {
        Vector3d pos = Players.position(c.player());
        String world = Players.worldName(c.player());
        if (pos == null || world == null) {
            c.bad(Messages.get(EventsText.POSITION_UNKNOWN));
            return;
        }
        c.reply(events.kothCreate(c.uuid(), name, world, pos.x, pos.y, pos.z, radius));
    }

    // ---- outposts ----------------------------------------------------------------

    private void outpost(ServerCmd root) {
        ServerCmd outpost = root.sub(new ServerCmd("outpost", Messages.get(EventsText.CMD_OUTPOST)));
        outpost.handler(c -> lines(c, Messages.get(EventsText.NO_OUTPOSTS), events.outposts()));

        PlayerCmd create = outpost.sub(adminHere("create", Messages.get(EventsText.CMD_OUTPOST_CREATE)));
        RequiredArg<String> name = create.withRequiredArg("name", Messages.get(EventsText.ARG_OUTPOST_NAME), ArgTypes.STRING);
        RequiredArg<String> boost = create.withRequiredArg("boost", Messages.get(EventsText.ARG_BOOST), ArgTypes.STRING);
        RequiredArg<Double> mult = create.withRequiredArg("multiplier", Messages.get(EventsText.ARG_MULTIPLIER), ArgTypes.DOUBLE);
        create.handler(c -> createOutpost(c, c.arg(name), c.arg(boost), c.arg(mult), 0));
        PlayerCmd createRadius = adminHereVariant(Messages.get(EventsText.CMD_OUTPOST_CREATE_RADIUS));
        RequiredArg<String> rName = createRadius.withRequiredArg("name", Messages.get(EventsText.ARG_OUTPOST_NAME), ArgTypes.STRING);
        RequiredArg<String> rBoost = createRadius.withRequiredArg("boost", Messages.get(EventsText.ARG_BOOST), ArgTypes.STRING);
        RequiredArg<Double> rMult = createRadius.withRequiredArg("multiplier", Messages.get(EventsText.ARG_MULTIPLIER), ArgTypes.DOUBLE);
        RequiredArg<Integer> rRadius = createRadius.withRequiredArg("radius", Messages.get(EventsText.ARG_RADIUS), ArgTypes.INTEGER);
        createRadius.handler(c -> createOutpost(c, c.arg(rName), c.arg(rBoost), c.arg(rMult), c.arg(rRadius)));
        create.variant(createRadius);

        named(outpost, "delete", Messages.get(EventsText.CMD_OUTPOST_DELETE), (c, n) -> c.reply(events.outpostDelete(c.uuid(), n)));
        named(outpost, "reset", Messages.get(EventsText.CMD_OUTPOST_RESET), (c, n) -> c.reply(events.outpostReset(c.uuid(), n)));
    }

    private void createOutpost(Call c, String name, String boost, double multiplier, int radius) {
        Vector3d pos = Players.position(c.player());
        String world = Players.worldName(c.player());
        if (pos == null || world == null) {
            c.bad(Messages.get(EventsText.POSITION_UNKNOWN));
            return;
        }
        c.reply(events.outpostCreate(c.uuid(), name, world, pos.x, pos.y, pos.z, radius, boost, multiplier));
    }

    // ---- envoys ------------------------------------------------------------------

    private void envoy(ServerCmd root) {
        ServerCmd envoy = root.sub(new ServerCmd("envoy", Messages.get(EventsText.CMD_ENVOY)));
        envoy.handler(c -> lines(c, Messages.get(EventsText.NO_ENVOYS_OR_TIMERS), events.envoys()));

        ServerCmd spawn = envoy.sub(admin("spawn", Messages.get(EventsText.CMD_ENVOY_SPAWN)));
        RequiredArg<String> zone = spawn.withRequiredArg("zoneTag", Messages.get(EventsText.ARG_ZONE), ArgTypes.STRING);
        spawn.handler(c -> c.reply(events.envoySpawn(c.uuid(), c.arg(zone))));

        ServerCmd schedule = envoy.sub(admin("schedule", Messages.get(EventsText.CMD_ENVOY_SCHEDULE)));
        RequiredArg<String> sZone = schedule.withRequiredArg("zoneTag", Messages.get(EventsText.ARG_ZONE), ArgTypes.STRING);
        RequiredArg<Integer> every = schedule.withRequiredArg("minutes", Messages.get(EventsText.ARG_MINUTES), ArgTypes.INTEGER);
        schedule.handler(c -> c.reply(events.envoySchedule(c.uuid(), c.arg(sZone), c.arg(every))));

        envoy.sub(new PlayerCmd("claim", Messages.get(EventsText.CMD_ENVOY_CLAIM))).handler(c -> {
            if (!events.enabled()) {
                c.bad(Messages.get(EventsText.TURNED_OFF));
                return;
            }
            c.reply(events.envoyClaim(c.uuid()));
        });
    }

    // ---- season ------------------------------------------------------------------

    private void season(ServerCmd root) {
        ServerCmd season = root.sub(new ServerCmd("season", Messages.get(EventsText.CMD_SEASON)));
        season.handler(c -> c.info(events.seasonStatus()));
        season.sub(new ServerCmd("status", Messages.get(EventsText.CMD_SEASON))).handler(c -> c.info(events.seasonStatus()));

        ServerCmd start = season.sub(admin("start", Messages.get(EventsText.CMD_SEASON_START)));
        start.handler(c -> c.reply(events.seasonStart(c.uuid(), 0, 0)));
        ServerCmd startGrace = adminVariant(Messages.get(EventsText.CMD_SEASON_START_GRACE));
        RequiredArg<Integer> g1 = startGrace.withRequiredArg("graceDays", Messages.get(EventsText.ARG_GRACE_DAYS), ArgTypes.INTEGER);
        startGrace.handler(c -> c.reply(events.seasonStart(c.uuid(), c.arg(g1), 0)));
        start.variant(startGrace);
        ServerCmd startBoth = adminVariant(Messages.get(EventsText.CMD_SEASON_START_BOTH));
        RequiredArg<Integer> g2 = startBoth.withRequiredArg("graceDays", Messages.get(EventsText.ARG_GRACE_DAYS), ArgTypes.INTEGER);
        RequiredArg<Integer> r2 = startBoth.withRequiredArg("raidDays", Messages.get(EventsText.ARG_RAID_DAYS), ArgTypes.INTEGER);
        startBoth.handler(c -> c.reply(events.seasonStart(c.uuid(), c.arg(g2), c.arg(r2))));
        start.variant(startBoth);

        season.sub(admin("end", Messages.get(EventsText.CMD_SEASON_END)))
                .handler(c -> c.reply(events.seasonEnd(c.uuid())));
    }

    // ---- bounties ----------------------------------------------------------------

    private void bounty(ServerCmd root) {
        ServerCmd bounty = root.sub(new ServerCmd("bounty", Messages.get(EventsText.CMD_BOUNTY)));
        bounty.handler(this::showBounties);
        PlayerCmd place = bounty.sub(new PlayerCmd("place", Messages.get(EventsText.CMD_BOUNTY_PLACE)));
        RequiredArg<String> who = place.withRequiredArg("player", Messages.get(EventsText.ARG_PLAYER), ArgTypes.STRING);
        RequiredArg<Double> amount = place.withRequiredArg("amount", Messages.get(EventsText.ARG_AMOUNT), ArgTypes.DOUBLE);
        place.handler(c -> {
            Targets.Target t = targets.resolveAnywhere(c.arg(who));
            if (t == null) {
                c.bad(Messages.get(EventsText.NO_PLAYER_NAMED, "player", c.arg(who)));
                return;
            }
            c.reply(events.bountyPlace(c.uuid(), t.uuid(), c.arg(amount)));
        });
        root.sub(new ServerCmd("bounties", Messages.get(EventsText.CMD_BOUNTY))).handler(this::showBounties);
    }

    private void showBounties(Call c) {
        lines(c, Messages.get(EventsText.NO_BOUNTIES), events.bounties());
    }

    // ---- payouts -----------------------------------------------------------------

    private void payouts(ServerCmd root) {
        ServerCmd payouts = root.sub(admin("payouts", Messages.get(EventsText.CMD_PAYOUTS)));
        payouts.handler(c -> lines(c, Messages.get(EventsText.NOTHING_TO_PAY), events.payoutsPreview()));
        payouts.sub(admin("preview", Messages.get(EventsText.CMD_PAYOUTS)))
                .handler(c -> lines(c, Messages.get(EventsText.NOTHING_TO_PAY), events.payoutsPreview()));
        payouts.sub(new ServerCmd("run", Messages.get(EventsText.CMD_PAYOUTS_RUN)).permission(PAYOUTS))
                .handler(c -> c.reply(events.payoutsRun(c.uuid())));
    }

    // ---- list and panel ----------------------------------------------------------

    private void list(Call c) {
        if (!events.enabled()) {
            c.info(Messages.get(EventsText.TURNED_OFF));
        }
        c.send(Msg.colored(Messages.get(EventsText.SECTION_HILLS), Msg.GOLD).bold(true));
        sub(c, Messages.get(EventsText.NO_HILLS), events.koths());
        c.send(Msg.colored(Messages.get(EventsText.SECTION_OUTPOSTS), Msg.GOLD).bold(true));
        sub(c, Messages.get(EventsText.NO_OUTPOSTS), events.outposts());
        c.send(Msg.colored(Messages.get(EventsText.SECTION_ENVOYS), Msg.GOLD).bold(true));
        sub(c, Messages.get(EventsText.NO_ENVOYS), events.envoys());
        c.send(Msg.colored(Messages.get(EventsText.SECTION_BOUNTIES), Msg.GOLD).bold(true));
        sub(c, Messages.get(EventsText.NO_BOUNTIES), events.bounties());
        c.send(Msg.colored(Messages.get(EventsText.SECTION_SEASON), Msg.GOLD).bold(true));
        c.send(Msg.colored(" " + events.seasonStatus(), Msg.TEXT));
    }

    private void panel(Call c) {
        PlayerRef player = c.player();
        World world = c.world() != null ? c.world() : Players.world(player);
        if (player == null || world == null) {
            c.bad(Messages.get(EventsText.PANEL_IN_GAME));
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
        RequiredArg<String> arg = cmd.withRequiredArg("name", Messages.get(EventsText.ARG_NAME), ArgTypes.STRING);
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
