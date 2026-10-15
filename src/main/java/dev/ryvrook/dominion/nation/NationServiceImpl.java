package dev.ryvrook.dominion.nation;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Nations group factions that are always allies. The nation is led by one faction and that
 * faction's leader runs it. Every change runs under one lock so a faction is never in two nations.
 */
public final class NationServiceImpl implements dev.ryvrook.dominion.service.NationService {
    static final int NAME_MAX = 32;

    private final DominionContext ctx;
    private final Map<UUID, Nation> nations = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    /** Serialises file writes. The minute save and the shutdown save share one temp file. */
    private final Object saveLock = new Object();
    private volatile boolean dirty;

    public NationServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    // ---- persistence -------------------------------------------------------

    private Path file() {
        return ctx.plugin().getDataDirectory().resolve("nations.json");
    }

    void load() {
        Document doc = ctx.files().read(file());
        if (doc == null) {
            return;
        }
        Object raw = doc.get("Nations");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Document d)) {
                continue;
            }
            try {
                Nation n = Nation.fromDocument(d);
                nations.put(n.id(), n);
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Skipping an unreadable nation entry");
            }
        }
    }

    void save() {
        synchronized (saveLock) {
            List<Document> docs = new ArrayList<>();
            synchronized (lock) {
                for (Nation n : nations.values()) {
                    docs.add(n.toDocument());
                    n.clearDirty();
                }
                dirty = false;
            }
            if (!ctx.files().write(file(), new Document("Version", 1).append("Nations", docs))) {
                // Keep the changes marked so the next minute tries again.
                dirty = true;
            }
        }
    }

    void saveIfDirty() {
        boolean any = dirty;
        for (Nation n : nations.values()) {
            any |= n.isDirty();
        }
        if (any) {
            save();
        }
    }

    /**
     * Brings faction links and nation member lists into agreement after loading. Members whose
     * faction is gone are dropped, factions pointing at a missing nation are cleared, and empty
     * nations are removed.
     */
    void reconcile() {
        synchronized (lock) {
            for (Nation n : new ArrayList<>(nations.values())) {
                for (UUID fid : n.members()) {
                    Faction f = ctx.repo().faction(fid);
                    if (f == null || (f.nationId() != null && !f.nationId().equals(n.id()))) {
                        n.removeMember(fid);
                    } else if (f.nationId() == null) {
                        f.setNationId(n.id());
                    }
                }
                for (UUID fid : n.invites()) {
                    if (ctx.repo().faction(fid) == null) {
                        n.uninvite(fid);
                    }
                }
                if (n.members().isEmpty()) {
                    nations.remove(n.id());
                    dirty = true;
                } else if (n.leaderFaction() == null || !n.isMember(n.leaderFaction())) {
                    n.setLeaderFaction(n.members().get(0));
                }
            }
            for (Faction f : ctx.repo().factions()) {
                UUID nid = f.nationId();
                if (nid != null) {
                    Nation n = nations.get(nid);
                    if (n == null || !n.isMember(f.id())) {
                        f.setNationId(null);
                    }
                }
            }
        }
    }

    /** Drops disbanded factions from their nation. */
    void onEvent(DominionEvent event) {
        if (!(event instanceof DominionEvent.FactionDisbanded gone)) {
            return;
        }
        synchronized (lock) {
            for (Nation n : new ArrayList<>(nations.values())) {
                n.uninvite(gone.factionId());
                if (n.isMember(gone.factionId())) {
                    n.removeMember(gone.factionId());
                    afterRemoval(n, Messages.get(NationText.DISBANDED_MEMBER_LEFT, "faction", gone.tag(), "nation", n.tag()));
                }
            }
        }
    }

    // ---- lookups -------------------------------------------------------------

    @Override
    public Nation nationOf(Faction faction) {
        if (faction == null || faction.nationId() == null) {
            return null;
        }
        Nation n = nations.get(faction.nationId());
        return n != null && n.isMember(faction.id()) ? n : null;
    }

    @Override
    public Nation byTag(String tag) {
        if (tag == null) {
            return null;
        }
        for (Nation n : nations.values()) {
            if (n.tag().equalsIgnoreCase(tag.strip())) {
                return n;
            }
        }
        return null;
    }

    @Override
    public List<Nation> all() {
        List<Nation> out = new ArrayList<>(nations.values());
        out.sort((a, b) -> a.tag().compareToIgnoreCase(b.tag()));
        return out;
    }

    @Override
    public boolean sameNation(Faction a, Faction b) {
        if (a == null || b == null || a.id().equals(b.id())) {
            return false;
        }
        Nation n = nationOf(a);
        return n != null && n.id().equals(b.nationId()) && n.isMember(b.id());
    }

    // ---- actions -------------------------------------------------------------

    @Override
    public Result create(UUID actor, String tag, String name) {
        Result off = disabled();
        if (off != null) {
            return off;
        }
        Faction f = ledFaction(actor);
        if (f == null) {
            return Result.fail(Messages.get(NationText.FOUND_LEADER_ONLY));
        }
        String clean = tag == null ? "" : tag.strip();
        String problem = tagProblem(clean);
        if (problem != null) {
            return Result.fail(problem);
        }
        String display = name == null ? "" : name.strip();
        if (display.length() > NAME_MAX) {
            return Result.fail(Messages.get(NationText.NAME_TOO_LONG, "max", NAME_MAX));
        }
        if (!plain(display)) {
            return Result.fail(Messages.get(NationText.NAME_SPECIAL));
        }
        Nation n;
        synchronized (lock) {
            if (nationOf(f) != null) {
                return Result.fail(Messages.get(NationText.ALREADY_IN_NATION));
            }
            if (byTag(clean) != null) {
                return Result.fail(Messages.get(NationText.TAG_TAKEN));
            }
            n = new Nation(UUID.randomUUID(), clean, display, f.id(), Time.now());
            nations.put(n.id(), n);
            f.setNationId(n.id());
            dirty = true;
        }
        audit(f, actor, Messages.get(NationText.AUDIT_FOUNDED, "nation", n.tag()));
        publish(n);
        return Result.pass(Messages.get(NationText.FOUNDED, "nation", n.tag()));
    }

    @Override
    public Result invite(UUID actor, String factionTag) {
        Result off = disabled();
        if (off != null) {
            return off;
        }
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail(Messages.get(NationText.INVITE_LEADER_ONLY));
        }
        Faction target = factions().byTag(factionTag);
        if (target == null || target.isSystem()) {
            return Result.fail(Messages.get(NationText.NO_FACTION_WITH_TAG));
        }
        synchronized (lock) {
            if (n.isMember(target.id())) {
                return Result.fail(Messages.get(NationText.ALREADY_IN_YOURS, "faction", target.tag()));
            }
            if (nationOf(target) != null) {
                return Result.fail(Messages.get(NationText.ALREADY_IN_ANOTHER, "faction", target.tag()));
            }
            if (n.invites().contains(target.id())) {
                return Result.fail(Messages.get(NationText.ALREADY_INVITED, "faction", target.tag()));
            }
            if (full(n)) {
                return Result.fail(Messages.get(NationText.FULL, "nation", n.tag()));
            }
            n.invite(target.id());
        }
        audit(own, actor, Messages.get(NationText.AUDIT_INVITED, "faction", target.tag(), "nation", n.tag()));
        tellLeader(target, Msg.info(Messages.get(NationText.INVITE_NOTICE, "faction", own.tag(), "nation", n.tag())));
        publish(n);
        return Result.pass(Messages.get(NationText.INVITED, "faction", target.tag(), "nation", n.tag()));
    }

    @Override
    public Result join(UUID actor, String nationTag) {
        Result off = disabled();
        if (off != null) {
            return off;
        }
        Faction f = ledFaction(actor);
        if (f == null) {
            return Result.fail(Messages.get(NationText.JOIN_LEADER_ONLY));
        }
        Nation n = byTag(nationTag);
        if (n == null) {
            return Result.fail(Messages.get(NationText.NO_NATION_WITH_TAG));
        }
        synchronized (lock) {
            if (nationOf(f) != null) {
                return Result.fail(Messages.get(NationText.ALREADY_IN_NATION));
            }
            if (!n.invites().contains(f.id())) {
                return Result.fail(Messages.get(NationText.NOT_INVITED, "nation", n.tag()));
            }
            if (full(n)) {
                return Result.fail(Messages.get(NationText.FULL, "nation", n.tag()));
            }
            if (nations.get(n.id()) != n) {
                return Result.fail(Messages.get(NationText.NATION_GONE));
            }
            n.uninvite(f.id());
            n.addMember(f.id());
            f.setNationId(n.id());
        }
        audit(f, actor, Messages.get(NationText.AUDIT_JOINED, "nation", n.tag()));
        Faction lead = factions().byId(n.leaderFaction());
        if (lead != null) {
            audit(lead, actor, Messages.get(NationText.JOINED_NOTICE, "faction", f.tag(), "nation", n.tag()));
        }
        toNation(n, Msg.good(Messages.get(NationText.JOINED_NOTICE, "faction", f.tag(), "nation", n.tag())));
        publish(n);
        return Result.pass(Messages.get(NationText.JOINED, "nation", n.tag()));
    }

    @Override
    public Result leave(UUID actor) {
        Faction f = ledFaction(actor);
        if (f == null) {
            return Result.fail(Messages.get(NationText.LEAVE_LEADER_ONLY));
        }
        Nation n = nationOf(f);
        if (n == null) {
            return Result.fail(Messages.get(NationText.NOT_IN_NATION));
        }
        synchronized (lock) {
            if (f.id().equals(n.leaderFaction()) && n.members().size() > 1) {
                return Result.fail(Messages.get(NationText.LEAVE_HAND_OVER));
            }
            n.removeMember(f.id());
            f.setNationId(null);
            afterRemoval(n, Messages.get(NationText.LEFT_NOTICE, "faction", f.tag(), "nation", n.tag()));
        }
        audit(f, actor, Messages.get(NationText.AUDIT_LEFT, "nation", n.tag()));
        return Result.pass(Messages.get(NationText.LEFT, "nation", n.tag()));
    }

    @Override
    public Result kick(UUID actor, String factionTag) {
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail(Messages.get(NationText.KICK_LEADER_ONLY));
        }
        Faction target = factions().byTag(factionTag);
        if (target == null) {
            return Result.fail(Messages.get(NationText.NO_FACTION_WITH_TAG));
        }
        synchronized (lock) {
            if (target.id().equals(own.id())) {
                return Result.fail(Messages.get(NationText.KICK_OWN));
            }
            if (!n.isMember(target.id())) {
                if (n.uninvite(target.id())) {
                    audit(own, actor, Messages.get(NationText.AUDIT_INVITE_WITHDRAWN, "faction", target.tag()));
                    publish(n);
                    return Result.pass(Messages.get(NationText.INVITE_WITHDRAWN, "faction", target.tag()));
                }
                return Result.fail(Messages.get(NationText.TARGET_NOT_MEMBER, "faction", target.tag()));
            }
            n.removeMember(target.id());
            if (n.id().equals(target.nationId())) {
                target.setNationId(null);
            }
            afterRemoval(n, Messages.get(NationText.REMOVED_NOTICE, "faction", target.tag(), "nation", n.tag()));
        }
        audit(own, actor, Messages.get(NationText.AUDIT_REMOVED, "faction", target.tag(), "nation", n.tag()));
        audit(target, actor, Messages.get(NationText.AUDIT_WAS_REMOVED, "nation", n.tag(), "faction", own.tag()));
        tellLeader(target, Msg.warn(Messages.get(NationText.WAS_REMOVED_NOTICE, "nation", n.tag())));
        return Result.pass(Messages.get(NationText.REMOVED, "faction", target.tag(), "nation", n.tag()));
    }

    @Override
    public Result disband(UUID actor, boolean confirmed) {
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail(Messages.get(NationText.DISBAND_LEADER_ONLY));
        }
        if (!confirmed) {
            return Result.fail(Messages.get(NationText.DISBAND_CONFIRM, "nation", n.tag()));
        }
        List<UUID> members;
        synchronized (lock) {
            if (nations.remove(n.id()) == null) {
                return Result.fail(Messages.get(NationText.NATION_GONE));
            }
            members = n.members();
            for (UUID fid : members) {
                Faction f = factions().byId(fid);
                if (f != null && n.id().equals(f.nationId())) {
                    f.setNationId(null);
                }
            }
            dirty = true;
        }
        for (UUID fid : members) {
            Faction f = factions().byId(fid);
            if (f != null) {
                audit(f, actor, Messages.get(NationText.AUDIT_DISBANDED, "nation", n.tag()));
                tellLeader(f, Msg.warn(Messages.get(NationText.DISBANDED_NOTICE, "nation", n.tag())));
            }
        }
        publish(n);
        return Result.pass(Messages.get(NationText.DISBANDED, "nation", n.tag()));
    }

    @Override
    public Result setLeaderFaction(UUID actor, String factionTag) {
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail(Messages.get(NationText.HANDOVER_LEADER_ONLY));
        }
        Faction target = factions().byTag(factionTag);
        if (target == null) {
            return Result.fail(Messages.get(NationText.NO_FACTION_WITH_TAG));
        }
        synchronized (lock) {
            if (target.id().equals(own.id())) {
                return Result.fail(Messages.get(NationText.ALREADY_LEADS, "nation", n.tag()));
            }
            if (!n.isMember(target.id())) {
                return Result.fail(Messages.get(NationText.TARGET_NOT_MEMBER, "faction", target.tag()));
            }
            n.setLeaderFaction(target.id());
        }
        audit(own, actor, Messages.get(NationText.AUDIT_HANDED, "nation", n.tag(), "faction", target.tag()));
        audit(target, actor, Messages.get(NationText.AUDIT_NOW_LEADS, "nation", n.tag()));
        toNation(n, Msg.info(Messages.get(NationText.NOW_LEADS_NOTICE, "faction", target.tag(), "nation", n.tag())));
        publish(n);
        return Result.pass(Messages.get(NationText.NOW_LEADS, "faction", target.tag(), "nation", n.tag()));
    }

    // ---- helpers -------------------------------------------------------------

    /**
     * After a member left, picks a new leading faction when needed and removes the nation once
     * it is empty. The text is a full sentence naming the faction that went. Caller holds the lock.
     */
    private void afterRemoval(Nation n, String why) {
        List<UUID> left = n.members();
        if (left.isEmpty()) {
            nations.remove(n.id());
            dirty = true;
            publish(n);
            return;
        }
        if (n.leaderFaction() == null || !n.isMember(n.leaderFaction())) {
            n.setLeaderFaction(left.get(0));
            Faction lead = factions().byId(left.get(0));
            if (lead != null) {
                audit(lead, null, Messages.get(NationText.AUDIT_NOW_LEADS_AFTER, "nation", n.tag(), "reason", why));
            }
        }
        toNation(n, Msg.warn(why));
        publish(n);
    }

    private Result disabled() {
        return ctx.settings().bool("nation.enabled") ? null : Result.fail(Messages.get(NationText.DISABLED));
    }

    private boolean full(Nation n) {
        int cap = ctx.settings().integer("nation.maxFactions");
        return cap > 0 && n.members().size() >= cap;
    }

    /** The faction the actor leads, or null. */
    private Faction ledFaction(UUID actor) {
        Faction f = factions().factionOf(actor);
        return f != null && !f.isSystem() && actor.equals(f.leader()) ? f : null;
    }

    private String tagProblem(String tag) {
        if (tag.isEmpty()) {
            return Messages.get(NationText.TAG_MISSING);
        }
        int min = ctx.settings().integer("nation.tagMin");
        int max = ctx.settings().integer("nation.tagMax");
        if (tag.length() < min) {
            return Messages.get(NationText.TAG_TOO_SHORT, "min", min);
        }
        if (tag.length() > max) {
            return Messages.get(NationText.TAG_TOO_LONG, "max", max);
        }
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (!(c < 128 && Character.isLetterOrDigit(c))) {
                return Messages.get(NationText.TAG_CHARACTERS);
            }
        }
        return null;
    }

    private static boolean plain(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void audit(Faction f, UUID who, String text) {
        factions().audit(f, who, null, "nation", text);
    }

    private void publish(Nation n) {
        dirty = true;
        ctx.bus().publish(new DominionEvent.NationChanged(n.id(), n.tag()));
    }

    private void tellLeader(Faction f, com.hypixel.hytale.server.core.Message message) {
        UUID leader = f.leader();
        if (leader != null && com.hypixel.hytale.server.core.universe.Universe.get() != null) {
            Msg.send(Players.online(leader), message);
        }
    }

    /** Tells the online leaders of every member faction. */
    private void toNation(Nation n, com.hypixel.hytale.server.core.Message message) {
        for (UUID fid : n.members()) {
            Faction f = factions().byId(fid);
            if (f != null) {
                tellLeader(f, message);
            }
        }
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }
}
