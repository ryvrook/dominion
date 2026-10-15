package dev.ryvrook.dominion.home;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.CombatTagService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.HomeService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Teleports;
import dev.ryvrook.dominion.util.Time;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Teleports wait out a warmup that the one second tick advances. Moving more than one block,
 * taking damage or becoming combat tagged cancels them, see {@link Warmups}. The teleport itself attaches the Teleport
 * component on the world thread that owns the player entity.
 */
public final class HomeServiceImpl implements HomeService {
    private static final Pattern WARP_NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    private static final int STUCK_SEARCH_RADIUS = 32;

    /** What a warmup teleports to once it ends. The target is resolved then, not when it starts. */
    private record Target(String label, Supplier<Location> target, boolean homeCooldown) {}

    private final DominionContext ctx;
    private final Warmups<Target> warmups = new Warmups<>();

    public HomeServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    private FactionService factions() { return ctx.service(FactionService.class); }
    private ClaimService claims() { return ctx.service(ClaimService.class); }
    private RelationService relations() { return ctx.service(RelationService.class); }

    // ---- home ----------------------------------------------------------------

    @Override
    public Result setHome(UUID actor) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(actor);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(actor, FactionAction.SET_HOME)) {
            return Result.fail(Messages.get(HomeText.NO_PERMISSION_SET_HOME));
        }
        Location here = currentLocation(actor);
        if (here == null) {
            return Result.fail(Messages.get(HomeText.POSITION_UNKNOWN));
        }
        if (!ownedBy(faction, here)) {
            return Result.fail(Messages.get(HomeText.HOME_OUTSIDE_LAND));
        }
        faction.setHome(here);
        fs.audit(faction, actor, nameOf(actor), "home", Messages.get(HomeText.AUDIT_HOME_SET, "coords", coords(here)));
        ctx.bus().publish(new DominionEvent.FactionChanged(faction.id()));
        return Result.pass(Messages.get(HomeText.HOME_SET, "coords", coords(here)));
    }

    @Override
    public Result delHome(UUID actor) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(actor);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(actor, FactionAction.SET_HOME)) {
            return Result.fail(Messages.get(HomeText.NO_PERMISSION_CHANGE_HOME));
        }
        if (faction.home() == null) {
            return Result.fail(Messages.get(HomeText.NO_HOME_SET));
        }
        faction.setHome(null);
        fs.audit(faction, actor, nameOf(actor), "home", Messages.get(HomeText.AUDIT_HOME_REMOVED));
        ctx.bus().publish(new DominionEvent.FactionChanged(faction.id()));
        return Result.pass(Messages.get(HomeText.HOME_REMOVED));
    }

    @Override
    public Result home(UUID player) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(player);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (faction.home() == null) {
            return Result.fail(Messages.get(HomeText.NO_HOME));
        }
        if (!fs.can(player, FactionAction.USE_HOME)) {
            return Result.fail(Messages.get(HomeText.NO_PERMISSION_USE_HOME));
        }
        Result allowed = teleportAllowed(player);
        if (allowed.failed()) {
            return allowed;
        }
        UUID factionId = faction.id();
        return begin(player, Messages.get(HomeText.LABEL_HOME), () -> {
            Faction f = factions().byId(factionId);
            return f == null ? null : f.home();
        }, ctx.settings().integer("home.warmupSeconds"), true);
    }

    // ---- warps ---------------------------------------------------------------

    private int maxWarps(Faction faction) {
        return Math.max(0, ctx.settings().integer("home.maxWarps") + 2 * faction.upgrade("warps"));
    }

    @Override
    public Result setWarp(UUID actor, String name, String password) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(actor);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(actor, FactionAction.SET_WARP)) {
            return Result.fail(Messages.get(HomeText.NO_PERMISSION_CREATE_WARP));
        }
        if (name == null || !WARP_NAME.matcher(name).matches()) {
            return Result.fail(Messages.get(HomeText.WARP_NAME_RULES));
        }
        boolean replacing = faction.warp(name) != null;
        int max = maxWarps(faction);
        if (!replacing && faction.warps().size() >= max) {
            return Result.fail(Messages.get(HomeText.WARP_LIMIT, "max", max));
        }
        Location here = currentLocation(actor);
        if (here == null) {
            return Result.fail(Messages.get(HomeText.POSITION_UNKNOWN));
        }
        if (!ownedBy(faction, here)) {
            return Result.fail(Messages.get(HomeText.WARP_OUTSIDE_LAND));
        }
        String pass = password == null || password.isBlank() ? null : password;
        double cost = ctx.settings().decimal("eco.costWarp");
        if (cost > 0) {
            EconomyService eco = ctx.serviceOrNull(EconomyService.class);
            if (eco != null && eco.enabled()) {
                Result paid = eco.bankCharge(faction, cost, Messages.get(HomeText.BANK_REASON_WARP, "name", name.toLowerCase()));
                if (paid.failed()) {
                    return Result.fail(Messages.get(HomeText.WARP_COST, "cost", eco.format(cost), "reason", paid.message()));
                }
            }
        }
        faction.setWarp(name, new Faction.Warp(here, pass));
        String auditId = replacing
                ? (pass == null ? HomeText.AUDIT_WARP_MOVED : HomeText.AUDIT_WARP_MOVED_LOCKED)
                : (pass == null ? HomeText.AUDIT_WARP_CREATED : HomeText.AUDIT_WARP_CREATED_LOCKED);
        fs.audit(faction, actor, nameOf(actor), "warp", Messages.get(auditId, "name", name.toLowerCase()));
        ctx.bus().publish(new DominionEvent.FactionChanged(faction.id()));
        String doneId = replacing
                ? (pass == null ? HomeText.WARP_MOVED : HomeText.WARP_MOVED_LOCKED)
                : (pass == null ? HomeText.WARP_CREATED : HomeText.WARP_CREATED_LOCKED);
        return Result.pass(Messages.get(doneId, "name", name.toLowerCase()));
    }

    @Override
    public Result delWarp(UUID actor, String name) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(actor);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(actor, FactionAction.SET_WARP)) {
            return Result.fail(Messages.get(HomeText.NO_PERMISSION_DELETE_WARP));
        }
        if (name == null || !faction.removeWarp(name)) {
            return Result.fail(Messages.get(HomeText.NO_SUCH_WARP, "name", name));
        }
        fs.audit(faction, actor, nameOf(actor), "warp", Messages.get(HomeText.AUDIT_WARP_DELETED, "name", name.toLowerCase()));
        ctx.bus().publish(new DominionEvent.FactionChanged(faction.id()));
        return Result.pass(Messages.get(HomeText.WARP_DELETED, "name", name.toLowerCase()));
    }

    @Override
    public Result warp(UUID player, String name, String password) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(player);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(player, FactionAction.USE_WARP)) {
            return Result.fail(Messages.get(HomeText.NO_PERMISSION_USE_WARP));
        }
        Faction.Warp warp = name == null ? null : faction.warp(name);
        if (warp == null || warp.location() == null) {
            return Result.fail(Messages.get(HomeText.NO_SUCH_WARP_HINT, "name", name));
        }
        if (warp.password() != null && !warp.password().isBlank() && !warp.password().equals(password)) {
            return Result.fail(Messages.get(password == null ? HomeText.WARP_LOCKED
                    : HomeText.WARP_WRONG_PASSWORD));
        }
        Result allowed = teleportAllowed(player);
        if (allowed.failed()) {
            return allowed;
        }
        UUID factionId = faction.id();
        String key = name.toLowerCase();
        String given = password;
        return begin(player, Messages.get(HomeText.LABEL_WARP, "name", key), () -> {
            Faction f = factions().byId(factionId);
            Faction.Warp w = f == null ? null : f.warp(key);
            // A password set or changed during the warmup applies to this teleport too.
            if (w == null || (w.password() != null && !w.password().isBlank() && !w.password().equals(given))) {
                return null;
            }
            return w.location();
        }, ctx.settings().integer("home.warmupSeconds"), true);
    }

    // ---- stuck ---------------------------------------------------------------

    @Override
    public Result stuck(UUID player) {
        PlayerRef ref = Players.online(player);
        String world = Players.worldName(ref);
        long chunk = Players.chunkOf(ref);
        if (ref == null || world == null || chunk == Long.MIN_VALUE) {
            return Result.fail(Messages.get(HomeText.POSITION_UNKNOWN));
        }
        if (inCombat(player)) {
            return Result.fail(Messages.get(HomeText.STUCK_IN_COMBAT, "time", Time.remaining(combatLeft(player))));
        }
        if (claims().ownerAt(world, chunk) == null) {
            return Result.fail(Messages.get(HomeText.ALREADY_WILDERNESS));
        }
        if (nearestWilderness(world, chunk) == Long.MIN_VALUE) {
            return Result.fail(Messages.get(HomeText.NO_WILDERNESS_NEAR));
        }
        return begin(player, Messages.get(HomeText.LABEL_WILDERNESS), () -> {
            PlayerRef now = Players.online(player);
            Vector3d pos = Players.position(now);
            String w = Players.worldName(now);
            if (pos == null || w == null) {
                return null;
            }
            long target = nearestWilderness(w, Keys.chunkAt(pos.x, pos.z));
            if (target == Long.MIN_VALUE) {
                return null;
            }
            double half = Keys.chunkSize() / 2.0;
            double x = Keys.minBlock(Keys.chunkX(target)) + half;
            double z = Keys.minBlock(Keys.chunkZ(target)) + half;
            Rotation3f rot = now.getTransform() == null ? null : now.getTransform().getRotation();
            return new Location(w, x, pos.y, z, rot == null ? 0f : rot.yaw(), rot == null ? 0f : rot.pitch());
        }, ctx.settings().integer("home.stuckWarmupSeconds"), false);
    }

    /** Searches rings of growing radius and returns the closest unclaimed chunk, or MIN_VALUE. */
    private long nearestWilderness(String world, long center) {
        ClaimService cs = claims();
        int cx = Keys.chunkX(center);
        int cz = Keys.chunkZ(center);
        for (int r = 1; r <= STUCK_SEARCH_RADIUS; r++) {
            long best = Long.MIN_VALUE;
            int bestDist = Integer.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    long idx = Keys.chunk(cx + dx, cz + dz);
                    if (cs.ownerAt(world, idx) != null) {
                        continue;
                    }
                    int dist = dx * dx + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = idx;
                    }
                }
            }
            if (best != Long.MIN_VALUE) {
                return best;
            }
        }
        return Long.MIN_VALUE;
    }

    // ---- warmups -------------------------------------------------------------

    /**
     * The shared combat tag, which also covers CombatLog when it is installed. Falls back to
     * Dominion's own tag when the combat module is not running.
     */
    private boolean inCombat(UUID player) {
        CombatTagService tags = ctx.serviceOrNull(CombatTagService.class);
        return tags != null ? tags.tagged(player) : ctx.player(player).combatTagged(Time.now());
    }

    private long combatLeft(UUID player) {
        CombatTagService tags = ctx.serviceOrNull(CombatTagService.class);
        return tags != null ? tags.remainingMillis(player) : Math.max(0L, ctx.player(player).combatTagUntil() - Time.now());
    }

    /** Combat, cooldown, enemy territory and nearby enemy checks shared by home and warps. */
    private Result teleportAllowed(UUID player) {
        PlayerRef ref = Players.online(player);
        Vector3d pos = Players.position(ref);
        String world = Players.worldName(ref);
        if (ref == null || pos == null || world == null) {
            return Result.fail(Messages.get(HomeText.POSITION_UNKNOWN));
        }
        PlayerData pd = ctx.player(player);
        long now = Time.now();
        if (inCombat(player)) {
            return Result.fail(Messages.get(HomeText.TELEPORT_IN_COMBAT, "time", Time.remaining(combatLeft(player))));
        }
        long cooldown = ctx.settings().integer("home.cooldownSeconds") * 1000L;
        long since = now - pd.lastHomeTeleport();
        if (pd.lastHomeTeleport() > 0 && since < cooldown) {
            return Result.fail(Messages.get(HomeText.TELEPORT_COOLDOWN, "time", Time.remaining(cooldown - since)));
        }
        Faction owner = claims().ownerAt(world, Keys.chunkAt(pos.x, pos.z));
        RelationService rs = relations();
        if (owner != null && !owner.isSystem() && rs.playerToFaction(player, owner) == Relation.ENEMY) {
            return Result.fail(Messages.get(HomeText.TELEPORT_ENEMY_LAND));
        }
        int radius = ctx.settings().integer("home.enemyRadius");
        if (radius > 0) {
            World w = Players.world(ref);
            if (w != null) {
                for (PlayerRef other : w.getPlayerRefs()) {
                    if (other.getUuid().equals(player)) {
                        continue;
                    }
                    Vector3d op = Players.position(other);
                    if (op != null && op.distance(pos) <= radius && rs.betweenPlayers(player, other.getUuid()) == Relation.ENEMY) {
                        return Result.fail(Messages.get(HomeText.TELEPORT_ENEMY_NEAR, "radius", radius));
                    }
                }
            }
        }
        return Result.pass();
    }

    private Result begin(UUID player, String label, Supplier<Location> target, int warmup, boolean homeCooldown) {
        PlayerRef ref = Players.online(player);
        Warmups.Pos pos = where(ref);
        if (ref == null || pos == null) {
            return Result.fail(Messages.get(HomeText.POSITION_UNKNOWN));
        }
        Target t = new Target(label, target, homeCooldown);
        int seconds = Math.max(0, warmup);
        if (seconds <= 0) {
            warmups.cancel(player);
            return complete(player, ref, t);
        }
        warmups.start(player, t, pos, seconds);
        return Result.pass(Messages.get(seconds == 1 ? HomeText.TELEPORTING_SECOND : HomeText.TELEPORTING_SECONDS,
                "destination", label, "seconds", seconds));
    }

    private static Warmups.Pos where(PlayerRef ref) {
        Vector3d pos = Players.position(ref);
        String world = Players.worldName(ref);
        return pos == null || world == null ? null : new Warmups.Pos(world, pos.x, pos.y, pos.z);
    }

    @Override
    public void tick() {
        if (warmups.isEmpty()) {
            return;
        }
        List<Warmups.Step<Target>> steps;
        try {
            steps = warmups.tick(id -> where(Players.online(id)), this::inCombat);
        } catch (RuntimeException ex) {
            ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion teleport warmup failed");
            return;
        }
        for (Warmups.Step<Target> step : steps) {
            try {
                PlayerRef ref = Players.online(step.player());
                if (ref == null) {
                    continue;
                }
                switch (step.kind()) {
                    case MOVED -> ref.sendMessage(Msg.bad(Messages.get(HomeText.CANCELLED_MOVED)));
                    case COMBAT -> ref.sendMessage(Msg.bad(Messages.get(HomeText.CANCELLED_COMBAT)));
                    case COUNTDOWN -> ref.sendMessage(Msg.info(Messages.get(HomeText.COUNTDOWN, "seconds", step.secondsLeft())));
                    case DONE -> {
                        Result r = complete(step.player(), ref, step.payload());
                        ref.sendMessage(r.success() ? Msg.good(r.message()) : Msg.bad(r.message()));
                    }
                    default -> {
                        // offline, nothing to tell
                    }
                }
            } catch (RuntimeException ex) {
                ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion teleport warmup failed");
            }
        }
    }

    @Override
    public void onDamaged(UUID player) {
        if (warmups.cancel(player)) {
            Msg.send(Players.online(player), Msg.bad(Messages.get(HomeText.CANCELLED_DAMAGE)));
        }
    }

    /** Cheap check so the damage system can skip the lookup when nobody is warming up. */
    boolean hasPending() {
        return !warmups.isEmpty();
    }

    void cancel(UUID player) {
        warmups.cancel(player);
    }

    private Result complete(UUID uuid, PlayerRef ref, Target t) {
        Location loc = t.target().get();
        if (loc == null) {
            return Result.fail(Messages.get(HomeText.DESTINATION_GONE));
        }
        if (!teleport(ref, loc)) {
            return Result.fail(Messages.get(HomeText.DESTINATION_WORLD_UNLOADED));
        }
        // The cooldown starts only when a teleport actually happens.
        if (t.homeCooldown()) {
            ctx.player(uuid).setLastHomeTeleport(Time.now());
        }
        return Result.pass(Messages.get(HomeText.TELEPORTED, "destination", t.label()));
    }

    /** Uses the shared teleport path, see {@link Teleports}. */
    private boolean teleport(PlayerRef player, Location loc) {
        return Teleports.teleport(player, loc);
    }

    // ---- land loss -----------------------------------------------------------

    void onEvent(DominionEvent event) {
        if (!(event instanceof DominionEvent.ClaimChanged change) || change.oldFaction() == null
                || change.oldFaction().equals(change.newFaction())) {
            return;
        }
        Faction faction = factions().byId(change.oldFaction());
        if (faction == null) {
            return;
        }
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        Location home = faction.home();
        if (inChunk(home, change.world(), change.chunkIndex())) {
            faction.setHome(null);
            factions().audit(faction, null, Messages.get(CommonText.SERVER), "home", Messages.get(HomeText.AUDIT_HOME_LOST));
            if (chat != null) {
                chat.alert(faction, Msg.warn(Messages.get(HomeText.HOME_LOST)));
            }
        }
        for (Map.Entry<String, Faction.Warp> e : faction.warps().entrySet()) {
            if (inChunk(e.getValue().location(), change.world(), change.chunkIndex())) {
                faction.removeWarp(e.getKey());
                factions().audit(faction, null, Messages.get(CommonText.SERVER), "warp",
                        Messages.get(HomeText.AUDIT_WARP_LOST, "name", e.getKey()));
                if (chat != null) {
                    chat.alert(faction, Msg.warn(Messages.get(HomeText.WARP_LOST, "name", e.getKey())));
                }
            }
        }
    }

    private static boolean inChunk(Location loc, String world, long chunk) {
        return loc != null && loc.world() != null && loc.world().equals(world) && loc.chunkIndex() == chunk;
    }

    // ---- helpers -------------------------------------------------------------

    private Location currentLocation(UUID uuid) {
        PlayerRef ref = Players.online(uuid);
        Vector3d pos = Players.position(ref);
        String world = Players.worldName(ref);
        if (pos == null || world == null) {
            return null;
        }
        Rotation3f rot = ref.getTransform() == null ? null : ref.getTransform().getRotation();
        return new Location(world, pos.x, pos.y, pos.z, rot == null ? 0f : rot.yaw(), rot == null ? 0f : rot.pitch());
    }

    private boolean ownedBy(Faction faction, Location loc) {
        Faction owner = claims().ownerAt(loc.world(), loc.chunkIndex());
        return owner != null && owner.id().equals(faction.id());
    }

    private String nameOf(UUID uuid) {
        PlayerRef ref = Players.online(uuid);
        return ref != null ? ref.getUsername() : ctx.player(uuid).name();
    }

    private static String coords(Location loc) {
        return (int) Math.floor(loc.x()) + ", " + (int) Math.floor(loc.y()) + ", " + (int) Math.floor(loc.z());
    }

    // ---- ally home and respawn ----------------------------------------------

    @Override
    public Result allyHome(UUID player, String allyTag) {
        FactionService fs = factions();
        Faction own = fs.factionOf(player);
        if (own == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        Faction ally = allyTag == null ? null : fs.byTag(allyTag);
        if (ally == null || ally.isSystem()) {
            return Result.fail(Messages.get(HomeText.NO_FACTION_CALLED, "tag", allyTag));
        }
        String denied = allyHomeDenied(own, ally);
        if (denied != null) {
            return Result.fail(denied);
        }
        Result allowed = teleportAllowed(player);
        if (allowed.failed()) {
            return allowed;
        }
        UUID ownId = own.id();
        UUID allyId = ally.id();
        return begin(player, Messages.get(HomeText.LABEL_ALLY_HOME, "faction", ally.tag()), () -> {
            Faction o = factions().byId(ownId);
            Faction a = factions().byId(allyId);
            if (o == null || a == null || allyHomeDenied(o, a) != null) {
                return null;
            }
            return a.home();
        }, ctx.settings().integer("home.warmupSeconds"), true);
    }

    /** Reason the members of own may not use the ally's home, or null when they may. */
    String allyHomeDenied(Faction own, Faction ally) {
        if (own.id().equals(ally.id())) {
            return Messages.get(HomeText.ALLY_HOME_OWN);
        }
        if (relations().between(own, ally) != Relation.ALLY) {
            return Messages.get(HomeText.ALLY_HOME_NOT_ALLY, "faction", ally.tag());
        }
        if (!ally.setting("allyHome", false) || !ally.relationCan(Relation.ALLY, FactionAction.USE_HOME)) {
            return Messages.get(HomeText.ALLY_HOME_CLOSED, "faction", ally.tag());
        }
        if (ally.home() == null) {
            return Messages.get(HomeText.ALLY_HOME_NONE, "faction", ally.tag());
        }
        return null;
    }

    @Override
    public Location respawnLocation(UUID player) {
        Faction faction = factions().factionOf(player);
        if (faction == null || faction.isSystem()) {
            return null;
        }
        boolean on = faction.setting("homeOnDeath", ctx.settings().bool("home.respawnAtHomeDefault"));
        return on ? faction.home() : null;
    }
}
