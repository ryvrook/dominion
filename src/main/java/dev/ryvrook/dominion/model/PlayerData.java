package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
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
    /** Id of the furthest tutorial step the player reached, null before the tutorial ever opened. */
    private String tutorialStep;
    /**
     * True once the player finished or skipped the tutorial, false after it opened or was reset. Null for a
     * record from an older file, which counts as finished when that file says the old tutorial was seen.
     */
    private Boolean tutorialFinished;
    private boolean mapHidden;
    private double balance;
    /**
     * True once the internal balance changed through a deposit, withdrawal or set. False for a record made by this
     * version that never moved money, so its balance is the starting grant whatever the setting says now. Null for
     * a record from an older file that never moved money since.
     */
    private Boolean balanceTouched;
    private int kills;
    private int deaths;
    /**
     * Memory of the counters already credited to Feats and TrackerAPI, such as the highest upgrade level credited
     * and the factions whose land the player entered once. Kept here rather than on the faction because the player
     * carries it across factions. Saved under Counters.
     */
    private final Map<String, Long> counters = new HashMap<>();
    private long combatTagUntil;
    private long lastHomeTeleport;
    private volatile boolean dirty;

    public PlayerData(UUID uuid, String name, double startingPower, double startingBalance) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.power = startingPower;
        this.balance = startingBalance;
        this.balanceTouched = Boolean.FALSE;
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
    public synchronized String tutorialStep() { return tutorialStep; }
    public synchronized void setTutorialStep(String id) {
        if (id != null && !id.equals(tutorialStep)) { tutorialStep = id; dirty = true; }
    }
    /** True once the tutorial was finished or skipped. An older record that saw the old tutorial counts as finished. */
    public synchronized boolean tutorialFinished() { return tutorialFinished != null ? tutorialFinished : tutorialSeen; }
    /** Sets the flag and answers true when it changed, so a finish is credited once. */
    public synchronized boolean setTutorialFinished(boolean b) {
        boolean changed = tutorialFinished() != b;
        tutorialFinished = b;
        tutorialSeen = tutorialSeen || b;
        dirty = true;
        return changed;
    }
    /** Forgets the tutorial progress so it opens again from the first step on the next join. */
    public synchronized void resetTutorial() { tutorialStep = null; tutorialFinished = Boolean.FALSE; tutorialSeen = false; dirty = true; }
    public synchronized boolean mapHidden() { return mapHidden; }
    public synchronized void setMapHidden(boolean b) { mapHidden = b; dirty = true; }
    public synchronized double balance() { return balance; }
    public synchronized void setBalance(double b) { balance = Math.max(0, b); balanceTouched = Boolean.TRUE; dirty = true; }
    /** True once the internal balance moved. A record without the flag, new or from an older file, answers false. */
    public synchronized boolean balanceTouched() { return Boolean.TRUE.equals(balanceTouched); }
    /** True for a record made by this version whose balance never moved, so it still holds only its starting grant. */
    public synchronized boolean balanceFresh() { return Boolean.FALSE.equals(balanceTouched); }
    public synchronized int kills() { return kills; }
    public synchronized void addKill() { kills++; dirty = true; }
    public synchronized int deaths() { return deaths; }
    public synchronized void addDeath() { deaths++; dirty = true; }
    /** A counter memory, 0 when it was never set. */
    public synchronized long counter(String key) { return counters.getOrDefault(key, 0L); }
    public synchronized void setCounter(String key, long value) { counters.put(key, value); dirty = true; }
    /** How many counter memories start with the prefix. */
    public synchronized int counterCount(String prefix) {
        int n = 0;
        for (String k : counters.keySet()) {
            if (k.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }
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
                .append("BalanceTouched", balanceTouched)
                .append("TutorialStep", tutorialStep).append("TutorialFinished", tutorialFinished)
                .append("Counters", counters.isEmpty() ? null : new Document(new HashMap<>(counters)));
    }

    public static PlayerData fromDocument(UUID uuid, Document d) {
        // A file without a name, or with a non text name, loads with an empty name so callers can
        // compare names without a null check. The next login fills it in.
        Object rawName = d.get("Name");
        PlayerData p = new PlayerData(uuid, rawName instanceof String s ? s : "", num(d, "Power"), num(d, "Balance"));
        String f = d.getString("Faction");
        p.factionId = f == null ? null : UUID.fromString(f);
        Object touched = d.get("BalanceTouched");
        p.balanceTouched = touched instanceof Boolean b ? b : null;
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
        p.tutorialStep = d.get("TutorialStep") instanceof String step && !step.isBlank() ? step : null;
        p.tutorialFinished = d.get("TutorialFinished") instanceof Boolean done ? done : null;
        p.kills = (int) num(d, "Kills");
        p.deaths = (int) num(d, "Deaths");
        if (d.get("Counters") instanceof Document counters) {
            for (Map.Entry<String, Object> e : counters.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    p.counters.put(e.getKey(), n.longValue());
                }
            }
        }
        p.dirty = false;
        return p;
    }

    private static double num(Document d, String key) {
        Object v = d.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
