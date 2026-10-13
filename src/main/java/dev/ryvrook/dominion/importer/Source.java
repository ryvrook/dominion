package dev.ryvrook.dominion.importer;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.IOException;
import java.util.UUID;

/** Reads one foreign mod's data into a Plan. Implementations must never write to source files. */
interface Source {
    String id();

    String label();

    void read(Locator loc, Plan plan) throws IOException;

    /** Resolves a world key that may be a world UUID or a world name. Null when the UUID is not a loaded world. */
    static String worldName(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        UUID id = Data.uuid(key);
        if (id == null) {
            return key;
        }
        try {
            Universe u = Universe.get();
            World w = u == null ? null : u.getWorld(id);
            return w == null ? null : w.getName();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
