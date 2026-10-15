package dev.ryvrook.dominion.land;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Teleports;
import dev.ryvrook.dominion.util.Time;
import dev.ryvrook.dominion.text.Messages;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Watches every online player's chunk for territory banners, enter events and auto claim, and
 * moves combat tagged players back out of SafeZones they walk into.
 */
final class Territory {
    private record Spot(String world, long chunk, UUID owner) {}

    private final Land land;
    private final ClaimServiceImpl claims;
    private final Map<UUID, Spot> last = new ConcurrentHashMap<>();
    private final Map<UUID, String> autoFailed = new ConcurrentHashMap<>();
    private final SafeZoneGuard guard = new SafeZoneGuard();
    /** When each player was last told about the SafeZone push back, so a pending teleport does not spam them. */
    private final Map<UUID, Long> toldAt = new ConcurrentHashMap<>();
    private static final long TELL_EVERY_MILLIS = 2000L;

    Territory(Land land, ClaimServiceImpl claims) {
        this.land = land;
        this.claims = claims;
    }

    void tick() {
        Universe u = Universe.get();
        if (u == null) {
            return;
        }
        for (PlayerRef p : new ArrayList<>(u.getPlayers())) {
            try {
                visit(p);
            } catch (RuntimeException e) {
                land.ctx.logger().at(Level.FINE).withCause(e).log("Territory check failed for %s", p.getUsername());
            }
        }
    }

    private void visit(PlayerRef p) {
        UUID id = p.getUuid();
        String world = Players.worldName(p);
        long chunk = Players.chunkOf(p);
        if (world == null || chunk == Long.MIN_VALUE) {
            return;
        }
        if (pushedBack(p, world, chunk)) {
            // The player is on their way back out, so the SafeZone is neither announced nor remembered.
            return;
        }
        Spot prev = last.get(id);
        if (prev != null && prev.world().equals(world) && prev.chunk() == chunk) {
            return;
        }
        PlayerData pd = land.repo().playerIfLoaded(id);
        if (pd != null && pd.autoClaim()) {
            autoClaim(p, world, chunk);
        }
        Faction owner = claims.ownerAt(world, chunk);
        UUID ownerId = owner == null ? null : owner.id();
        last.put(id, new Spot(world, chunk, ownerId));
        UUID prevOwner = prev == null ? null : prev.owner();
        boolean worldChanged = prev == null || !prev.world().equals(world);
        if (!worldChanged && Objects.equals(prevOwner, ownerId)) {
            return;
        }
        if (prev == null && owner == null) {
            return;
        }
        land.publish(new DominionEvent.PlayerEnteredTerritory(id, world, prevOwner, ownerId));
        banner(p, owner, world, chunk);
    }

    /**
     * Runs the SafeZone guard for one tick and starts the push back when it says so. True when the
     * player is being moved back out.
     */
    private boolean pushedBack(PlayerRef p, String world, long chunk) {
        Vector3d pos = Players.position(p);
        if (pos == null) {
            return false;
        }
        UUID id = p.getUuid();
        Faction here = claims.ownerAt(world, chunk);
        boolean safe = here != null && here.type() == FactionType.SAFEZONE;
        Rotation3f rot = p.getTransform() == null ? null : p.getTransform().getRotation();
        SafeZoneGuard.Spot now = new SafeZoneGuard.Spot(world, pos.x, pos.y, pos.z,
                rot == null ? 0f : rot.yaw(), rot == null ? 0f : rot.pitch());
        boolean enabled = safe && land.settings().bool("combat.blockSafeZoneEntry");
        if (guard.observe(id, now, safe, enabled, () -> land.tagged(id)) != SafeZoneGuard.Verdict.PUSH_BACK) {
            return false;
        }
        SafeZoneGuard.Spot back = guard.anchor(id);
        if (back == null || !Teleports.teleport(p, new Location(back.world(), back.x(), back.y(), back.z(), back.yaw(), back.pitch()))) {
            return false;
        }
        long nowMs = Time.now();
        Long told = toldAt.get(id);
        if (told == null || nowMs - told >= TELL_EVERY_MILLIS) {
            toldAt.put(id, nowMs);
            Msg.send(p, Msg.bad(Messages.get(LandText.SAFEZONE_COMBAT)));
        }
        return true;
    }

    private void autoClaim(PlayerRef p, String world, long chunk) {
        UUID id = p.getUuid();
        Faction own = land.factionOf(id);
        Faction here = claims.ownerAt(world, chunk);
        if (own != null && here != null && own.id().equals(here.id())) {
            return;
        }
        Result r = claims.claim(id, world, chunk);
        if (r.success()) {
            autoFailed.remove(id);
            return;
        }
        String key = world + "|" + chunk;
        if (!key.equals(autoFailed.put(id, key))) {
            Msg.send(p, Msg.bad(r.message()));
        }
    }

    private void banner(PlayerRef p, Faction owner, String world, long chunk) {
        if (!land.settings().bool("land.territoryBanners")) {
            return;
        }
        UiService ui = land.ctx.serviceOrNull(UiService.class);
        if (ui == null) {
            return;
        }
        String title = owner == null ? Messages.get(LandText.WILDERNESS) : owner.name();
        String subtitle = subtitle(p.getUuid(), owner, world, chunk);
        String color = land.colorFor(p.getUuid(), owner);
        Runnable show = () -> {
            try {
                ui.territoryBanner(p, title, subtitle, color);
            } catch (RuntimeException e) {
                land.ctx.logger().at(Level.FINE).withCause(e).log("Territory banner failed");
            }
        };
        World w = Players.world(p);
        if (w != null) {
            w.execute(show);
        } else {
            show.run();
        }
    }

    private String subtitle(UUID viewer, Faction owner, String world, long chunk) {
        if (owner == null) {
            return Messages.get(LandText.BANNER_UNCLAIMED);
        }
        List<String> flags = new ArrayList<>();
        if (owner.type() == FactionType.SAFEZONE) {
            flags.add(Messages.get(LandText.FLAG_SAFEZONE));
        } else if (owner.type() == FactionType.WARZONE) {
            flags.add(Messages.get(LandText.FLAG_WARZONE));
        } else {
            if (claims.raidable(owner)) {
                flags.add(Messages.get(LandText.FLAG_RAIDABLE));
            }
            Faction own = land.factionOf(viewer);
            if (own != null && claims.breached(world, chunk, own.id())) {
                flags.add(Messages.get(LandText.FLAG_BREACHED));
            }
        }
        String desc = owner.description() == null ? "" : owner.description().trim();
        String flagText = String.join(", ", flags);
        if (flagText.isEmpty()) {
            return desc;
        }
        return desc.isEmpty() ? flagText : desc + " (" + flagText + ")";
    }

    /** Forces a fresh banner on the next tick, for example after the player changes faction. */
    void reset(UUID player) {
        last.remove(player);
        autoFailed.remove(player);
    }

    /** Drops everything kept for a player who left the server. */
    void forget(UUID player) {
        reset(player);
        guard.forget(player);
        toldAt.remove(player);
    }
}
