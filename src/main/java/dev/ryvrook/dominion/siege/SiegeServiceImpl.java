package dev.ryvrook.dominion.siege;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runs every siege. A siege starts in CAPTURING around a banner point, then either fails, is
 * defended, or breaches the target chunk and its owned neighbours for a while before expiring.
 * All state changes happen under one lock and every phase change is saved to sieges.json.
 */
public final class SiegeServiceImpl implements SiegeService {
    private static final int HISTORY_LIMIT = 200;
    private static final long DEFEND_MILLIS = 30_000L;
    private static final long STATUS_MILLIS = 60_000L;
    private static final long SAVE_MILLIS = 60_000L;
    static final String IMMUNE_UNTIL = "siege.immuneUntil";

    private final DominionContext ctx;
    private final Object lock = new Object();
    private final List<Siege> active = new ArrayList<>();
    private final Deque<Siege> history = new ArrayDeque<>();
    private final Map<UUID, Long> lastStatus = new HashMap<>();
    private boolean dirty;
    private long lastSave = Time.now();

    public SiegeServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    // ---- persistence -------------------------------------------------------

    private Path file() {
        return ctx.plugin().getDataDirectory().resolve("sieges.json");
    }

    void load() {
        Document doc = ctx.files().read(file());
        if (doc == null) {
            return;
        }
        synchronized (lock) {
            active.clear();
            history.clear();
            for (Document d : docs(doc, "Active")) {
                Siege s = parse(d);
                if (s != null && s.active()) {
                    active.add(s);
                } else if (s != null) {
                    history.addLast(s);
                }
            }
            for (Document d : docs(doc, "History")) {
                Siege s = parse(d);
                if (s != null && history.size() < HISTORY_LIMIT) {
                    history.addLast(s);
                }
            }
        }
    }

    private Siege parse(Document d) {
        try {
            return Siege.fromDocument(d);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Skipping an unreadable siege entry");
            return null;
        }
    }

    private static List<Document> docs(Document doc, String key) {
        List<Document> out = new ArrayList<>();
        Object raw = doc.get(key);
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Document d) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    void save() {
        Document doc;
        synchronized (lock) {
            List<Document> a = new ArrayList<>();
            for (Siege s : active) {
                a.add(s.toDocument());
            }
            List<Document> h = new ArrayList<>();
            for (Siege s : history) {
                h.add(s.toDocument());
            }
            doc = new Document("Version", 1).append("Active", a).append("History", h);
            dirty = false;
            lastSave = Time.now();
        }
        ctx.files().write(file(), doc);
    }

    /** Breaches live in memory in the land module, so a restart must hand them back. */
    void restoreBreaches() {
        List<Siege> breached = new ArrayList<>();
        synchronized (lock) {
            for (Siege s : active) {
                if (s.phase() == Siege.Phase.BREACHED) {
                    breached.add(s);
                }
            }
        }
        for (Siege s : breached) {
            applyBreach(s, s.phaseEndsAt());
        }
    }

    // ---- queries -----------------------------------------------------------

    @Override
    public List<Siege> active() {
        synchronized (lock) {
            return new ArrayList<>(active);
        }
    }

    @Override
    public List<Siege> activeFor(Faction faction) {
        List<Siege> out = new ArrayList<>();
        if (faction == null) {
            return out;
        }
        synchronized (lock) {
            for (Siege s : active) {
                if (involves(s, faction.id())) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    @Override
    public List<Siege> history(Faction faction, int limit) {
        List<Siege> out = new ArrayList<>();
        synchronized (lock) {
            for (Siege s : history) {
                if (out.size() >= limit) {
                    break;
                }
                if (faction == null || involves(s, faction.id())) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    @Override
    public Siege byId(UUID id) {
        if (id == null) {
            return null;
        }
        synchronized (lock) {
            for (Siege s : active) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            for (Siege s : history) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public int raidPoints(Faction faction) {
        if (faction == null) {
            return 0;
        }
        long points = faction.stat("siegesWon") * 25 - faction.stat("siegesLost") * 5;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, points));
    }

    // ---- start and cancel --------------------------------------------------

    @Override
    public Result start(UUID actor) {
        Settings s = ctx.settings();
        FactionService fs = ctx.service(FactionService.class);
        ClaimService claims = ctx.service(ClaimService.class);
        ShieldService shields = ctx.serviceOrNull(ShieldService.class);
        boolean eotw = shields != null ? shields.eotwActive() : s.bool("eotw.active");

        Faction attacker = fs.factionOf(actor);
        if (attacker == null) {
            return Result.fail("You are not in a faction.");
        }
        if (attacker.isSystem()) {
            return Result.fail("Server zones cannot start sieges.");
        }
        if (!fs.can(actor, FactionAction.SIEGE)) {
            return Result.fail("Your rank cannot start sieges.");
        }
        if (!s.bool("raid.enabled") || !s.bool("raid.modeSiege")) {
            return Result.fail("Sieges are turned off on this server.");
        }
        if (!eotw && graceActive(shields, s)) {
            return Result.fail("Sieges are paused during the grace period.");
        }
        int minAttackers = s.integer("raid.minAttackersOnline");
        if (onlineMembers(attacker) < minAttackers) {
            return Result.fail("Your faction needs " + minAttackers + " members online to start a siege.");
        }

        PlayerRef ref = Players.online(actor);
        Vector3d pos = Players.position(ref);
        String world = Players.worldName(ref);
        if (pos == null || world == null) {
            return Result.fail("Your position is not known yet. Try again in a moment.");
        }
        long here = Keys.chunkAt(pos.x, pos.z);
        Faction hereOwner = claims.ownerAt(world, here);
        if (hereOwner != null && !hereOwner.id().equals(attacker.id())) {
            return Result.fail("Stand in the wilderness or your own land next to enemy land to start a siege.");
        }

        RelationService relations = ctx.serviceOrNull(RelationService.class);
        Faction defender = null;
        long target = 0;
        for (long n : Keys.neighbours(here)) {
            Faction owner = claims.ownerAt(world, n);
            if (owner == null || owner.isSystem() || owner.id().equals(attacker.id())) {
                continue;
            }
            Relation rel = relations != null ? relations.between(attacker, owner) : attacker.relationTo(owner.id());
            if (rel == Relation.ENEMY) {
                defender = owner;
                target = n;
                break;
            }
        }
        if (defender == null) {
            return Result.fail("There is no enemy land next to you. Stand beside a chunk owned by an enemy faction.");
        }

        int minDefenders = s.integer("raid.minDefendersOnline");
        if (!eotw && onlineMembers(defender) < minDefenders) {
            return Result.fail(defender.tag() + " needs " + minDefenders + " members online before it can be sieged.");
        }
        if (!eotw && shields != null && shields.shielded(defender, world, target)) {
            return Result.fail("That land of " + defender.tag() + " is under its shield right now.");
        }
        long now = Time.now();
        if (!eotw) {
            long immune = defender.stat(IMMUNE_UNTIL);
            if (immune > now) {
                return Result.fail(defender.tag() + " held off a siege recently and is safe for "
                        + Time.remaining(immune - now) + ".");
            }
        }

        Siege siege;
        synchronized (lock) {
            for (Siege a : active) {
                if (involves(a, attacker.id())) {
                    return Result.fail("Your faction is already in a siege.");
                }
                if (involves(a, defender.id())) {
                    return Result.fail(defender.tag() + " is already in a siege.");
                }
            }
            if (!eotw) {
                long cooldown = Math.max(0, s.integer("raid.attackerCooldownMinutes")) * 60_000L;
                long last = lastSiegeAt(attacker.id(), defender.id());
                if (last > 0 && now - last < cooldown) {
                    return Result.fail("You can siege " + defender.tag() + " again in "
                            + Time.remaining(last + cooldown - now) + ".");
                }
            }
            siege = new Siege(UUID.randomUUID(), attacker.id(), defender.id(), world, target,
                    pos.x, pos.y, pos.z, now, actor);
            siege.setPhase(Siege.Phase.CAPTURING, now + Math.max(1, s.integer("siege.captureMinutes")) * 60_000L);
            active.add(siege);
            lastStatus.put(siege.id(), now);
        }

        String where = Keys.describe(target);
        String who = nameOf(actor);
        fs.audit(attacker, actor, who, "siege", "Started a siege against " + defender.tag() + " at chunk " + where
                + " in " + world);
        fs.audit(defender, actor, who, "siege", attacker.tag() + " started a siege at chunk " + where + " in " + world);
        phaseChanged(siege, attacker, defender);
        return Result.pass("Your siege against " + defender.tag() + " has begun. Hold the banner for "
                + Time.remaining(siege.phaseEndsAt() - now) + ".");
    }

    @Override
    public Result adminCancel(UUID admin, String adminName, UUID siegeId) {
        Siege siege = byId(siegeId);
        if (siege == null || !siege.active()) {
            return Result.fail("No active siege has that id.");
        }
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs != null) {
            Faction attacker = fs.byId(siege.attacker());
            Faction defender = fs.byId(siege.defender());
            if (attacker != null) {
                fs.audit(attacker, admin, adminName, "siege", "An admin ended the siege against " + (defender == null ? "an unknown faction" : defender.tag()));
            }
            if (defender != null) {
                fs.audit(defender, admin, adminName, "siege", "An admin ended the siege by " + (attacker == null ? "an unknown faction" : attacker.tag()));
            }
        }
        finish(siege, Siege.Phase.FAILED);
        return Result.pass("The siege was ended.");
    }

    @Override
    public Result cancel(UUID actor) {
        FactionService fs = ctx.service(FactionService.class);
        Faction attacker = fs.factionOf(actor);
        if (attacker == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!fs.can(actor, FactionAction.SIEGE)) {
            return Result.fail("Your rank cannot call off sieges.");
        }
        Siege siege = null;
        synchronized (lock) {
            for (Siege s : active) {
                if (s.attacker().equals(attacker.id())) {
                    siege = s;
                    break;
                }
            }
            if (siege == null) {
                return Result.fail("Your faction is not running a siege.");
            }
            if (siege.phase() != Siege.Phase.CAPTURING) {
                return Result.fail("The walls are already breached so the siege cannot be called off.");
            }
        }
        Faction defender = fs.byId(siege.defender());
        String tag = defender != null ? defender.tag() : "the defenders";
        fs.audit(attacker, actor, nameOf(actor), "siege", "Called off the siege against " + tag);
        finish(siege, Siege.Phase.FAILED);
        return Result.pass("You called off the siege against " + tag + ".");
    }

    // ---- tick ----------------------------------------------------------------

    private record Presence(Faction faction, String world, Vector3d pos) {}

    @Override
    public void tick() {
        List<Siege> current = active();
        if (current.isEmpty()) {
            if (dirty) {
                save();
            }
            return;
        }
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs == null) {
            return;
        }
        long now = Time.now();
        List<Presence> players = null;
        Settings s = ctx.settings();
        for (Siege siege : current) {
            Faction attacker = fs.byId(siege.attacker());
            Faction defender = fs.byId(siege.defender());
            if (attacker == null || defender == null) {
                finish(siege, Siege.Phase.FAILED);
                continue;
            }
            Siege.Phase phase = siege.phase();
            if (phase == Siege.Phase.BREACHED) {
                if (now >= siege.phaseEndsAt()) {
                    expire(siege, attacker, defender);
                }
                continue;
            }
            if (phase != Siege.Phase.CAPTURING) {
                continue;
            }
            if (players == null) {
                players = presence(fs);
            }
            int attackers = count(players, siege, attacker, s.integer("siege.attackerRadius"));
            int defenders = count(players, siege, defender, s.integer("siege.defenderRadius"));

            if (defenders > 0 && attackers == 0) {
                if (siege.defenderPresentSince() == 0) {
                    siege.setDefenderPresentSince(now);
                } else if (now - siege.defenderPresentSince() >= DEFEND_MILLIS) {
                    defended(siege, attacker, defender);
                    continue;
                }
            } else {
                siege.setDefenderPresentSince(0);
            }

            if (attackers == 0) {
                if (siege.attackerAbsentSince() == 0) {
                    siege.setAttackerAbsentSince(now);
                } else if (now - siege.attackerAbsentSince() >= Math.max(1, s.integer("siege.absentSecondsToFail")) * 1000L) {
                    finish(siege, Siege.Phase.FAILED);
                    continue;
                }
            } else {
                siege.setAttackerAbsentSince(0);
            }

            // The walls only fall while an attacker holds the banner. Otherwise the absence timer decides.
            if (now >= siege.phaseEndsAt() && attackers > 0) {
                breach(siege, attacker, defender);
                continue;
            }

            boolean sendStatus;
            synchronized (lock) {
                Long last = lastStatus.get(siege.id());
                sendStatus = last == null || now - last >= STATUS_MILLIS;
                if (sendStatus) {
                    lastStatus.put(siege.id(), now);
                }
            }
            if (sendStatus) {
                contestStatus(siege, attacker, defender, attackers, defenders, now);
            }
        }
        if (dirty && now - lastSave >= SAVE_MILLIS) {
            save();
        }
    }

    private List<Presence> presence(FactionService fs) {
        List<Presence> out = new ArrayList<>();
        Universe u = Universe.get();
        if (u == null) {
            return out;
        }
        Collection<PlayerRef> refs = u.getPlayers();
        for (PlayerRef p : refs) {
            Faction f = fs.factionOf(p.getUuid());
            if (f == null) {
                continue;
            }
            Vector3d pos = Players.position(p);
            String world = Players.worldName(p);
            if (pos != null && world != null) {
                out.add(new Presence(f, world, pos));
            }
        }
        return out;
    }

    private static int count(List<Presence> players, Siege siege, Faction faction, int radius) {
        double r2 = (double) radius * radius;
        int n = 0;
        for (Presence p : players) {
            if (!p.faction().id().equals(faction.id()) || !p.world().equals(siege.world())) {
                continue;
            }
            double dx = p.pos().x - siege.x();
            double dy = p.pos().y - siege.y();
            double dz = p.pos().z - siege.z();
            if (dx * dx + dy * dy + dz * dz <= r2) {
                n++;
            }
        }
        return n;
    }

    private void contestStatus(Siege siege, Faction attacker, Faction defender, int attackers, int defenders, long now) {
        String left = Time.remaining(siege.phaseEndsAt() - now);
        String crowd = plural(attackers, "attacker") + " and " + plural(defenders, "defender")
                + " are at the banner on chunk " + Keys.describe(siege.chunkIndex()) + ".";
        alert(attacker, Msg.info("Siege on " + defender.tag() + " continues. " + crowd + " The walls fall in " + left + "."));
        alert(defender, Msg.warn("The siege by " + attacker.tag() + " continues. " + crowd
                + " Your walls fall in " + left + " unless you clear the banner."));
    }

    // ---- phase changes -------------------------------------------------------

    private void breach(Siege siege, Faction attacker, Faction defender) {
        long until = Time.now() + Math.max(1, ctx.settings().integer("siege.breachMinutes")) * 60_000L;
        synchronized (lock) {
            if (siege.phase() != Siege.Phase.CAPTURING) {
                return;
            }
            siege.setPhase(Siege.Phase.BREACHED, until);
            lastStatus.remove(siege.id());
        }
        applyBreach(siege, until);
        publishBreach(siege, true);
        String where = Keys.describe(siege.chunkIndex());
        FactionService fs = ctx.service(FactionService.class);
        fs.audit(attacker, null, "Server", "siege", "Breached " + defender.tag() + " at chunk " + where);
        fs.audit(defender, null, "Server", "siege", attacker.tag() + " breached the land at chunk " + where);
        phaseChanged(siege, attacker, defender);
    }

    private void applyBreach(Siege siege, long until) {
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        if (claims == null) {
            return;
        }
        for (long c : breachChunks(siege)) {
            claims.setBreached(siege.world(), c, siege.attacker(), until);
        }
    }

    /** The target chunk and its neighbours that the defender owns, which is the land a breach opens. */
    private List<Long> breachChunks(Siege siege) {
        List<Long> out = new ArrayList<>();
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        if (claims == null) {
            return out;
        }
        List<Long> chunks = new ArrayList<>();
        chunks.add(siege.chunkIndex());
        for (long n : Keys.neighbours(siege.chunkIndex())) {
            chunks.add(n);
        }
        for (long c : chunks) {
            Faction owner = claims.ownerAt(siege.world(), c);
            if (owner != null && owner.id().equals(siege.defender())) {
                out.add(c);
            }
        }
        return out;
    }

    /** Tells listeners such as the vault and the map that the breached chunks opened or closed. */
    private void publishBreach(Siege siege, boolean breached) {
        for (long c : breachChunks(siege)) {
            ctx.bus().publish(new DominionEvent.BreachChanged(siege.world(), c, siege.attacker(), siege.defender(), breached));
        }
    }

    private void defended(Siege siege, Faction attacker, Faction defender) {
        long hours = Math.max(0, ctx.settings().integer("siege.immunityHoursAfterDefence")) + immunityBonus(defender);
        long until = Time.now() + hours * 3_600_000L;
        if (until > defender.stat(IMMUNE_UNTIL)) {
            setStat(defender, IMMUNE_UNTIL, until);
        }
        defender.addStat("siegesDefended", 1);
        FactionService fs = ctx.service(FactionService.class);
        fs.audit(defender, null, "Server", "siege", "Held off the siege by " + attacker.tag());
        fs.audit(attacker, null, "Server", "siege", defender.tag() + " held off the siege");
        finish(siege, Siege.Phase.DEFENDED);
    }

    private void expire(Siege siege, Faction attacker, Faction defender) {
        synchronized (lock) {
            if (siege.phase() != Siege.Phase.BREACHED) {
                return;
            }
        }
        attacker.addStat("siegesWon", 1);
        defender.addStat("siegesLost", 1);
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress != null && siege.startedBy() != null) {
            try {
                progress.advance(siege.startedBy(), "siege", 1);
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Progress update for a siege failed");
            }
        }
        FactionService fs = ctx.service(FactionService.class);
        fs.audit(attacker, null, "Server", "siege", "Won the siege against " + defender.tag()
                + " with " + siege.blocksBroken() + " blocks broken and " + siege.containersOpened() + " containers opened");
        fs.audit(defender, null, "Server", "siege", "Lost the siege to " + attacker.tag());
        finish(siege, Siege.Phase.EXPIRED);
    }

    /** Ends a siege in a final phase, moves it to history, saves and tells both sides. */
    private void finish(Siege siege, Siege.Phase phase) {
        boolean wasBreached;
        synchronized (lock) {
            if (!siege.active()) {
                return;
            }
            wasBreached = siege.phase() == Siege.Phase.BREACHED;
            siege.setPhase(phase, Time.now());
            active.remove(siege);
            lastStatus.remove(siege.id());
            history.addFirst(siege);
            while (history.size() > HISTORY_LIMIT) {
                history.removeLast();
            }
        }
        if (wasBreached && phase != Siege.Phase.EXPIRED) {
            // An admin ended a breach early, so the land must close now rather than when the timer runs out.
            applyBreach(siege, 0L);
        }
        if (wasBreached) {
            publishBreach(siege, false);
        }
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        Faction attacker = fs == null ? null : fs.byId(siege.attacker());
        Faction defender = fs == null ? null : fs.byId(siege.defender());
        if (phase == Siege.Phase.FAILED && attacker != null) {
            attacker.addStat("siegesFailed", 1);
        }
        phaseChanged(siege, attacker, defender);
    }

    /** Saves, publishes and alerts both factions about the siege's current phase. */
    private void phaseChanged(Siege siege, Faction attacker, Faction defender) {
        synchronized (lock) {
            dirty = true;
        }
        save();
        ctx.bus().publish(new DominionEvent.SiegePhase(siege.id(), siege.phase().name(), siege.attacker(),
                siege.defender(), siege.world(), siege.chunkIndex()));
        String aTag = attacker != null ? attacker.tag() : "the attackers";
        String dTag = defender != null ? defender.tag() : "the defenders";
        String where = "chunk " + Keys.describe(siege.chunkIndex()) + " in " + siege.world();
        long left = siege.phaseEndsAt() - Time.now();
        Message toAttacker;
        Message toDefender;
        switch (siege.phase()) {
            case CAPTURING -> {
                toAttacker = Msg.warn("Your faction raised a siege banner against " + dTag + " at " + where
                        + ". Stay near the banner for " + Time.remaining(left) + " to breach their land.");
                toDefender = Msg.bad(aTag + " raised a siege banner against your land at " + where
                        + ". Stand by the banner to break the siege.");
            }
            case BREACHED -> {
                toAttacker = Msg.good("The land of " + dTag + " is breached at " + where + ". You can raid it for "
                        + Time.remaining(left) + ".");
                toDefender = Msg.bad(aTag + " breached your land at " + where + ". The breach lasts "
                        + Time.remaining(left) + ".");
            }
            case FAILED -> {
                toAttacker = Msg.bad("Your siege against " + dTag + " at " + where + " failed.");
                toDefender = Msg.good("The siege by " + aTag + " at " + where + " failed.");
            }
            case DEFENDED -> {
                long hours = Math.max(0, ctx.settings().integer("siege.immunityHoursAfterDefence")) + immunityBonus(defender);
                toAttacker = Msg.bad(dTag + " held off your siege at " + where + ".");
                toDefender = Msg.good("Your faction held off the siege by " + aTag + " at " + where
                        + (hours > 0 ? ". You are safe from sieges for " + plural(hours, "hour") + "." : "."));
            }
            default -> {
                toAttacker = Msg.info("The breach of " + dTag + " at " + where + " has ended. The siege is won.");
                toDefender = Msg.info("The breach by " + aTag + " at " + where + " has ended.");
            }
        }
        alert(attacker, toAttacker);
        alert(defender, toDefender);
    }

    // ---- combat hooks --------------------------------------------------------

    @Override
    public void onDeath(UUID player, String world, long chunkIndex) {
        Siege siege = siegeAt(world, chunkIndex, false);
        if (siege == null) {
            return;
        }
        Faction f = ctx.service(FactionService.class).factionOf(player);
        if (f == null) {
            return;
        }
        if (f.id().equals(siege.attacker())) {
            siege.addAttackerDeath();
        } else if (f.id().equals(siege.defender())) {
            siege.addDefenderDeath();
        } else {
            return;
        }
        markDirty();
    }

    @Override
    public void onBreachAction(UUID player, String world, long chunkIndex, boolean container) {
        Siege siege = siegeAt(world, chunkIndex, true);
        if (siege == null) {
            return;
        }
        Faction f = ctx.service(FactionService.class).factionOf(player);
        if (f == null || !f.id().equals(siege.attacker())) {
            return;
        }
        if (container) {
            siege.addContainersOpened(1);
        } else {
            siege.addBlocksBroken(1);
        }
        markDirty();
    }

    /** The active siege whose target chunk, its neighbours or banner chunk contain the location. */
    private Siege siegeAt(String world, long chunkIndex, boolean breachedOnly) {
        if (world == null) {
            return null;
        }
        synchronized (lock) {
            for (Siege s : active) {
                if (!world.equals(s.world()) || (breachedOnly && s.phase() != Siege.Phase.BREACHED)) {
                    continue;
                }
                if (s.chunkIndex() == chunkIndex || Keys.adjacent(s.chunkIndex(), chunkIndex)
                        || Keys.chunkAt(s.x(), s.z()) == chunkIndex) {
                    return s;
                }
            }
        }
        return null;
    }

    // ---- helpers -------------------------------------------------------------

    private void markDirty() {
        synchronized (lock) {
            dirty = true;
        }
    }

    /** One extra hour of immunity for each level of the immunity upgrade. */
    private int immunityBonus(Faction defender) {
        if (defender == null) {
            return 0;
        }
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        return Math.max(0, progress != null ? progress.upgradeLevel(defender, "immunity") : defender.upgrade("immunity"));
    }

    private static boolean involves(Siege s, UUID faction) {
        return s.attacker().equals(faction) || s.defender().equals(faction);
    }

    /** Start time of the newest siege by this attacker on this defender, or zero. Caller holds the lock. */
    private long lastSiegeAt(UUID attacker, UUID defender) {
        long last = 0;
        for (Siege s : active) {
            if (s.attacker().equals(attacker) && s.defender().equals(defender)) {
                last = Math.max(last, s.startedAt());
            }
        }
        for (Siege s : history) {
            if (s.attacker().equals(attacker) && s.defender().equals(defender)) {
                last = Math.max(last, s.startedAt());
            }
        }
        return last;
    }

    private boolean graceActive(ShieldService shields, Settings s) {
        if (shields != null) {
            return shields.graceActive();
        }
        if (!s.bool("grace.active")) {
            return false;
        }
        long ends = s.longValue("grace.endsAt");
        return ends <= 0 || Time.now() < ends;
    }

    private static int onlineMembers(Faction f) {
        int n = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                n++;
            }
        }
        return n;
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

    private static void setStat(Faction f, String key, long value) {
        long current = f.stat(key);
        if (current != value) {
            f.addStat(key, value - current);
        }
    }

    private static String plural(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private String nameOf(UUID uuid) {
        PlayerRef ref = Players.online(uuid);
        return ref != null ? ref.getUsername() : ctx.player(uuid).name();
    }
}
