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

    ClaimServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.land = new Land(ctx);
        this.raids = new Raids(land, this);
        this.territory = new Territory(land, this);
        this.maps = new LandMap(land, this);
        this.decay = new Decay(land, this);
        this.spawn = new SpawnGuard(land);
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
        long idx = Players.chunkOf(Players.online(player));
        if (idx == Long.MIN_VALUE) {
            return null;
        }
        return new long[] { idx, Keys.chunkX(idx), Keys.chunkZ(idx) };
    }

    @Override
    public String playerWorld(UUID player) {
        return Players.worldName(Players.online(player));
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
            return Result.fail("Your position is not known yet.");
        }
        Faction f = land.factionOf(actor);
        if (f == null) {
            return Result.fail("You need to be in a faction to claim land.");
        }
        if (!land.can(actor, FactionAction.CLAIM, f)) {
            return Result.fail("Your rank cannot claim land.");
        }
        if (!worldAllowed(world)) {
            return Result.fail("Land cannot be claimed in this world.");
        }
        if (spawn.near(world, chunk, actor)) {
            return Result.fail("This land is too close to spawn to claim.");
        }
        Faction current = ownerAt(world, chunk);
        if (current != null && current.isSystem()) {
            return Result.fail("This land belongs to the server.");
        }
        if (current != null && current.id().equals(f.id())) {
            return Result.fail("Your faction already owns this land.");
        }
        if (settings().bool("land.mustBeConnected") && !touches(world, chunk, f.id())
                && repo().claimsOf(world, f.id()).length > 0) {
            return Result.fail("New claims must touch land your faction already owns.");
        }
        Faction blocker = bufferBlocker(world, chunk, f, current);
        if (blocker != null) {
            return Result.fail("This land is too close to " + blocker.tag() + ".");
        }
        int count = claimCount(f);
        int max = maxClaims(f);
        if (count >= max) {
            return Result.fail("Your faction has no claim power left. It holds " + count + " of " + max + " claims.");
        }
        EconomyService eco = land.economy();
        double cost = eco == null || f.isSystem() ? 0 : claimCost(count);
        if (cost > 0 && f.bank() + 1e-6 < cost) {
            return Result.fail("This claim costs " + eco.format(cost) + " and the faction bank only holds "
                    + eco.format(f.bank()) + ".");
        }
        Raids.Verdict verdict = null;
        if (current != null) {
            if (land.between(f, current) != Relation.ENEMY) {
                return Result.fail(current.tag() + " owns this land. Only enemy land can be taken.");
            }
            if (!onEdgeOf(world, chunk, current.id())) {
                return Result.fail("You can only take chunks at the edge of " + current.tag() + " land.");
            }
            verdict = raids.check(f, current, world, chunk, false);
            if (verdict.result().failed()) {
                return verdict.result();
            }
        }
        if (cost > 0) {
            Result paid = eco.bankCharge(f, cost, "a land claim by " + land.nameOf(actor));
            if (paid.failed()) {
                return Result.fail(paid.message() + ".");
            }
        }
        repo().putClaim(world, chunk, new ClaimEntry(f.id(), Time.now(), actor, null));
        if (verdict != null && verdict.dominance()) {
            raids.markDominance(f, current);
        }
        String where = Keys.describe(chunk);
        changed(world, chunk, current, f);
        land.audit(f, actor, current == null ? "Claimed chunk " + where + " in " + world
                : "Took chunk " + where + " in " + world + " from " + current.tag());
        land.progress(actor, "claim");
        String paidText = cost > 0 ? " The faction bank paid " + eco.format(cost) + "." : "";
        if (current != null) {
            land.audit(current, actor, f.tag() + " took chunk " + where + " in " + world);
            clearHomeIfIn(current, world, chunk);
            return Result.pass("Took chunk " + where + " from " + current.tag() + "." + paidText);
        }
        return Result.pass("Claimed chunk " + where + " for " + f.tag() + "." + paidText);
    }

    @Override
    public Result claimRadius(UUID actor, String world, long centerIndex, int radius) {
        int max = settings().integer("land.maxRadiusClaim");
        if (radius < 0) {
            return Result.fail("The radius cannot be negative.");
        }
        if (radius > max) {
            return Result.fail("The largest radius you can claim is " + max + ".");
        }
        return claimMany(actor, world, square(centerIndex, radius));
    }

    @Override
    public Result claimLine(UUID actor, String world, long startIndex, int dx, int dz, int length) {
        int max = settings().integer("land.maxLineClaim");
        if (length < 1) {
            return Result.fail("A line needs at least one chunk.");
        }
        if (length > max) {
            return Result.fail("The longest line you can claim is " + max + " chunks.");
        }
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        if (sx == 0 && sz == 0) {
            return Result.fail("Pick a direction for the line.");
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
                return Result.fail("Your faction already owns all of that land.");
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
                return Result.fail(reason == null ? "No land could be claimed." : reason);
            }
            String msg = "Claimed " + claimed + " of " + total + " chunks.";
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
                return Result.fail("Your faction does not own this land.");
            }
            double refund = release(actor, f, world, chunkIndex);
            return Result.pass("Released chunk " + Keys.describe(chunkIndex) + "." + refundText(refund));
        }
    }

    @Override
    public Result unclaimRadius(UUID actor, String world, long centerIndex, int radius) {
        int max = settings().integer("land.maxRadiusClaim");
        if (radius < 0) {
            return Result.fail("The radius cannot be negative.");
        }
        if (radius > max) {
            return Result.fail("The largest radius you can unclaim is " + max + ".");
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
                return Result.fail("Your faction does not own any land in that area.");
            }
            return Result.pass("Released " + n + " chunks." + refundText(refund));
        }
    }

    @Override
    public Result unclaimAll(UUID actor, boolean confirmed) {
        synchronized (lock) {
            Faction f = land.factionOf(actor);
            if (f == null) {
                return Result.fail("You are not in a faction.");
            }
            if (!land.can(actor, FactionAction.DISBAND, f)) {
                return Result.fail("Your rank cannot release all of your land.");
            }
            int count = claimCount(f);
            if (count == 0) {
                return Result.fail("Your faction does not own any land.");
            }
            if (!confirmed) {
                return Result.fail("This releases all " + count + " chunks your faction owns. Confirm to continue.");
            }
            int n = 0;
            double refund = 0;
            for (String world : new ArrayList<>(repo().claimWorlds())) {
                for (long c : repo().claimsOf(world, f.id())) {
                    refund += release(actor, f, world, c);
                    n++;
                }
            }
            return Result.pass("Released all " + n + " chunks." + refundText(refund));
        }
    }

    private Result mayUnclaim(UUID actor, Faction f) {
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!land.can(actor, FactionAction.UNCLAIM, f)) {
            return Result.fail("Your rank cannot unclaim land.");
        }
        return null;
    }

    private String refundText(double refund) {
        EconomyService eco = land.economy();
        return refund > 0 && eco != null ? " The faction bank got " + eco.format(refund) + " back." : "";
    }

    /** Removes an owned chunk, refunds the faction bank and returns the refund paid. */
    private double release(UUID actor, Faction f, String world, long chunk) {
        repo().removeClaim(world, chunk);
        clearHomeIfIn(f, world, chunk);
        changed(world, chunk, f, null);
        land.audit(f, actor, "Released chunk " + Keys.describe(chunk) + " in " + world);
        EconomyService eco = land.economy();
        if (eco == null) {
            return 0;
        }
        double refund = Math.floor(claimCost(claimCount(f)) * settings().decimal("eco.refundUnclaimPercent")) / 100.0;
        if (refund > 0 && eco.bankCredit(f, refund, "an unclaim refund for " + land.nameOf(actor)).success()) {
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
            return Result.fail("That faction does not exist.");
        }
        if (world == null) {
            return Result.fail("That world is not known.");
        }
        synchronized (lock) {
            Faction old = ownerAt(world, chunkIndex);
            if (old != null && old.id().equals(target.id())) {
                return Result.fail(target.tag() + " already owns this land.");
            }
            repo().putClaim(world, chunkIndex, new ClaimEntry(target.id(), Time.now(), actor, null));
            String where = Keys.describe(chunkIndex);
            if (old != null) {
                clearHomeIfIn(old, world, chunkIndex);
            }
            // Quiet only skips the per chunk noise for free land. Taking land from a faction still
            // tells that faction, so its warps and home in the chunk are cleared by the listeners.
            if (!quiet || old != null) {
                changed(world, chunkIndex, old, target);
            }
            if (!quiet) {
                land.audit(target, actor, "An admin gave chunk " + where + " in " + world + " to the faction");
            }
            if (old != null) {
                land.audit(old, actor, "An admin gave chunk " + where + " in " + world + " to " + target.tag());
            }
            return Result.pass("Chunk " + where + " now belongs to " + target.tag() + ".");
        }
    }

    @Override
    public Result adminUnclaim(UUID actor, String world, long chunkIndex) {
        synchronized (lock) {
            Faction old = ownerAt(world, chunkIndex);
            if (old == null) {
                return Result.fail("Nobody owns this land.");
            }
            repo().removeClaim(world, chunkIndex);
            clearHomeIfIn(old, world, chunkIndex);
            changed(world, chunkIndex, old, null);
            land.audit(old, actor, "An admin released chunk " + Keys.describe(chunkIndex) + " in " + world);
            return Result.pass("Released chunk " + Keys.describe(chunkIndex) + " from " + old.tag() + ".");
        }
    }

    @Override
    public Result createZone(UUID actor, FactionType type, String name) {
        if (type == null || type == FactionType.NORMAL) {
            return Result.fail("A zone must be a SafeZone or a WarZone.");
        }
        if (name == null || name.isBlank() || name.contains(" ")) {
            return Result.fail("Give the zone a name with no spaces.");
        }
        synchronized (lock) {
            if (repo().tagTaken(name)) {
                return Result.fail("A faction or zone named " + name + " already exists.");
            }
            Faction zone = new Faction(UUID.randomUUID(), name, name, null, Time.now());
            zone.setType(type);
            zone.setColor(type == FactionType.SAFEZONE ? Land.SAFEZONE : Land.WARZONE);
            repo().addFaction(zone);
            land.publish(new DominionEvent.FactionCreated(zone.id()));
            String label = type == FactionType.SAFEZONE ? "SafeZone" : "WarZone";
            land.audit(zone, actor, "Created the " + label + " " + name);
            String world = playerWorld(actor);
            long[] here = playerChunk(actor);
            if (world == null || here == null) {
                return Result.pass("Created the " + label + " " + name + ".");
            }
            Result r = adminClaim(actor, zone, world, here[0]);
            return Result.pass("Created the " + label + " " + name + (r.success() ? " and claimed your chunk." : "."));
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
        territory.reset(player);
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
            clearHomeIfIn(f, world, chunk);
            changed(world, chunk, f, null);
            land.audit(f, null, "Lost chunk " + Keys.describe(chunk) + " in " + world
                    + " after " + days + " days with no member online");
        }
    }

    // ---- rule helpers -----------------------------------------------------

    private void changed(String world, long chunk, Faction from, Faction to) {
        land.publish(new DominionEvent.ClaimChanged(world, chunk,
                from == null ? null : from.id(), to == null ? null : to.id()));
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
