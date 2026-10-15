package dev.ryvrook.dominion.events;

import dev.ryvrook.dominion.text.Messages;

import org.bson.Document;

/** A supply drop inside a war zone. Guarded by the service lock. */
final class Envoy {
    final String id;
    final String zone;
    final String world;
    final int x;
    volatile int y;
    final int z;
    final long spawnedAt;
    final long expiresAt;
    volatile boolean placed;
    volatile String block = "";

    Envoy(String id, String zone, String world, int x, int y, int z, long spawnedAt, long expiresAt) {
        this.id = id;
        this.zone = zone;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnedAt = spawnedAt;
        this.expiresAt = expiresAt;
    }

    String where() {
        return Messages.get(EventsText.WHERE, "x", x, "y", y, "z", z, "world", world);
    }

    Document toDocument() {
        return new Document("Id", id).append("Zone", zone).append("World", world).append("X", x).append("Y", y)
                .append("Z", z).append("SpawnedAt", spawnedAt).append("ExpiresAt", expiresAt)
                .append("Placed", placed).append("Block", block);
    }

    static Envoy fromDocument(Document d) {
        Envoy e = new Envoy(d.getString("Id"), d.getString("Zone"), d.getString("World"), (int) Hill.num(d, "X"),
                (int) Hill.num(d, "Y"), (int) Hill.num(d, "Z"), (long) Hill.num(d, "SpawnedAt"),
                (long) Hill.num(d, "ExpiresAt"));
        e.placed = Boolean.TRUE.equals(d.getBoolean("Placed"));
        e.block = d.getString("Block") == null ? "" : d.getString("Block");
        return e;
    }
}
