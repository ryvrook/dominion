package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.ImportService;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Previews and runs imports from other land mods. Source files are only read. Every created
 * faction is recorded in imports/<source>.json under Dominion's data folder so a second run
 * skips factions that were already imported.
 */
final class ImportServiceImpl implements ImportService {
    private static final int MAX_LISTED = 25;
    private static final int DESCRIPTION_MAX = 120;

    private final DominionContext ctx;
    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Object lock = new Object();
    /** How long a preview allows a run of the same source. */
    static final long PREVIEW_WINDOW_MS = 10 * 60_000L;
    /** Key for console previews, which carry no admin. */
    private static final UUID CONSOLE = new UUID(0L, 0L);
    /** The source each admin last previewed and when. */
    private final Map<UUID, Previewed> previews = new java.util.concurrent.ConcurrentHashMap<>();

    private record Previewed(String source, long at) {}

    ImportServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        for (Source s : List.of(new SimpleClaimsSource(), new HyFactionsSource(), new HyperFactionsSource(), new UltimateFactionsSource())) {
            sources.put(s.id(), s);
        }
    }

    @Override
    public List<String> sources() {
        return List.copyOf(sources.keySet());
    }

    // ---- planning --------------------------------------------------------

    /** What will happen to one source faction once Dominion's current state is taken into account. */
    private static final class Decision {
        final Plan.Faction f;
        String tag;
        String name;
        UUID leader;
        String leaderName;
        final List<Plan.Member> joins = new ArrayList<>();
        final Map<String, List<Long>> claims = new LinkedHashMap<>();
        int taken;
        int duplicate;
        final List<String> issues = new ArrayList<>();

        Decision(Plan.Faction f) {
            this.f = f;
        }

        int claimCount() {
            int n = 0;
            for (List<Long> l : claims.values()) {
                n += l.size();
            }
            return n;
        }
    }

    private record Resolved(Plan plan, List<Decision> decisions, List<String> skipped) {}

    private Source find(String source) {
        return source == null ? null : sources.get(source.strip().toLowerCase(Locale.ROOT));
    }

    private Resolved resolve(Source source) {
        Plan plan = new Plan(source.id(), source.label());
        try {
            source.read(new Locator(ctx), plan);
        } catch (Exception e) {
            plan.unsupported = "The " + source.label() + " data could not be read because " + e.getMessage();
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion import could not read %s data", source.label());
        }
        List<Decision> decisions = new ArrayList<>();
        List<String> skipped = new ArrayList<>(plan.skipped);
        Map<String, String> ledger = ledger(source.id());
        Map<UUID, Faction> memberOf = new HashMap<>();
        for (Faction f : ctx.repo().factions()) {
            for (Member m : f.members()) {
                memberOf.put(m.uuid(), f);
            }
        }
        Set<UUID> assigned = new HashSet<>();
        Set<String> usedTags = new HashSet<>();
        Map<String, Set<Long>> planned = new HashMap<>();
        for (Plan.Faction f : plan.factions) {
            String label = f.name == null ? f.sourceId : f.name;
            Faction previous = ctx.repo().faction(Data.uuid(ledger.get(f.sourceId)));
            if (previous != null) {
                skipped.add(label + " was already imported as " + previous.tag());
                continue;
            }
            if (ledger.containsKey(f.sourceId)) {
                // A faction imported earlier and disbanded since is not brought back.
                skipped.add(label + " was imported before and that faction no longer exists");
                continue;
            }
            Decision d = new Decision(f);
            List<Plan.Member> free = new ArrayList<>();
            for (Plan.Member m : f.members.values()) {
                Faction other = memberOf.get(m.uuid());
                if (other != null) {
                    d.issues.add(nameOf(m) + " is already in the Dominion faction " + other.tag() + " and is left out");
                } else if (!assigned.add(m.uuid())) {
                    d.issues.add(nameOf(m) + " belongs to an earlier faction in this import and is left out");
                } else {
                    free.add(m);
                }
            }
            Plan.Member lead = null;
            for (Plan.Member m : free) {
                if (m.uuid().equals(f.leader)) {
                    lead = m;
                }
            }
            if (lead == null) {
                for (Plan.Member m : free) {
                    if (lead == null || m.rank().ordinal() > lead.rank().ordinal()) {
                        lead = m;
                    }
                }
                if (lead != null) {
                    d.issues.add("The original leader cannot lead it, so " + nameOf(lead) + " becomes the leader");
                }
            }
            if (lead == null) {
                skipped.add(label + " is skipped because none of its members is free to lead it");
                free.forEach(m -> assigned.remove(m.uuid()));
                continue;
            }
            d.leader = lead.uuid();
            d.leaderName = nameOf(lead);
            for (Plan.Member m : free) {
                if (!m.uuid().equals(d.leader)) {
                    d.joins.add(m);
                }
            }
            d.name = cleanName(label);
            String wanted = usableTag(f.preferredTag) ? f.preferredTag.strip() : label;
            d.tag = uniqueTag(wanted, usedTags);
            if (d.tag == null) {
                skipped.add(label + " is skipped because no free tag could be made from its name");
                free.forEach(m -> assigned.remove(m.uuid()));
                continue;
            }
            usedTags.add(d.tag.toLowerCase(Locale.ROOT));
            if (!d.tag.equalsIgnoreCase(wanted.strip())) {
                String why = ctx.repo().tagTaken(Tags.base(wanted, ctx.settings())) ? " because that tag is already used in Dominion" : "";
                d.issues.add("Its tag becomes " + d.tag + why);
            }
            for (Map.Entry<String, java.util.LinkedHashSet<Long>> e : f.chunks.entrySet()) {
                Set<Long> seen = planned.computeIfAbsent(e.getKey(), w -> new HashSet<>());
                for (long idx : e.getValue()) {
                    if (ctx.repo().claimAt(e.getKey(), idx) != null) {
                        d.taken++;
                    } else if (!seen.add(idx)) {
                        d.duplicate++;
                    } else {
                        d.claims.computeIfAbsent(e.getKey(), w -> new ArrayList<>()).add(idx);
                    }
                }
            }
            if (d.taken > 0) {
                d.issues.add(plural(d.taken, "of its chunks") + " already claimed in Dominion and skipped");
            }
            if (d.duplicate > 0) {
                d.issues.add(plural(d.duplicate, "of its chunks") + " also claimed by an earlier faction in this import and skipped");
            }
            decisions.add(d);
        }
        return new Resolved(plan, decisions, skipped);
    }

    // ---- preview ---------------------------------------------------------

    /**
     * A preview recorded for the given admin, so that admin may run the same source within 10 minutes.
     * The console passes no admin and its previews only cover console runs.
     */
    @Override
    public List<String> preview(UUID admin, String source) {
        List<String> lines = describe(source);
        Source s = find(source);
        if (s != null) {
            previews.put(admin == null ? CONSOLE : admin, new Previewed(s.id(), System.currentTimeMillis()));
        }
        return lines;
    }

    /** True when this admin, or the console when there is no admin, previewed the source in the last 10 minutes. */
    private boolean previewed(UUID admin, String sourceId) {
        long now = System.currentTimeMillis();
        previews.values().removeIf(p -> now - p.at() > PREVIEW_WINDOW_MS);
        Previewed p = previews.get(admin == null ? CONSOLE : admin);
        return p != null && p.source().equals(sourceId) && now - p.at() <= PREVIEW_WINDOW_MS;
    }

    private List<String> describe(String source) {
        Source s = find(source);
        if (s == null) {
            return List.of("There is no import source called " + source + ". Choose one of " + String.join(", ", sources.keySet()) + ".");
        }
        Resolved r = resolve(s);
        Plan plan = r.plan();
        List<String> out = new ArrayList<>();
        out.add(plan.label + " import preview. Nothing is changed until the import is run.");
        if (plan.experimental) {
            out.add("This source is experimental because " + plan.label + " is closed source and its format is not officially documented.");
        }
        if (plan.unsupported != null) {
            out.add(plan.unsupported + ".");
        }
        if (plan.found == null) {
            out.add("No " + plan.label + " data was found.");
            plan.searched.forEach(p -> out.add("Looked for " + p));
            return out;
        }
        out.add("Reading " + plan.label + " data from " + plan.found);
        int members = 0;
        int chunks = 0;
        for (Decision d : r.decisions()) {
            members += 1 + d.joins.size();
            chunks += d.claimCount();
        }
        out.add(plural(r.decisions().size(), "faction") + " with " + plural(members, "member") + " and " + plural(chunks, "chunk") + " would be created.");
        for (Decision d : r.decisions()) {
            out.add(d.f.name + " would become " + d.tag + " led by " + d.leaderName + " with " + plural(1 + d.joins.size(), "member")
                    + " and " + plural(d.claimCount(), "chunk") + worlds(d) + ".");
            for (String issue : d.issues) {
                out.add("  " + issue + ".");
            }
        }
        if (!r.skipped().isEmpty()) {
            out.add(plural(r.skipped().size(), "record") + " skipped.");
            for (int i = 0; i < r.skipped().size() && i < MAX_LISTED; i++) {
                out.add("  " + r.skipped().get(i) + ".");
            }
            if (r.skipped().size() > MAX_LISTED) {
                out.add("  And " + (r.skipped().size() - MAX_LISTED) + " more skipped records.");
            }
        }
        for (String note : plan.notes) {
            out.add(note + ".");
        }
        out.add("Homes, relations, power, banks and permissions are not imported.");
        return out;
    }

    private static String worlds(Decision d) {
        if (d.claims.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        d.claims.forEach((w, l) -> parts.add(l.size() + " in " + w));
        return ", " + String.join(" and ", parts);
    }

    // ---- run -------------------------------------------------------------

    @Override
    public Result run(UUID admin, String adminName, String source) {
        Source s = find(source);
        if (s == null) {
            return Result.fail("There is no import source called " + source + ". Choose one of " + String.join(", ", sources.keySet()));
        }
        if (!previewed(admin, s.id())) {
            return Result.fail("Preview the import from " + s.id() + " first and run it within 10 minutes of the preview");
        }
        String who = adminName == null || adminName.isBlank() ? "An admin" : adminName;
        FactionService factions = ctx.service(FactionService.class);
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        synchronized (lock) {
            Resolved r = resolve(s);
            Plan plan = r.plan();
            if (plan.found == null) {
                return Result.fail(plan.unsupported != null ? plan.unsupported : "No " + plan.label + " data was found. Use the preview to see where Dominion looked");
            }
            if (r.decisions().isEmpty()) {
                return Result.fail("There is nothing new to import from " + plan.label + ". " + plural(r.skipped().size(), "record") + " skipped");
            }
            Map<String, String> ledger = ledger(s.id());
            int created = 0;
            int joined = 0;
            int landed = 0;
            int lost = 0;
            try {
                for (Decision d : r.decisions()) {
                    if (ctx.repo().tagTaken(d.tag)) {
                        log("Skipped %s because the tag %s was taken while importing", d.f.name, d.tag);
                        continue;
                    }
                    if (ctx.repo().faction(ctx.player(d.leader).factionId()) != null) {
                        log("Skipped %s because %s joined a faction while importing", d.f.name, d.leaderName);
                        continue;
                    }
                    long now = Time.now();
                    Faction f = new Faction(UUID.randomUUID(), d.tag, d.name, d.leader, d.f.createdAt > 0 ? d.f.createdAt : now);
                    applyDefaults(f);
                    if (d.f.color != null) {
                        f.setColor(d.f.color);
                    }
                    String desc = d.f.description == null ? "" : d.f.description.replaceAll("\\p{Cntrl}", " ").strip();
                    f.setDescription(desc.length() > DESCRIPTION_MAX ? desc.substring(0, DESCRIPTION_MAX) : desc);
                    f.putMember(new Member(d.leader, d.leaderName, Rank.LEADER, now));
                    ctx.repo().addFaction(f);
                    PlayerData leader = ctx.player(d.leader);
                    if (knownName(d.leader, d.f) != null) {
                        leader.setName(d.leaderName);
                    }
                    leader.setFactionId(f.id());
                    ctx.bus().publish(new DominionEvent.FactionCreated(f.id()));
                    ctx.bus().publish(new DominionEvent.MemberJoined(f.id(), d.leader));
                    created++;
                    ledger.put(d.f.sourceId, f.id().toString());
                    for (Plan.Member m : d.joins) {
                        Result jr;
                        try {
                            jr = factions.forceJoin(admin, who, m.uuid(), nameOf(m), f);
                        } catch (RuntimeException e) {
                            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion import failed to add %s to %s", nameOf(m), f.tag());
                            jr = Result.fail(String.valueOf(e.getMessage()));
                        }
                        if (f.isMember(m.uuid()) && jr.failed()) {
                            jr = Result.pass("");
                        }
                        if (jr.failed()) {
                            log("Could not add %s to %s because %s", nameOf(m), f.tag(), jr.message());
                            continue;
                        }
                        joined++;
                        Member member = f.member(m.uuid());
                        if (member != null && m.rank() != Rank.RECRUIT && member.rank() != m.rank()) {
                            synchronized (f) {
                                member.setRank(m.rank() == Rank.LEADER ? Rank.OFFICER : m.rank());
                            }
                            f.putMember(member);
                        }
                    }
                    if (claims == null) {
                        lost += d.claimCount();
                    } else {
                        for (Map.Entry<String, List<Long>> e : d.claims.entrySet()) {
                            for (long idx : e.getValue()) {
                                ClaimEntry existing = ctx.repo().claimAt(e.getKey(), idx);
                                if (existing != null) {
                                    lost++;
                                    continue;
                                }
                                boolean ok;
                                try {
                                    // Quiet, so a large import writes one audit line and one event per faction instead of per chunk.
                                    ok = claims.adminClaim(admin, f, e.getKey(), idx, true).success();
                                } catch (RuntimeException ex) {
                                    ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion import failed to claim %s in %s", Keys.describe(idx), e.getKey());
                                    ok = ctx.repo().claimAt(e.getKey(), idx) != null;
                                }
                                if (ok) {
                                    landed++;
                                } else {
                                    lost++;
                                }
                            }
                        }
                    }
                    // One audit line and one FactionChanged per faction, published after all of its claims.
                    factions.audit(f, admin, who, "admin", who + " imported this faction from " + plan.label + " where it was "
                            + d.f.name + ", with " + plural(f.memberCount(), "member") + " and " + plural(ctx.repo().claimCount(f.id()), "chunk"));
                    ctx.repo().saveFaction(f);
                    ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
                    log("%s imported %s from %s as %s", who, d.f.name, plan.label, f.tag());
                }
            } finally {
                saveLedger(s.id(), ledger);
            }
            StringBuilder msg = new StringBuilder("Imported " + plural(created, "faction") + " from " + plan.label + " with "
                    + plural(created + joined, "member") + " and " + plural(landed, "chunk") + ".");
            if (lost > 0) {
                msg.append(' ').append(plural(lost, "chunk")).append(" could not be claimed.");
            }
            if (claims == null) {
                msg.append(" The land module is not loaded so no land was imported.");
            }
            if (!r.skipped().isEmpty()) {
                msg.append(' ').append(plural(r.skipped().size(), "record")).append(" skipped. The preview lists them.");
            }
            msg.append(" The ").append(plan.label).append(" data was left untouched.");
            log("%s", msg);
            return created == 0 ? Result.fail(msg.toString()) : Result.pass(msg.toString());
        }
    }

    /** Mirrors the defaults the faction module gives a newly created faction. */
    private void applyDefaults(Faction f) {
        f.setSetting("open", false);
        f.setSetting("friendlyFire", ctx.settings().bool("combat.friendlyFireDefault"));
        boolean allyBuild = ctx.settings().bool("relation.allyBuildDefault");
        f.setSetting("allyBuild", allyBuild);
        f.setRelationPerm(Relation.ALLY, FactionAction.BUILD, allyBuild);
        boolean truceInteract = ctx.settings().bool("relation.truceInteractDefault");
        f.setSetting("truceInteract", truceInteract);
        f.setRelationPerm(Relation.TRUCE, FactionAction.INTERACT, truceInteract);
        f.setSetting("allyHome", true);
        f.setSetting("homeOnDeath", ctx.settings().bool("home.respawnAtHomeDefault"));
        f.setSetting("loginAlerts", ctx.settings().bool("chat.loginAlerts"));
    }

    // ---- names and tags --------------------------------------------------

    private String nameOf(Plan.Member m) {
        if (m.name() != null && !m.name().isBlank()) {
            return m.name();
        }
        PlayerData p = ctx.repo().playerIfLoaded(m.uuid());
        if (p != null && p.name() != null && !p.name().isBlank()) {
            return p.name();
        }
        return "Player" + Data.shortId(m.uuid());
    }

    private static String knownName(UUID uuid, Plan.Faction f) {
        Plan.Member m = f.members.get(uuid);
        return m == null || m.name() == null || m.name().isBlank() ? null : m.name();
    }

    private String cleanName(String name) {
        String n = name.replaceAll("\\p{Cntrl}", " ").strip();
        int max = Math.max(1, ctx.settings().integer("faction.nameMax"));
        return n.length() > max ? n.substring(0, max).strip() : n;
    }

    /** True when the source's own tag fits Dominion's tag rules without changes. */
    private boolean usableTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        String t = tag.strip();
        return t.length() >= ctx.settings().integer("faction.tagMin") && t.length() <= ctx.settings().integer("faction.tagMax")
                && t.equals(Tags.base(t, ctx.settings()));
    }

    private String uniqueTag(String wanted, Set<String> used) {
        return Tags.unique(wanted, ctx.settings(), t -> !ctx.repo().tagTaken(t) && !used.contains(t.toLowerCase(Locale.ROOT)));
    }

    // ---- ledger ----------------------------------------------------------

    private Path ledgerFile(String source) {
        return ctx.repo().root().resolve("imports").resolve(source + ".json");
    }

    private Map<String, String> ledger(String source) {
        Map<String, String> out = new HashMap<>();
        Path file = ledgerFile(source);
        if (Data.isFile(file)) {
            try {
                Data.json(file).forEach((k, v) -> out.put(k, String.valueOf(v)));
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion import ledger %s is unreadable", file);
            }
        }
        return out;
    }

    private void saveLedger(String source, Map<String, String> ledger) {
        Document d = new Document();
        ledger.forEach(d::append);
        ctx.files().write(ledgerFile(source), d);
    }

    /** A count with its noun, adding is or are when the noun starts with of. */
    private static String plural(int n, String noun) {
        if (noun.startsWith("of ")) {
            return n + " " + noun + (n == 1 ? " is" : " are");
        }
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private void log(String format, Object... args) {
        ctx.logger().at(Level.INFO).log("Dominion import " + String.format(Locale.ROOT, format, args));
    }
}
