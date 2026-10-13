package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.UUID;

/** A siege in progress or finished. Mutable fields are guarded by the siege service. */
public final class Siege {
    public enum Phase { CAPTURING, BREACHED, FAILED, DEFENDED, EXPIRED }

    private final UUID id;
    private final UUID attacker;
    private final UUID defender;
    private final String world;
    private final long chunkIndex;
    private final double x;
    private final double y;
    private final double z;
    private final long startedAt;
    private final UUID startedBy;
    private Phase phase = Phase.CAPTURING;
    private long phaseEndsAt;
    private long attackerAbsentSince;
    private long defenderPresentSince;
    private int blocksBroken;
    private int containersOpened;
    private int attackerDeaths;
    private int defenderDeaths;

    public Siege(UUID id, UUID attacker, UUID defender, String world, long chunkIndex, double x, double y, double z,
                 long startedAt, UUID startedBy) {
        this.id = id;
        this.attacker = attacker;
        this.defender = defender;
        this.world = world;
        this.chunkIndex = chunkIndex;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startedAt = startedAt;
        this.startedBy = startedBy;
    }

    public UUID id() { return id; }
    public UUID attacker() { return attacker; }
    public UUID defender() { return defender; }
    public String world() { return world; }
    public long chunkIndex() { return chunkIndex; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public long startedAt() { return startedAt; }
    public UUID startedBy() { return startedBy; }
    public synchronized Phase phase() { return phase; }
    public synchronized void setPhase(Phase phase, long endsAt) { this.phase = phase; this.phaseEndsAt = endsAt; }
    public synchronized long phaseEndsAt() { return phaseEndsAt; }
    public synchronized long attackerAbsentSince() { return attackerAbsentSince; }
    public synchronized void setAttackerAbsentSince(long t) { attackerAbsentSince = t; }
    public synchronized long defenderPresentSince() { return defenderPresentSince; }
    public synchronized void setDefenderPresentSince(long t) { defenderPresentSince = t; }
    public synchronized int blocksBroken() { return blocksBroken; }
    public synchronized void addBlocksBroken(int n) { blocksBroken += n; }
    public synchronized int containersOpened() { return containersOpened; }
    public synchronized void addContainersOpened(int n) { containersOpened += n; }
    public synchronized int attackerDeaths() { return attackerDeaths; }
    public synchronized void addAttackerDeath() { attackerDeaths++; }
    public synchronized int defenderDeaths() { return defenderDeaths; }
    public synchronized void addDefenderDeath() { defenderDeaths++; }
    public synchronized boolean active() { return phase == Phase.CAPTURING || phase == Phase.BREACHED; }

    public synchronized Document toDocument() {
        return new Document("Id", id.toString()).append("Attacker", attacker.toString()).append("Defender", defender.toString())
                .append("World", world).append("Chunk", chunkIndex).append("X", x).append("Y", y).append("Z", z)
                .append("StartedAt", startedAt).append("StartedBy", startedBy == null ? null : startedBy.toString())
                .append("Phase", phase.name()).append("PhaseEndsAt", phaseEndsAt)
                .append("BlocksBroken", blocksBroken).append("ContainersOpened", containersOpened)
                .append("AttackerDeaths", attackerDeaths).append("DefenderDeaths", defenderDeaths);
    }

    public static Siege fromDocument(Document d) {
        String by = d.getString("StartedBy");
        Siege s = new Siege(UUID.fromString(d.getString("Id")), UUID.fromString(d.getString("Attacker")),
                UUID.fromString(d.getString("Defender")), d.getString("World"), d.get("Chunk", Number.class).longValue(),
                d.get("X", Number.class).doubleValue(), d.get("Y", Number.class).doubleValue(), d.get("Z", Number.class).doubleValue(),
                d.get("StartedAt", Number.class).longValue(), by == null ? null : UUID.fromString(by));
        s.phase = Phase.valueOf(d.getString("Phase"));
        s.phaseEndsAt = d.get("PhaseEndsAt", Number.class).longValue();
        s.blocksBroken = d.get("BlocksBroken", Number.class).intValue();
        s.containersOpened = d.get("ContainersOpened", Number.class).intValue();
        s.attackerDeaths = d.get("AttackerDeaths", Number.class).intValue();
        s.defenderDeaths = d.get("DefenderDeaths", Number.class).intValue();
        return s;
    }
}
