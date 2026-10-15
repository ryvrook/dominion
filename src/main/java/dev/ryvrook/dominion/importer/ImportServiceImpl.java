package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.text.Messages;

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

    private Source find(String source) {
        return source == null ? null : sources.get(source.strip().toLowerCase(Locale.ROOT));
    }

    /** Dominion's live state as the planner sees it. */
    private final Planner.State state = new Planner.State() {
        @Override
        public dev.ryvrook.dominion.config.Settings settings() {
            return ctx.settings();
        }

        @Override
        public String tagOf(UUID factionId) {
            Faction f = factionId == null ? null : ctx.repo().faction(factionId);
            return f == null ? null : f.tag();
        }

        @Override
        public Map<UUID, String> memberships() {
            Map<UUID, String> out = new HashMap<>();
            for (Faction f : ctx.repo().factions()) {
                for (Member m : f.members()) {
                    out.put(m.uuid(), f.tag());
                }
            }
            return out;
        }

        @Override
        public boolean tagTaken(String tag) {
            return ctx.repo().tagTaken(tag);
        }

        @Override
        public boolean claimed(String world, long chunk) {
            return ctx.repo().claimAt(world, chunk) != null;
        }

        @Override
        public String knownName(UUID player) {
            PlayerData p = ctx.repo().playerIfLoaded(player);
            return p == null ? null : p.name();
        }
    };

    private Planner.Resolved resolve(Source source) {
        Plan plan = new Plan(source.id(), source.label());
        try {
            source.read(new Locator(ctx), plan);
        } catch (Exception e) {
            plan.unsupported = Messages.get(ImporterText.UNREADABLE, "source", source.label(), "reason", e.getMessage());
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion import could not read %s data", source.label());
        }
        Map<String, String> ledger = ledger(source.id());
        return Planner.resolve(plan, ledger == null ? Map.of() : ledger, state);
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
            return List.of(Messages.get(ImporterText.NO_SUCH_SOURCE, "source", source, "sources", String.join(", ", sources.keySet())));
        }
        Planner.Resolved r = resolve(s);
        Plan plan = r.plan();
        List<String> out = new ArrayList<>();
        out.add(Messages.get(ImporterText.PREVIEW_HEADER, "source", plan.label));
        if (plan.experimental) {
            out.add(Messages.get(ImporterText.EXPERIMENTAL, "source", plan.label));
        }
        if (plan.unsupported != null) {
            out.add(Messages.get(ImporterText.CANNOT_RUN_UNREADABLE, "problem", plan.unsupported));
        }
        if (ledger(s.id()) == null) {
            out.add(Messages.get(ImporterText.LEDGER_UNREADABLE, "file", ledgerFile(s.id())));
        }
        if (plan.found == null) {
            out.add(Messages.get(ImporterText.NOTHING_FOUND, "source", plan.label));
            plan.searched.forEach(p -> out.add(Messages.get(ImporterText.LOOKED_FOR, "path", p)));
            return out;
        }
        out.add(Messages.get(ImporterText.READING_FROM, "source", plan.label, "path", plan.found));
        int members = 0;
        int chunks = 0;
        for (Planner.Decision d : r.decisions()) {
            members += 1 + d.joins.size();
            chunks += d.claimCount();
        }
        out.add(Messages.get(ImporterText.WOULD_CREATE, "factions", factions(r.decisions().size()), "members", members(members),
                "chunks", chunks(chunks)));
        for (Planner.Decision d : r.decisions()) {
            out.add(Messages.get(ImporterText.WOULD_BECOME, "faction", d.f.name, "tag", d.tag, "leader", d.leaderName,
                    "members", members(1 + d.joins.size()), "chunks", chunks(d.claimCount()), "worlds", worlds(d)));
            for (String issue : d.issues) {
                out.add("  " + Messages.get(ImporterText.LINE, "text", issue));
            }
        }
        if (!r.skipped().isEmpty()) {
            out.add(Messages.get(ImporterText.SKIPPED_COUNT, "records", records(r.skipped().size())));
            for (int i = 0; i < r.skipped().size() && i < MAX_LISTED; i++) {
                out.add("  " + Messages.get(ImporterText.LINE, "text", r.skipped().get(i)));
            }
            if (r.skipped().size() > MAX_LISTED) {
                out.add("  " + Messages.get(ImporterText.MORE_SKIPPED, "count", r.skipped().size() - MAX_LISTED));
            }
        }
        for (String note : plan.notes) {
            out.add(Messages.get(ImporterText.LINE, "text", note));
        }
        out.add(Messages.get(ImporterText.NOT_IMPORTED));
        return out;
    }

    private static String worlds(Planner.Decision d) {
        if (d.claims.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        d.claims.forEach((w, l) -> parts.add(Messages.get(ImporterText.WORLD_PART, "count", l.size(), "world", w)));
        String joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = Messages.get(ImporterText.WORLDS_AND, "list", joined, "last", parts.get(i));
        }
        return Messages.get(ImporterText.WORLDS, "parts", joined);
    }

    // ---- run -------------------------------------------------------------

    @Override
    public Result run(UUID admin, String adminName, String source) {
        Source s = find(source);
        if (s == null) {
            return Result.fail(Messages.get(ImporterText.RUN_NO_SUCH_SOURCE, "source", source, "sources", String.join(", ", sources.keySet())));
        }
        if (!previewed(admin, s.id())) {
            return Result.fail(Messages.get(ImporterText.PREVIEW_FIRST, "source", s.id()));
        }
        String who = adminName == null || adminName.isBlank() ? Messages.get(ImporterText.AN_ADMIN) : adminName;
        FactionService factions = ctx.service(FactionService.class);
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        synchronized (lock) {
            Planner.Resolved r = resolve(s);
            Plan plan = r.plan();
            if (plan.unsupported != null) {
                // A read that failed part way would import some factions without their land and mark them done for good.
                return Result.fail(Messages.get(ImporterText.NOTHING_IMPORTED, "problem", plan.unsupported));
            }
            if (plan.found == null) {
                return Result.fail(Messages.get(ImporterText.RUN_NOTHING_FOUND, "source", plan.label));
            }
            if (r.decisions().isEmpty()) {
                return Result.fail(Messages.get(ImporterText.NOTHING_NEW, "source", plan.label, "records", records(r.skipped().size())));
            }
            Map<String, String> ledger = ledger(s.id());
            if (ledger == null) {
                return Result.fail(Messages.get(ImporterText.RUN_LEDGER_UNREADABLE, "file", ledgerFile(s.id())));
            }
            int created = 0;
            int joined = 0;
            int landed = 0;
            int lost = 0;
            try {
                for (Planner.Decision d : r.decisions()) {
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
                    factions.audit(f, admin, who, "admin", Messages.get(ImporterText.AUDIT_IMPORTED, "admin", who, "source", plan.label,
                            "name", d.f.name, "members", members(f.memberCount()), "chunks", chunks(ctx.repo().claimCount(f.id()))));
                    ctx.repo().saveFaction(f);
                    ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
                    log("%s imported %s from %s as %s", who, d.f.name, plan.label, f.tag());
                }
            } finally {
                saveLedger(s.id(), ledger);
            }
            StringBuilder msg = new StringBuilder(Messages.get(ImporterText.IMPORTED, "factions", factions(created), "source", plan.label,
                    "members", members(created + joined), "chunks", chunks(landed)));
            if (lost > 0) {
                msg.append(' ').append(Messages.get(ImporterText.CHUNKS_LOST, "chunks", chunks(lost)));
            }
            if (claims == null) {
                msg.append(' ').append(Messages.get(ImporterText.NO_LAND_MODULE));
            }
            if (!r.skipped().isEmpty()) {
                msg.append(' ').append(Messages.get(ImporterText.RUN_SKIPPED, "records", records(r.skipped().size())));
            }
            msg.append(' ').append(Messages.get(ImporterText.UNTOUCHED, "source", plan.label));
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
        return Planner.nameOf(m, state);
    }

    private static String knownName(UUID uuid, Plan.Faction f) {
        Plan.Member m = f.members.get(uuid);
        return m == null || m.name() == null || m.name().isBlank() ? null : m.name();
    }

    // ---- ledger ----------------------------------------------------------

    private Path ledgerFile(String source) {
        return ctx.repo().root().resolve("imports").resolve(source + ".json");
    }

    /** The record of earlier imports from a source, or null when the file exists but cannot be read. */
    private Map<String, String> ledger(String source) {
        Map<String, String> out = new HashMap<>();
        Path file = ledgerFile(source);
        if (Data.isFile(file)) {
            try {
                Data.json(file).forEach((k, v) -> out.put(k, String.valueOf(v)));
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion import ledger %s is unreadable", file);
                return null;
            }
        }
        return out;
    }

    private void saveLedger(String source, Map<String, String> ledger) {
        Document d = new Document();
        ledger.forEach(d::append);
        ctx.files().write(ledgerFile(source), d);
    }

    private static String factions(int n) {
        return Planner.plural(n, ImporterText.FACTIONS_ONE, ImporterText.FACTIONS_MANY);
    }

    private static String members(int n) {
        return Planner.plural(n, ImporterText.MEMBERS_ONE, ImporterText.MEMBERS_MANY);
    }

    private static String chunks(int n) {
        return Planner.plural(n, ImporterText.CHUNKS_ONE, ImporterText.CHUNKS_MANY);
    }

    private static String records(int n) {
        return Planner.plural(n, ImporterText.RECORDS_ONE, ImporterText.RECORDS_MANY);
    }

    private void log(String format, Object... args) {
        ctx.logger().at(Level.INFO).log("Dominion import " + String.format(Locale.ROOT, format, args));
    }
}
