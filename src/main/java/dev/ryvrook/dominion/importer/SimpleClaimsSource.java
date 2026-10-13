package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.model.Rank;
import org.bson.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simple Claims keeps its data in universe/SimpleClaims. Current versions use the SQLite file
 * SimpleClaims.db with the tables parties, party_members, claims, name_cache and reserved_chunks.
 * Older versions used Parties.json, Claims.json and NameCache.json in the same folder. Claims are
 * 32 block chunk coordinates keyed by world name. Each party becomes a faction named after its owner.
 */
final class SimpleClaimsSource implements Source {

    @Override public String id() { return "simpleclaims"; }
    @Override public String label() { return "Simple Claims"; }

    @Override
    public void read(Locator loc, Plan plan) throws IOException {
        Set<Path> folders = new LinkedHashSet<>();
        folders.add(loc.universe.resolve("SimpleClaims"));
        for (Path p : loc.modFolders(List.of("Buuz135_SimpleClaims", "SimpleClaims"), "simpleclaims")) {
            if (!p.getFileName().toString().contains("_backup_")) {
                folders.add(p);
            }
        }
        for (Path f : folders) {
            Path db = f.resolve("SimpleClaims.db");
            Path legacy = f.resolve("Claims.json");
            plan.looked(db);
            plan.looked(legacy);
        }
        for (Path f : folders) {
            Path db = f.resolve("SimpleClaims.db");
            if (Data.isFile(db) && Files.size(db) > 0) {
                SqliteFile sql = SqliteFile.open(db);
                if (sql.hasTable("parties") && sql.hasTable("claims") && !sql.rows("parties").isEmpty()) {
                    plan.found = db;
                    warnJournal(db, plan);
                    readDatabase(sql, plan);
                    return;
                }
            }
            if (Data.isFile(f.resolve("Parties.json")) || Data.isFile(f.resolve("Claims.json"))) {
                plan.found = f;
                readLegacy(f, plan);
                return;
            }
        }
    }

    private static void warnJournal(Path db, Plan plan) throws IOException {
        for (String suffix : List.of("-wal", "-journal")) {
            Path side = db.resolveSibling(db.getFileName() + suffix);
            if (Data.isFile(side) && Files.size(side) > 0) {
                plan.notes.add("SimpleClaims.db" + suffix + " is not empty, so the newest Simple Claims changes may be missing. "
                        + "Stop the server with Simple Claims installed once, or remove Simple Claims first, then import");
            }
        }
    }

    // ---- SQLite ----------------------------------------------------------

    private static void readDatabase(SqliteFile sql, Plan plan) throws IOException {
        Map<UUID, String> names = new HashMap<>();
        for (Map<String, Object> r : sql.rows("name_cache")) {
            UUID id = Data.uuid(r.get("uuid"));
            if (id != null && r.get("name") != null) {
                names.put(id, r.get("name").toString());
            }
        }
        Map<String, Plan.Faction> byId = new HashMap<>();
        for (Map<String, Object> r : sql.rows("parties")) {
            String id = r.get("id") == null ? null : r.get("id").toString();
            UUID owner = Data.uuid(r.get("owner"));
            if (id == null || owner == null) {
                plan.skipped.add("A party has no id or owner");
                continue;
            }
            String ownerName = names.getOrDefault(owner, str(r.get("created_user_name")));
            Plan.Faction f = party(id, owner, ownerName, str(r.get("name")), r.get("color") instanceof Long c ? c : null);
            byId.put(id, f);
            plan.factions.add(f);
        }
        for (Map<String, Object> r : sql.rows("party_members")) {
            Plan.Faction f = byId.get(str(r.get("party_id")));
            UUID m = Data.uuid(r.get("member_uuid"));
            if (f != null && m != null) {
                f.addMember(m, names.get(m), m.equals(f.leader) ? Rank.LEADER : Rank.MEMBER);
            }
        }
        for (Map<String, Object> r : sql.rows("claims")) {
            claim(byId, str(r.get("party_owner")), str(r.get("dimension")), r.get("chunkx"), r.get("chunkz"), plan);
        }
        int reserved = sql.hasTable("reserved_chunks") ? sql.rows("reserved_chunks").size() : 0;
        if (reserved > 0) {
            plan.skipped.add(reserved + " reserved chunks are not imported because Dominion has no reservations");
        }
    }

    // ---- legacy JSON, also the layout HyFactions uses ----------------------

    private static void readLegacy(Path dir, Plan plan) throws IOException {
        Map<UUID, String> names = nameCache(dir.resolve("NameCache.json"), plan);
        Map<String, Plan.Faction> byId = new HashMap<>();
        Path parties = dir.resolve("Parties.json");
        if (Data.isFile(parties)) {
            for (Document p : Data.docs(Data.json(parties), "Parties")) {
                String id = Data.str(p, "Id");
                UUID owner = Data.uuid(p.get("Owner"));
                if (id == null || owner == null) {
                    plan.skipped.add("A party in Parties.json has no id or owner");
                    continue;
                }
                Plan.Faction f = party(id, owner, names.get(owner), Data.str(p, "Name"), Data.num(p, "Color"));
                for (Object m : Data.list(p, "Members")) {
                    UUID u = Data.uuid(m);
                    if (u != null) {
                        f.addMember(u, names.get(u), u.equals(owner) ? Rank.LEADER : Rank.MEMBER);
                    }
                }
                byId.put(id, f);
                plan.factions.add(f);
            }
        }
        Path claims = dir.resolve("Claims.json");
        if (Data.isFile(claims)) {
            legacyClaims(Data.json(claims), byId, plan);
        }
    }

    static Map<UUID, String> nameCache(Path file, Plan plan) {
        Map<UUID, String> names = new HashMap<>();
        if (!Data.isFile(file)) {
            return names;
        }
        try {
            for (Document v : Data.docs(Data.json(file), "Values")) {
                UUID id = Data.uuid(v.get("UUID"));
                if (id != null && Data.str(v, "Name") != null) {
                    names.put(id, Data.str(v, "Name"));
                }
            }
        } catch (Exception e) {
            plan.notes.add(file.getFileName() + " could not be read so some member names are unknown");
        }
        return names;
    }

    /** Claims.json shape {Dimensions:[{Dimension, ChunkInfo:[{UUID, ChunkX, ChunkY}]}]} where ChunkY is the Z axis. */
    static void legacyClaims(Document root, Map<String, Plan.Faction> byId, Plan plan) {
        for (Document dim : Data.docs(root, "Dimensions")) {
            String world = Data.str(dim, "Dimension");
            for (Document c : Data.docs(dim, "ChunkInfo")) {
                claim(byId, Data.str(c, "UUID"), world, c.get("ChunkX"), c.get("ChunkY"), plan);
            }
        }
    }

    private static void claim(Map<String, Plan.Faction> byId, String owner, String world, Object x, Object z, Plan plan) {
        Plan.Faction f = owner == null ? null : byId.get(owner);
        if (!(x instanceof Number nx) || !(z instanceof Number nz) || world == null) {
            plan.skipped.add("A claim is missing its world or chunk coordinates");
            return;
        }
        if (f == null) {
            plan.skipped.add("The claim at " + nx + ", " + nz + " in " + world + " belongs to an unknown party " + owner);
            return;
        }
        f.sourceCells++;
        for (long idx : Data.chunks(nx.intValue(), nz.intValue(), 32)) {
            f.addChunk(world, idx);
        }
    }

    private static Plan.Faction party(String id, UUID owner, String ownerName, String partyName, Long color) {
        Plan.Faction f = new Plan.Faction(id);
        String who = ownerName == null || ownerName.isBlank() ? "Party" + Data.shortId(owner) : ownerName.strip();
        f.name = who;
        f.preferredTag = who;
        f.description = partyName == null ? "" : partyName.strip();
        f.color = Data.color(color);
        f.leader = owner;
        f.addMember(owner, ownerName, Rank.LEADER);
        return f;
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString().strip();
    }
}
