package dev.ryvrook.dominion.model;

import org.bson.Document;

/** A stored position with facing, independent of live world objects. */
public record Location(String world, double x, double y, double z, float yaw, float pitch) {

    public long chunkIndex() {
        return com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
    }

    public Document toDocument() {
        return new Document("World", world).append("X", x).append("Y", y).append("Z", z)
                .append("Yaw", (double) yaw).append("Pitch", (double) pitch);
    }

    public static Location fromDocument(Document d) {
        if (d == null) {
            return null;
        }
        return new Location(d.getString("World"),
                d.get("X", Number.class).doubleValue(),
                d.get("Y", Number.class).doubleValue(),
                d.get("Z", Number.class).doubleValue(),
                d.get("Yaw", Number.class).floatValue(),
                d.get("Pitch", Number.class).floatValue());
    }
}
