package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.model.Rank;
import org.bson.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ultimate Factions writes three JSON files to plugins_data/HytaleFactions under the server
 * working directory (its readme says plugins_data/UltimateFactions). factions.json holds the
 * factions with a member UUID to role map, claims.json holds claims keyed by world UUID, and
 * players.json holds last known names. Its claims use 16 block cells, not 32 block Hytale chunks,
 * so several of its cells fold into one Dominion chunk.
 */
final class UltimateFactionsSource implements Source {
    static final int CELL = 16;

    @Override public String id() { return "ultimatefactions"; }
    @Override public String label() { return "Ultimate Factions"; }

    @Override
    public void read(Locator loc, Plan plan) throws IOException {
        Set<Path> folders = new LinkedHashSet<>();
        Path data = loc.work.resolve("plugins_data");
        folders.add(data.resolve("HytaleFactions"));
        folders.add(data.resolve("UltimateFactions"));
        folders.addAll(Data.foldersLike(data, "faction"));
        folders.addAll(loc.modFolders(List.of("com.tinky_Ultimate Faction", "UltimateFactions", "HytaleFactions"), "ultimate", "hytalefactions"));
        Path dir = null;
        for (Path f : folders) {
            plan.looked(f.resolve("factions.json"));
            if (dir == null && Data.isFile(f.resolve("factions.json"))) {
                dir = f;
            }
        }
        if (dir == null) {
            return;
        }
        plan.found = dir;
        Map<UUID, String> names = new HashMap<>();
        Path playersFile = dir.resolve("players.json");
        if (Data.isFile(playersFile)) {
            try {
                Document players = Data.doc(Data.json(playersFile), "players");
                if (players != null) {
                    for (Map.Entry<String, Object> e : players.entrySet()) {
                        UUID id = Data.uuid(e.getKey());
                        if (id != null && e.getValue() instanceof Document p && Data.str(p, "lastKnownName") != null) {
                            names.put(id, Data.str(p, "lastKnownName"));
                        }
                    }
                }
            } catch (Exception e) {
                plan.notes.add("players.json could not be read so some member names are unknown");
            }
        }
        Map<String, Plan.Faction> byId = new HashMap<>();
        for (Document d : Data.docs(Data.json(dir.resolve("factions.json")), "factions")) {
            String id = Data.str(d, "id");
            String name = Data.str(d, "name");
            if (id == null || name == null) {
                plan.skipped.add("A faction in factions.json has no id or name");
                continue;
            }
            Plan.Faction f = new Plan.Faction(id);
            f.name = name;
            f.createdAt = Data.epochMillis(Data.num(d, "createdAt"));
            f.leader = Data.uuid(d.get("leaderUuid"));
            Document members = Data.doc(d, "members");
            if (members != null) {
                for (Map.Entry<String, Object> e : members.entrySet()) {
                    UUID uuid = Data.uuid(e.getKey());
                    if (uuid == null) {
                        plan.skipped.add("A member of " + name + " has no valid UUID");
                        continue;
                    }
                    String role = String.valueOf(e.getValue()).toUpperCase(Locale.ROOT);
                    Rank rank = uuid.equals(f.leader) || role.equals("LEADER") ? Rank.LEADER
                            : role.equals("OFFICER") ? Rank.OFFICER : Rank.MEMBER;
                    f.addMember(uuid, names.get(uuid), rank);
                    if (rank == Rank.LEADER && f.leader == null) {
                        f.leader = uuid;
                    }
                }
            }
            if (f.leader != null && !f.members.containsKey(f.leader)) {
                f.addMember(f.leader, names.get(f.leader), Rank.LEADER);
            }
            byId.put(id, f);
            plan.factions.add(f);
        }
        Path claimsFile = dir.resolve("claims.json");
        if (!Data.isFile(claimsFile)) {
            plan.notes.add("claims.json was not found so no land is imported");
            return;
        }
        int zones = 0;
        List<String> missingWorlds = new ArrayList<>();
        for (Document c : Data.docs(Data.json(claimsFile), "claims")) {
            String fid = Data.str(c, "factionId");
            if ("__SAFEZONE__".equals(fid) || "__WARZONE__".equals(fid)) {
                zones++;
                continue;
            }
            Plan.Faction f = byId.get(fid);
            Long x = Data.num(c, "chunkX");
            Long z = Data.num(c, "chunkZ");
            String key = Data.str(c, "worldKey");
            if (f == null || x == null || z == null) {
                plan.skipped.add("A claim at " + x + ", " + z + " belongs to an unknown faction " + fid);
                continue;
            }
            String world = Source.worldName(key);
            if (world == null) {
                if (!missingWorlds.contains(key)) {
                    missingWorlds.add(key);
                }
                plan.skipped.add("A claim of " + f.name + " is in world " + key + " which is not loaded, so its name is unknown");
                continue;
            }
            f.sourceCells++;
            for (long idx : Data.chunks(x.intValue(), z.intValue(), CELL)) {
                f.addChunk(world, idx);
            }
        }
        if (zones > 0) {
            plan.skipped.add(zones + " safe zone and war zone cells are not imported. Recreate them with Dominion zone commands");
        }
        if (!missingWorlds.isEmpty()) {
            plan.notes.add("Claims are stored by world UUID. Load those worlds and preview again to include them");
        }
        plan.notes.add("Ultimate Factions claims 16 block cells. Each Dominion chunk is " + dev.ryvrook.dominion.util.Keys.chunkSize()
                + " blocks wide, so a chunk is claimed in full when any of its cells was claimed");
    }
}
