package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Mission;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The last 50 human readable lines built from bus events, newest last. */
final class Feed {
    record Line(long at, String type, String text) {}

    private static final int LIMIT = 50;
    private static final long REPEAT_WINDOW_MS = 10_000L;

    private final DominionContext ctx;
    private final Deque<Line> lines = new ArrayDeque<>();

    Feed(DominionContext ctx) {
        this.ctx = ctx;
    }

    synchronized List<Line> lines() {
        return new ArrayList<>(lines);
    }

    void accept(DominionEvent event) {
        Line line = describe(event);
        if (line == null) {
            return;
        }
        synchronized (this) {
            Line last = lines.peekLast();
            if (last != null && last.text().equals(line.text()) && line.at() - last.at() < REPEAT_WINDOW_MS) {
                return;
            }
            lines.addLast(line);
            while (lines.size() > LIMIT) {
                lines.removeFirst();
            }
        }
    }

    private Line describe(DominionEvent event) {
        long now = Time.now();
        String text = switch (event) {
            case DominionEvent.FactionCreated e -> "The faction " + tag(e.factionId()) + " was founded.";
            case DominionEvent.FactionDisbanded e -> "The faction " + e.tag() + " was disbanded.";
            case DominionEvent.MemberJoined e -> player(e.player()) + " joined " + tag(e.factionId()) + ".";
            case DominionEvent.MemberLeft e -> player(e.player()) + left(e.reason()) + tag(e.factionId()) + ".";
            case DominionEvent.ClaimChanged e -> claim(e);
            case DominionEvent.RelationChanged e -> relation(e);
            case DominionEvent.SiegePhase e -> siege(e);
            case DominionEvent.MarketTrade e -> player(e.buyer()) + " bought " + e.quantity() + " " + item(e.itemId())
                    + " for " + money(e.price()) + ".";
            case DominionEvent.UpgradeBought e -> tag(e.factionId()) + " raised its " + e.key() + " upgrade to level " + e.level() + ".";
            case DominionEvent.MissionCompleted e -> tag(e.factionId()) + " completed the mission " + mission(e.missionId()) + ".";
            case DominionEvent.GraceChanged e -> e.active() ? "The grace period has begun." : "The grace period has ended.";
            default -> null;
        };
        return text == null ? null : new Line(now, type(event), text);
    }

    private static String type(DominionEvent event) {
        return switch (event) {
            case DominionEvent.FactionCreated e -> "faction";
            case DominionEvent.FactionDisbanded e -> "faction";
            case DominionEvent.MemberJoined e -> "join";
            case DominionEvent.MemberLeft e -> "leave";
            case DominionEvent.ClaimChanged e -> "claim";
            case DominionEvent.RelationChanged e -> "relation";
            case DominionEvent.SiegePhase e -> "siege";
            case DominionEvent.MarketTrade e -> "trade";
            case DominionEvent.UpgradeBought e -> "upgrade";
            case DominionEvent.MissionCompleted e -> "mission";
            case DominionEvent.GraceChanged e -> "grace";
            default -> "other";
        };
    }

    private static String left(String reason) {
        String r = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (r.contains("kick")) {
            return " was kicked from ";
        }
        if (r.contains("ban")) {
            return " was banned from ";
        }
        return " left ";
    }

    private String claim(DominionEvent.ClaimChanged e) {
        String where = " in " + e.world() + " at chunk " + Keys.describe(e.chunkIndex()) + ".";
        if (e.newFaction() != null && e.oldFaction() == null) {
            return tag(e.newFaction()) + " claimed land" + where;
        }
        if (e.newFaction() != null) {
            return tag(e.newFaction()) + " took land from " + tag(e.oldFaction()) + where;
        }
        if (e.oldFaction() != null) {
            return tag(e.oldFaction()) + " gave up land" + where;
        }
        return null;
    }

    private String relation(DominionEvent.RelationChanged e) {
        String a = tag(e.a());
        String b = tag(e.b());
        String r = e.relation() == null ? "" : e.relation().toUpperCase(Locale.ROOT);
        return switch (r) {
            case "ALLY" -> a + " and " + b + " are now allies.";
            case "TRUCE" -> a + " and " + b + " agreed to a truce.";
            case "ENEMY" -> a + " declared " + b + " an enemy.";
            default -> a + " and " + b + " are now neutral.";
        };
    }

    private String siege(DominionEvent.SiegePhase e) {
        String att = tag(e.attacker());
        String def = tag(e.defender());
        String p = e.phase() == null ? "" : e.phase().toUpperCase(Locale.ROOT);
        return switch (p) {
            case "CAPTURING" -> att + " began a siege on " + def + ".";
            case "BREACHED" -> att + " breached the defences of " + def + ".";
            case "FAILED" -> "The siege by " + att + " on " + def + " failed.";
            case "DEFENDED" -> def + " held off the siege by " + att + ".";
            case "EXPIRED" -> "The siege by " + att + " on " + def + " came to an end.";
            default -> "The siege by " + att + " on " + def + " changed.";
        };
    }

    private String tag(UUID factionId) {
        Faction f = ctx.repo().faction(factionId);
        return f == null ? "A faction" : f.tag();
    }

    private String player(UUID uuid) {
        if (uuid == null) {
            return "Someone";
        }
        String n = ctx.player(uuid).name();
        return n == null || n.isBlank() ? "Someone" : n;
    }

    private String money(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? String.format(Locale.ROOT, "%.2f coins", amount) : eco.format(amount);
    }

    private String mission(String id) {
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress != null) {
            for (boolean weekly : new boolean[] { false, true }) {
                for (Mission m : progress.activeMissions(weekly)) {
                    if (m.id().equals(id)) {
                        return m.name();
                    }
                }
            }
        }
        return id;
    }

    static String item(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "items";
        }
        String s = itemId.replace("_Item", "").replace('_', ' ').trim();
        return s.isEmpty() ? itemId : s;
    }
}
