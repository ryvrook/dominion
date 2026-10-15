package dev.ryvrook.dominion.land;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.storage.Repository;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Claim service. Ownership lives in the repository. Every ownership change publishes
 * ClaimChanged and writes an audit line. Claim and unclaim calls are serialised on one lock so
 * contiguity, buffer and power checks never race each other.
 */
public final class ClaimServiceImpl implements ClaimService {
    private final DominionContext ctx;
    private final Land land;
    private final Raids raids;
    private final Territory territory;
    private final LandMap maps;
    private final Decay decay;
    private final SpawnGuard spawn;
    private final Object lock = new Object();
    /**
     * Reclaiming a chunk the faction lost within this window earns no claim mission credit, so
     * unclaiming and reclaiming, or two factions trading a chunk back and forth, cannot farm it.
     * Weekly missions are the longest window a claim counter can feed. The marks are stored on the
     * faction so a restart keeps them. This is the default, land.reclaimWindowDays sets the window.
     */
    static final long MISSION_WINDOW_MILLIS = 7 * 86_400_000L;

    ClaimServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.land = new Land(ctx);
        this.raids = new Raids(land, this);
        this.territory = new Territory(land, this);
        this.maps = new LandMap(land, this);
        this.decay = new Decay(land, this);
        this.spawn = new SpawnGuard(land);
    }

    /** The shared service helpers. Package tests use it to stand in for online players. */
    Land land() {
        return land;
    }

    private Repository repo() {
        return ctx.repo();
    }

    private Settings settings() {
        return ctx.settings();
    }

    // ---- lookups ----------------------------------------------------------

    @Override
    public Faction ownerAt(String world, long chunkIndex) {
        return world == null ? null : repo().ownerAt(world, chunkIndex);
    }

    @Override
    public ClaimEntry claimAt(String world, long chunkIndex) {
        return world == null ? null : repo().claimAt(world, chunkIndex);
    }

    @Override
    public int claimCount(Faction faction) {
        return faction == null ? 0 : repo().claimCount(faction.id());
    }

    @Override
    public int maxClaims(Faction faction) {
        if (faction == null) {
            return 0;
        }
        if (faction.isSystem()) {
            return Integer.MAX_VALUE;
        }
        double perPower = settings().decimal("land.claimsPerPower");
        // In the deaths pool mode faction power is the pool, which is at most 10 and drops with every
        // death. Claims instead follow the roster at full player power so deaths only affect raidability.
        double power = settings().bool("power.dtrMode")
                ? settings().decimal("power.max") * Math.max(1, faction.memberCount()) + faction.powerBoost()
                : land.power(faction);
        long base = (long) Math.floor(Math.max(0, power) * perPower);
        long total = base + faction.stat("bonusClaims");
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
    }

    /** Returns {chunkIndex, chunkX, chunkZ} for the online player, or null. */
    @Override
    public long[] playerChunk(UUID player) {
        long idx = Players.chunkOf(Land.liveRef(player));
        if (idx == Long.MIN_VALUE) {
            return null;
        }
        return new long[] { idx, Keys.chunkX(idx), Keys.chunkZ(idx) };
    }

    @Override
    public String playerWorld(UUID player) {
        return Players.worldName(Land.liveRef(player));
    }

    // ---- claiming ---------------------------------------------------------

    @Override
    public Result claim(UUID actor, String world, long chunkIndex) {
        synchronized (lock) {
            return claimChecked(actor, world, chunkIndex);
        }
    }

    private Result claimChecked(UUID actor, String world, long chunk) {
        if (world == null) {
            return Result.fail(Messages.get(CommonText.POSITION_UNKNOWN));
        }
        Faction f = land.factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(LandText.CLAIM_NEED_FACTION));
        }
        if (!land.can(actor, FactionAction.CLAIM, f)) {
            return Result.fail(Messages.get(LandText.CLAIM_RANK));
        }
        if (settings().bool("combat.blockClaimsWhileTagged") && land.tagged(actor)) {
            return Result.fail(Messages.get(LandText.CLAIM_IN_COMBAT));
        }
        if (!worldAllowed(world)) {
            return Result.fail(Messages.get(LandText.CLAIM_WORLD_BLOCKED));
        }
        if (spawn.near(world, chunk, actor)) {
            return Result.fail(Messages.get(LandText.CLAIM_NEAR_SPAWN));
        }
        Faction current = ownerAt(world, chunk);
        if (current != null && current.isSystem()) {
            return Result.fail(Messages.get(LandText.CLAIM_SERVER_LAND));
        }
        if (current != null && current.id().equals(f.id())) {
            return Result.fail(Messages.get(LandText.CLAIM_ALREADY_OWN));
        }
        if (settings().bool("land.mustBeConnected") && !touches(world, chunk, f.id())
                && repo().claimsOf(world, f.id()).length > 0) {
            return Result.fail(Messages.get(LandText.CLAIM_NOT_CONNECTED));
        }
        Faction blocker = bufferBlocker(world, chunk, f, current);
        if (blocker != null) {
            return Result.fail(Messages.get(LandText.CLAIM_BUFFER, "faction", blocker.tag()));
        }
        int count = claimCount(f);
        int max = maxClaims(f);
        if (count >= max) {
            return Result.fail(Messages.get(LandText.CLAIM_NO_POWER, "count", count, "max", max));
        }
        EconomyService eco = land.economy();
        double cost = eco == null || f.isSystem() ? 0 : claimCost(count);
        if (cost > 0 && f.bank() + 1e-6 < cost) {
            return Result.fail(Messages.get(LandText.CLAIM_TOO_EXPENSIVE, "cost", eco.format(cost), "bank", eco.format(f.bank())));
        }
        Raids.Verdict verdict = null;
        if (current != null) {
            if (land.between(f, current) != Relation.ENEMY) {
                return Result.fail(Messages.get(LandText.CLAIM_NOT_ENEMY, "faction", current.tag()));
            }
            if (!onEdgeOf(world, chunk, current.id())) {
                return Result.fail(Messages.get(LandText.CLAIM_EDGE_ONLY, "faction", current.tag()));
            }
            verdict = raids.check(f, current, world, chunk, false);
            if (verdict.result().failed()) {
                return verdict.result();
            }
        }
        if (cost > 0) {
            Result paid = eco.bankCharge(f, cost, Messages.get(LandText.BANK_CLAIM_REASON, "name", land.nameOf(actor)));
            if (paid.failed()) {
                return Result.fail(paid.message() + ".");
            }
        }
        repo().putClaim(world, chunk, new ClaimEntry(f.id(), Time.now(), actor, null));
        if (verdict != null && verdict.dominance()) {
            raids.markDominance(f, current);
        }
        String where = Keys.describe(chunk);
        changed(world, chunk, current, f, actor, newLand(f, world, chunk));
        land.audit(f, actor, current == null ? Messages.get(LandText.AUDIT_CLAIMED, "chunk", where, "world", world)
                : Messages.get(LandText.AUDIT_TOOK, "chunk", where, "world", world, "faction", current.tag()));
        if (newLand(f, world, chunk)) {
            land.progress(actor, "claim");
        }
        String paidText = cost > 0 ? " " + Messages.get(LandText.BANK_PAID, "amount", eco.format(cost)) : "";
        if (current != null) {
            noteReleased(current, world, chunk);
            land.audit(current, actor, Messages.get(LandText.AUDIT_TAKEN_BY, "faction", f.tag(), "chunk", where, "world", world));
            clearHomeIfIn(current, world, chunk);
            return Result.pass(Messages.get(LandText.TOOK, "chunk", where, "faction", current.tag()) + paidText);
        }
        return Result.pass(Messages.get(LandText.CLAIMED, "chunk", where, "faction", f.tag()) + paidText);
    }

    @Override
    public Result claimRadius(UUID actor, String world, long centerIndex, int radius) {
        int max = settings().integer("land.maxRadiusClaim");
        if (radius < 0) {
            return Result.fail(Messages.get(LandText.RADIUS_NEGATIVE));
        }
        if (radius > max) {
            return Result.fail(Messages.get(LandText.CLAIM_RADIUS_MAX, "max", max));
        }
        return claimMany(actor, world, square(centerIndex, radius));
    }

    @Override
    public Result claimLine(UUID actor, String world, long startIndex, int dx, int dz, int length) {
        int max = settings().integer("land.maxLineClaim");
        if (length < 1) {
            return Result.fail(Messages.get(LandText.LINE_EMPTY));
        }
        if (length > max) {
            return Result.fail(Messages.get(LandText.LINE_MAX, "max", max));
        }
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        if (sx == 0 && sz == 0) {
            return Result.fail(Messages.get(LandText.LINE_DIRECTION));
        }
        int x = Keys.chunkX(startIndex);
        int z = Keys.chunkZ(startIndex);
        List<Long> chunks = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            chunks.add(Keys.chunk(x + sx * i, z + sz * i));
        }
        return claimMany(actor, world, chunks);
    }

    /** Claims as many chunks as the rules allow, retrying until no further chunk succeeds. */
    private Result claimMany(UUID actor, String world, List<Long> chunks) {
        synchronized (lock) {
            Faction f = land.factionOf(actor);
            List<Long> pending = new ArrayList<>();
            for (long c : chunks) {
                Faction o = ownerAt(world, c);
                if (f == null || o == null || !o.id().equals(f.id())) {
                    pending.add(c);
                }
            }
            int total = pending.size();
            if (total == 0) {
                return Result.fail(Messages.get(LandText.AREA_ALREADY_OWN));
            }
            int claimed = 0;
            String reason = null;
            boolean progress = true;
            while (progress && !pending.isEmpty()) {
                progress = false;
                reason = null;
                for (int i = 0; i < pending.size(); i++) {
                    Result r = claimChecked(actor, world, pending.get(i));
                    if (r.success()) {
                        pending.remove(i--);
                        claimed++;
                        progress = true;
                    } else if (reason == null) {
                        reason = r.message();
                    }
                }
            }
            if (claimed == 0) {
                return Result.fail(reason == null ? Messages.get(LandText.AREA_NONE_CLAIMED) : reason);
            }
            String msg = Messages.get(LandText.AREA_CLAIMED, "claimed", claimed, "total", total);
            return Result.pass(reason == null ? msg : msg + " " + reason);
        }
    }

    // ---- unclaiming -------------------------------------------------------

    @Override
    public Result unclaim(UUID actor, String world, long chunkIndex) {
        synchronized (lock) {
            Faction f = land.factionOf(actor);
            Result denied = mayUnclaim(actor, f);
            if (denied != null) {
                return denied;
            }
            Faction owner = ownerAt(world, chunkIndex);
            if (owner == null || !owner.id().equals(f.id())) {
                return Result.fail(Messages.get(LandText.UNCLAIM_NOT_OWN));
            }
            double refund = release(actor, f, world, chunkIndex);
            return Result.pass(Messages.get(LandText.RELEASED, "chunk", Keys.describe(chunkIndex)) + refundText(refund));
        }
    }

    @Override
    public Result unclaimRadius(UUID actor, String world, long centerIndex, int radius) {
        int max = settings().integer("land.maxRadiusClaim");
        if (radius < 0) {
            return Result.fail(Messages.get(LandText.RADIUS_NEGATIVE));
        }
        if (radius > max) {
            return Result.fail(Messages.get(LandText.UNCLAIM_RADIUS_MAX, "max", max));
        }
        synchronized (lock) {
            Faction f = land.factionOf(actor);
            Result denied = mayUnclaim(actor, f);
            if (denied != null) {
                return denied;
            }
            int n = 0;
            double refund = 0;
            for (long c : square(centerIndex, radius)) {
                Faction owner = ownerAt(world, c);
                if (owner != null && owner.id().equals(f.id())) {
                    refund += release(actor, f, world, c);
                    n++;
                }
            }
            if (n == 0) {
                return Result.fail(Messages.get(LandText.UNCLAIM_AREA_NONE));
            }
            return Result.pass(Messages.get(LandText.RELEASED_MANY, "count", n) + refundText(refund));
        }
    }

    @Override
    public Result unclaimAll(UUID actor, boolean confirmed) {
        synchronized (lock) {
            Faction f = land.factionOf(actor);
            if (f == null) {
                return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
            }
            if (!land.can(actor, FactionAction.DISBAND, f)) {
                return Result.fail(Messages.get(LandText.UNCLAIM_ALL_RANK));
            }
            if (settings().bool("combat.blockClaimsWhileTagged") && land.tagged(actor)) {
                return Result.fail(Messages.get(LandText.UNCLAIM_IN_COMBAT));
            }
            int count = claimCount(f);
            if (count == 0) {
                return Result.fail(Messages.get(LandText.UNCLAIM_NO_LAND));
            }
            if (!confirmed) {
                return Result.fail(Messages.get(LandText.UNCLAIM_ALL_CONFIRM, "count", count));
            }
            int n = 0;
            double refund = 0;
            for (String world : new ArrayList<>(repo().claimWorlds())) {
                for (long c : repo().claimsOf(world, f.id())) {
                    refund += release(actor, f, world, c);
                    n++;
                }
            }
            return Result.pass(Messages.get(LandText.RELEASED_ALL, "count", n) + refundText(refund));
        }
    }

    private Result mayUnclaim(UUID actor, Faction f) {
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!land.can(actor, FactionAction.UNCLAIM, f)) {
            return Result.fail(Messages.get(LandText.UNCLAIM_RANK));
        }
        if (settings().bool("combat.blockClaimsWhileTagged") && land.tagged(actor)) {
            return Result.fail(Messages.get(LandText.UNCLAIM_IN_COMBAT));
        }
        return null;
    }

    private String refundText(double refund) {
        EconomyService eco = land.economy();
        return refund > 0 && eco != null ? " " + Messages.get(LandText.BANK_REFUND, "amount", eco.format(refund)) : "";
    }

    /** Removes an owned chunk, refunds the faction bank and returns the refund paid. */
    private double release(UUID actor, Faction f, String world, long chunk) {
        repo().removeClaim(world, chunk);
        boolean counts = newLand(f, world, chunk);
        noteReleased(f, world, chunk);
        clearHomeIfIn(f, world, chunk);
        changed(world, chunk, f, null, actor, counts);
        land.audit(f, actor, Messages.get(LandText.AUDIT_RELEASED, "chunk", Keys.describe(chunk), "world", world));
        EconomyService eco = land.economy();
        if (eco == null) {
            return 0;
        }
        double refund = Math.floor(claimCost(claimCount(f)) * settings().decimal("eco.refundUnclaimPercent")) / 100.0;
        if (refund > 0 && eco.bankCredit(f, refund, Messages.get(LandText.BANK_UNCLAIM_REASON, "name", land.nameOf(actor))).success()) {
            return refund;
        }
        return 0;
    }

    // ---- admin and zones --------------------------------------------------

    @Override
    public Result adminClaim(UUID actor, Faction target, String world, long chunkIndex) {
        return adminClaim(actor, target, world, chunkIndex, false);
    }

    /**
     * Admin claim. With quiet true the claim index and counts are updated and a lost home is still
     * cleared, but no audit line and no ClaimChanged event are written for a free chunk. The caller
     * then audits and publishes once for the whole batch. A chunk taken from another faction is
     * still announced to that faction.
     */
    @Override
    public Result adminClaim(UUID actor, Faction target, String world, long chunkIndex, boolean quiet) {
        if (target == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        if (world == null) {
            return Result.fail(Messages.get(CommonText.WORLD_UNKNOWN));
        }
        synchronized (lock) {
            Faction old = ownerAt(world, chunkIndex);
            if (old != null && old.id().equals(target.id())) {
                return Result.fail(Messages.get(LandText.ADMIN_ALREADY_OWNS, "faction", target.tag()));
            }
            repo().putClaim(world, chunkIndex, new ClaimEntry(target.id(), Time.now(), actor, null));
            String where = Keys.describe(chunkIndex);
            if (old != null) {
                noteReleased(old, world, chunkIndex);
                clearHomeIfIn(old, world, chunkIndex);
            }
            // Quiet only skips the per chunk noise for free land. Taking land from a faction still
            // tells that faction, so its warps and home in the chunk are cleared by the listeners.
            if (!quiet || old != null) {
                changed(world, chunkIndex, old, target, actor, false);
            }
            if (!quiet) {
                land.audit(target, actor, Messages.get(LandText.AUDIT_ADMIN_GAVE_US, "chunk", where, "world", world));
            }
            if (old != null) {
                land.audit(old, actor, Messages.get(LandText.AUDIT_ADMIN_GAVE_OTHER, "chunk", where, "world", world, "faction", target.tag()));
            }
            return Result.pass(Messages.get(LandText.ADMIN_GAVE, "chunk", where, "faction", target.tag()));
        }
    }

    @Override
    public Result adminUnclaim(UUID actor, String world, long chunkIndex) {
        synchronized (lock) {
            Faction old = ownerAt(world, chunkIndex);
            if (old == null) {
                return Result.fail(Messages.get(LandText.ADMIN_NOBODY_OWNS));
            }
            repo().removeClaim(world, chunkIndex);
            noteReleased(old, world, chunkIndex);
            clearHomeIfIn(old, world, chunkIndex);
            changed(world, chunkIndex, old, null, actor, false);
            land.audit(old, actor, Messages.get(LandText.AUDIT_ADMIN_RELEASED, "chunk", Keys.describe(chunkIndex), "world", world));
            return Result.pass(Messages.get(LandText.ADMIN_RELEASED, "chunk", Keys.describe(chunkIndex), "faction", old.tag()));
        }
    }

    @Override
    public Result createZone(UUID actor, FactionType type, String name) {
        if (type == null || type == FactionType.NORMAL) {
            return Result.fail(Messages.get(LandText.ZONE_TYPE));
        }
        if (name == null || name.isBlank() || name.contains(" ")) {
            return Result.fail(Messages.get(LandText.ZONE_NAME));
        }
        synchronized (lock) {
            if (repo().tagTaken(name)) {
                return Result.fail(Messages.get(LandText.ZONE_TAKEN, "name", name));
            }
            Faction zone = new Faction(UUID.randomUUID(), name, name, null, Time.now());
            zone.setType(type);
            zone.setColor(type == FactionType.SAFEZONE ? Land.SAFEZONE : Land.WARZONE);
            repo().addFaction(zone);
            land.publish(new DominionEvent.FactionCreated(zone.id()));
            boolean safe = type == FactionType.SAFEZONE;
            land.audit(zone, actor, Messages.get(safe ? LandText.AUDIT_SAFEZONE_CREATED : LandText.AUDIT_WARZONE_CREATED, "name", name));
            String world = playerWorld(actor);
            long[] here = playerChunk(actor);
            if (world == null || here == null) {
                return Result.pass(Messages.get(safe ? LandText.SAFEZONE_CREATED : LandText.WARZONE_CREATED, "name", name));
            }
            Result r = adminClaim(actor, zone, world, here[0]);
            String done = r.success()
                    ? (safe ? LandText.SAFEZONE_CREATED_CLAIMED : LandText.WARZONE_CREATED_CLAIMED)
                    : (safe ? LandText.SAFEZONE_CREATED : LandText.WARZONE_CREATED);
            return Result.pass(Messages.get(done, "name", name));
        }
    }

    // ---- raiding ----------------------------------------------------------

    @Override
    public boolean raidable(Faction defender) {
        return raids.raidable(defender);
    }

    /** Hot path for protection checks. The faction pair part of the answer is cached for five seconds. */
    @Override
    public Result raidableBy(Faction attacker, Faction defender, String world, long chunkIndex) {
        return raids.check(attacker, defender, world, chunkIndex, true).result();
    }

    @Override
    public boolean breached(String world, long chunkIndex, UUID attackerFaction) {
        return raids.breached(world, chunkIndex, attackerFaction);
    }

    @Override
    public void setBreached(String world, long chunkIndex, UUID attackerFaction, long until) {
        raids.setBreached(world, chunkIndex, attackerFaction, until);
    }

    // ---- ticks and maps ---------------------------------------------------

    @Override
    public void territoryTick() {
        territory.tick();
    }

    @Override
    public void decayTick() {
        decay.tick();
    }

    @Override
    public List<Message> chatMap(UUID player, int width, int height) {
        return maps.chat(player, width, height);
    }

    @Override
    public List<String[]> gridMap(UUID player, int radius) {
        return maps.grid(player, radius);
    }

    // ---- module hooks -----------------------------------------------------

    void forget(UUID player) {
        territory.forget(player);
    }

    void onEvent(DominionEvent event) {
        if (event instanceof DominionEvent.MemberLeft left) {
            territory.reset(left.player());
            PlayerData pd = repo().playerIfLoaded(left.player());
            if (pd != null && pd.autoClaim()) {
                pd.setAutoClaim(false);
            }
        } else if (event instanceof DominionEvent.FactionDisbanded gone) {
            raids.forgetFaction(gone.factionId());
        }
    }

    void decayRemove(Faction f, String world, long chunk, int days) {
        synchronized (lock) {
            Faction owner = ownerAt(world, chunk);
            if (owner == null || !owner.id().equals(f.id())) {
                return;
            }
            repo().removeClaim(world, chunk);
            noteReleased(f, world, chunk);
            clearHomeIfIn(f, world, chunk);
            changed(world, chunk, f, null, null, false);
            land.audit(f, null, Messages.get(LandText.AUDIT_DECAYED, "chunk", Keys.describe(chunk), "world", world, "days", days));
        }
    }

    // ---- rule helpers -----------------------------------------------------

    /** The mission window from land.reclaimWindowDays. Zero turns the memory off. */
    private long reclaimWindowMillis() {
        return settings().integer("land.reclaimWindowDays") * 86_400_000L;
    }

    /** Records that the faction lost the chunk, by its own choice or not. */
    private void noteReleased(Faction f, String world, long chunk) {
        f.noteReleased(world, chunk, Time.now(), reclaimWindowMillis());
    }

    /** True unless the faction lost this chunk within the mission window. */
    private boolean newLand(Faction f, String world, long chunk) {
        return !f.releasedWithin(world, chunk, Time.now(), reclaimWindowMillis());
    }

    /** Publishes the change. counts is false for admin moves, decay and any chunk the faction released lately. */
    private void changed(String world, long chunk, Faction from, Faction to, UUID actor, boolean counts) {
        land.publish(new DominionEvent.ClaimChanged(world, chunk,
                from == null ? null : from.id(), to == null ? null : to.id(), actor, counts));
    }

    private static void clearHomeIfIn(Faction f, String world, long chunk) {
        Location home = f.home();
        if (home != null && world.equals(home.world()) && home.chunkIndex() == chunk) {
            f.setHome(null);
        }
    }

    private double claimCost(int existing) {
        double base = settings().decimal("eco.costClaim");
        if (base <= 0) {
            return 0;
        }
        double growth = settings().decimal("eco.costClaimGrowthPercent") / 100.0;
        return Math.round(base * (1.0 + growth * Math.max(0, existing)) * 100.0) / 100.0;
    }

    private boolean worldAllowed(String world) {
        String list = settings().text("land.allowedWorlds");
        boolean any = false;
        for (String w : list.split(",")) {
            String t = w.trim();
            if (t.isEmpty()) {
                continue;
            }
            any = true;
            if (t.equalsIgnoreCase(world)) {
                return true;
            }
        }
        return !any;
    }

    private boolean touches(String world, long chunk, UUID factionId) {
        for (long n : Keys.neighbours(chunk)) {
            Faction o = ownerAt(world, n);
            if (o != null && o.id().equals(factionId)) {
                return true;
            }
        }
        return false;
    }

    /** True when at least one side of the chunk touches land the owner does not hold. */
    private boolean onEdgeOf(String world, long chunk, UUID ownerId) {
        for (long n : Keys.neighbours(chunk)) {
            Faction o = ownerAt(world, n);
            if (o == null || !o.id().equals(ownerId)) {
                return true;
            }
        }
        return false;
    }

    /** First other normal faction within the buffer distance, ignoring the chunk's current owner. */
    private Faction bufferBlocker(String world, long chunk, Faction f, Faction current) {
        int b = settings().integer("land.bufferChunks");
        if (b <= 0) {
            return null;
        }
        int cx = Keys.chunkX(chunk);
        int cz = Keys.chunkZ(chunk);
        for (int dx = -b; dx <= b; dx++) {
            for (int dz = -b; dz <= b; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Faction o = ownerAt(world, Keys.chunk(cx + dx, cz + dz));
                if (o == null || o.isSystem() || o.id().equals(f.id())) {
                    continue;
                }
                if (current != null && o.id().equals(current.id())) {
                    continue;
                }
                return o;
            }
        }
        return null;
    }

    /** Chunks of a square around the centre, nearest first so contiguity grows outward. */
    private static List<Long> square(long center, int radius) {
        int cx = Keys.chunkX(center);
        int cz = Keys.chunkZ(center);
        List<long[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cells.add(new long[] { Keys.chunk(cx + dx, cz + dz), (long) dx * dx + (long) dz * dz });
            }
        }
        cells.sort(Comparator.comparingLong(c -> c[1]));
        List<Long> out = new ArrayList<>(cells.size());
        for (long[] c : cells) {
            out.add(c[0]);
        }
        return out;
    }
}
