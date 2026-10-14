package dev.ryvrook.dominion.nation;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.FactionService;
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
                    afterRemoval(n, gone.tag() + " was disbanded and left the nation " + n.tag());
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
            return Result.fail("Only a faction leader can found a nation");
        }
        String clean = tag == null ? "" : tag.strip();
        String problem = tagProblem(clean);
        if (problem != null) {
            return Result.fail(problem);
        }
        String display = name == null ? "" : name.strip();
        if (display.length() > NAME_MAX) {
            return Result.fail("Nation names can be at most " + NAME_MAX + " characters long");
        }
        if (!plain(display)) {
            return Result.fail("Nation names cannot contain special characters");
        }
        Nation n;
        synchronized (lock) {
            if (nationOf(f) != null) {
                return Result.fail("Your faction is already in a nation");
            }
            if (byTag(clean) != null) {
                return Result.fail("A nation with that tag already exists");
            }
            n = new Nation(UUID.randomUUID(), clean, display, f.id(), Time.now());
            nations.put(n.id(), n);
            f.setNationId(n.id());
            dirty = true;
        }
        audit(f, actor, "Founded the nation " + n.tag());
        publish(n);
        return Result.pass("Founded the nation " + n.tag());
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
            return Result.fail("Only the leader of the leading faction can invite to a nation");
        }
        Faction target = factions().byTag(factionTag);
        if (target == null || target.isSystem()) {
            return Result.fail("No faction has that tag");
        }
        synchronized (lock) {
            if (n.isMember(target.id())) {
                return Result.fail(target.tag() + " is already in your nation");
            }
            if (nationOf(target) != null) {
                return Result.fail(target.tag() + " is already in another nation");
            }
            if (n.invites().contains(target.id())) {
                return Result.fail(target.tag() + " already has an invite");
            }
            if (full(n)) {
                return Result.fail(n.tag() + " already has the most factions allowed");
            }
            n.invite(target.id());
        }
        audit(own, actor, "Invited " + target.tag() + " to the nation " + n.tag());
        tellLeader(target, Msg.info(own.tag() + " invited your faction to the nation " + n.tag()
                + ". Use the nation join command with the tag " + n.tag() + " to accept"));
        publish(n);
        return Result.pass("Invited " + target.tag() + " to " + n.tag());
    }

    @Override
    public Result join(UUID actor, String nationTag) {
        Result off = disabled();
        if (off != null) {
            return off;
        }
        Faction f = ledFaction(actor);
        if (f == null) {
            return Result.fail("Only a faction leader can join a nation");
        }
        Nation n = byTag(nationTag);
        if (n == null) {
            return Result.fail("No nation has that tag");
        }
        synchronized (lock) {
            if (nationOf(f) != null) {
                return Result.fail("Your faction is already in a nation");
            }
            if (!n.invites().contains(f.id())) {
                return Result.fail(n.tag() + " has not invited your faction");
            }
            if (full(n)) {
                return Result.fail(n.tag() + " already has the most factions allowed");
            }
            if (nations.get(n.id()) != n) {
                return Result.fail("That nation no longer exists");
            }
            n.uninvite(f.id());
            n.addMember(f.id());
            f.setNationId(n.id());
        }
        audit(f, actor, "Joined the nation " + n.tag());
        Faction lead = factions().byId(n.leaderFaction());
        if (lead != null) {
            audit(lead, actor, f.tag() + " joined the nation " + n.tag());
        }
        toNation(n, Msg.good(f.tag() + " joined the nation " + n.tag()));
        publish(n);
        return Result.pass("Your faction joined " + n.tag());
    }

    @Override
    public Result leave(UUID actor) {
        Faction f = ledFaction(actor);
        if (f == null) {
            return Result.fail("Only a faction leader can leave a nation");
        }
        Nation n = nationOf(f);
        if (n == null) {
            return Result.fail("Your faction is not in a nation");
        }
        synchronized (lock) {
            if (f.id().equals(n.leaderFaction()) && n.members().size() > 1) {
                return Result.fail("Hand the nation to another faction first, or disband it");
            }
            n.removeMember(f.id());
            f.setNationId(null);
            afterRemoval(n, f.tag() + " left the nation " + n.tag());
        }
        audit(f, actor, "Left the nation " + n.tag());
        return Result.pass("Your faction left " + n.tag());
    }

    @Override
    public Result kick(UUID actor, String factionTag) {
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail("Only the leader of the leading faction can remove factions from a nation");
        }
        Faction target = factions().byTag(factionTag);
        if (target == null) {
            return Result.fail("No faction has that tag");
        }
        synchronized (lock) {
            if (target.id().equals(own.id())) {
                return Result.fail("You cannot remove your own faction. Disband the nation instead");
            }
            if (!n.isMember(target.id())) {
                if (n.uninvite(target.id())) {
                    audit(own, actor, "Withdrew the nation invite for " + target.tag());
                    publish(n);
                    return Result.pass("Withdrew the invite for " + target.tag());
                }
                return Result.fail(target.tag() + " is not in your nation");
            }
            n.removeMember(target.id());
            if (n.id().equals(target.nationId())) {
                target.setNationId(null);
            }
            afterRemoval(n, target.tag() + " was removed from the nation " + n.tag());
        }
        audit(own, actor, "Removed " + target.tag() + " from the nation " + n.tag());
        audit(target, actor, "Was removed from the nation " + n.tag() + " by " + own.tag());
        tellLeader(target, Msg.warn("Your faction was removed from the nation " + n.tag()));
        return Result.pass("Removed " + target.tag() + " from " + n.tag());
    }

    @Override
    public Result disband(UUID actor, boolean confirmed) {
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail("Only the leader of the leading faction can disband a nation");
        }
        if (!confirmed) {
            return Result.fail("Please confirm that you want to disband the nation " + n.tag());
        }
        List<UUID> members;
        synchronized (lock) {
            if (nations.remove(n.id()) == null) {
                return Result.fail("That nation no longer exists");
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
                audit(f, actor, "The nation " + n.tag() + " was disbanded");
                tellLeader(f, Msg.warn("The nation " + n.tag() + " was disbanded"));
            }
        }
        publish(n);
        return Result.pass("Disbanded the nation " + n.tag());
    }

    @Override
    public Result setLeaderFaction(UUID actor, String factionTag) {
        Faction own = ledFaction(actor);
        Nation n = own == null ? null : nationOf(own);
        if (n == null || !own.id().equals(n.leaderFaction())) {
            return Result.fail("Only the leader of the leading faction can hand over the nation");
        }
        Faction target = factions().byTag(factionTag);
        if (target == null) {
            return Result.fail("No faction has that tag");
        }
        synchronized (lock) {
            if (target.id().equals(own.id())) {
                return Result.fail("Your faction already leads " + n.tag());
            }
            if (!n.isMember(target.id())) {
                return Result.fail(target.tag() + " is not in your nation");
            }
            n.setLeaderFaction(target.id());
        }
        audit(own, actor, "Handed the nation " + n.tag() + " to " + target.tag());
        audit(target, actor, "Now leads the nation " + n.tag());
        toNation(n, Msg.info(target.tag() + " now leads the nation " + n.tag()));
        publish(n);
        return Result.pass(target.tag() + " now leads " + n.tag());
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
                audit(lead, null, "Now leads the nation " + n.tag() + " after " + why);
            }
        }
        toNation(n, Msg.warn(why));
        publish(n);
    }

    private Result disabled() {
        return ctx.settings().bool("nation.enabled") ? null : Result.fail("Nations are turned off on this server");
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
            return "Please choose a nation tag";
        }
        int min = ctx.settings().integer("nation.tagMin");
        int max = ctx.settings().integer("nation.tagMax");
        if (tag.length() < min) {
            return "Nation tags must be at least " + min + " characters long";
        }
        if (tag.length() > max) {
            return "Nation tags can be at most " + max + " characters long";
        }
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (!(c < 128 && Character.isLetterOrDigit(c))) {
                return "Nation tags may only use letters and digits";
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
