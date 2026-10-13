package dev.ryvrook.dominion.model;

import java.util.UUID;

/** A player's membership record inside a faction. Mutable, guarded by the faction lock. */
public final class Member {
    private final UUID uuid;
    private String name;
    private Rank rank;
    private String title = "";
    private long joinedAt;
    private long lastSeen;
    private long holdUntil;
    private int kills;
    private int deaths;

    public Member(UUID uuid, String name, Rank rank, long joinedAt) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.rank = rank;
        this.joinedAt = joinedAt;
        this.lastSeen = joinedAt;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void setName(String name) { if (name != null) this.name = name; }
    public Rank rank() { return rank; }
    public void setRank(Rank rank) { this.rank = rank; }
    public String title() { return title; }
    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public long joinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }
    public long lastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public long holdUntil() { return holdUntil; }
    public void setHoldUntil(long holdUntil) { this.holdUntil = holdUntil; }
    public boolean onHold(long now) { return now < holdUntil; }
    public int kills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public int deaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
}
