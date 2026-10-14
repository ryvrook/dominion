package dev.ryvrook.dominion.events;

import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A persistent outpost with control from 0 to 100 per faction. Guarded by the service lock. */
final class Outpost {
    final String name;
    final String world;
    final double x;
    final double y;
    final double z;
    final int radius;
    final String boost;
    final double multiplier;

    UUID holder;
    long heldSince;
    final Map<UUID, Double> control = new HashMap<>();
    boolean contested;

    Outpost(String name, String world, double x, double y, double z, int radius, String boost, double multiplier) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.boost = boost;
        this.multiplier = multiplier;
    }

    boolean inside(String w, double px, double py, double pz) {
        if (!world.equals(w)) {
            return false;
        }
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dz * dz <= (double) radius * radius && Math.abs(dy) <= Math.max(6, radius);
    }

    double controlOf(UUID faction) {
        return faction == null ? 0 : control.getOrDefault(faction, 0.0);
    }

    /**
     * Advances control by one second. counts holds the players of each faction inside the outpost.
     * A lone faction gains gain percent per player up to 100 while every other faction decays. An
     * empty outpost decays for everyone. Two or more factions freeze it and mark it contested.
     * Returns the faction that takes the outpost this second, or null when the holder stays.
     */
    UUID step(Map<UUID, Integer> counts, double gain, double decay) {
        contested = counts.size() > 1;
        if (counts.isEmpty()) {
            decayAll(decay, null);
            return null;
        }
        if (counts.size() > 1) {
            return null;
        }
        Map.Entry<UUID, Integer> only = counts.entrySet().iterator().next();
        UUID f = only.getKey();
        double v = Math.min(100.0, controlOf(f) + Math.max(0, gain) * only.getValue());
        control.put(f, v);
        decayAll(decay, f);
        if (!f.equals(holder) && v >= 100.0 && controlOf(holder) < 100.0) {
            return f;
        }
        return null;
    }

    private void decayAll(double decay, UUID except) {
        if (decay <= 0 || control.isEmpty()) {
            return;
        }
        control.replaceAll((k, v) -> k.equals(except) ? v : Math.max(0, v - decay));
        control.entrySet().removeIf(e -> e.getValue() <= 0 && !e.getKey().equals(holder));
    }

    Document toDocument() {
        Document c = new Document();
        for (Map.Entry<UUID, Double> e : control.entrySet()) {
            c.append(e.getKey().toString(), e.getValue());
        }
        Document d = new Document("Name", name).append("World", world).append("X", x).append("Y", y).append("Z", z)
                .append("Radius", radius).append("Boost", boost).append("Multiplier", multiplier)
                .append("HeldSince", heldSince).append("Control", c);
        if (holder != null) {
            d.append("Holder", holder.toString());
        }
        return d;
    }

    static Outpost fromDocument(Document d) {
        Outpost o = new Outpost(d.getString("Name"), d.getString("World"), Hill.num(d, "X"), Hill.num(d, "Y"),
                Hill.num(d, "Z"), (int) Hill.num(d, "Radius"), d.getString("Boost"), Hill.num(d, "Multiplier"));
        String h = d.getString("Holder");
        o.holder = h == null ? null : UUID.fromString(h);
        o.heldSince = (long) Hill.num(d, "HeldSince");
        Object raw = d.get("Control");
        if (raw instanceof Document c) {
            for (String k : c.keySet()) {
                Object v = c.get(k);
                if (v instanceof Number n) {
                    o.control.put(UUID.fromString(k), n.doubleValue());
                }
            }
        }
        return o;
    }
}
