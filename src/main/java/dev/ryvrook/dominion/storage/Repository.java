package dev.ryvrook.dominion.storage;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.PlayerData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * In-memory source of truth for factions, players and claims, backed by JSON files.
 * Claims are indexed per world by chunk index so every lookup is O(1). Thread safe.
 */
public final class Repository {
    private final JsonFiles files;
    private final Path root;
    private final HytaleLogger logger;

    private final Map<UUID, Faction> factions = new ConcurrentHashMap<>();
    private final Map<String, UUID> factionsByTag = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<String, Long2ObjectMap<ClaimEntry>> claims = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> claimCounts = new ConcurrentHashMap<>();
    private final Dirty claimsDirty = new Dirty();
    /** Claims of factions that did not load, per world, written back unchanged on every save. */
    private final Map<String, Document> orphanClaims = new ConcurrentHashMap<>();

    public Repository(JsonFiles files, Path root, HytaleLogger logger) {
        this.files = files;
        this.root = root;
        this.logger = logger;
    }

    // ---- loading ---------------------------------------------------------

    public void loadAll(Function<UUID, PlayerData> playerFactory) {
        int swept = files.sweep(root);
        if (swept > 0) {
            logger.at(Level.INFO).log("Dominion removed %d unfinished temporary files", swept);
        }
        int f = 0;
        for (Path p : files.list(root.resolve("factions"), "*.json")) {
            Faction faction = files.read(p, Faction::fromDocument);
            if (faction == null) {
                logger.at(Level.WARNING).log("Skipping faction file %s, it could not be read", p.getFileName());
                continue;
            }
            factions.put(faction.id(), faction);
            factionsByTag.put(faction.tag().toLowerCase(Locale.ROOT), faction.id());
            f++;
        }
        int c = 0;
        for (Path p : files.list(root.resolve("claims"), "*.json")) {
            Document d = files.read(p);
            if (d == null) {
                continue;
            }
            String world = p.getFileName().toString().replaceFirst("\\.json$", "");
            Long2ObjectMap<ClaimEntry> map = new Long2ObjectOpenHashMap<>();
            Document orphans = new Document();
            for (Map.Entry<String, Object> e : d.entrySet()) {
                if (!(e.getValue() instanceof Document cd)) {
                    continue;
                }
                try {
                    ClaimEntry entry = claimFromDocument(cd);
                    long index = Long.parseLong(e.getKey());
                    if (!factions.containsKey(entry.factionId())) {
                        orphans.append(e.getKey(), cd);
                        continue;
                    }
                    map.put(index, entry);
                    c++;
                } catch (Exception ex) {
                    logger.at(Level.WARNING).withCause(ex).log("Skipping claim %s in %s", e.getKey(), world);
                }
            }
            if (!orphans.isEmpty()) {
                // The owning faction did not load, maybe its file is missing or could not be read. The
                // claims are not enforced, but they stay in the world file through every save, so they
                // come back once the faction loads again. A copy goes next to the file for admins.
                orphanClaims.put(world, orphans);
                Path aside = p.resolveSibling(world + ".orphans-" + System.currentTimeMillis() + ".txt");
                files.writeText(aside, orphans.toJson(), false);
                logger.at(Level.WARNING).log("Dominion found %d claims in %s whose faction did not load. They are kept in the "
                        + "claims file but not enforced, and a copy was saved to %s", orphans.size(), world, aside.getFileName());
            }
            claims.put(world, map);
        }
        recountClaims();
        logger.at(Level.INFO).log("Dominion loaded %d factions and %d claims", f, c);
    }

    static ClaimEntry claimFromDocument(Document cd) {
        UUID fid = UUID.fromString(cd.getString("F"));
        String by = cd.getString("By");
        Number at = cd.get("At", Number.class);
        return new ClaimEntry(fid, at == null ? 0L : at.longValue(), by == null ? null : UUID.fromString(by), cd.getString("Z"));
    }

    static Document claimToDocument(ClaimEntry v) {
        return new Document("F", v.factionId().toString())
                .append("At", v.claimedAt()).append("By", v.claimedBy() == null ? null : v.claimedBy().toString())
                .append("Z", v.zoneId());
    }

    public PlayerData player(UUID uuid, Function<UUID, PlayerData> factory) {
        return players.computeIfAbsent(uuid, id -> {
            PlayerData loaded = files.read(playerFile(id), d -> PlayerData.fromDocument(id, d));
            if (loaded != null) {
                return loaded;
            }
            PlayerData p = factory.apply(id);
            p.clearDirty();
            markPlayerDirty(p);
            return p;
        });
    }

    private Path playerFile(UUID id) {
        return root.resolve("players").resolve(id + ".json");
    }

    public PlayerData playerIfLoaded(UUID uuid) {
        return players.get(uuid);
    }

    /** True when the player is loaded or has a saved record, without loading or creating one. */
    public boolean knows(UUID uuid) {
        return uuid != null && (players.containsKey(uuid) || java.nio.file.Files.exists(playerFile(uuid)));
    }

    public Collection<PlayerData> loadedPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    /**
     * Saves and drops a loaded player. The file write runs outside the map so loads of other
     * players never wait on the disk. The record is removed only when the save worked, it is still
     * the loaded instance and nothing changed it during the save. Otherwise it stays loaded and
     * dirty and the next autosave tries again. While it stays in the map a load of the same player
     * returns it, so nobody reads the file before the last changes land. Returns true when dropped.
     */
    public boolean unloadPlayer(UUID uuid) {
        PlayerData p = players.get(uuid);
        if (p == null) {
            return true;
        }
        if (!savePlayer(p)) {
            return false;
        }
        boolean[] dropped = {false};
        players.computeIfPresent(uuid, (id, current) -> {
            if (current == p && !current.isDirty()) {
                dropped[0] = true;
                return null;
            }
            return current;
        });
        return dropped[0];
    }

    private void markPlayerDirty(PlayerData p) {
        p.setLastSeen(p.lastSeen());
    }

    // ---- factions --------------------------------------------------------

    public Faction faction(UUID id) {
        return id == null ? null : factions.get(id);
    }

    public Faction factionByTag(String tag) {
        if (tag == null) {
            return null;
        }
        UUID id = factionsByTag.get(tag.toLowerCase(Locale.ROOT));
        return id == null ? null : factions.get(id);
    }

    public boolean tagTaken(String tag) {
        return factionsByTag.containsKey(tag.toLowerCase(Locale.ROOT));
    }

    public Collection<Faction> factions() {
        return Collections.unmodifiableCollection(factions.values());
    }

    public List<Faction> factions(FactionType type) {
        List<Faction> out = new ArrayList<>();
        for (Faction f : factions.values()) {
            if (f.type() == type) {
                out.add(f);
            }
        }
        return out;
    }

    public void addFaction(Faction f) {
        factions.put(f.id(), f);
        factionsByTag.put(f.tag().toLowerCase(Locale.ROOT), f.id());
        saveFaction(f);
    }

    public void retag(Faction f, String newTag) {
        factionsByTag.remove(f.tag().toLowerCase(Locale.ROOT));
        f.setTag(newTag);
        factionsByTag.put(newTag.toLowerCase(Locale.ROOT), f.id());
    }

    public void removeFaction(Faction f) {
        factions.remove(f.id());
        factionsByTag.remove(f.tag().toLowerCase(Locale.ROOT));
        for (Long2ObjectMap<ClaimEntry> map : claims.values()) {
            synchronized (map) {
                map.values().removeIf(c -> c.factionId().equals(f.id()));
            }
        }
        claimCounts.remove(f.id());
        claimsDirty.mark();
        files.delete(factionFile(f.id()));
    }

    // ---- claims ----------------------------------------------------------

    public ClaimEntry claimAt(String world, long chunkIndex) {
        Long2ObjectMap<ClaimEntry> map = claims.get(world);
        if (map == null) {
            return null;
        }
        synchronized (map) {
            return map.get(chunkIndex);
        }
    }

    public Faction ownerAt(String world, long chunkIndex) {
        ClaimEntry c = claimAt(world, chunkIndex);
        return c == null ? null : factions.get(c.factionId());
    }

    public void putClaim(String world, long chunkIndex, ClaimEntry entry) {
        Long2ObjectMap<ClaimEntry> map = claims.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());
        synchronized (map) {
            ClaimEntry old = map.put(chunkIndex, entry);
            if (old != null) {
                claimCounts.merge(old.factionId(), -1, Integer::sum);
            }
        }
        claimCounts.merge(entry.factionId(), 1, Integer::sum);
        claimsDirty.mark();
    }

    /**
     * Compare and set for one claim. Writes updated only when the stored entry still equals
     * expected, both compared by value, so a change made by another thread in between is never
     * overwritten. A null expected means the chunk must be unclaimed, a null updated removes the
     * claim. Claim counts and the dirty flag follow the change. Returns true when it was written.
     */
    public boolean replaceClaim(String world, long chunkIndex, ClaimEntry expected, ClaimEntry updated) {
        Long2ObjectMap<ClaimEntry> map = claims.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());
        synchronized (map) {
            ClaimEntry current = map.get(chunkIndex);
            if (!java.util.Objects.equals(current, expected)) {
                return false;
            }
            if (java.util.Objects.equals(current, updated)) {
                return true;
            }
            if (updated == null) {
                map.remove(chunkIndex);
            } else {
                map.put(chunkIndex, updated);
            }
            if (current != null) {
                claimCounts.merge(current.factionId(), -1, Integer::sum);
            }
            if (updated != null) {
                claimCounts.merge(updated.factionId(), 1, Integer::sum);
            }
        }
        claimsDirty.mark();
        return true;
    }

    public ClaimEntry removeClaim(String world, long chunkIndex) {
        Long2ObjectMap<ClaimEntry> map = claims.get(world);
        if (map == null) {
            return null;
        }
        ClaimEntry old;
        synchronized (map) {
            old = map.remove(chunkIndex);
        }
        if (old != null) {
            claimCounts.merge(old.factionId(), -1, Integer::sum);
            claimsDirty.mark();
        }
        return old;
    }

    public int claimCount(UUID factionId) {
        return claimCounts.getOrDefault(factionId, 0);
    }

    /** Snapshot of a faction's claims in a world as chunk indexes. */
    public long[] claimsOf(String world, UUID factionId) {
        Long2ObjectMap<ClaimEntry> map = claims.get(world);
        if (map == null) {
            return new long[0];
        }
        synchronized (map) {
            return map.long2ObjectEntrySet().stream().filter(e -> e.getValue().factionId().equals(factionId))
                    .mapToLong(Long2ObjectMap.Entry::getLongKey).toArray();
        }
    }

    /** Snapshot of every claim in a world. The returned map is a copy. */
    public Long2ObjectMap<ClaimEntry> claimsIn(String world) {
        Long2ObjectMap<ClaimEntry> map = claims.get(world);
        if (map == null) {
            return new Long2ObjectOpenHashMap<>();
        }
        synchronized (map) {
            return new Long2ObjectOpenHashMap<>(map);
        }
    }

    public Collection<String> claimWorlds() {
        return Collections.unmodifiableCollection(claims.keySet());
    }

    private void recountClaims() {
        claimCounts.clear();
        for (Long2ObjectMap<ClaimEntry> map : claims.values()) {
            for (ClaimEntry c : map.values()) {
                claimCounts.merge(c.factionId(), 1, Integer::sum);
            }
        }
    }

    // ---- saving ----------------------------------------------------------

    /** Orders snapshots of the same record. Taken under the record's monitor together with its document. */
    private final java.util.concurrent.atomic.AtomicLong generations = new java.util.concurrent.atomic.AtomicLong();

    private record Snapshot(long generation, Document document) {}

    /**
     * Clears the dirty flag and takes the document and a generation in one step under the record's
     * monitor, which every mutator of the record also holds. A change made after this marks the
     * record dirty again and is saved next time, and of two saves racing for the same file the one
     * with the newer state always wins, whichever thread reaches the disk last.
     */
    private Snapshot snapshot(Object monitor, Runnable clearDirty, java.util.function.Supplier<Document> document) {
        synchronized (monitor) {
            clearDirty.run();
            return new Snapshot(generations.incrementAndGet(), document.get());
        }
    }

    public boolean saveFaction(Faction f) {
        if (factions.get(f.id()) != f) {
            return false;
        }
        Snapshot snap = snapshot(f, f::clearDirty, f::toDocument);
        // The existence check runs again under the file's lock, the same lock removeFaction's delete
        // takes, so a faction disbanded while a save was queued is never written back.
        JsonFiles.Versioned v = files.writeVersioned(factionFile(f.id()), snap.generation(),
                () -> factions.get(f.id()) == f, snap.document());
        if (v == JsonFiles.Versioned.FAILED) {
            f.touchZones();
        }
        return v == JsonFiles.Versioned.WRITTEN || v == JsonFiles.Versioned.SUPERSEDED;
    }

    private Path factionFile(UUID id) {
        return root.resolve("factions").resolve(id + ".json");
    }

    public boolean savePlayer(PlayerData p) {
        Snapshot snap = snapshot(p, p::clearDirty, p::toDocument);
        JsonFiles.Versioned v = files.writeVersioned(playerFile(p.uuid()), snap.generation(), () -> true, snap.document());
        if (v == JsonFiles.Versioned.FAILED) {
            markPlayerDirty(p);
        }
        return v != JsonFiles.Versioned.FAILED;
    }

    public boolean saveClaims() {
        claimsDirty.clear();
        boolean ok = true;
        for (Map.Entry<String, Long2ObjectMap<ClaimEntry>> e : claims.entrySet()) {
            Document d = new Document();
            synchronized (e.getValue()) {
                for (Long2ObjectMap.Entry<ClaimEntry> c : e.getValue().long2ObjectEntrySet()) {
                    d.append(Long.toString(c.getLongKey()), claimToDocument(c.getValue()));
                }
            }
            Document orphans = orphanClaims.get(e.getKey());
            if (orphans != null) {
                // A live claim made since start wins over a kept one on the same chunk.
                orphans.forEach((k, v) -> d.putIfAbsent(k, v));
            }
            ok &= files.write(root.resolve("claims").resolve(e.getKey() + ".json"), d);
        }
        if (!ok) {
            claimsDirty.mark();
        }
        return ok;
    }

    /** True when a claim changed since the last claims save. */
    public boolean claimsDirty() {
        return claimsDirty.is();
    }

    public void saveDirty() {
        for (Faction f : factions.values()) {
            if (f.isDirty()) {
                saveFaction(f);
            }
        }
        for (PlayerData p : players.values()) {
            if (p.isDirty()) {
                savePlayer(p);
            }
        }
        if (claimsDirty.is()) {
            saveClaims();
        }
    }

    public void saveAll() {
        for (Faction f : factions.values()) {
            saveFaction(f);
        }
        for (PlayerData p : players.values()) {
            savePlayer(p);
        }
        saveClaims();
    }

    public Path root() {
        return root;
    }
}
