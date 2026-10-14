package dev.ryvrook.dominion.util;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Online player lookups and positions. Null when offline, and also while the universe is not
 * running yet or any more, such as during start, shutdown or a unit test.
 */
public final class Players {
    private Players() {}

    public static PlayerRef online(UUID uuid) {
        Universe u = uuid == null ? null : Universe.get();
        return u == null ? null : u.getPlayer(uuid);
    }

    public static PlayerRef byName(String name) {
        Universe u = name == null || name.isBlank() ? null : Universe.get();
        return u == null ? null : u.getPlayerByUsername(name, com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE);
    }

    public static boolean isOnline(UUID uuid) {
        return online(uuid) != null;
    }

    public static Vector3d position(PlayerRef player) {
        Transform t = player == null ? null : player.getTransform();
        return t == null || t.getPosition() == null ? null : new Vector3d(t.getPosition());
    }

    /** World the player is currently in, or null. */
    public static World world(PlayerRef player) {
        if (player == null) {
            return null;
        }
        UUID worldId = player.getWorldUuid();
        Universe u = worldId == null ? null : Universe.get();
        return u == null ? null : u.getWorld(worldId);
    }

    public static String worldName(PlayerRef player) {
        World w = world(player);
        return w == null ? null : w.getName();
    }

    public static long chunkOf(PlayerRef player) {
        Vector3d p = position(player);
        return p == null ? Long.MIN_VALUE : Keys.chunkAt(p.x, p.z);
    }
}
