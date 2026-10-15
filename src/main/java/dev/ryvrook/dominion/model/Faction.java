package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One faction. All mutation goes through synchronized methods because world threads and the
 * scheduler both touch it. Serialises itself to a BSON document for JSON storage.
 */
public final class Faction {
    public static final int VERSION = 1;

    private final UUID id;
    private FactionType type = FactionType.NORMAL;
    private String tag;
    private String name;
    private String color = "#7fd6ff";
    private String description = "";
    private String motd = "";
    private long createdAt;
    private UUID leader;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final Map<UUID, Long> invites = new HashMap<>();
    private final List<UUID> requests = new ArrayList<>();
    private final List<UUID> bans = new ArrayList<>();
    private final Map<String, Rank> roster = new LinkedHashMap<>();
    /** Which roster entry each player joined through, so a later rename cannot revive the entry. */
    private final Map<UUID, String> rosterIds = new HashMap<>();
    /** When the faction last lost each chunk, keyed world then chunk index, for the claim mission guard. */
    private final Map<String, Long> released = new HashMap<>();
    private final Map<UUID, Relation> relations = new HashMap<>();
    private final Map<UUID, RelationRequest> relationRequests = new HashMap<>();
    private Location home;
    private final Map<String, Warp> warps = new LinkedHashMap<>();
    private final EnumMap<Rank, EnumSet<FactionAction>> rankPerms = new EnumMap<>(Rank.class);
    private final EnumMap<Relation, EnumSet<FactionAction>> relationPerms = new EnumMap<>(Relation.class);
    private double bank;
    private double powerBoost;
    private final Map<String, Integer> upgrades = new HashMap<>();
    private int points;
    private int shieldStartHour = -1;
    private final Map<String, Long> stats = new HashMap<>();
    private final Map<String, Boolean> settings = new HashMap<>();
    private final Map<String, String> rankNames = new HashMap<>();
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private UUID nationId;
    private String coinageAccount;
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private volatile boolean dirty;

    public record RelationRequest(Relation type, UUID from, long expiresAt) {}
    public record Warp(Location location, String password) {}

    public Faction(UUID id, String tag, String name, UUID leader, long createdAt) {
        this.id = id;
        this.tag = tag;
        this.name = name == null || name.isBlank() ? tag : name;
        this.leader = leader;
        this.createdAt = createdAt;
        applyDefaultPerms();
    }

    // ---- identity --------------------------------------------------------

    public UUID id() { return id; }
    public synchronized FactionType type() { return type; }
    public synchronized void setType(FactionType type) { this.type = type; touch(); }
    public synchronized boolean isSystem() { return type != FactionType.NORMAL; }
    public synchronized String tag() { return tag; }
    public synchronized void setTag(String tag) { this.tag = tag; touch(); }
    public synchronized String name() { return name; }
    public synchronized void setName(String name) { this.name = name; touch(); }
    /** Tag and name for display, or the name alone when the tag would only repeat it. */
    public synchronized String label() { return label(tag, name); }
    /** True when the tag adds something next to the name, so a separate tag element is worth drawing. */
    public synchronized boolean tagDiffers() { return tagDiffers(tag, name); }

    /** The shared label rule for factions and nations. */
    public static String label(String tag, String name) {
        String t = tag == null ? "" : tag.trim();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return t;
        return tagDiffers(t, n) ? t + "  " + n : n;
    }

    /** False when the tag is blank, the name is blank or both read the same ignoring case. */
    public static boolean tagDiffers(String tag, String name) {
        String t = tag == null ? "" : tag.trim();
        String n = name == null ? "" : name.trim();
        return !t.isEmpty() && !n.isEmpty() && !t.equalsIgnoreCase(n);
    }
    public synchronized String color() { return color; }
    public synchronized void setColor(String color) { this.color = color; touch(); }
    public synchronized String description() { return description; }
    public synchronized void setDescription(String d) { this.description = d == null ? "" : d; touch(); }
    public synchronized String motd() { return motd; }
    public synchronized void setMotd(String m) { this.motd = m == null ? "" : m; touch(); }
    public long createdAt() { return createdAt; }
    public synchronized UUID leader() { return leader; }
    public synchronized void setLeader(UUID leader) { this.leader = leader; touch(); }

    // ---- members ---------------------------------------------------------

    public synchronized Member member(UUID uuid) { return members.get(uuid); }
    public synchronized boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public synchronized int memberCount() { return members.size(); }
    public synchronized List<Member> members() { return new ArrayList<>(members.values()); }
    public synchronized void putMember(Member m) { members.put(m.uuid(), m); touch(); }
    public synchronized Member removeMember(UUID uuid) { Member m = members.remove(uuid); if (m != null) touch(); return m; }
    public synchronized Rank rankOf(UUID uuid) {
        Member m = members.get(uuid);
        return m == null ? null : (uuid.equals(leader) ? Rank.LEADER : m.rank());
    }

    public synchronized Map<UUID, Long> invites() { return new HashMap<>(invites); }
    public synchronized void invite(UUID uuid, long expiresAt) { invites.put(uuid, expiresAt); touch(); }
    public synchronized boolean uninvite(UUID uuid) { boolean r = invites.remove(uuid) != null; if (r) touch(); return r; }
    public synchronized boolean hasValidInvite(UUID uuid, long now) {
        Long exp = invites.get(uuid);
        return exp != null && exp > now;
    }

    public synchronized List<UUID> requests() { return new ArrayList<>(requests); }
    public synchronized void addRequest(UUID uuid) { if (!requests.contains(uuid)) { requests.add(uuid); touch(); } }
    public synchronized boolean removeRequest(UUID uuid) { boolean r = requests.remove(uuid); if (r) touch(); return r; }

    public synchronized List<UUID> bans() { return new ArrayList<>(bans); }
    public synchronized boolean isBanned(UUID uuid) { return bans.contains(uuid); }
    public synchronized void ban(UUID uuid) { if (!bans.contains(uuid)) { bans.add(uuid); touch(); } }
    public synchronized boolean unban(UUID uuid) { boolean r = bans.remove(uuid); if (r) touch(); return r; }

    public synchronized Map<String, Rank> roster() { return new LinkedHashMap<>(roster); }
    public synchronized void rosterPut(String name, Rank rank) { roster.put(rosterKey(name), rank); touch(); }
    public synchronized boolean rosterRemove(String name) {
        String key = rosterKey(name);
        boolean r = roster.remove(key) != null;
        rosterIds.values().removeIf(key::equals);
        if (r) touch();
        return r;
    }
    /** Roster entry for a name, looked up the same way it is stored. */
    public synchronized Rank rosterRank(String name) { return name == null ? null : roster.get(rosterKey(name)); }
    /** Records that the player joined through the roster entry for this name. */
    public synchronized void rosterBind(UUID player, String name) {
        if (player != null && name != null && roster.containsKey(rosterKey(name))) { rosterIds.put(player, rosterKey(name)); touch(); }
    }
    /**
     * Removes every roster entry that belongs to the player, the one they joined through and any of
     * the given names. Returns the removed names.
     */
    public synchronized List<String> rosterRemovePlayer(UUID player, String... names) {
        List<String> keys = new ArrayList<>();
        String bound = player == null ? null : rosterIds.remove(player);
        if (bound != null) keys.add(bound);
        for (String n : names) {
            if (n != null && !n.isBlank() && !keys.contains(rosterKey(n))) keys.add(rosterKey(n));
        }
        List<String> removed = new ArrayList<>();
        for (String k : keys) {
            if (roster.remove(k) != null) removed.add(k);
            rosterIds.values().removeIf(k::equals);
        }
        if (bound != null || !removed.isEmpty()) touch();
        return removed;
    }
    public synchronized Map<UUID, String> rosterIds() { return new HashMap<>(rosterIds); }
    static String rosterKey(String name) { return name.toLowerCase(java.util.Locale.ROOT); }

    // ---- released land ---------------------------------------------------

    private static String landKey(String world, long chunk) { return world + "|" + chunk; }
    /** Notes that the faction lost the chunk now, dropping marks older than the window. */
    public synchronized void noteReleased(String world, long chunk, long now, long window) {
        released.values().removeIf(t -> now - t >= window);
        released.put(landKey(world, chunk), now);
        touch();
    }
    /** True when the faction lost this chunk less than the window ago. */
    public synchronized boolean releasedWithin(String world, long chunk, long now, long window) {
        Long at = released.get(landKey(world, chunk));
        return at != null && now - at < window;
    }
    public synchronized Map<String, Long> released() { return new HashMap<>(released); }

    // ---- relations -------------------------------------------------------

    public synchronized Relation relationTo(UUID otherFaction) {
        if (otherFaction == null) {
            return Relation.NEUTRAL;
        }
        if (otherFaction.equals(id)) {
            return Relation.OWN;
        }
        return relations.getOrDefault(otherFaction, Relation.NEUTRAL);
    }
    public synchronized Map<UUID, Relation> relations() { return new HashMap<>(relations); }
    public synchronized void setRelation(UUID other, Relation relation) {
        if (relation == null || relation == Relation.NEUTRAL || relation == Relation.OWN) {
            relations.remove(other);
        } else {
            relations.put(other, relation);
        }
        touch();
    }
    public synchronized int countRelation(Relation r) {
        int n = 0;
        for (Relation v : relations.values()) {
            if (v == r) n++;
        }
        return n;
    }
    public synchronized Map<UUID, RelationRequest> relationRequests() { return new HashMap<>(relationRequests); }
    public synchronized void putRelationRequest(UUID other, RelationRequest req) { relationRequests.put(other, req); touch(); }
    public synchronized RelationRequest removeRelationRequest(UUID other) { RelationRequest r = relationRequests.remove(other); if (r != null) touch(); return r; }

    // ---- home and warps --------------------------------------------------

    public synchronized Location home() { return home; }
    public synchronized void setHome(Location home) { this.home = home; touch(); }
    public synchronized Map<String, Warp> warps() { return new LinkedHashMap<>(warps); }
    public synchronized Warp warp(String name) { return warps.get(name.toLowerCase()); }
    public synchronized void setWarp(String name, Warp warp) { warps.put(name.toLowerCase(), warp); touch(); }
    public synchronized boolean removeWarp(String name) { boolean r = warps.remove(name.toLowerCase()) != null; if (r) touch(); return r; }

    // ---- permissions -----------------------------------------------------

    public synchronized boolean rankCan(Rank rank, FactionAction action) {
        if (rank == Rank.LEADER) {
            return true;
        }
        EnumSet<FactionAction> set = rankPerms.get(rank);
        return set != null && set.contains(action);
    }
    public synchronized boolean relationCan(Relation relation, FactionAction action) {
        EnumSet<FactionAction> set = relationPerms.get(relation);
        return set != null && set.contains(action);
    }
    public synchronized void setRankPerm(Rank rank, FactionAction action, boolean allowed) {
        if (rank == Rank.LEADER) return;
        EnumSet<FactionAction> set = rankPerms.computeIfAbsent(rank, r -> EnumSet.noneOf(FactionAction.class));
        if (allowed) set.add(action); else set.remove(action);
        touch();
    }
    public synchronized void setRelationPerm(Relation relation, FactionAction action, boolean allowed) {
        if (!relation.isDiplomatic() || !action.relationScoped()) return;
        EnumSet<FactionAction> set = relationPerms.computeIfAbsent(relation, r -> EnumSet.noneOf(FactionAction.class));
        if (allowed) set.add(action); else set.remove(action);
        touch();
    }
    public synchronized EnumSet<FactionAction> rankPerms(Rank rank) {
        EnumSet<FactionAction> set = rankPerms.get(rank);
        return set == null ? EnumSet.noneOf(FactionAction.class) : EnumSet.copyOf(set);
    }
    public synchronized EnumSet<FactionAction> relationPerms(Relation relation) {
        EnumSet<FactionAction> set = relationPerms.get(relation);
        return set == null ? EnumSet.noneOf(FactionAction.class) : EnumSet.copyOf(set);
    }
    public synchronized void applyDefaultPerms() {
        defaultRankPerms();
        defaultRelationPerms();
        touch();
    }

    private void defaultRankPerms() {
        rankPerms.clear();
        rankPerms.put(Rank.RECRUIT, EnumSet.of(FactionAction.INTERACT, FactionAction.USE_HOME, FactionAction.USE_WARP, FactionAction.DEPOSIT));
        rankPerms.put(Rank.MEMBER, EnumSet.of(FactionAction.BUILD, FactionAction.CONTAINER, FactionAction.INTERACT,
                FactionAction.DAMAGE_ENTITY, FactionAction.USE_HOME, FactionAction.USE_WARP, FactionAction.DEPOSIT, FactionAction.VAULT));
        rankPerms.put(Rank.OFFICER, EnumSet.of(FactionAction.BUILD, FactionAction.CONTAINER, FactionAction.INTERACT,
                FactionAction.DAMAGE_ENTITY, FactionAction.CLAIM, FactionAction.UNCLAIM, FactionAction.INVITE, FactionAction.KICK,
                FactionAction.PROMOTE, FactionAction.SET_HOME, FactionAction.USE_HOME, FactionAction.SET_WARP, FactionAction.USE_WARP,
                FactionAction.DEPOSIT, FactionAction.WITHDRAW, FactionAction.VAULT, FactionAction.RELATIONS, FactionAction.SIEGE,
                FactionAction.ANNOUNCE));
    }

    private void defaultRelationPerms() {
        relationPerms.clear();
        relationPerms.put(Relation.ALLY, EnumSet.of(FactionAction.INTERACT, FactionAction.USE_HOME));
        relationPerms.put(Relation.TRUCE, EnumSet.of(FactionAction.INTERACT));
    }

    // ---- economy, progression, misc --------------------------------------

    public synchronized double bank() { return bank; }
    public synchronized void setBank(double bank) { this.bank = Math.max(0, bank); touch(); }
    public synchronized double powerBoost() { return powerBoost; }
    public synchronized void setPowerBoost(double b) { this.powerBoost = b; touch(); }
    public synchronized int upgrade(String key) { return upgrades.getOrDefault(key, 0); }
    public synchronized void setUpgrade(String key, int level) { upgrades.put(key, level); touch(); }
    public synchronized Map<String, Integer> upgrades() { return new HashMap<>(upgrades); }
    public synchronized int points() { return points; }
    public synchronized void setPoints(int points) { this.points = points; touch(); }
    public synchronized int shieldStartHour() { return shieldStartHour; }
    public synchronized void setShieldStartHour(int h) { this.shieldStartHour = h; touch(); }
    public synchronized long stat(String key) { return stats.getOrDefault(key, 0L); }
    public synchronized long addStat(String key, long amount) { long v = stats.merge(key, amount, Long::sum); touch(); return v; }
    public synchronized Map<String, Long> stats() { return new HashMap<>(stats); }
    public synchronized void removeStat(String key) { if (stats.remove(key) != null) touch(); }
    public synchronized void removeStatsWithPrefix(String prefix) { if (stats.keySet().removeIf(k -> k.startsWith(prefix))) touch(); }
    public synchronized boolean setting(String key, boolean def) { return settings.getOrDefault(key, def); }
    public synchronized void setSetting(String key, boolean value) { settings.put(key, value); touch(); }
    public synchronized Map<String, Boolean> settings() { return new HashMap<>(settings); }

    /** Display name for a rank, custom or default. */
    public synchronized String rankName(Rank rank) {
        String n = rankNames.get(rank.name());
        return n == null || n.isBlank() ? rank.displayName() : n;
    }
    public synchronized void setRankName(Rank rank, String name) {
        if (name == null || name.isBlank()) rankNames.remove(rank.name()); else rankNames.put(rank.name(), name);
        touch();
    }
    public synchronized Map<String, String> rankNames() { return new HashMap<>(rankNames); }
    public synchronized Zone zone(String id) { return id == null ? null : zones.get(id); }
    public synchronized List<Zone> zones() { return new ArrayList<>(zones.values()); }
    public synchronized void putZone(Zone zone) { zones.put(zone.id(), zone); touch(); }
    public synchronized Zone removeZone(String id) { Zone z = zones.remove(id); if (z != null) touch(); return z; }
    public synchronized void touchZones() { touch(); }
    public synchronized UUID nationId() { return nationId; }
    public synchronized void setNationId(UUID id) { nationId = id; touch(); }
    /** The handle of the faction's Coinage shared account once its bank moved there, else null. */
    public synchronized String coinageAccount() { return coinageAccount; }
    public synchronized void setCoinageAccount(String handle) { coinageAccount = handle; touch(); }

    public synchronized void audit(AuditEntry entry, int limit) {
        audit.addFirst(entry);
        while (audit.size() > Math.max(10, limit)) {
            audit.removeLast();
        }
        touch();
    }
    public synchronized List<AuditEntry> audit() { return new ArrayList<>(audit); }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    private void touch() { dirty = true; }

    // ---- serialisation ---------------------------------------------------

    public synchronized Document toDocument() {
        Document d = new Document("Version", VERSION)
                .append("Id", id.toString()).append("Type", type.name()).append("Tag", tag).append("Name", name)
                .append("Color", color).append("Description", description).append("Motd", motd)
                .append("CreatedAt", createdAt).append("Leader", leader == null ? null : leader.toString())
                .append("Bank", bank).append("PowerBoost", powerBoost).append("Points", points)
                .append("ShieldStartHour", shieldStartHour);
        List<Document> ms = new ArrayList<>();
        for (Member m : members.values()) {
            ms.add(new Document("Uuid", m.uuid().toString()).append("Name", m.name()).append("Rank", m.rank().name())
                    .append("Title", m.title()).append("JoinedAt", m.joinedAt()).append("LastSeen", m.lastSeen())
                    .append("HoldUntil", m.holdUntil()).append("Kills", m.kills()).append("Deaths", m.deaths()));
        }
        d.append("Members", ms);
        Document inv = new Document();
        invites.forEach((k, v) -> inv.append(k.toString(), v));
        d.append("Invites", inv);
        d.append("Requests", requests.stream().map(UUID::toString).toList());
        d.append("Bans", bans.stream().map(UUID::toString).toList());
        Document ros = new Document();
        roster.forEach((k, v) -> ros.append(k, v.name()));
        d.append("Roster", ros);
        Document rid = new Document();
        rosterIds.forEach((k, v) -> rid.append(k.toString(), v));
        d.append("RosterIds", rid);
        d.append("Released", new Document(new LinkedHashMap<>(released)));
        Document rel = new Document();
        relations.forEach((k, v) -> rel.append(k.toString(), v.name()));
        d.append("Relations", rel);
        Document rr = new Document();
        relationRequests.forEach((k, v) -> rr.append(k.toString(), new Document("Type", v.type().name())
                .append("From", v.from().toString()).append("ExpiresAt", v.expiresAt())));
        d.append("RelationRequests", rr);
        d.append("Home", home == null ? null : home.toDocument());
        Document ws = new Document();
        warps.forEach((k, v) -> ws.append(k, new Document("Location", v.location().toDocument()).append("Password", v.password())));
        d.append("Warps", ws);
        Document rp = new Document();
        rankPerms.forEach((k, v) -> rp.append(k.name(), v.stream().map(Enum::name).toList()));
        d.append("RankPerms", rp);
        Document lp = new Document();
        relationPerms.forEach((k, v) -> lp.append(k.name(), v.stream().map(Enum::name).toList()));
        d.append("RelationPerms", lp);
        d.append("Upgrades", new Document(new LinkedHashMap<>(upgrades)));
        Document st = new Document();
        stats.forEach(st::append);
        d.append("Stats", st);
        Document se = new Document();
        settings.forEach(se::append);
        d.append("Settings", se);
        List<Document> au = new ArrayList<>();
        for (AuditEntry e : audit) {
            au.add(e.toDocument());
        }
        d.append("Audit", au);
        Document rn = new Document();
        rankNames.forEach(rn::append);
        d.append("RankNames", rn);
        List<Document> zs = new ArrayList<>();
        for (Zone z : zones.values()) {
            zs.add(z.toDocument());
        }
        d.append("Zones", zs);
        d.append("Nation", nationId == null ? null : nationId.toString());
        d.append("CoinageAccount", coinageAccount);
        return d;
    }

    /**
     * Reads a faction leniently. A faction that fails to load is skipped and loses its claims at
     * the next claims save, so only the id is required. Every other field and entry is read on its
     * own and a damaged one falls back to its default or is skipped alone.
     */
    @SuppressWarnings("unchecked")
    public static Faction fromDocument(Document d) {
        UUID id = UUID.fromString(d.getString("Id"));
        UUID leaderId = uuidOrNull(d.get("Leader"));
        Faction f = new Faction(id, text(d, "Tag", id.toString().substring(0, 8)), text(d, "Name", null), leaderId,
                (long) num(d, "CreatedAt"));
        f.type = FactionType.NORMAL;
        try {
            if (d.get("Type") != null) f.type = FactionType.valueOf(String.valueOf(d.get("Type")));
        } catch (IllegalArgumentException ignored) {
            // unknown type, kept as a normal faction rather than lost
        }
        f.color = text(d, "Color", f.color);
        f.description = text(d, "Description", "");
        f.motd = text(d, "Motd", "");
        f.bank = num(d, "Bank");
        f.powerBoost = num(d, "PowerBoost");
        f.points = (int) num(d, "Points");
        f.shieldStartHour = d.get("ShieldStartHour") instanceof Number n ? n.intValue() : -1;
        for (Document m : list(d, "Members")) {
            try {
                UUID uuid = UUID.fromString(m.getString("Uuid"));
                Rank rank = parseRank(m.getString("Rank"), uuid.equals(f.leader) ? Rank.LEADER : Rank.RECRUIT);
                long joined = (long) num(m, "JoinedAt");
                Member mem = new Member(uuid, text(m, "Name", ""), rank, joined);
                mem.setTitle(text(m, "Title", ""));
                mem.setLastSeen(m.get("LastSeen") instanceof Number n ? n.longValue() : joined);
                mem.setHoldUntil((long) num(m, "HoldUntil"));
                mem.setKills((int) num(m, "Kills"));
                mem.setDeaths((int) num(m, "Deaths"));
                f.members.put(mem.uuid(), mem);
            } catch (RuntimeException ignored) {
                // a member entry without a readable id cannot be kept
            }
        }
        if (f.leader != null && !f.members.containsKey(f.leader)) {
            // The leader's own entry was damaged. Without it the leader could neither act nor leave.
            f.members.put(f.leader, new Member(f.leader, "", Rank.LEADER, f.createdAt));
        }
        doc(d, "Invites").forEach((k, v) -> {
            UUID u = uuidOrNull(k);
            if (u != null && v instanceof Number n) f.invites.put(u, n.longValue());
        });
        for (Object s : rawList(d, "Requests")) {
            UUID u = uuidOrNull(s);
            if (u != null && !f.requests.contains(u)) f.requests.add(u);
        }
        for (Object s : rawList(d, "Bans")) {
            // A ban is kept whenever the id reads, losing one would let the player back in.
            UUID u = uuidOrNull(s);
            if (u != null && !f.bans.contains(u)) f.bans.add(u);
        }
        doc(d, "Roster").forEach((k, v) -> {
            Rank r = v == null ? null : parseRank(v.toString(), null);
            if (r != null && r != Rank.LEADER) f.roster.put(rosterKey(k), r);
        });
        doc(d, "RosterIds").forEach((k, v) -> {
            UUID u = uuidOrNull(k);
            if (u != null && v instanceof String s && f.roster.containsKey(rosterKey(s))) f.rosterIds.put(u, rosterKey(s));
        });
        doc(d, "Released").forEach((k, v) -> {
            if (v instanceof Number n) f.released.put(k, n.longValue());
        });
        doc(d, "Relations").forEach((k, v) -> {
            try {
                Relation r = Relation.valueOf(String.valueOf(v));
                UUID u = UUID.fromString(k);
                if (r != Relation.NEUTRAL && r != Relation.OWN) f.relations.put(u, r);
            } catch (IllegalArgumentException ignored) {
                // unknown relation or id, treated as neutral
            }
        });
        doc(d, "RelationRequests").forEach((k, v) -> {
            try {
                Document r = (Document) v;
                f.relationRequests.put(UUID.fromString(k), new RelationRequest(Relation.valueOf(r.getString("Type")),
                        UUID.fromString(r.getString("From")), r.get("ExpiresAt", Number.class).longValue()));
            } catch (RuntimeException ignored) {
                // a damaged request is dropped, the sender can ask again
            }
        });
        try {
            f.home = Location.fromDocument(d.get("Home", Document.class));
        } catch (RuntimeException ignored) {
            f.home = null;
        }
        doc(d, "Warps").forEach((k, v) -> {
            try {
                Document w = (Document) v;
                Location at = Location.fromDocument(w.get("Location", Document.class));
                if (at != null) f.warps.put(k, new Warp(at, w.getString("Password")));
            } catch (RuntimeException ignored) {
                // a damaged warp is dropped, it can be set again
            }
        });
        Document rp = d.get("RankPerms") instanceof Document x ? x : null;
        if (rp != null) {
            f.rankPerms.clear();
            rp.forEach((k, v) -> {
                Rank r = parseRank(k, null);
                if (r != null && v instanceof List<?> l) f.rankPerms.put(r, toActions(l));
            });
            if (f.rankPerms.isEmpty()) {
                // Nothing usable was stored, so the defaults apply rather than a grid with no permissions.
                f.defaultRankPerms();
            }
        }
        Document lp = d.get("RelationPerms") instanceof Document x ? x : null;
        if (lp != null) {
            f.relationPerms.clear();
            lp.forEach((k, v) -> {
                try {
                    if (v instanceof List<?> l) f.relationPerms.put(Relation.valueOf(k), toActions(l));
                } catch (IllegalArgumentException ignored) {
                    // unknown relation from a newer or older version
                }
            });
            if (f.relationPerms.isEmpty()) {
                f.defaultRelationPerms();
            }
        }
        doc(d, "Upgrades").forEach((k, v) -> {
            if (v instanceof Number n) f.upgrades.put(k, n.intValue());
        });
        doc(d, "Stats").forEach((k, v) -> {
            if (v instanceof Number n) f.stats.put(k, n.longValue());
        });
        doc(d, "Settings").forEach((k, v) -> f.settings.put(k, Boolean.TRUE.equals(v)));
        for (Document a : list(d, "Audit")) {
            try { f.audit.addLast(AuditEntry.fromDocument(a)); } catch (RuntimeException ignored) { }
        }
        doc(d, "RankNames").forEach((k, v) -> {
            if (v != null && parseRank(k, null) != null) f.rankNames.put(k, v.toString());
        });
        for (Document z : list(d, "Zones")) {
            try {
                Zone zone = Zone.fromDocument(z);
                if (zone.id() != null) f.zones.put(zone.id(), zone);
            } catch (RuntimeException ignored) {
                // a damaged zone is dropped and its chunks follow the faction grid
            }
        }
        f.nationId = uuidOrNull(d.get("Nation"));
        f.coinageAccount = d.get("CoinageAccount") instanceof String h && !h.isBlank() ? h : null;
        f.dirty = false;
        return f;
    }

    private static String text(Document d, String key, String fallback) {
        return d.get(key) instanceof String s ? s : fallback;
    }

    private static UUID uuidOrNull(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Document doc(Document d, String key) {
        return d.get(key) instanceof Document x ? x : new Document();
    }

    private static List<?> rawList(Document d, String key) {
        return d.get(key) instanceof List<?> l ? l : List.of();
    }

    private static List<Document> list(Document d, String key) {
        List<Document> out = new ArrayList<>();
        for (Object o : rawList(d, key)) {
            if (o instanceof Document x) out.add(x);
        }
        return out;
    }

    private static EnumSet<FactionAction> toActions(List<?> names) {
        EnumSet<FactionAction> set = EnumSet.noneOf(FactionAction.class);
        for (Object n : names) {
            try {
                set.add(FactionAction.valueOf(String.valueOf(n)));
            } catch (IllegalArgumentException ignored) {
                // unknown action from a newer or older version
            }
        }
        return set;
    }

    /** Rank by its stored name, or the fallback when the name is missing or unknown. */
    static Rank parseRank(String name, Rank fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Rank.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static double num(Document d, String key) {
        Object v = d.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    public static List<Member> sortedByRank(List<Member> members) {
        List<Member> list = new ArrayList<>(members);
        list.sort((a, b) -> b.rank().compareTo(a.rank()));
        return Collections.unmodifiableList(list);
    }
}
