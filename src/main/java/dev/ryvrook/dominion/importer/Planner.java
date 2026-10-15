package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.text.Messages;

import dev.ryvrook.dominion.config.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns what a source reader found into decisions against Dominion's current state, without
 * changing anything. The state is reached through {@link State} so the rules can be tested without
 * a server.
 */
final class Planner {
    private Planner() {}

    /** The parts of Dominion's state an import plan depends on. */
    interface State {
        Settings settings();

        /** Tag of an existing faction, or null when there is none with that id. */
        String tagOf(UUID factionId);

        /** Player to the tag of the Dominion faction they are in. */
        Map<UUID, String> memberships();

        boolean tagTaken(String tag);

        boolean claimed(String world, long chunk);

        /** A name Dominion already knows for the player, or null. */
        String knownName(UUID player);
    }

    /** What will happen to one source faction once Dominion's current state is taken into account. */
    static final class Decision {
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

    record Resolved(Plan plan, List<Decision> decisions, List<String> skipped) {}

    static Resolved resolve(Plan plan, Map<String, String> ledger, State st) {
        List<Decision> decisions = new ArrayList<>();
        List<String> skipped = new ArrayList<>(plan.skipped);
        Map<UUID, String> memberOf = st.memberships();
        Set<UUID> assigned = new HashSet<>();
        Set<String> usedTags = new HashSet<>();
        Map<String, Set<Long>> planned = new HashMap<>();
        for (Plan.Faction f : plan.factions) {
            String label = f.name == null || f.name.isBlank() ? f.sourceId : f.name;
            String previous = st.tagOf(Data.uuid(ledger.get(f.sourceId)));
            if (previous != null) {
                skipped.add(Messages.get(ImporterText.ALREADY_IMPORTED, "faction", label, "tag", previous));
                continue;
            }
            if (ledger.containsKey(f.sourceId)) {
                // A faction imported earlier and disbanded since is not brought back.
                skipped.add(Messages.get(ImporterText.IMPORTED_AND_GONE, "faction", label));
                continue;
            }
            Decision d = new Decision(f);
            List<Plan.Member> free = new ArrayList<>();
            for (Plan.Member m : f.members.values()) {
                String other = memberOf.get(m.uuid());
                if (other != null) {
                    d.issues.add(Messages.get(ImporterText.MEMBER_ELSEWHERE, "player", nameOf(m, st), "tag", other));
                } else if (!assigned.add(m.uuid())) {
                    d.issues.add(Messages.get(ImporterText.MEMBER_EARLIER, "player", nameOf(m, st)));
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
                    d.issues.add(Messages.get(ImporterText.NEW_LEADER, "player", nameOf(lead, st)));
                }
            }
            if (lead == null) {
                skipped.add(Messages.get(ImporterText.NO_LEADER, "faction", label));
                free.forEach(m -> assigned.remove(m.uuid()));
                continue;
            }
            d.leader = lead.uuid();
            d.leaderName = nameOf(lead, st);
            for (Plan.Member m : free) {
                if (!m.uuid().equals(d.leader)) {
                    d.joins.add(m);
                }
            }
            d.name = cleanName(label, st.settings());
            String wanted = usableTag(f.preferredTag, st.settings()) ? f.preferredTag.strip() : label;
            d.tag = Tags.unique(wanted, st.settings(), t -> !st.tagTaken(t) && !usedTags.contains(t.toLowerCase(Locale.ROOT)));
            if (d.tag == null) {
                skipped.add(Messages.get(ImporterText.NO_TAG, "faction", label));
                free.forEach(m -> assigned.remove(m.uuid()));
                continue;
            }
            usedTags.add(d.tag.toLowerCase(Locale.ROOT));
            if (!d.tag.equalsIgnoreCase(wanted.strip())) {
                d.issues.add(Messages.get(st.tagTaken(Tags.base(wanted, st.settings())) ? ImporterText.TAG_BECOMES_TAKEN
                        : ImporterText.TAG_BECOMES, "tag", d.tag));
            }
            for (Map.Entry<String, LinkedHashSet<Long>> e : f.chunks.entrySet()) {
                Set<Long> seen = planned.computeIfAbsent(e.getKey(), w -> new HashSet<>());
                for (long idx : e.getValue()) {
                    if (st.claimed(e.getKey(), idx)) {
                        d.taken++;
                    } else if (!seen.add(idx)) {
                        d.duplicate++;
                    } else {
                        d.claims.computeIfAbsent(e.getKey(), w -> new ArrayList<>()).add(idx);
                    }
                }
            }
            if (d.taken > 0) {
                d.issues.add(plural(d.taken, ImporterText.TAKEN_ONE, ImporterText.TAKEN_MANY));
            }
            if (d.duplicate > 0) {
                d.issues.add(plural(d.duplicate, ImporterText.DUPLICATE_ONE, ImporterText.DUPLICATE_MANY));
            }
            decisions.add(d);
        }
        return new Resolved(plan, decisions, skipped);
    }

    static String nameOf(Plan.Member m, State st) {
        if (m.name() != null && !m.name().isBlank()) {
            return m.name();
        }
        String known = st.knownName(m.uuid());
        if (known != null && !known.isBlank()) {
            return known;
        }
        return "Player" + Data.shortId(m.uuid());
    }

    static String cleanName(String name, Settings s) {
        String n = name.replaceAll("\\p{Cntrl}", " ").strip();
        int max = Math.max(1, s.integer("faction.nameMax"));
        return n.length() > max ? n.substring(0, max).strip() : n;
    }

    /** True when the source's own tag fits Dominion's tag rules without changes. */
    static boolean usableTag(String tag, Settings s) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        String t = tag.strip();
        return t.length() >= s.integer("faction.tagMin") && t.length() <= s.integer("faction.tagMax")
                && t.equals(Tags.base(t, s));
    }

    /** The message for one or for many with the count filled in. */
    static String plural(int n, String one, String many) {
        return Messages.get(n == 1 ? one : many, "count", n);
    }
}
