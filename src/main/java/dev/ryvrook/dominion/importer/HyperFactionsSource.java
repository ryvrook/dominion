package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.model.Rank;
import org.bson.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HyperFactions keeps one JSON file per faction in data/factions/<uuid>.json inside its plugin
 * folder (older installs used factions/ without data/). Each file holds the faction's members
 * with their role and its claims as 32 block chunk coordinates with a world name.
 */
final class HyperFactionsSource implements Source {

    @Override public String id() { return "hyperfactions"; }
    @Override public String label() { return "HyperFactions"; }

    @Override
    public void read(Locator loc, Plan plan) throws IOException {
        Path dir = null;
        Path root = null;
        for (Path folder : loc.modFolders(List.of("com.hyperfactions_HyperFactions", "HyperFactions"), "hyperfaction")) {
            for (Path candidate : List.of(folder.resolve("data").resolve("factions"), folder.resolve("factions"))) {
                plan.looked(candidate);
                if (dir == null && !Data.list(candidate, "*.json").isEmpty()) {
                    dir = candidate;
                    root = candidate.getParent();
                }
            }
        }
        if (dir == null) {
            return;
        }
        plan.found = dir;
        for (Path file : Data.list(dir, "*.json")) {
            Document d;
            try {
                d = Data.json(file);
            } catch (Exception e) {
                plan.skipped.add("The file " + file.getFileName() + " could not be read because " + e.getMessage());
                continue;
            }
            readFaction(file, d, plan);
        }
        Path zones = root.resolve("zones.json");
        if (Data.isFile(zones)) {
            plan.skipped.add("Safe and war zones in zones.json are not imported. Recreate them with Dominion zone commands");
        }
    }

    private static void readFaction(Path file, Document d, Plan plan) {
        String id = Data.str(d, "id");
        String name = Data.str(d, "name");
        if (id == null || name == null) {
            plan.skipped.add("The file " + file.getFileName() + " has no faction id or name");
            return;
        }
        Plan.Faction f = new Plan.Faction(id);
        f.name = name;
        f.preferredTag = Data.str(d, "tag");
        f.description = Data.str(d, "description") == null ? "" : Data.str(d, "description");
        f.color = Data.hexColor(Data.str(d, "color"));
        f.createdAt = Data.epochMillis(Data.num(d, "createdAt"));
        for (Document m : Data.docs(d, "members")) {
            UUID uuid = Data.uuid(m.get("uuid"));
            if (uuid == null) {
                plan.skipped.add("A member of " + name + " has no valid UUID");
                continue;
            }
            String role = String.valueOf(Data.str(m, "role")).toUpperCase(Locale.ROOT);
            Rank rank = switch (role) {
                case "LEADER" -> Rank.LEADER;
                case "OFFICER" -> Rank.OFFICER;
                default -> Rank.MEMBER;
            };
            f.addMember(uuid, Data.str(m, "username"), rank);
            if (rank == Rank.LEADER && f.leader == null) {
                f.leader = uuid;
            }
        }
        for (Document c : Data.docs(d, "claims")) {
            String world = Data.str(c, "world");
            Long x = Data.num(c, "chunkX");
            Long z = Data.num(c, "chunkZ");
            if (world == null || x == null || z == null) {
                plan.skipped.add("A claim of " + name + " is missing its world or chunk coordinates");
                continue;
            }
            f.sourceCells++;
            for (long idx : Data.chunks(x.intValue(), z.intValue(), 32)) {
                f.addChunk(world, idx);
            }
        }
        plan.factions.add(f);
    }
}
