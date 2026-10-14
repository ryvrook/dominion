package dev.ryvrook.dominion.events;

import org.bson.Document;

import java.util.List;
import java.util.UUID;

/** A KOTH hill. Mutable state is guarded by the service lock. */
final class Hill {
    final String name;
    final String world;
    final double x;
    final double y;
    final double z;
    final int radius;

    boolean running;
    long startedAt;
    UUID capturer;
    int held;
    String lastWinner = "";
    long lastCapturedAt;
    Schedule schedule;
    long nextRun;
    boolean warned10;
    boolean warned1;
    long lastNotice;
    boolean contested;

    Hill(String name, String world, double x, double y, double z, int radius) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
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

    /** What one second at an open hill did. */
    enum Step { CONTESTED, EMPTY, TAKING, HOLDING, CAPTURED }

    /**
     * Advances an open hill by one second given the distinct factions standing inside it, in the
     * order they were seen. Two or more factions freeze the count. An empty hill loses its
     * capturer. A new single faction starts at one second, and the same faction adds a second until
     * it reaches capSeconds. Messages and rewards are left to the caller.
     */
    Step step(List<UUID> factionsInside, long capSeconds) {
        if (factionsInside.size() > 1) {
            contested = true;
            return Step.CONTESTED;
        }
        contested = false;
        if (factionsInside.isEmpty()) {
            capturer = null;
            held = 0;
            return Step.EMPTY;
        }
        UUID f = factionsInside.get(0);
        Step step;
        if (!f.equals(capturer)) {
            capturer = f;
            held = 1;
            step = Step.TAKING;
        } else {
            held++;
            step = Step.HOLDING;
        }
        return held >= capSeconds ? Step.CAPTURED : step;
    }

    /** A scheduled opening missed by more than this, for example while the server was down, is skipped. */
    static final long MISSED_MILLIS = 5 * 60_000L;

    /** True when the scheduled opening is overdue by more than MISSED_MILLIS. */
    static boolean missed(long nextRun, long now) {
        return nextRun > 0 && now - nextRun > MISSED_MILLIS;
    }

    Document toDocument() {
        Document d = new Document("Name", name).append("World", world).append("X", x).append("Y", y).append("Z", z)
                .append("Radius", radius).append("Running", running).append("StartedAt", startedAt)
                .append("Held", held).append("LastWinner", lastWinner).append("LastCapturedAt", lastCapturedAt)
                .append("NextRun", nextRun);
        if (capturer != null) {
            d.append("Capturer", capturer.toString());
        }
        if (schedule != null) {
            d.append("Schedule", schedule.text());
        }
        return d;
    }

    static Hill fromDocument(Document d) {
        Hill h = new Hill(d.getString("Name"), d.getString("World"), num(d, "X"), num(d, "Y"), num(d, "Z"),
                (int) num(d, "Radius"));
        h.running = Boolean.TRUE.equals(d.getBoolean("Running"));
        h.startedAt = (long) num(d, "StartedAt");
        h.held = (int) num(d, "Held");
        h.lastWinner = d.getString("LastWinner") == null ? "" : d.getString("LastWinner");
        h.lastCapturedAt = (long) num(d, "LastCapturedAt");
        h.nextRun = (long) num(d, "NextRun");
        String cap = d.getString("Capturer");
        h.capturer = cap == null ? null : UUID.fromString(cap);
        String sched = d.getString("Schedule");
        h.schedule = sched == null ? null : Schedule.parse(sched);
        return h;
    }

    static double num(Document d, String key) {
        Object o = d.get(key);
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
