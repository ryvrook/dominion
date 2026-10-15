package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Time;

import java.util.List;
import java.util.UUID;

/**
 * The sieges tab of the panel. Shows active sieges, the raid history, the daily shield window
 * and the grace period. Each part appears only when its service is installed.
 */
final class SiegeTab {
    static final String ID = "sieges";
    private static final int HISTORY = 10;

    private final DominionContext ctx;
    private final DominionPanel panel;

    SiegeTab(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    private SiegeService sieges() {
        return ctx.serviceOrNull(SiegeService.class);
    }

    private ShieldService shields() {
        return ctx.serviceOrNull(ShieldService.class);
    }

    boolean available() {
        return sieges() != null || shields() != null;
    }

    String label() {
        return sieges() != null ? Messages.get(UiText.SIEGES) : Messages.get(UiText.SHIELD);
    }

    String count(Faction f) {
        SiegeService s = sieges();
        if (s == null) {
            return "";
        }
        int n = s.activeFor(f).size();
        return n > 0 ? Messages.get(UiText.ACTIVE_COUNT, "count", n) : "";
    }

    void build(UICommandBuilder c, Body body, Faction f) {
        SiegeService sieges = sieges();
        ShieldService shields = shields();
        long now = Time.now();
        if (sieges != null) {
            List<Siege> active = sieges.activeFor(f);
            panel.title(c, Messages.get(UiText.SIEGES), active.isEmpty() ? Messages.get(UiText.NO_SIEGES_NOW)
                    : Messages.get(UiText.IN_PROGRESS_COUNT, "count", active.size()));
            body.section(Messages.get(UiText.ACTIVE_SIEGES));
            if (active.isEmpty()) {
                body.text(Messages.get(UiText.NOT_IN_SIEGE), Msg.MUTED);
            }
            for (Siege s : active) {
                boolean attacking = s.attacker().equals(f.id());
                body.row(Messages.get(UiText.SIEGE_PAIR, "attacker", tag(s.attacker()), "defender", tag(s.defender())),
                        attacking ? Msg.GOLD : Msg.BAD,
                        new String[] {phaseName(s.phase()), Messages.get(UiText.ENDS_IN, "time", Time.remaining(s.phaseEndsAt() - now)),
                                attacking ? Messages.get(UiText.YOU_ATTACK) : Messages.get(UiText.YOU_DEFEND), ""});
                body.text(contest(s, now), Msg.MUTED);
            }
            body.buttons(Btn.of(Messages.get(UiText.START_SIEGE), "siege_start"), Btn.of(Messages.get(UiText.CANCEL_SIEGE), "siege_cancel"));
            body.text(Messages.get(UiText.SIEGE_HOWTO), Msg.MUTED);

            body.section(Messages.get(UiText.RAID_HISTORY));
            List<Siege> history = sieges.history(f, HISTORY);
            if (history.isEmpty()) {
                body.text(Messages.get(UiText.NO_SIEGES_YET), Msg.MUTED);
            }
            for (Siege s : history) {
                boolean attacked = s.attacker().equals(f.id());
                body.row(Messages.get(UiText.SIEGE_PAIR, "attacker", tag(s.attacker()), "defender", tag(s.defender())), null,
                        new String[] {outcome(s.phase(), attacked),
                                attacked ? Messages.get(UiText.YOU_ATTACKED) : Messages.get(UiText.YOU_DEFENDED),
                                Time.ago(s.startedAt()), Messages.get(UiText.BLOCKS_COUNT, "count", s.blocksBroken())});
            }
        } else {
            panel.title(c, Messages.get(UiText.SHIELD), Messages.get(UiText.DAILY_PROTECTION));
        }

        if (shields != null) {
            body.section(Messages.get(UiText.SHIELD));
            String status = shields.status(f);
            body.text(status == null || status.isBlank() ? Messages.get(UiText.NO_SHIELD_INFO) : status);
            if (shields.graceActive()) {
                body.stat(Messages.get(UiText.GRACE_PERIOD),
                        Messages.get(UiText.ENDS_IN, "time", Time.remaining(shields.graceEndsAt() - now)), Msg.INFO);
                body.text(Messages.get(UiText.GRACE_HINT), Msg.MUTED);
            }
            if (shields.eotwActive()) {
                body.text(Messages.get(UiText.EOTW_ACTIVE), Msg.BAD);
            }
            int start = f.shieldStartHour();
            body.text(start < 0
                    ? Messages.get(UiText.SHIELD_PICK_HOUR)
                    : Messages.get(UiText.SHIELD_STARTS_AT, "hour", start,
                    "delay", ctx.settings().integer("shield.changeDelayHours")), Msg.MUTED);
            body.hours(start, "shield_hour");
            if (start >= 0) {
                body.buttons(Btn.of(Messages.get(UiText.REMOVE_WINDOW), "shield_hour", "-1"));
            }
        }
    }

    /** Handles sieges tab buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        switch (e.action) {
            case "siege_start" -> {
                SiegeService s = sieges();
                panel.reply(s == null ? Result.fail(Messages.get(UiText.SIEGES_UNAVAILABLE)) : s.start(panel.uuid()));
            }
            case "siege_cancel" -> {
                SiegeService s = sieges();
                panel.reply(s == null ? Result.fail(Messages.get(UiText.SIEGES_UNAVAILABLE)) : s.cancel(panel.uuid()));
            }
            case "shield_hour" -> {
                ShieldService s = shields();
                if (s == null) {
                    panel.reply(Result.fail(Messages.get(UiText.SHIELDS_UNAVAILABLE)));
                } else {
                    try {
                        panel.reply(s.setWindow(panel.uuid(), Integer.parseInt(e.value())));
                    } catch (NumberFormatException ex) {
                        panel.reply(Result.fail(Messages.get(UiText.HOUR_UNREADABLE)));
                    }
                }
            }
            default -> {
                return null;
            }
        }
        return true;
    }

    private String contest(Siege s, long now) {
        if (s.phase() == Siege.Phase.BREACHED) {
            return Messages.get(UiText.SIEGE_BREACHED_STATE, "blocks", s.blocksBroken(), "containers", s.containersOpened(),
                    "attackerDeaths", s.attackerDeaths(), "defenderDeaths", s.defenderDeaths());
        }
        String deaths = " " + Messages.get(UiText.SIEGE_DEATHS, "attackerDeaths", s.attackerDeaths(), "defenderDeaths", s.defenderDeaths());
        if (s.attackerAbsentSince() > 0) {
            long failAt = s.attackerAbsentSince() + ctx.settings().integer("siege.absentSecondsToFail") * 1000L;
            return Messages.get(UiText.SIEGE_NO_ATTACKERS, "time", Time.remaining(failAt - now)) + deaths;
        }
        if (s.defenderPresentSince() > 0) {
            return Messages.get(UiText.SIEGE_DEFENDERS_HOLD) + deaths;
        }
        return Messages.get(UiText.SIEGE_ATTACKERS_HOLD) + deaths;
    }

    static String phaseName(Siege.Phase phase) {
        return switch (phase) {
            case CAPTURING -> Messages.get(UiText.PHASE_CAPTURING);
            case BREACHED -> Messages.get(UiText.PHASE_BREACHED);
            case FAILED -> Messages.get(UiText.PHASE_FAILED);
            case DEFENDED -> Messages.get(UiText.PHASE_DEFENDED);
            case EXPIRED -> Messages.get(UiText.PHASE_EXPIRED);
        };
    }

    private static String outcome(Siege.Phase phase, boolean attacked) {
        return switch (phase) {
            case BREACHED, EXPIRED -> attacked ? Messages.get(UiText.OUTCOME_WON) : Messages.get(UiText.OUTCOME_LOST);
            case FAILED, DEFENDED -> attacked ? Messages.get(UiText.OUTCOME_LOST) : Messages.get(UiText.OUTCOME_HELD);
            case CAPTURING -> Messages.get(UiText.OUTCOME_ONGOING);
        };
    }

    private String tag(UUID factionId) {
        Faction f = factionId == null ? null : ctx.service(FactionService.class).byId(factionId);
        return f == null ? Messages.get(UiText.FORMER_FACTION) : f.tag();
    }
}
