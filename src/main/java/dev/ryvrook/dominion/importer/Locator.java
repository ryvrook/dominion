package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.DominionContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where to look for other mods' data. The server keeps each plugin's data folder in
 * mods/Group_Name next to Dominion's own folder, so the parent of Dominion's data directory is
 * the mods folder. Older mods also write to folders under the working directory or the universe.
 */
final class Locator {
    final Path work;
    final List<Path> mods;
    final Path universe;

    Locator(DominionContext ctx) {
        this.work = Path.of("").toAbsolutePath().normalize();
        Set<Path> m = new LinkedHashSet<>();
        try {
            Path own = ctx.plugin().getDataDirectory();
            if (own != null && own.toAbsolutePath().getParent() != null) {
                m.add(own.toAbsolutePath().normalize().getParent());
            }
        } catch (RuntimeException ignored) {
            // fall back to the working directory below
        }
        m.add(work.resolve("mods"));
        this.mods = new ArrayList<>(m);
        Path u;
        try {
            u = com.hypixel.hytale.server.core.Constants.UNIVERSE_PATH;
        } catch (Throwable t) {
            u = null;
        }
        this.universe = (u == null ? work.resolve("universe") : u.toAbsolutePath()).normalize();
    }

    /** Explicit folders, used by tests and tools that do not run inside the server. */
    Locator(Path work, List<Path> mods, Path universe) {
        this.work = work;
        this.mods = List.copyOf(mods);
        this.universe = universe;
    }

    /** Fixed candidates under every mods folder plus any mods sub folder whose name contains a needle. */
    List<Path> modFolders(List<String> names, String... needles) {
        Set<Path> out = new LinkedHashSet<>();
        for (Path dir : mods) {
            for (String n : names) {
                out.add(dir.resolve(n));
            }
            out.addAll(Data.foldersLike(dir, needles));
        }
        return new ArrayList<>(out);
    }
}
