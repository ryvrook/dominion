package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.UUID;

/** Per-player state that is not tied to a faction. Synchronized like Faction. */
public final class PlayerData {
    public enum Channel { PUBLIC, FACTION, ALLY, OFFICER, NATION }

    private final UUID uuid;
    private String name;
    private UUID factionId;
    private double power;
    private long lastSeen;
    private long lastDeath;
    private Channel channel = Channel.PUBLIC;
    private boolean autoClaim;
    private boolean hud = true;
    private boolean alerts = true;
    private boolean tutorialSeen;
    private boolean mapHidden;
    private double balance;
    /** True once the internal balance changed through a deposit, withdrawal or set. Null for a record that never moved money. */
    private Boolean balanceTouched;
    private int kills;
    private int deaths;
    private long combatTagUntil;
    private long lastHomeTeleport;
    private volatile boolean dirty;

    public PlayerData(UUID uuid, String name, double startingPower, double startingBalance) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.power = startingPower;
        this.balance = startingBalance;
        this.lastSeen = System.currentTimeMillis();
    }

    public UUID uuid() { return uuid; }
    public synchronized String name() { return name; }
    public synchronized void setName(String n) { if (n != null && !n.equals(name)) { name = n; dirty = true; } }
    public synchronized UUID factionId() { return factionId; }
    public synchronized void setFactionId(UUID id) { factionId = id; dirty = true; }
    public synchronized double power() { return power; }
    public synchronized void setPower(double p) { power = p; dirty = true; }
    public synchronized long lastSeen() { return lastSeen; }
    public synchronized void setLastSeen(long t) { lastSeen = t; dirty = true; }
    public synchronized long lastDeath() { return lastDeath; }
    public synchronized void setLastDeath(long t) { lastDeath = t; dirty = true; }
    public synchronized Channel channel() { return channel; }
    public synchronized void setChannel(Channel c) { channel = c; dirty = true; }
    public synchronized boolean autoClaim() { return autoClaim; }
    public synchronized void setAutoClaim(boolean b) { autoClaim = b; dirty = true; }
    public synchronized boolean hud() { return hud; }
    public synchronized void setHud(boolean b) { hud = b; dirty = true; }
    public synchronized boolean alerts() { return alerts; }
    public synchronized void setAlerts(boolean b) { alerts = b; dirty = true; }
    public synchronized boolean tutorialSeen() { return tutorialSeen; }
    public synchronized void setTutorialSeen(boolean b) { tutorialSeen = b; dirty = true; }
    public synchronized boolean mapHidden() { return mapHidden; }
    public synchronized void setMapHidden(boolean b) { mapHidden = b; dirty = true; }
    public synchronized double balance() { return balance; }
    public synchronized void setBalance(double b) { balance = Math.max(0, b); balanceTouched = Boolean.TRUE; dirty = true; }
    /** True once the internal balance moved. A record without the flag, new or from an older file, answers false. */
    public synchronized boolean balanceTouched() { return Boolean.TRUE.equals(balanceTouched); }
    public synchronized int kills() { return kills; }
    public synchronized void addKill() { kills++; dirty = true; }
    public synchronized int deaths() { return deaths; }
    public synchronized void addDeath() { deaths++; dirty = true; }
    public synchronized long combatTagUntil() { return combatTagUntil; }
    public synchronized void setCombatTagUntil(long t) { combatTagUntil = t; }
    public synchronized boolean combatTagged(long now) { return now < combatTagUntil; }
    public synchronized long lastHomeTeleport() { return lastHomeTeleport; }
    public synchronized void setLastHomeTeleport(long t) { lastHomeTeleport = t; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public synchronized Document toDocument() {
        return new Document("Uuid", uuid.toString()).append("Name", name)
                .append("Faction", factionId == null ? null : factionId.toString())
                .append("Power", power).append("LastSeen", lastSeen).append("LastDeath", lastDeath)
                .append("Channel", channel.name()).append("AutoClaim", autoClaim).append("Hud", hud)
                .append("Alerts", alerts).append("TutorialSeen", tutorialSeen).append("MapHidden", mapHidden)
                .append("Balance", balance).append("Kills", kills).append("Deaths", deaths)
                .append("BalanceTouched", balanceTouched);
    }

    public static PlayerData fromDocument(UUID uuid, Document d) {
        // A file without a name, or with a non text name, loads with an empty name so callers can
        // compare names without a null check. The next login fills it in.
        Object rawName = d.get("Name");
        PlayerData p = new PlayerData(uuid, rawName instanceof String s ? s : "", num(d, "Power"), num(d, "Balance"));
        String f = d.getString("Faction");
        p.factionId = f == null ? null : UUID.fromString(f);
        p.balanceTouched = Boolean.TRUE.equals(d.get("BalanceTouched")) ? Boolean.TRUE : null;
        p.lastSeen = (long) num(d, "LastSeen");
        p.lastDeath = (long) num(d, "LastDeath");
        try {
            p.channel = Channel.valueOf(d.getString("Channel") == null ? "PUBLIC" : d.getString("Channel"));
        } catch (IllegalArgumentException e) {
            p.channel = Channel.PUBLIC;
        }
        p.autoClaim = Boolean.TRUE.equals(d.get("AutoClaim"));
        p.hud = !Boolean.FALSE.equals(d.get("Hud"));
        p.alerts = !Boolean.FALSE.equals(d.get("Alerts"));
        p.tutorialSeen = Boolean.TRUE.equals(d.get("TutorialSeen"));
        p.mapHidden = Boolean.TRUE.equals(d.get("MapHidden"));
        p.kills = (int) num(d, "Kills");
        p.deaths = (int) num(d, "Deaths");
        p.dirty = false;
        return p;
    }

    private static double num(Document d, String key) {
        Object v = d.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
