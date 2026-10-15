package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Mission;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.text.Messages;
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
    private long lastAt;

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
            // Strictly increasing, like the chat lines, because the web map hands over each line
            // newer than the last one it saw and would drop a second line in the same millisecond.
            long at = Math.max(line.at(), lastAt + 1);
            lastAt = at;
            lines.addLast(new Line(at, line.type(), line.text()));
            while (lines.size() > LIMIT) {
                lines.removeFirst();
            }
        }
    }

    private Line describe(DominionEvent event) {
        long now = Time.now();
        String text = switch (event) {
            case DominionEvent.FactionCreated e -> Messages.get(ExportText.FOUNDED, "faction", tag(e.factionId()));
            case DominionEvent.FactionDisbanded e -> Messages.get(ExportText.DISBANDED, "faction", e.tag());
            case DominionEvent.MemberJoined e -> Messages.get(ExportText.JOINED, "player", player(e.player()), "faction", tag(e.factionId()));
            case DominionEvent.MemberLeft e -> Messages.get(left(e.reason()), "player", player(e.player()), "faction", tag(e.factionId()));
            case DominionEvent.ClaimChanged e -> claim(e);
            case DominionEvent.RelationChanged e -> relation(e);
            case DominionEvent.SiegePhase e -> siege(e);
            case DominionEvent.MarketTrade e -> Messages.get(ExportText.TRADE, "player", player(e.buyer()), "count", e.quantity(),
                    "item", item(e.itemId()), "amount", money(e.price()));
            case DominionEvent.UpgradeBought e -> Messages.get(ExportText.UPGRADE, "faction", tag(e.factionId()), "upgrade", e.key(),
                    "level", e.level());
            case DominionEvent.MissionCompleted e -> Messages.get(ExportText.MISSION, "faction", tag(e.factionId()),
                    "mission", mission(e.missionId()));
            case DominionEvent.GraceChanged e -> Messages.get(e.active() ? ExportText.GRACE_BEGUN : ExportText.GRACE_ENDED);
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

    /** The message id for a member leaving, picked by the reason. */
    private static String left(String reason) {
        String r = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (r.contains("kick")) {
            return ExportText.KICKED;
        }
        if (r.contains("ban")) {
            return ExportText.BANNED;
        }
        return ExportText.LEFT;
    }

    private String claim(DominionEvent.ClaimChanged e) {
        String chunk = Keys.describe(e.chunkIndex());
        if (e.newFaction() != null && e.oldFaction() == null) {
            return Messages.get(ExportText.CLAIMED, "faction", tag(e.newFaction()), "world", e.world(), "chunk", chunk);
        }
        if (e.newFaction() != null) {
            return Messages.get(ExportText.TOOK_LAND, "faction", tag(e.newFaction()), "other", tag(e.oldFaction()),
                    "world", e.world(), "chunk", chunk);
        }
        if (e.oldFaction() != null) {
            return Messages.get(ExportText.GAVE_UP, "faction", tag(e.oldFaction()), "world", e.world(), "chunk", chunk);
        }
        return null;
    }

    private String relation(DominionEvent.RelationChanged e) {
        String a = tag(e.a());
        String b = tag(e.b());
        String r = e.relation() == null ? "" : e.relation().toUpperCase(Locale.ROOT);
        String id = switch (r) {
            case "ALLY" -> ExportText.ALLIES;
            case "TRUCE" -> ExportText.TRUCE;
            case "ENEMY" -> ExportText.ENEMY;
            default -> ExportText.NEUTRAL;
        };
        return Messages.get(id, "a", a, "b", b);
    }

    private String siege(DominionEvent.SiegePhase e) {
        String att = tag(e.attacker());
        String def = tag(e.defender());
        String p = e.phase() == null ? "" : e.phase().toUpperCase(Locale.ROOT);
        String id = switch (p) {
            case "CAPTURING" -> ExportText.SIEGE_BEGAN;
            case "BREACHED" -> ExportText.SIEGE_BREACHED;
            case "FAILED" -> ExportText.SIEGE_FAILED;
            case "DEFENDED" -> ExportText.SIEGE_DEFENDED;
            case "EXPIRED" -> ExportText.SIEGE_EXPIRED;
            default -> ExportText.SIEGE_CHANGED;
        };
        return Messages.get(id, "attacker", att, "defender", def);
    }

    private String tag(UUID factionId) {
        Faction f = ctx.repo().faction(factionId);
        return f == null ? Messages.get(ExportText.A_FACTION) : f.tag();
    }

    private String player(UUID uuid) {
        if (uuid == null) {
            return Messages.get(ExportText.SOMEONE);
        }
        // Loaded data first, so naming an offline player never creates a player file for them.
        dev.ryvrook.dominion.model.PlayerData pd = ctx.repo().playerIfLoaded(uuid);
        String n = pd != null ? pd.name() : nameFromRoster(uuid);
        return n == null || n.isBlank() ? Messages.get(ExportText.SOMEONE) : n;
    }

    private String nameFromRoster(UUID uuid) {
        for (Faction f : ctx.repo().factions()) {
            dev.ryvrook.dominion.model.Member m = f.member(uuid);
            if (m != null) {
                return m.name();
            }
        }
        return null;
    }

    private String money(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? Messages.get(ExportText.COINS, "amount", String.format(Locale.ROOT, "%.2f", amount)) : eco.format(amount);
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
            return Messages.get(ExportText.ITEMS);
        }
        String s = itemId.replace("_Item", "").replace('_', ' ').trim();
        return s.isEmpty() ? itemId : s;
    }
}
