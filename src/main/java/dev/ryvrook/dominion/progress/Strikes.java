package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin strikes per faction, stored together in progress/strikes.json. */
public final class Strikes {
    public record Strike(int points, String reason, UUID by, String byName, long at) {}

    private final JsonFiles files;
    private final Path file;
    private final Map<UUID, List<Strike>> strikes = new HashMap<>();

    Strikes(JsonFiles files, Path file) {
        this.files = files;
        this.file = file;
    }

    synchronized void load() {
        strikes.clear();
        Document d = files.read(file);
        if (d == null) {
            return;
        }
        for (Map.Entry<String, Object> e : d.entrySet()) {
            if (!(e.getValue() instanceof List<?> list)) {
                continue;
            }
            try {
                UUID faction = UUID.fromString(e.getKey());
                List<Strike> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Document s) {
                        String by = s.getString("By");
                        Number points = s.get("Points", Number.class);
                        Number at = s.get("At", Number.class);
                        out.add(new Strike(points == null ? 1 : points.intValue(), s.getString("Reason"),
                                by == null ? null : UUID.fromString(by), s.getString("ByName"), at == null ? 0 : at.longValue()));
                    }
                }
                strikes.put(faction, out);
            } catch (Exception ignored) {
                // a bad entry is skipped, the rest still loads
            }
        }
    }

    private void save() {
        Document d = new Document();
        for (Map.Entry<UUID, List<Strike>> e : strikes.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            List<Document> list = new ArrayList<>();
            for (Strike s : e.getValue()) {
                list.add(new Document("Points", s.points()).append("Reason", s.reason())
                        .append("By", s.by() == null ? null : s.by().toString()).append("ByName", s.byName()).append("At", s.at()));
            }
            d.append(e.getKey().toString(), list);
        }
        files.write(file, d);
    }

    synchronized List<Strike> of(UUID faction) {
        List<Strike> list = strikes.get(faction);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Sum of strike points, each point counts as one strike for the value penalty. */
    synchronized int weight(UUID faction) {
        int total = 0;
        for (Strike s : strikes.getOrDefault(faction, List.of())) {
            total += Math.max(0, s.points());
        }
        return total;
    }

    /** Adds a strike and returns its one based number, the index unstrike takes. */
    synchronized int add(UUID faction, Strike strike) {
        List<Strike> list = strikes.computeIfAbsent(faction, k -> new ArrayList<>());
        list.add(strike);
        save();
        return list.size();
    }

    /** Removes by zero based position. Returns the removed strike or null. */
    synchronized Strike remove(UUID faction, int position) {
        List<Strike> list = strikes.get(faction);
        if (list == null || position < 0 || position >= list.size()) {
            return null;
        }
        Strike s = list.remove(position);
        save();
        return s;
    }

    synchronized void forget(UUID faction) {
        if (strikes.remove(faction) != null) {
            save();
        }
    }
}
