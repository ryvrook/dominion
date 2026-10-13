package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * A named sub area inside a faction's claims with its own permission grid. Chunks are assigned
 * through ClaimEntry.zoneId. A missing grid entry falls back to the faction grid.
 */
public final class Zone {
    private final String id;
    private String name;
    private final EnumMap<Rank, EnumSet<FactionAction>> rankPerms = new EnumMap<>(Rank.class);
    private final EnumMap<Relation, EnumSet<FactionAction>> relationPerms = new EnumMap<>(Relation.class);

    public Zone(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() { return id; }
    public synchronized String name() { return name; }
    public synchronized void setName(String n) { name = n; }

    /** Null when the zone has no override for this rank. */
    public synchronized Boolean rankCan(Rank rank, FactionAction action) {
        EnumSet<FactionAction> set = rankPerms.get(rank);
        return set == null ? null : set.contains(action);
    }
    public synchronized Boolean relationCan(Relation relation, FactionAction action) {
        EnumSet<FactionAction> set = relationPerms.get(relation);
        return set == null ? null : set.contains(action);
    }
    public synchronized void setRankPerm(Rank rank, FactionAction action, boolean allowed) {
        EnumSet<FactionAction> set = rankPerms.computeIfAbsent(rank, r -> EnumSet.noneOf(FactionAction.class));
        if (allowed) set.add(action); else set.remove(action);
    }
    public synchronized void setRelationPerm(Relation relation, FactionAction action, boolean allowed) {
        EnumSet<FactionAction> set = relationPerms.computeIfAbsent(relation, r -> EnumSet.noneOf(FactionAction.class));
        if (allowed) set.add(action); else set.remove(action);
    }
    public synchronized boolean hasRankOverride(Rank rank) { return rankPerms.containsKey(rank); }
    public synchronized boolean hasRelationOverride(Relation relation) { return relationPerms.containsKey(relation); }
    public synchronized void clearOverrides() { rankPerms.clear(); relationPerms.clear(); }

    public synchronized Document toDocument() {
        Document rp = new Document();
        rankPerms.forEach((k, v) -> rp.append(k.name(), v.stream().map(Enum::name).toList()));
        Document lp = new Document();
        relationPerms.forEach((k, v) -> lp.append(k.name(), v.stream().map(Enum::name).toList()));
        return new Document("Id", id).append("Name", name).append("RankPerms", rp).append("RelationPerms", lp);
    }

    @SuppressWarnings("unchecked")
    public static Zone fromDocument(Document d) {
        Zone z = new Zone(d.getString("Id"), d.getString("Name"));
        Document rp = d.get("RankPerms", Document.class);
        if (rp != null) rp.forEach((k, v) -> z.rankPerms.put(Rank.valueOf(k), toSet((List<String>) v)));
        Document lp = d.get("RelationPerms", Document.class);
        if (lp != null) lp.forEach((k, v) -> z.relationPerms.put(Relation.valueOf(k), toSet((List<String>) v)));
        return z;
    }

    private static EnumSet<FactionAction> toSet(List<String> names) {
        EnumSet<FactionAction> set = EnumSet.noneOf(FactionAction.class);
        for (String n : names) {
            try { set.add(FactionAction.valueOf(n)); } catch (IllegalArgumentException ignored) { }
        }
        return set;
    }
}
