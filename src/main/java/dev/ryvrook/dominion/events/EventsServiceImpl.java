package dev.ryvrook.dominion.events;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Perms;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * KOTH hills, outposts, envoys, bounties and the season clock. All state sits behind one lock.
 * Side effects such as payments, chat and bus events are collected while the lock is held and
 * run after it is released, so other services are never called with the lock taken.
 */
public final class EventsServiceImpl implements EventsService {
    static final long MINUTE = 60_000L;
    static final long DAY = 86_400_000L;
    private static final long MAX_KOTH_MILLIS = 60 * MINUTE;
    private static final long SAVE_MILLIS = 60_000L;
    private static final long NOTICE_MILLIS = 60_000L;
    private static final long KILL_DEDUPE_MILLIS = 5_000L;
    private static final int MAX_RADIUS = 64;
    private static final int SNAPSHOT_SIZE = 10;
    static final double CLAIM_DISTANCE = 3.0;
    static final List<String> BOOSTS = List.of("xp", "missions", "tax", "sell");
    /** Loot handed out when an envoy is claimed. A setting named events.envoyLoot overrides it when defined. */
    private static final String DEFAULT_LOOT = "Ingredient_Bar_Gold:8|Ingredient_Bar_Iron:16|Rock_Gem_Diamond:2|Rock_Gem_Emerald:2|Potion_Health_Greater:3";

    static final double MIN_OUTPOST_DECAY = 0.05;

    private final DominionContext ctx;
    private final java.util.concurrent.atomic.AtomicBoolean decayWarned = new java.util.concurrent.atomic.AtomicBoolean();
    private final Object lock = new Object();
    /** Serialises season start and end, so the tick and an admin cannot both end the same season. */
    private final Object seasonLock = new Object();
    private final SaveOrder eventsOrder = new SaveOrder();
    private final SaveOrder bountiesOrder = new SaveOrder();
    private final Map<String, Hill> hills = new LinkedHashMap<>();
    private final Map<String, Outpost> outposts = new LinkedHashMap<>();
    private final List<Envoy> envoys = new ArrayList<>();
    /** Zone tag in lower case to minutes between drops and the next drop time. */
    private final Map<String, long[]> envoyTimers = new LinkedHashMap<>();
    private final Map<UUID, Bounty> bounties = new LinkedHashMap<>();
    private final Map<UUID, Long> recentKills = new HashMap<>();
    private final Map<UUID, Long> pendingOutpostSeconds = new HashMap<>();
    private final List<Document> snapshot = new ArrayList<>();
    private long snapshotAt;
    private long payoutsRunAt;
    private boolean dirty;
    private boolean bountiesDirty;
    private long lastSave = Time.now();
    private long ticks;

    /** Bounty on one player, stacked from every placer. */
    static final class Bounty {
        final UUID target;
        String name;
        double amount;
        final Map<UUID, Double> placers = new LinkedHashMap<>();
        long placedAt;

        Bounty(UUID target, String name) {
            this.target = target;
            this.name = name;
        }
    }

    /** One online player with a faction and a known position. */
    private record Presence(UUID player, Faction faction, String world, Vector3d pos) {}

    public EventsServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    // ---- persistence ---------------------------------------------------------

    private Path eventsFile() {
        return ctx.plugin().getDataDirectory().resolve("events.json");
    }

    private Path bountiesFile() {
        return ctx.plugin().getDataDirectory().resolve("bounties.json");
    }

    void load() {
        Document doc = ctx.files().read(eventsFile());
        synchronized (lock) {
            if (doc != null) {
                for (Document d : docs(doc, "Hills")) {
                    try {
                        Hill h = Hill.fromDocument(d);
                        hills.put(key(h.name), h);
                    } catch (RuntimeException e) {
                        ctx.logger().at(Level.WARNING).withCause(e).log("Skipping an unreadable hill");
                    }
                }
                for (Document d : docs(doc, "Outposts")) {
                    try {
                        Outpost o = Outpost.fromDocument(d);
                        outposts.put(key(o.name), o);
                    } catch (RuntimeException e) {
                        ctx.logger().at(Level.WARNING).withCause(e).log("Skipping an unreadable outpost");
                    }
                }
                for (Document d : docs(doc, "Envoys")) {
                    try {
                        envoys.add(Envoy.fromDocument(d));
                    } catch (RuntimeException e) {
                        ctx.logger().at(Level.WARNING).withCause(e).log("Skipping an unreadable envoy");
                    }
                }
                for (Document d : docs(doc, "EnvoyTimers")) {
                    String zone = d.getString("Zone");
                    if (zone != null) {
                        envoyTimers.put(key(zone), new long[] {(long) Hill.num(d, "Every"), (long) Hill.num(d, "NextAt")});
                    }
                }
                snapshot.addAll(docs(doc, "Snapshot"));
                snapshotAt = (long) Hill.num(doc, "SnapshotAt");
                payoutsRunAt = (long) Hill.num(doc, "PayoutsRunAt");
            }
            Document b = ctx.files().read(bountiesFile());
            if (b != null) {
                for (Document d : docs(b, "Bounties")) {
                    try {
                        UUID target = UUID.fromString(d.getString("Target"));
                        Bounty bounty = new Bounty(target, d.getString("Name") == null ? "" : d.getString("Name"));
                        bounty.amount = Hill.num(d, "Amount");
                        bounty.placedAt = (long) Hill.num(d, "PlacedAt");
                        Object raw = d.get("Placers");
                        if (raw instanceof Document p) {
                            for (String k : p.keySet()) {
                                if (p.get(k) instanceof Number n) {
                                    bounty.placers.put(UUID.fromString(k), n.doubleValue());
                                }
                            }
                        }
                        if (bounty.amount > 0) {
                            bounties.put(target, bounty);
                        }
                    } catch (RuntimeException e) {
                        ctx.logger().at(Level.WARNING).withCause(e).log("Skipping an unreadable bounty");
                    }
                }
            }
        }
    }

    /** Saves both files. Returns false when events.json could not be written. */
    boolean save() {
        Document events;
        Document bountyDoc;
        long eventsGen;
        long bountiesGen;
        synchronized (lock) {
            flushOutpostSeconds();
            List<Document> h = new ArrayList<>();
            for (Hill hill : hills.values()) {
                h.add(hill.toDocument());
            }
            List<Document> o = new ArrayList<>();
            for (Outpost outpost : outposts.values()) {
                o.add(outpost.toDocument());
            }
            List<Document> e = new ArrayList<>();
            for (Envoy envoy : envoys) {
                e.add(envoy.toDocument());
            }
            List<Document> t = new ArrayList<>();
            for (Map.Entry<String, long[]> timer : envoyTimers.entrySet()) {
                t.add(new Document("Zone", timer.getKey()).append("Every", timer.getValue()[0])
                        .append("NextAt", timer.getValue()[1]));
            }
            events = new Document("Version", 1).append("Hills", h).append("Outposts", o).append("Envoys", e)
                    .append("EnvoyTimers", t).append("Snapshot", new ArrayList<>(snapshot))
                    .append("SnapshotAt", snapshotAt).append("PayoutsRunAt", payoutsRunAt);
            List<Document> b = new ArrayList<>();
            for (Bounty bounty : bounties.values()) {
                Document placers = new Document();
                for (Map.Entry<UUID, Double> p : bounty.placers.entrySet()) {
                    placers.append(p.getKey().toString(), p.getValue());
                }
                b.add(new Document("Target", bounty.target.toString()).append("Name", bounty.name)
                        .append("Amount", bounty.amount).append("PlacedAt", bounty.placedAt).append("Placers", placers));
            }
            bountyDoc = new Document("Version", 1).append("Bounties", b);
            eventsGen = eventsOrder.next();
            bountiesGen = bountiesOrder.next();
            dirty = false;
            bountiesDirty = false;
            lastSave = Time.now();
        }
        // Writes land in snapshot order, so an older tick snapshot never overwrites a newer one such as
        // the one carrying PayoutsRunAt. A failed write keeps the data pending for the next tick.
        Document e = events;
        Document b = bountyDoc;
        boolean saved = true;
        if (eventsOrder.write(eventsGen, () -> ctx.files().write(eventsFile(), e)) == SaveOrder.Outcome.FAILED) {
            saved = false;
            synchronized (lock) {
                dirty = true;
            }
            ctx.logger().at(Level.WARNING).log("Saving events.json failed, it will be retried");
        }
        if (bountiesOrder.write(bountiesGen, () -> ctx.files().write(bountiesFile(), b)) == SaveOrder.Outcome.FAILED) {
            synchronized (lock) {
                bountiesDirty = true;
            }
            ctx.logger().at(Level.WARNING).log("Saving bounties.json failed, it will be retried");
        }
        return saved;
    }

    private static List<Document> docs(Document doc, String key) {
        List<Document> out = new ArrayList<>();
        if (doc.get(key) instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Document d) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    // ---- shared helpers ------------------------------------------------------

    @Override
    public boolean enabled() {
        return ctx.settings().bool("events.enabled");
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,24}");
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v));
    }

    private static String at(String world, double x, double y, double z) {
        return fmt(x) + ", " + fmt(y) + ", " + fmt(z) + " in " + world;
    }

    private FactionService factions() {
        return ctx.serviceOrNull(FactionService.class);
    }

    private Faction faction(UUID id) {
        if (id == null) {
            return null;
        }
        FactionService fs = factions();
        return fs != null ? fs.byId(id) : ctx.repo().faction(id);
    }

    private String tagOf(UUID id) {
        Faction f = faction(id);
        return f == null ? "a disbanded faction" : f.tag();
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "Server";
        }
        PlayerRef ref = Players.online(uuid);
        return ref != null ? ref.getUsername() : ctx.player(uuid).name();
    }

    /** Sends a line to every online player. */
    void broadcast(Message message) {
        Universe u = Universe.get();
        if (u == null) {
            return;
        }
        for (PlayerRef p : u.getPlayers()) {
            Msg.send(p, message);
        }
    }

    private void alert(Faction faction, Message message) {
        if (faction == null) {
            return;
        }
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat != null) {
            chat.sendToFaction(faction, message, false);
            return;
        }
        for (Member m : faction.members()) {
            Msg.send(Players.online(m.uuid()), message);
        }
    }

    private void publish(String kind, String name, String phase, String world, UUID faction, String text) {
        try {
            ctx.bus().publish(new DominionEvent.GameEvent(kind, name, phase, world, faction, text));
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("A Dominion event listener failed");
        }
    }

    private void audit(Faction f, String text) {
        FactionService fs = factions();
        if (fs != null && f != null) {
            fs.audit(f, null, "Server", "events", text);
        }
    }

    private void run(List<Runnable> after) {
        for (Runnable r : after) {
            try {
                r.run();
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("A Dominion events task failed");
            }
        }
    }

    private List<Presence> presence() {
        List<Presence> out = new ArrayList<>();
        Universe u = Universe.get();
        FactionService fs = factions();
        if (u == null || fs == null) {
            return out;
        }
        for (PlayerRef p : u.getPlayers()) {
            Faction f = fs.factionOf(p.getUuid());
            if (f == null || f.isSystem()) {
                continue;
            }
            Vector3d pos = Players.position(p);
            String world = Players.worldName(p);
            if (pos != null && world != null) {
                out.add(new Presence(p.getUuid(), f, world, pos));
            }
        }
        return out;
    }

    // ---- KOTH ------------------------------------------------------------------

    @Override
    public Result kothCreate(UUID admin, String name, String world, double x, double y, double z, int radius) {
        if (!validName(name)) {
            return Result.fail("Hill names use letters, numbers, dashes and underscores, up to 24 characters.");
        }
        if (world == null || world.isBlank()) {
            return Result.fail("Your position is not known yet. Try again in a moment.");
        }
        int r = radius > 0 ? radius : Math.max(1, ctx.settings().integer("events.kothRadius"));
        if (r > MAX_RADIUS) {
            return Result.fail("A hill radius can be at most " + MAX_RADIUS + " blocks.");
        }
        synchronized (lock) {
            if (hills.containsKey(key(name))) {
                return Result.fail("A hill named " + name + " already exists.");
            }
            hills.put(key(name), new Hill(name, world, x, y, z, r));
            dirty = true;
        }
        save();
        return Result.pass("Hill " + name + " is set at " + at(world, x, y, z) + " with a radius of " + r + " blocks.");
    }

    @Override
    public Result kothDelete(UUID admin, String name) {
        synchronized (lock) {
            if (hills.remove(key(name)) == null) {
                return Result.fail("No hill is named " + name + ".");
            }
            dirty = true;
        }
        save();
        return Result.pass("Hill " + name + " was removed.");
    }

    @Override
    public Result kothStart(UUID admin, String name) {
        if (!enabled()) {
            return Result.fail("Events are turned off on this server.");
        }
        List<Runnable> after = new ArrayList<>();
        synchronized (lock) {
            Hill h = hills.get(key(name));
            if (h == null) {
                return Result.fail("No hill is named " + name + ".");
            }
            if (h.running) {
                return Result.fail("Hill " + h.name + " is already open.");
            }
            open(h, Time.now(), after);
        }
        run(after);
        save();
        return Result.pass("Hill " + name + " is open.");
    }

    @Override
    public Result kothStop(UUID admin, String name) {
        List<Runnable> after = new ArrayList<>();
        synchronized (lock) {
            Hill h = hills.get(key(name));
            if (h == null) {
                return Result.fail("No hill is named " + name + ".");
            }
            if (!h.running) {
                return Result.fail("Hill " + h.name + " is not open.");
            }
            close(h, "was closed by an admin with no winner", after);
        }
        run(after);
        save();
        return Result.pass("Hill " + name + " is closed.");
    }

    @Override
    public Result kothSchedule(UUID admin, String name, String cronLike) {
        String text = cronLike == null ? "" : cronLike.trim();
        String msg;
        synchronized (lock) {
            Hill h = hills.get(key(name));
            if (h == null) {
                return Result.fail("No hill is named " + name + ".");
            }
            if (text.equalsIgnoreCase("off") || text.equalsIgnoreCase("none") || text.equalsIgnoreCase("clear")) {
                h.schedule = null;
                h.nextRun = 0;
                dirty = true;
                msg = "Hill " + h.name + " no longer opens on a schedule.";
            } else {
                Schedule s = Schedule.parse(text);
                if (s == null) {
                    return Result.fail("Use every and a number of minutes such as every 30m, or at and times such as at 18:00,21:30.");
                }
                long now = Time.now();
                h.schedule = s;
                h.nextRun = s.next(now);
                h.warned10 = false;
                h.warned1 = false;
                dirty = true;
                msg = "Hill " + h.name + " opens " + s.describe() + ". The next opening is in "
                        + Time.remaining(h.nextRun - now) + ".";
            }
        }
        // The file is written outside the lock so the tick never waits on the disk.
        save();
        return Result.pass(msg);
    }

    @Override
    public List<String> hillNames() {
        synchronized (lock) {
            List<String> names = new ArrayList<>();
            for (Hill h : hills.values()) {
                names.add(h.name);
            }
            return names;
        }
    }

    @Override
    public List<String> outpostNames() {
        synchronized (lock) {
            List<String> names = new ArrayList<>();
            for (Outpost o : outposts.values()) {
                names.add(o.name);
            }
            return names;
        }
    }

    @Override
    public List<String> koths() {
        List<String> names;
        synchronized (lock) {
            names = new ArrayList<>();
            for (Hill h : hills.values()) {
                names.add(h.name);
            }
        }
        List<String> out = new ArrayList<>();
        for (String n : names) {
            String s = kothStatus(n);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public String kothStatus(String name) {
        synchronized (lock) {
            Hill h = hills.get(key(name));
            if (h == null) {
                return null;
            }
            long now = Time.now();
            String base = "Hill " + h.name + " at " + at(h.world, h.x, h.y, h.z);
            if (h.running) {
                long cap = capSeconds();
                String state = h.contested ? " is contested"
                        : h.capturer == null ? " is open and nobody holds it"
                        : " is open and " + tagOf(h.capturer) + " has held it for " + Time.remaining(h.held * 1000L)
                        + " of " + Time.remaining(cap * 1000L);
                return base + state + ". It closes in " + Time.remaining(h.startedAt + MAX_KOTH_MILLIS - now) + ".";
            }
            String last = h.lastWinner.isBlank() ? "" : " Last won by " + h.lastWinner + " " + Time.ago(h.lastCapturedAt) + ".";
            if (h.schedule != null && h.nextRun > 0) {
                return base + " is closed and opens in " + Time.remaining(h.nextRun - now) + ", "
                        + h.schedule.describe() + "." + last;
            }
            return base + " is closed with no schedule." + last;
        }
    }

    private long capSeconds() {
        return Math.max(10, ctx.settings().integer("events.kothCapSeconds"));
    }

    /** Opens a hill. Caller holds the lock. */
    private void open(Hill h, long now, List<Runnable> after) {
        h.running = true;
        h.startedAt = now;
        h.capturer = null;
        h.held = 0;
        h.contested = false;
        h.lastNotice = now;
        dirty = true;
        String text = "Hill " + h.name + " is open at " + at(h.world, h.x, h.y, h.z) + ". Hold it for "
                + Time.remaining(capSeconds() * 1000L) + " to win " + rewardText() + ".";
        String name = h.name;
        String world = h.world;
        after.add(() -> {
            broadcast(Msg.warn(text));
            publish("koth", name, "started", world, null, text);
        });
    }

    private String rewardText() {
        return rewardText(true);
    }

    /** The hill prize, addressed to the players who could win it or, after a capture, to the winners. */
    private String rewardText(boolean yours) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        double coins = ctx.settings().decimal("events.kothRewardCoins");
        int points = ctx.settings().integer("events.kothRewardPoints");
        List<String> parts = new ArrayList<>();
        if (coins > 0 && eco != null && eco.enabled()) {
            parts.add(eco.format(coins) + (yours ? " for your faction bank" : " for its bank"));
        }
        if (points > 0) {
            parts.add(points + " faction points");
        }
        return parts.isEmpty() ? "the hill" : String.join(" and ", parts);
    }

    /**
     * Moves a scheduled opening that passed while the hill was open to the next one, so a hill
     * that ran over its slot does not reopen the moment it closes. Caller holds the lock.
     */
    private static void reschedule(Hill h, long now) {
        if (h.schedule != null && h.nextRun > 0 && h.nextRun <= now) {
            h.nextRun = h.schedule.next(now);
            h.warned10 = false;
            h.warned1 = false;
        }
    }

    /** Closes a hill without a winner. Caller holds the lock. */
    private void close(Hill h, String why, List<Runnable> after) {
        reschedule(h, Time.now());
        h.running = false;
        h.capturer = null;
        h.held = 0;
        h.contested = false;
        dirty = true;
        String text = "Hill " + h.name + " " + why + ".";
        String name = h.name;
        String world = h.world;
        after.add(() -> {
            broadcast(Msg.info(text));
            publish("koth", name, "stopped", world, null, text);
        });
    }

    private void tickHill(Hill h, long now, List<Presence> players, List<Runnable> after) {
        if (!h.running) {
            if (h.schedule == null || h.nextRun <= 0) {
                return;
            }
            if (Hill.missed(h.nextRun, now)) {
                // The opening was missed, usually while the server was down, so wait for the next one.
                h.nextRun = h.schedule.next(now);
                h.warned10 = false;
                h.warned1 = false;
                dirty = true;
                return;
            }
            long left = h.nextRun - now;
            if (left <= 0) {
                h.nextRun = h.schedule.next(now);
                h.warned10 = false;
                h.warned1 = false;
                open(h, now, after);
                return;
            }
            if (left <= MINUTE && !h.warned1) {
                h.warned1 = true;
                h.warned10 = true;
                String text = "Hill " + h.name + " opens in one minute at " + at(h.world, h.x, h.y, h.z) + ".";
                after.add(() -> broadcast(Msg.info(text)));
            } else if (left <= 10 * MINUTE && left > MINUTE && !h.warned10) {
                h.warned10 = true;
                String text = "Hill " + h.name + " opens in ten minutes at " + at(h.world, h.x, h.y, h.z) + ".";
                after.add(() -> broadcast(Msg.info(text)));
            }
            return;
        }
        if (now - h.startedAt >= MAX_KOTH_MILLIS) {
            close(h, "closed after an hour with no winner", after);
            return;
        }
        Map<UUID, Presence> inside = new LinkedHashMap<>();
        for (Presence p : players) {
            if (h.inside(p.world(), p.pos().x, p.pos().y, p.pos().z)) {
                inside.putIfAbsent(p.faction().id(), p);
            }
        }
        boolean wasContested = h.contested;
        UUID hadCapturer = h.capturer;
        long cap = capSeconds();
        Hill.Step step = h.step(new ArrayList<>(inside.keySet()), cap);
        if (step == Hill.Step.CONTESTED) {
            if (!wasContested) {
                String text = "Hill " + h.name + " is contested.";
                after.add(() -> broadcast(Msg.warn(text)));
            }
            return;
        }
        if (step == Hill.Step.EMPTY) {
            if (hadCapturer != null) {
                dirty = true;
            }
            return;
        }
        Presence holder = inside.values().iterator().next();
        Faction f = holder.faction();
        if (!f.id().equals(hadCapturer)) {
            h.lastNotice = now;
            dirty = true;
            String text = f.tag() + " is taking hill " + h.name + ".";
            after.add(() -> broadcast(Msg.info(text)));
        }
        if (step == Hill.Step.CAPTURED) {
            capture(h, f, holder.player(), after);
            return;
        }
        if (now - h.lastNotice >= NOTICE_MILLIS) {
            h.lastNotice = now;
            String text = f.tag() + " holds hill " + h.name + " and wins in " + Time.remaining((cap - h.held) * 1000L) + ".";
            after.add(() -> broadcast(Msg.info(text)));
        }
    }

    private void capture(Hill h, Faction f, UUID player, List<Runnable> after) {
        long now = Time.now();
        reschedule(h, now);
        h.running = false;
        h.capturer = null;
        h.held = 0;
        h.contested = false;
        h.lastWinner = f.tag();
        h.lastCapturedAt = now;
        dirty = true;
        String name = h.name;
        String world = h.world;
        after.add(() -> {
            double coins = ctx.settings().decimal("events.kothRewardCoins");
            int points = ctx.settings().integer("events.kothRewardPoints");
            EconomyService eco = ctx.serviceOrNull(EconomyService.class);
            if (coins > 0 && eco != null && eco.enabled()) {
                Result r = eco.bankCredit(f, coins, "capturing hill " + name);
                if (r.failed()) {
                    ctx.logger().at(Level.WARNING).log("Hill reward for %s failed, %s", f.tag(), r.message());
                }
            }
            if (points > 0) {
                synchronized (f) {
                    f.setPoints(f.points() + points);
                }
            }
            f.addStat("kothWins", 1);
            ProgressService progress = ctx.serviceOrNull(ProgressService.class);
            if (progress != null && player != null) {
                try {
                    progress.advance(player, "koth", 1);
                } catch (RuntimeException e) {
                    ctx.logger().at(Level.WARNING).withCause(e).log("Progress update for a hill failed");
                }
            }
            audit(f, "Captured hill " + name);
            String text = f.tag() + " captured hill " + name + " and won " + rewardText(false) + ".";
            publish("koth", name, "captured", world, f.id(), text);
            broadcast(Msg.good(text));
        });
    }

    // ---- outposts --------------------------------------------------------------

    @Override
    public Result outpostCreate(UUID admin, String name, String world, double x, double y, double z, int radius,
                                String boost, double multiplier) {
        if (!validName(name)) {
            return Result.fail("Outpost names use letters, numbers, dashes and underscores, up to 24 characters.");
        }
        if (world == null || world.isBlank()) {
            return Result.fail("Your position is not known yet. Try again in a moment.");
        }
        String kind = boost == null ? "" : boost.trim().toLowerCase(Locale.ROOT);
        if (!BOOSTS.contains(kind)) {
            return Result.fail("The boost must be xp, missions, tax or sell.");
        }
        if (!(multiplier > 0) || multiplier > 10) {
            return Result.fail("The multiplier must be above 0 and at most 10.");
        }
        int r = radius > 0 ? radius : Math.max(1, ctx.settings().integer("events.kothRadius"));
        if (r > MAX_RADIUS) {
            return Result.fail("An outpost radius can be at most " + MAX_RADIUS + " blocks.");
        }
        synchronized (lock) {
            if (outposts.containsKey(key(name))) {
                return Result.fail("An outpost named " + name + " already exists.");
            }
            outposts.put(key(name), new Outpost(name, world, x, y, z, r, kind, multiplier));
            dirty = true;
        }
        save();
        return Result.pass("Outpost " + name + " is set at " + at(world, x, y, z) + " with a radius of " + r
                + " blocks. Holding it gives " + boostName(kind) + " of " + multiplier + " times.");
    }

    @Override
    public Result outpostDelete(UUID admin, String name) {
        synchronized (lock) {
            if (outposts.remove(key(name)) == null) {
                return Result.fail("No outpost is named " + name + ".");
            }
            dirty = true;
        }
        save();
        return Result.pass("Outpost " + name + " was removed.");
    }

    @Override
    public Result outpostReset(UUID admin, String name) {
        UUID old;
        synchronized (lock) {
            Outpost o = outposts.get(key(name));
            if (o == null) {
                return Result.fail("No outpost is named " + name + ".");
            }
            old = o.holder;
            o.holder = null;
            o.heldSince = 0;
            o.control.clear();
            dirty = true;
        }
        save();
        if (old != null) {
            alert(faction(old), Msg.warn("An admin reset outpost " + name + ". Your faction no longer holds it."));
        }
        return Result.pass("Outpost " + name + " was reset and nobody holds it.");
    }

    @Override
    public List<String> outposts() {
        List<String> names = new ArrayList<>();
        synchronized (lock) {
            for (Outpost o : outposts.values()) {
                names.add(o.name);
            }
        }
        List<String> out = new ArrayList<>();
        for (String n : names) {
            String s = outpostStatus(n);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public String outpostStatus(String name) {
        synchronized (lock) {
            Outpost o = outposts.get(key(name));
            if (o == null) {
                return null;
            }
            String base = "Outpost " + o.name + " at " + at(o.world, o.x, o.y, o.z) + " gives " + boostName(o.boost) + " of "
                    + o.multiplier + " times";
            String holder = o.holder == null ? ". Nobody holds it"
                    : ". " + tagOf(o.holder) + " holds it with " + fmt(o.controlOf(o.holder)) + " percent control";
            UUID top = null;
            double best = 0;
            for (Map.Entry<UUID, Double> e : o.control.entrySet()) {
                if (!e.getKey().equals(o.holder) && e.getValue() > best) {
                    best = e.getValue();
                    top = e.getKey();
                }
            }
            String challenger = top == null ? "" : " and " + tagOf(top) + " is at " + fmt(best) + " percent";
            return base + holder + challenger + (o.contested ? ". It is contested." : ".");
        }
    }

    @Override
    public Faction outpostHolder(String name) {
        UUID holder;
        synchronized (lock) {
            Outpost o = outposts.get(key(name));
            holder = o == null ? null : o.holder;
        }
        return faction(holder);
    }

    @Override
    public double boostFor(Faction faction, String kind) {
        if (faction == null || kind == null || !enabled()) {
            return 1.0;
        }
        String k = kind.toLowerCase(Locale.ROOT);
        double m = 1.0;
        synchronized (lock) {
            for (Outpost o : outposts.values()) {
                if (faction.id().equals(o.holder) && o.boost.equals(k)) {
                    m *= o.multiplier;
                }
            }
        }
        return m;
    }

    private void tickOutpost(Outpost o, List<Presence> players, List<Runnable> after) {
        Settings s = ctx.settings();
        double gain = Math.max(0, s.decimal("events.outpostPercentPerPlayerPerSecond"));
        double decay = outpostDecay();
        if (o.holder != null && faction(o.holder) == null) {
            o.control.remove(o.holder);
            o.holder = null;
            dirty = true;
        }
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, Faction> byId = new HashMap<>();
        for (Presence p : players) {
            if (o.inside(p.world(), p.pos().x, p.pos().y, p.pos().z)) {
                counts.merge(p.faction().id(), 1, Integer::sum);
                byId.put(p.faction().id(), p.faction());
            }
        }
        UUID taker = o.step(counts, gain, decay);
        if (taker != null) {
            changeHolder(o, byId.get(taker), after);
        }
        if (o.holder != null) {
            pendingOutpostSeconds.merge(o.holder, 1L, Long::sum);
        }
    }

    /**
     * Control lost per second while nobody holds the point. A setting of 0 or close to it would let
     * a holder keep an outpost forever, so anything below 0.05 per second reads as that minimum and
     * a single log line says so the first time that happens.
     */
    double outpostDecay() {
        double v = ctx.settings().decimal("events.outpostDecayPerSecond");
        if (v >= MIN_OUTPOST_DECAY) {
            return v;
        }
        if (decayWarned.compareAndSet(false, true)) {
            ctx.logger().at(Level.INFO).log(
                    "events.outpostDecayPerSecond is %s so outposts decay at the minimum of %s per second", v, MIN_OUTPOST_DECAY);
        }
        return MIN_OUTPOST_DECAY;
    }

    private void changeHolder(Outpost o, Faction winner, List<Runnable> after) {
        UUID old = o.holder;
        o.holder = winner.id();
        o.heldSince = Time.now();
        dirty = true;
        String name = o.name;
        String world = o.world;
        String boost = o.boost;
        double mult = o.multiplier;
        after.add(() -> {
            Faction loser = faction(old);
            String text = winner.tag() + " took outpost " + name + (loser != null ? " from " + loser.tag() : "") + ".";
            publish("outpost", name, "captured", world, winner.id(), text);
            alert(winner, Msg.good("Your faction took outpost " + name + ". You now get " + boostName(boost) + " of "
                    + mult + " times while you hold it."));
            audit(winner, "Took outpost " + name);
            if (loser != null) {
                alert(loser, Msg.bad(winner.tag() + " took outpost " + name + " from your faction."));
                audit(loser, "Lost outpost " + name + " to " + winner.tag());
            }
        });
    }

    /** Moves accumulated holding seconds onto the faction stat. Caller holds the lock. */
    private void flushOutpostSeconds() {
        for (Map.Entry<UUID, Long> e : pendingOutpostSeconds.entrySet()) {
            Faction f = faction(e.getKey());
            if (f != null) {
                f.addStat("outpostSeconds", e.getValue());
            }
        }
        pendingOutpostSeconds.clear();
    }

    // ---- envoys ----------------------------------------------------------------

    @Override
    public Result envoySpawn(UUID admin, String zoneTag) {
        if (!enabled()) {
            return Result.fail("Events are turned off on this server.");
        }
        Faction zone = zoneTag == null ? null : ctx.repo().factionByTag(zoneTag);
        if (zone == null) {
            return Result.fail("No zone has the tag " + zoneTag + ".");
        }
        if (zone.type() != FactionType.WARZONE) {
            return Result.fail(zone.tag() + " is not a war zone.");
        }
        List<String> worlds = new ArrayList<>();
        List<Long> chunks = new ArrayList<>();
        for (String w : ctx.repo().claimWorlds()) {
            for (long c : ctx.repo().claimsOf(w, zone.id())) {
                worlds.add(w);
                chunks.add(c);
            }
        }
        if (chunks.isEmpty()) {
            return Result.fail(zone.tag() + " has no claimed land for an envoy to land on.");
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int pick = rnd.nextInt(chunks.size());
        String worldName = worlds.get(pick);
        long chunk = chunks.get(pick);
        int size = Keys.chunkSize();
        int x = Keys.minBlock(Keys.chunkX(chunk)) + rnd.nextInt(size);
        int z = Keys.minBlock(Keys.chunkZ(chunk)) + rnd.nextInt(size);
        int y = fallbackHeight(admin, zone, worldName);
        long now = Time.now();
        long life = Math.max(1, ctx.settings().integer("events.envoyDespawnMinutes")) * MINUTE;
        String id = Long.toString(now % 1_000_000L, 36) + Integer.toString(rnd.nextInt(36 * 36), 36);
        Envoy envoy = new Envoy(id, zone.tag(), worldName, x, y, z, now, now + life);
        synchronized (lock) {
            envoys.add(envoy);
            dirty = true;
        }
        String tag = zone.tag();
        World world = Universe.get() == null ? null : Universe.get().getWorld(worldName);
        if (world == null) {
            announceEnvoy(envoy, tag);
            return Result.pass("An envoy dropped in " + tag + " at " + envoy.where() + ". Its world is not loaded so no chest was placed.");
        }
        String chest = ctx.settings().text("events.envoyChestItem");
        world.execute(() -> {
            try {
                Integer top = Blocks.height(world, x, z);
                if (top != null) {
                    envoy.y = top + 1;
                }
                Blocks.set(world, x, envoy.y, z, chest, ok -> {
                    envoy.placed = ok;
                    envoy.block = ok ? chest : "";
                    markDirty();
                    if (ok && !active(envoy)) {
                        // Claimed or expired before the write landed, so the removal skipped it. Clear it now on this world thread.
                        Blocks.clear(world, x, envoy.y, z);
                    }
                    if (!ok) {
                        ctx.logger().at(Level.INFO).log("Envoy chest %s could not be placed at %s", chest, envoy.where());
                    }
                });
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Placing an envoy chest failed");
            }
            announceEnvoy(envoy, tag);
        });
        return Result.pass("An envoy is dropping in " + tag + ".");
    }

    /**
     * Height used when the world column cannot be read. The admin's own height when the admin
     * stands in that world, then the zone home, then a safe default above sea level.
     */
    private int fallbackHeight(UUID admin, Faction zone, String world) {
        PlayerRef ref = admin == null ? null : Players.online(admin);
        Vector3d pos = Players.position(ref);
        if (pos != null && world.equals(Players.worldName(ref))) {
            return (int) Math.floor(pos.y) + 1;
        }
        Location home = zone.home();
        if (home != null && world.equals(home.world())) {
            return (int) Math.floor(home.y()) + 1;
        }
        return 100;
    }

    private void announceEnvoy(Envoy envoy, String tag) {
        long left = envoy.expiresAt - Time.now();
        String text = "An envoy landed in " + tag + " at " + envoy.where() + ". Stand next to it and run /fe envoy claim within "
                + Time.remaining(left) + " to take the loot.";
        broadcast(Msg.warn(text));
        publish("envoy", envoy.id, "spawned", envoy.world, null, text);
    }

    private boolean active(Envoy envoy) {
        synchronized (lock) {
            return envoys.contains(envoy);
        }
    }

    private void markDirty() {
        synchronized (lock) {
            dirty = true;
        }
    }

    @Override
    public Result envoySchedule(UUID admin, String zoneTag, int everyMinutes) {
        Faction zone = zoneTag == null ? null : ctx.repo().factionByTag(zoneTag);
        if (zone == null) {
            return Result.fail("No zone has the tag " + zoneTag + ".");
        }
        if (zone.type() != FactionType.WARZONE) {
            return Result.fail(zone.tag() + " is not a war zone.");
        }
        if (everyMinutes > 0 && everyMinutes < 5) {
            return Result.fail("The shortest envoy timer is 5 minutes.");
        }
        synchronized (lock) {
            if (everyMinutes <= 0) {
                envoyTimers.remove(key(zone.tag()));
            } else {
                envoyTimers.put(key(zone.tag()), new long[] {everyMinutes, Time.now() + everyMinutes * MINUTE});
            }
            dirty = true;
        }
        save();
        if (everyMinutes <= 0) {
            return Result.pass("Envoys no longer drop in " + zone.tag() + " on a timer.");
        }
        return Result.pass("An envoy drops in " + zone.tag() + " every " + everyMinutes + " minutes.");
    }

    @Override
    public List<String> envoys() {
        List<String> out = new ArrayList<>();
        long now = Time.now();
        synchronized (lock) {
            for (Envoy e : envoys) {
                out.add("Envoy in " + e.zone + " at " + e.where() + " vanishes in " + Time.remaining(e.expiresAt - now)
                        + (e.placed ? "." : ". No chest could be placed so it is marked by coordinates only."));
            }
            for (Map.Entry<String, long[]> t : envoyTimers.entrySet()) {
                Faction zone = ctx.repo().factionByTag(t.getKey());
                String tag = zone == null ? t.getKey() : zone.tag();
                out.add("Envoys drop in " + tag + " every " + t.getValue()[0] + " minutes. The next one lands in "
                        + Time.remaining(t.getValue()[1] - now) + ".");
            }
        }
        return out;
    }

    /** Hands the loot of the nearest envoy within reach to the player through the market mailbox. */
    public Result envoyClaim(UUID player) {
        PlayerRef ref = Players.online(player);
        Vector3d pos = Players.position(ref);
        String world = Players.worldName(ref);
        if (pos == null || world == null) {
            return Result.fail("Your position is not known yet. Try again in a moment.");
        }
        MarketService market = ctx.serviceOrNull(MarketService.class);
        if (market == null) {
            return Result.fail("Envoy loot is delivered through the market mailbox, which is not installed.");
        }
        Envoy found = null;
        synchronized (lock) {
            double best = CLAIM_DISTANCE * CLAIM_DISTANCE;
            for (Envoy e : envoys) {
                if (!e.world.equals(world)) {
                    continue;
                }
                double dx = pos.x - (e.x + 0.5);
                double dy = pos.y - e.y;
                double dz = pos.z - (e.z + 0.5);
                double d = dx * dx + dy * dy + dz * dz;
                if (d <= best) {
                    best = d;
                    found = e;
                }
            }
            if (found == null) {
                return Result.fail("No envoy is within " + (int) CLAIM_DISTANCE + " blocks of you.");
            }
            envoys.remove(found);
            dirty = true;
        }
        List<ItemStack> loot = loot();
        market.mail(player, loot, 0);
        removeChest(found);
        String name = ref.getUsername();
        String text = name + " claimed the envoy in " + found.zone + ".";
        broadcast(Msg.info(text));
        publish("envoy", found.id, "claimed", found.world, null, text);
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress != null) {
            try {
                progress.advance(player, "envoy", 1);
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Progress update for an envoy failed");
            }
        }
        save();
        return Result.pass("The envoy loot is in your market mailbox. Collect it with /f market collect.");
    }

    private List<ItemStack> loot() {
        Object raw = ctx.settings().raw("events.envoyLoot");
        String spec = raw == null || raw.toString().isBlank() ? DEFAULT_LOOT : raw.toString();
        List<ItemStack> out = new ArrayList<>();
        for (String part : spec.split("\\|")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int colon = p.lastIndexOf(':');
            String id = colon > 0 ? p.substring(0, colon) : p;
            int qty = 1;
            if (colon > 0) {
                try {
                    qty = Math.max(1, Integer.parseInt(p.substring(colon + 1).trim()));
                } catch (NumberFormatException e) {
                    qty = 1;
                }
            }
            try {
                out.add(new ItemStack(id.trim(), qty));
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).log("Envoy loot item %s is not valid", id);
            }
        }
        return out;
    }

    private void removeChest(Envoy e) {
        if (!e.placed) {
            return;
        }
        World world = Universe.get() == null ? null : Universe.get().getWorld(e.world);
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Blocks.clear(world, e.x, e.y, e.z);
            } catch (RuntimeException ex) {
                ctx.logger().at(Level.WARNING).withCause(ex).log("Removing an envoy chest failed");
            }
        });
    }

    private void tickEnvoys(long now, List<Runnable> after, List<String> drops) {
        List<Envoy> expired = new ArrayList<>();
        for (Envoy e : envoys) {
            if (now >= e.expiresAt) {
                expired.add(e);
            }
        }
        if (!expired.isEmpty()) {
            envoys.removeAll(expired);
            dirty = true;
            for (Envoy e : expired) {
                String text = "The envoy in " + e.zone + " at " + e.where() + " vanished unclaimed.";
                after.add(() -> {
                    removeChest(e);
                    broadcast(Msg.muted(text));
                    publish("envoy", e.id, "despawned", e.world, null, text);
                });
            }
        }
        for (Map.Entry<String, long[]> t : envoyTimers.entrySet()) {
            long[] v = t.getValue();
            if (now >= v[1]) {
                v[1] = now + Math.max(5, v[0]) * MINUTE;
                dirty = true;
                drops.add(t.getKey());
            }
        }
    }

    // ---- bounties ----------------------------------------------------------------

    @Override
    public Result bountyPlace(UUID placer, UUID target, double amount) {
        if (!enabled()) {
            return Result.fail("Events are turned off on this server.");
        }
        if (placer == null || target == null) {
            return Result.fail("That player could not be found.");
        }
        if (placer.equals(target)) {
            return Result.fail("You cannot place a bounty on yourself.");
        }
        double min = ctx.settings().decimal("events.bountyMin");
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco == null || !eco.enabled()) {
            return Result.fail("Bounties need the economy, which is turned off.");
        }
        switch (Payouts.checkAmount(amount, min, eco.wholeUnits())) {
            case TOO_SMALL -> {
                return Result.fail("The smallest bounty is " + eco.format(Math.max(min, 0)) + ".");
            }
            case NOT_WHOLE -> {
                return Result.fail("Bounties must be whole amounts.");
            }
            case NONE -> { }
        }
        String targetName = nameOf(target);
        Result paid = eco.withdraw(placer, amount, "bounty on " + targetName);
        if (paid.failed()) {
            return paid;
        }
        double total;
        synchronized (lock) {
            Bounty b = bounties.computeIfAbsent(target, t -> new Bounty(t, targetName));
            b.name = targetName;
            b.amount += amount;
            b.placers.merge(placer, amount, Double::sum);
            b.placedAt = Time.now();
            total = b.amount;
            bountiesDirty = true;
        }
        save();
        String text = nameOf(placer) + " put " + eco.format(amount) + " on the head of " + targetName
                + ". The bounty is now " + eco.format(total) + ".";
        broadcast(Msg.warn(text));
        publish("bounty", targetName, "placed", null, null, text);
        return Result.pass("Your bounty of " + eco.format(amount) + " on " + targetName + " is placed.");
    }

    @Override
    public List<String> bounties() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        List<Bounty> list;
        synchronized (lock) {
            list = new ArrayList<>(bounties.values());
        }
        list.sort((a, b) -> Double.compare(b.amount, a.amount));
        List<String> out = new ArrayList<>();
        for (Bounty b : list) {
            String money = eco != null ? eco.format(b.amount) : fmt(b.amount);
            out.add(b.name + " is worth " + money + " from " + b.placers.size()
                    + (b.placers.size() == 1 ? " placer." : " placers."));
        }
        return out;
    }

    /** Bounty rows for the panel, highest first, as name and amount. */
    List<Object[]> bountyRows() {
        List<Object[]> out = new ArrayList<>();
        synchronized (lock) {
            for (Bounty b : bounties.values()) {
                out.add(new Object[] {b.name, b.amount, b.placers.size()});
            }
        }
        out.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));
        return out;
    }

    @Override
    public void onPlayerKill(UUID killer, UUID victim) {
        if (killer == null || victim == null || killer.equals(victim) || !enabled()) {
            return;
        }
        FactionService fs = factions();
        Faction kf = fs == null ? null : fs.factionOf(killer);
        Faction vf = fs == null ? null : fs.factionOf(victim);
        Bounty bounty;
        long now = Time.now();
        synchronized (lock) {
            // Combat and the bounty death system can both report the same death.
            Long last = recentKills.get(victim);
            if (last != null && now - last < KILL_DEDUPE_MILLIS) {
                return;
            }
            recentKills.put(victim, now);
            recentKills.values().removeIf(t -> now - t > KILL_DEDUPE_MILLIS * 4);
            bounty = bounties.get(victim);
            if (bounty == null) {
                return;
            }
            // Factions cannot farm the bounty on their own member, and placers cannot collect their own money.
            if (!Payouts.collectable(killer, kf == null ? null : kf.id(), vf == null ? null : vf.id(), bounty.placers.keySet())) {
                return;
            }
            bounties.remove(victim);
            bountiesDirty = true;
        }
        // Deaths arrive on the world thread, so the payment, the file write and the broadcast run on the scheduler.
        try {
            HytaleServer.SCHEDULED_EXECUTOR.execute(() -> payBounty(killer, kf, bounty));
        } catch (RuntimeException e) {
            restoreBounty(bounty);
            ctx.logger().at(Level.WARNING).withCause(e).log("Bounty payout to %s could not be queued", killer);
        }
    }

    /** Puts an unpaid bounty back, adding to any bounty placed on the same player since. */
    private void restoreBounty(Bounty bounty) {
        synchronized (lock) {
            Bounty current = bounties.get(bounty.target);
            if (current == null) {
                bounties.put(bounty.target, bounty);
            } else {
                current.amount += bounty.amount;
                bounty.placers.forEach((k, v) -> current.placers.merge(k, v, Double::sum));
            }
            bountiesDirty = true;
        }
    }

    private void payBounty(UUID killer, Faction kf, Bounty bounty) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco == null) {
            restoreBounty(bounty);
            return;
        }
        Result r;
        try {
            r = eco.deposit(killer, bounty.amount, "bounty on " + bounty.name);
        } catch (RuntimeException e) {
            r = Result.fail(String.valueOf(e.getMessage()));
        }
        if (r.failed()) {
            restoreBounty(bounty);
            ctx.logger().at(Level.WARNING).log("Bounty payout to %s failed, %s", killer, r.message());
            return;
        }
        save();
        String text = nameOf(killer) + " collected the bounty of " + eco.format(bounty.amount) + " on " + bounty.name + ".";
        broadcast(Msg.good(text));
        publish("bounty", bounty.name, "claimed", null, kf == null ? null : kf.id(), text);
    }

    // ---- season ------------------------------------------------------------------

    @Override
    public Result seasonStart(UUID admin, int graceDays, int raidDays) {
        synchronized (seasonLock) {
            return startSeason(admin, graceDays, raidDays);
        }
    }

    private Result startSeason(UUID admin, int graceDays, int raidDays) {
        if (graceDays < 0 || raidDays < 0) {
            return Result.fail("Days cannot be negative.");
        }
        Settings s = ctx.settings();
        if (s.bool("season.active")) {
            return Result.fail("A season is already running. End it first with /fe season end.");
        }
        long now = Time.now();
        ShieldService shields = ctx.serviceOrNull(ShieldService.class);
        if (shields != null) {
            if (shields.eotwActive()) {
                shields.setEotw(admin, false);
            }
            if (graceDays > 0) {
                Result g = shields.setGrace(admin, now + graceDays * DAY);
                if (g.failed()) {
                    return g;
                }
            }
        } else if (graceDays > 0) {
            return Result.fail("Grace needs the shield module, which is not installed.");
        }
        long raidEnds = raidDays > 0 ? now + (graceDays + (long) raidDays) * DAY : 0L;
        s.set("season.active", true);
        s.set("season.startedAt", now);
        s.set("season.raidEndsAt", raidEnds);
        synchronized (lock) {
            snapshot.clear();
            snapshotAt = 0;
            payoutsRunAt = 0;
            dirty = true;
        }
        save();
        String text = "A new season has begun." + (graceDays > 0 ? " Grace lasts " + plural(graceDays, "day") + "." : "")
                + (raidDays > 0 ? " The end of the world comes in " + plural(graceDays + raidDays, "day") + "." : "");
        broadcast(Msg.good(text));
        publish("season", "season", "started", null, null, text);
        return Result.pass("The season started.");
    }

    @Override
    public Result seasonEnd(UUID admin) {
        synchronized (seasonLock) {
            return endSeason(admin);
        }
    }

    private Result endSeason(UUID admin) {
        Settings s = ctx.settings();
        synchronized (lock) {
            // Ending twice would refreeze the board and clear the payout record, letting payouts run again.
            if (!s.bool("season.active") && snapshotAt > 0) {
                return Result.fail("The season already ended " + Time.ago(snapshotAt) + ". Start a new one first.");
            }
        }
        ShieldService shields = ctx.serviceOrNull(ShieldService.class);
        if (shields != null) {
            if (shields.graceActive()) {
                shields.endGrace(admin);
            }
            if (!shields.eotwActive()) {
                Result r = shields.setEotw(admin, true);
                if (r.failed()) {
                    return r;
                }
            }
        }
        List<Document> frozen = leaderboard(SNAPSHOT_SIZE);
        long now = Time.now();
        synchronized (lock) {
            snapshot.clear();
            snapshot.addAll(frozen);
            snapshotAt = now;
            payoutsRunAt = 0;
            dirty = true;
        }
        s.set("season.active", false);
        s.set("season.raidEndsAt", 0L);
        save();
        List<String> tags = new ArrayList<>();
        for (Document d : frozen) {
            tags.add(d.getString("Tag"));
        }
        String text = "The season is over and the end of the world has begun."
                + (frozen.isEmpty() ? "" : (frozen.size() == 1 ? " The top faction is " : " The top factions are ")
                + Payouts.topList(tags, 3) + ".");
        broadcast(Msg.warn(text));
        publish("season", "season", "ended", null, null, text);
        return Result.pass("The season ended and the leaderboard is frozen.");
    }

    private List<Document> leaderboard(int limit) {
        List<Document> out = new ArrayList<>();
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        List<Faction> top;
        if (progress != null) {
            top = progress.top(limit * 2);
        } else {
            FactionService fs = factions();
            top = fs == null ? List.of() : new ArrayList<>(fs.all());
            top.sort((a, b) -> Integer.compare(b.points(), a.points()));
        }
        int rank = 0;
        for (Faction f : top) {
            if (f.isSystem()) {
                continue;
            }
            rank++;
            out.add(new Document("Rank", rank).append("Tag", f.tag()).append("Id", f.id().toString())
                    .append("Value", progress != null ? progress.value(f) : (double) f.points()));
            if (rank >= limit) {
                break;
            }
        }
        return out;
    }

    @Override
    public String seasonStatus() {
        Settings s = ctx.settings();
        long now = Time.now();
        ShieldService shields = ctx.serviceOrNull(ShieldService.class);
        StringBuilder out = new StringBuilder();
        if (s.bool("season.active")) {
            out.append("The season started ").append(Time.ago(s.longValue("season.startedAt"))).append(".");
            if (shields != null && shields.graceActive()) {
                long ends = shields.graceEndsAt();
                out.append(ends > 0 ? " Grace ends in " + Time.remaining(ends - now) + "." : " Grace is on.");
            }
            long raid = s.longValue("season.raidEndsAt");
            if (raid > 0) {
                out.append(" The end of the world begins in ").append(Time.remaining(raid - now)).append(".");
            }
        } else if (shields != null && shields.eotwActive()) {
            out.append("The season is over and the end of the world is running.");
        } else {
            out.append("No season is running.");
        }
        synchronized (lock) {
            if (snapshotAt > 0) {
                out.append(" The leaderboard was frozen ").append(Time.ago(snapshotAt)).append(".");
                if (payoutsRunAt > 0) {
                    out.append(" Payouts ran ").append(Time.ago(payoutsRunAt)).append(".");
                }
            }
        }
        return out.toString();
    }

    /** The factions that would be paid, frozen at season end or live otherwise, as rank and tag. */
    private List<String[]> payoutTargets() {
        int n = Math.max(0, ctx.settings().integer("events.payoutTop"));
        List<String[]> out = new ArrayList<>();
        List<Document> board;
        synchronized (lock) {
            board = new ArrayList<>(snapshot);
        }
        if (board.isEmpty()) {
            board = leaderboard(n);
        }
        for (Document d : board) {
            if (out.size() >= n) {
                break;
            }
            // The frozen board keeps the id, so a faction that renamed is still paid and a faction that
            // took a freed tag is not. A disbanded faction or an unsafe tag is skipped.
            String tag = d.getString("Tag");
            String id = d.getString("Id");
            if (id != null) {
                Faction f;
                try {
                    f = faction(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                    f = null;
                }
                if (f == null) {
                    ctx.logger().at(Level.INFO).log("Season payout skips %s because the faction no longer exists", tag);
                    continue;
                }
                tag = f.tag();
            }
            if (!Payouts.safeTag(tag)) {
                ctx.logger().at(Level.WARNING).log("Season payout skips the tag %s because it is not plain letters and digits", tag);
                continue;
            }
            out.add(new String[] {String.valueOf((int) Hill.num(d, "Rank")), tag});
        }
        return out;
    }

    private List<String> templates() {
        return Payouts.templates(ctx.settings().text("events.payoutCommands"));
    }

    @Override
    public List<String> payoutsPreview() {
        List<String> out = new ArrayList<>();
        List<String[]> targets = payoutTargets();
        List<String> templates = templates();
        boolean frozen;
        synchronized (lock) {
            frozen = !snapshot.isEmpty();
        }
        if (targets.isEmpty()) {
            out.add("No factions qualify for payouts yet.");
            return out;
        }
        if (templates.isEmpty()) {
            out.add("No payout commands are set. Add them to events.payoutCommands in config.json, separated by pipes.");
            return out;
        }
        out.add(frozen ? "Payouts use the leaderboard frozen at the end of the season."
                : "No season has ended so payouts use the live leaderboard.");
        for (String[] t : targets) {
            for (String template : templates) {
                out.add("Rank " + t[0] + " " + t[1] + " runs /" + fill(template, t[1], t[0]));
            }
        }
        return out;
    }

    private static String fill(String template, String tag, String rank) {
        return Payouts.fill(template, tag, rank);
    }

    @Override
    public Result payoutsRun(UUID admin) {
        if (admin != null) {
            PlayerRef ref = Players.online(admin);
            // The node comes from permissions.adminPayouts, Perms.payouts() names the one in use.
            if (ref == null || !ref.hasPermission(Perms.payouts())) {
                return Result.fail("Running payouts needs the " + EventsCommands.PAYOUTS + " permission.");
            }
        }
        List<String[]> targets = payoutTargets();
        List<String> templates = templates();
        if (targets.isEmpty()) {
            return Result.fail("No factions qualify for payouts yet.");
        }
        if (templates.isEmpty()) {
            return Result.fail("No payout commands are set in events.payoutCommands in config.json.");
        }
        synchronized (lock) {
            // Only a season start or a season end clears this, so payouts never run twice for one board.
            if (payoutsRunAt > 0) {
                return Result.fail("Payouts already ran for this season " + Time.ago(payoutsRunAt) + ".");
            }
            payoutsRunAt = Time.now();
            dirty = true;
        }
        // The record must be on disk before any command runs, or a restart could pay the same board again.
        if (!save()) {
            synchronized (lock) {
                payoutsRunAt = 0;
                dirty = true;
            }
            return Result.fail("The payout record could not be saved, so no payout commands ran. Check the log.");
        }
        int ran = 0;
        int failed = 0;
        CommandManager manager = CommandManager.get();
        for (String[] t : targets) {
            for (String template : templates) {
                String command = fill(template, t[1], t[0]);
                ctx.logger().at(Level.INFO).log("Season payout command %s", command);
                if (manager == null) {
                    failed++;
                    continue;
                }
                try {
                    manager.handleCommand(ConsoleSender.INSTANCE, command).exceptionally(e -> {
                        ctx.logger().at(Level.WARNING).withCause(e).log("Season payout command failed, %s", command);
                        return null;
                    });
                    ran++;
                } catch (RuntimeException e) {
                    failed++;
                    ctx.logger().at(Level.WARNING).withCause(e).log("Season payout command failed, %s", command);
                }
            }
        }
        publish("season", "payouts", "ran", null, null, "Season payouts ran for " + plural(targets.size(), "faction") + ".");
        if (manager == null) {
            return Result.fail("The command manager is not available, so the payout commands were only written to the log.");
        }
        return Result.pass("Sent " + plural(ran, "payout command") + " to the console"
                + (failed > 0 ? " and " + failed + " failed. Check the log." : "."));
    }

    private void tickSeason(long now) {
        Settings s = ctx.settings();
        if (!s.bool("season.active")) {
            return;
        }
        long raid = s.longValue("season.raidEndsAt");
        if (raid > 0 && now >= raid) {
            ctx.logger().at(Level.INFO).log("The season raid period ended, starting the end of the world");
            seasonEnd(null);
        }
    }

    /** The boost with its article, such as an xp boost or a tax boost. */
    static String boostName(String kind) {
        String k = kind == null ? "" : kind;
        boolean vowel = !k.isEmpty() && "aeiox".indexOf(Character.toLowerCase(k.charAt(0))) >= 0;
        return (vowel ? "an " : "a ") + k + " boost";
    }

    private static String plural(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    // ---- map points ----------------------------------------------------------------

    @Override
    public List<PointOfInterest> points(String world) {
        List<PointOfInterest> out = new ArrayList<>();
        synchronized (lock) {
            for (Hill h : hills.values()) {
                if (world != null && !world.equals(h.world)) {
                    continue;
                }
                String label = "Hill " + h.name + (h.running ? " is open" : "");
                out.add(new PointOfInterest("hill:" + key(h.name), "hill", label, h.world, h.x, h.y, h.z,
                        h.capturer, h.running ? Msg.WARN : Msg.MUTED, h.running ? h.startedAt + MAX_KOTH_MILLIS : 0L));
            }
            for (Outpost o : outposts.values()) {
                if (world != null && !world.equals(o.world)) {
                    continue;
                }
                Faction holder = faction(o.holder);
                String label = "Outpost " + o.name + (holder != null ? " held by " + holder.tag() : "");
                String color = holder != null && holder.color() != null && holder.color().matches("#[0-9a-fA-F]{6}")
                        ? holder.color() : Msg.INFO;
                out.add(new PointOfInterest("outpost:" + key(o.name), "outpost", label, o.world, o.x, o.y, o.z,
                        o.holder, color, 0L));
            }
            for (Envoy e : envoys) {
                if (world != null && !world.equals(e.world)) {
                    continue;
                }
                out.add(new PointOfInterest("envoy:" + e.id, "envoy", "Envoy in " + e.zone, e.world, e.x + 0.5, e.y,
                        e.z + 0.5, null, Msg.GOLD, e.expiresAt));
            }
        }
        return out;
    }

    // ---- panel views ------------------------------------------------------------------

    /** A row for the events page with a name, a state and a timer. */
    record Row(String name, String state, String timer, String color) {}

    List<Row> hillRows() {
        List<Row> out = new ArrayList<>();
        long now = Time.now();
        synchronized (lock) {
            for (Hill h : hills.values()) {
                if (h.running) {
                    String state = h.contested ? "Contested" : h.capturer == null ? "Open" : tagOf(h.capturer) + " "
                            + Math.min(100, h.held * 100 / Math.max(1, capSeconds())) + "%";
                    out.add(new Row(h.name, state, "closes in " + Time.remaining(h.startedAt + MAX_KOTH_MILLIS - now), Msg.WARN));
                } else {
                    String timer = h.schedule != null && h.nextRun > 0 ? "opens in " + Time.remaining(h.nextRun - now) : "no schedule";
                    out.add(new Row(h.name, h.lastWinner.isBlank() ? "Closed" : "Won by " + h.lastWinner, timer, Msg.TEXT));
                }
            }
        }
        return out;
    }

    List<Row> outpostRows() {
        List<Row> out = new ArrayList<>();
        synchronized (lock) {
            for (Outpost o : outposts.values()) {
                String holder = o.holder == null ? "Unheld" : tagOf(o.holder) + " " + fmt(o.controlOf(o.holder)) + "%";
                out.add(new Row(o.name, holder, o.boost + " x" + o.multiplier + (o.contested ? ", contested" : ""),
                        o.holder == null ? Msg.TEXT : Msg.GOOD));
            }
        }
        return out;
    }

    List<Row> envoyRows() {
        List<Row> out = new ArrayList<>();
        long now = Time.now();
        synchronized (lock) {
            for (Envoy e : envoys) {
                out.add(new Row("Envoy in " + e.zone, e.x + ", " + e.y + ", " + e.z, "vanishes in " + Time.remaining(e.expiresAt - now), Msg.GOLD));
            }
            for (Map.Entry<String, long[]> t : envoyTimers.entrySet()) {
                Faction zone = ctx.repo().factionByTag(t.getKey());
                out.add(new Row("Drops in " + (zone == null ? t.getKey() : zone.tag()), "every " + t.getValue()[0] + "m",
                        "next in " + Time.remaining(t.getValue()[1] - now), Msg.MUTED));
            }
        }
        return out;
    }

    List<String> snapshotLines() {
        List<String> out = new ArrayList<>();
        synchronized (lock) {
            for (Document d : snapshot) {
                out.add((int) Hill.num(d, "Rank") + ".  " + d.getString("Tag"));
            }
        }
        return out;
    }

    // ---- tick ----------------------------------------------------------------------------

    @Override
    public void tick() {
        long now = Time.now();
        ticks++;
        if (!enabled()) {
            if (dirtyAny() && now - lastSave >= SAVE_MILLIS) {
                save();
            }
            return;
        }
        List<Runnable> after = new ArrayList<>();
        List<String> drops = new ArrayList<>();
        try {
            boolean needPresence;
            synchronized (lock) {
                needPresence = !outposts.isEmpty();
                for (Hill h : hills.values()) {
                    needPresence |= h.running;
                }
            }
            List<Presence> players = needPresence ? presence() : List.of();
            synchronized (lock) {
                for (Hill h : hills.values()) {
                    tickHill(h, now, players, after);
                }
                for (Outpost o : outposts.values()) {
                    tickOutpost(o, players, after);
                }
                if (ticks % 10 == 0) {
                    flushOutpostSeconds();
                }
                tickEnvoys(now, after, drops);
            }
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion events tick failed");
        }
        run(after);
        for (String zone : drops) {
            Result r = envoySpawn(null, zone);
            if (r.failed()) {
                ctx.logger().at(Level.INFO).log("Scheduled envoy in %s did not drop, %s", zone, r.message());
            }
        }
        if (ticks % 60 == 0) {
            tickSeason(now);
        }
        if (dirtyAny() && now - lastSave >= SAVE_MILLIS) {
            save();
        }
    }

    private boolean dirtyAny() {
        synchronized (lock) {
            return dirty || bountiesDirty || !pendingOutpostSeconds.isEmpty();
        }
    }

    /** Names of hills and outposts for tab hints. */
    Set<String> names() {
        Set<String> out = new HashSet<>();
        synchronized (lock) {
            for (Hill h : hills.values()) {
                out.add(h.name);
            }
            for (Outpost o : outposts.values()) {
                out.add(o.name);
            }
        }
        return out;
    }
}
