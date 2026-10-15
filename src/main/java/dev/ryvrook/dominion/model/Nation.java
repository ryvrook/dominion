package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A group of allied factions with a shared chat and shared outposts. */
public final class Nation {
    private final UUID id;
    private String tag;
    private String name;
    private String color;
    private UUID leaderFaction;
    private final List<UUID> members = new ArrayList<>();
    private final List<UUID> invites = new ArrayList<>();
    private final long createdAt;
    private volatile boolean dirty;

    public Nation(UUID id, String tag, String name, UUID leaderFaction, long createdAt) {
        this.id = id;
        this.tag = tag;
        this.name = name == null || name.isBlank() ? tag : name;
        this.color = "#d99cff";
        this.leaderFaction = leaderFaction;
        this.createdAt = createdAt;
        this.members.add(leaderFaction);
        this.dirty = true;
    }

    public UUID id() { return id; }
    public synchronized String tag() { return tag; }
    public synchronized void setTag(String t) { tag = t; dirty = true; }
    public synchronized String name() { return name; }
    public synchronized void setName(String n) { name = n; dirty = true; }
    /** Tag and name for display, or the name alone when the tag would only repeat it. */
    public synchronized String label() { return Faction.label(tag, name); }
    /** True when the tag adds something next to the name. */
    public synchronized boolean tagDiffers() { return Faction.tagDiffers(tag, name); }
    public synchronized String color() { return color; }
    public synchronized void setColor(String c) { color = c; dirty = true; }
    public synchronized UUID leaderFaction() { return leaderFaction; }
    public synchronized void setLeaderFaction(UUID f) { leaderFaction = f; dirty = true; }
    public synchronized List<UUID> members() { return new ArrayList<>(members); }
    public synchronized boolean isMember(UUID faction) { return members.contains(faction); }
    public synchronized void addMember(UUID faction) { if (!members.contains(faction)) { members.add(faction); dirty = true; } }
    public synchronized boolean removeMember(UUID faction) { boolean r = members.remove(faction); if (r) dirty = true; return r; }
    public synchronized List<UUID> invites() { return new ArrayList<>(invites); }
    public synchronized void invite(UUID faction) { if (!invites.contains(faction)) { invites.add(faction); dirty = true; } }
    public synchronized boolean uninvite(UUID faction) { boolean r = invites.remove(faction); if (r) dirty = true; return r; }
    public long createdAt() { return createdAt; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public synchronized Document toDocument() {
        return new Document("Id", id.toString()).append("Tag", tag).append("Name", name).append("Color", color)
                .append("Leader", leaderFaction == null ? null : leaderFaction.toString())
                .append("Members", members.stream().map(UUID::toString).toList())
                .append("Invites", invites.stream().map(UUID::toString).toList())
                .append("CreatedAt", createdAt);
    }

    public static Nation fromDocument(Document d) {
        UUID leaderId = null;
        try {
            leaderId = d.get("Leader") instanceof String s ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException ignored) {
            // reconcile picks a new leading faction
        }
        Nation n = new Nation(UUID.fromString(d.getString("Id")), d.getString("Tag"), d.getString("Name"),
                leaderId, d.get("CreatedAt") instanceof Number c ? c.longValue() : 0L);
        n.members.clear();
        // A damaged id is skipped alone so the rest of the nation still loads.
        for (Object s : d.get("Members") instanceof List<?> l ? l : List.of()) {
            try { UUID u = UUID.fromString(String.valueOf(s)); if (!n.members.contains(u)) n.members.add(u); } catch (IllegalArgumentException ignored) { }
        }
        for (Object s : d.get("Invites") instanceof List<?> l ? l : List.of()) {
            try { UUID u = UUID.fromString(String.valueOf(s)); if (!n.invites.contains(u)) n.invites.add(u); } catch (IllegalArgumentException ignored) { }
        }
        if (d.getString("Color") != null) n.color = d.getString("Color");
        n.dirty = false;
        return n;
    }
}
