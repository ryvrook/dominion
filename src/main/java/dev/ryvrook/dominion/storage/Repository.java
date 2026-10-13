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

    public Repository(JsonFiles files, Path root, HytaleLogger logger) {
        this.files = files;
        this.root = root;
        this.logger = logger;
    }

    // ---- loading ---------------------------------------------------------

    public void loadAll(Function<UUID, PlayerData> playerFactory) {
        int f = 0;
        for (Path p : files.list(root.resolve("factions"), "*.json")) {
            Document d = files.read(p);
            if (d == null) {
                continue;
            }
            try {
                Faction faction = Faction.fromDocument(d);
                factions.put(faction.id(), faction);
                factionsByTag.put(faction.tag().toLowerCase(Locale.ROOT), faction.id());
                f++;
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Skipping faction file %s", p);
            }
        }
        int c = 0;
        for (Path p : files.list(root.resolve("claims"), "*.json")) {
            Document d = files.read(p);
            if (d == null) {
                continue;
            }
            String world = p.getFileName().toString().replaceFirst("\\.json$", "");
            Long2ObjectMap<ClaimEntry> map = new Long2ObjectOpenHashMap<>();
            for (Map.Entry<String, Object> e : d.entrySet()) {
                if (!(e.getValue() instanceof Document cd)) {
                    continue;
                }
                try {
                    UUID fid = UUID.fromString(cd.getString("F"));
                    if (!factions.containsKey(fid)) {
                        continue;
                    }
                    String by = cd.getString("By");
                    map.put(Long.parseLong(e.getKey()), new ClaimEntry(fid, cd.get("At", Number.class).longValue(),
                            by == null ? null : UUID.fromString(by), cd.getString("Z")));
                    c++;
                } catch (Exception ex) {
                    logger.at(Level.WARNING).withCause(ex).log("Skipping claim %s in %s", e.getKey(), world);
                }
            }
            claims.put(world, map);
        }
        recountClaims();
        logger.at(Level.INFO).log("Dominion loaded %d factions and %d claims", f, c);
    }

    public PlayerData player(UUID uuid, Function<UUID, PlayerData> factory) {
        return players.computeIfAbsent(uuid, id -> {
            Document d = files.read(root.resolve("players").resolve(id + ".json"));
            if (d != null) {
                try {
                    return PlayerData.fromDocument(id, d);
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log("Corrupt player file for %s", id);
                }
            }
            PlayerData p = factory.apply(id);
            p.clearDirty();
            markPlayerDirty(p);
            return p;
        });
    }

    public PlayerData playerIfLoaded(UUID uuid) {
        return players.get(uuid);
    }

    public Collection<PlayerData> loadedPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public void unloadPlayer(UUID uuid) {
        PlayerData p = players.remove(uuid);
        if (p != null) {
            savePlayer(p);
        }
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
            map.values().removeIf(c -> c.factionId().equals(f.id()));
        }
        claimCounts.remove(f.id());
        claimsDirty.mark();
        files.delete(root.resolve("factions").resolve(f.id() + ".json"));
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

    public void saveFaction(Faction f) {
        files.write(root.resolve("factions").resolve(f.id() + ".json"), f.toDocument());
        f.clearDirty();
    }

    public void savePlayer(PlayerData p) {
        files.write(root.resolve("players").resolve(p.uuid() + ".json"), p.toDocument());
        p.clearDirty();
    }

    public void saveClaims() {
        for (Map.Entry<String, Long2ObjectMap<ClaimEntry>> e : claims.entrySet()) {
            Document d = new Document();
            synchronized (e.getValue()) {
                for (Long2ObjectMap.Entry<ClaimEntry> c : e.getValue().long2ObjectEntrySet()) {
                    ClaimEntry v = c.getValue();
                    d.append(Long.toString(c.getLongKey()), new Document("F", v.factionId().toString())
                            .append("At", v.claimedAt()).append("By", v.claimedBy() == null ? null : v.claimedBy().toString())
                            .append("Z", v.zoneId()));
                }
            }
            files.write(root.resolve("claims").resolve(e.getKey() + ".json"), d);
        }
        claimsDirty.clear();
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
