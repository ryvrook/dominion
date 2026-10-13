package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.model.Rank;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything a source reader found, before Dominion conflicts are resolved. */
final class Plan {
    final String source;
    final String label;
    final List<String> searched = new ArrayList<>();
    final List<Faction> factions = new ArrayList<>();
    final List<String> skipped = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    Path found;
    boolean experimental;
    String unsupported;

    Plan(String source, String label) {
        this.source = source;
        this.label = label;
    }

    void looked(Path p) {
        String s = p.toAbsolutePath().normalize().toString();
        if (!searched.contains(s)) {
            searched.add(s);
        }
    }

    record Member(UUID uuid, String name, Rank rank) {}

    /** One faction, party or team from the source. */
    static final class Faction {
        final String sourceId;
        String name;
        String preferredTag;
        String description = "";
        String color;
        long createdAt;
        UUID leader;
        final Map<UUID, Member> members = new LinkedHashMap<>();
        /** World name to Dominion chunk indexes. */
        final Map<String, LinkedHashSet<Long>> chunks = new LinkedHashMap<>();
        int sourceCells;

        Faction(String sourceId) {
            this.sourceId = sourceId;
        }

        void addMember(UUID uuid, String name, Rank rank) {
            if (uuid == null) {
                return;
            }
            Member old = members.get(uuid);
            String keep = name == null || name.isBlank() ? (old == null ? null : old.name()) : name;
            Rank best = old == null || rank.atLeast(old.rank()) ? rank : old.rank();
            members.put(uuid, new Member(uuid, keep, best));
        }

        void addChunk(String world, long index) {
            chunks.computeIfAbsent(world, w -> new LinkedHashSet<>()).add(index);
        }

        int chunkCount() {
            int n = 0;
            for (LinkedHashSet<Long> s : chunks.values()) {
                n += s.size();
            }
            return n;
        }
    }
}
