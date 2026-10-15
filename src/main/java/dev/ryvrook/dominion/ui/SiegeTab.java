package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
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

    private final DominionContext ctx;
    private final DominionPanel panel;

    SiegeTab(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    /** The siege service while raid.enabled and raid.modeSiege are on, otherwise null. */
    private SiegeService sieges() {
        boolean on = ctx.settings().bool("raid.enabled") && ctx.settings().bool("raid.modeSiege");
        return on ? ctx.serviceOrNull(SiegeService.class) : null;
    }

    /** The shield service while shield.enabled is on, otherwise null. */
    private ShieldService shields() {
        return ctx.settings().bool("shield.enabled") ? ctx.serviceOrNull(ShieldService.class) : null;
    }

    boolean available() {
        return sieges() != null || shields() != null;
    }

    String label() {
        return sieges() != null ? "Sieges" : "Shield";
    }

    String count(Faction f) {
        SiegeService s = sieges();
        if (s == null) {
            return "";
        }
        int n = s.activeFor(f).size();
        return n > 0 ? n + " active" : "";
    }

    void build(UICommandBuilder c, Body body, Faction f) {
        SiegeService sieges = sieges();
        ShieldService shields = shields();
        long now = Time.now();
        if (sieges != null) {
            List<Siege> active = sieges.activeFor(f);
            panel.title(c, "Sieges", active.isEmpty() ? "No sieges right now" : active.size() + " in progress");
            body.section("Active sieges");
            if (active.isEmpty()) {
                body.text("Your faction is not part of any siege right now.", Msg.MUTED);
            }
            for (Siege s : active) {
                boolean attacking = s.attacker().equals(f.id());
                body.row(tag(s.attacker()) + " against " + tag(s.defender()), attacking ? Msg.GOLD : Msg.BAD,
                        new String[] {phaseName(s.phase()), "Ends in " + Time.remaining(s.phaseEndsAt() - now),
                                attacking ? "You attack" : "You defend", ""});
                body.text(contest(s, now), Msg.MUTED);
            }
            body.buttons(Btn.of("Start siege", "siege_start"), Btn.of("Cancel siege", "siege_cancel"));
            body.text("Stand next to enemy land and press Start siege to plant your banner on the nearest enemy chunk. "
                    + "Your attackers must stay near the banner until the capture timer runs out. Cancel siege calls off "
                    + "the siege your faction started.", Msg.MUTED);

            body.section("Raid history");
            List<Siege> history = sieges.history(f, ctx.settings().integer("ui.siegeHistory"));
            if (history.isEmpty()) {
                body.text("No sieges have been fought yet.", Msg.MUTED);
            }
            for (Siege s : history) {
                boolean attacked = s.attacker().equals(f.id());
                body.row(tag(s.attacker()) + " against " + tag(s.defender()), null,
                        new String[] {outcome(s.phase(), attacked), attacked ? "You attacked" : "You defended",
                                Time.ago(s.startedAt()), s.blocksBroken() + " blocks"});
            }
        } else {
            panel.title(c, "Shield", "Daily protection window");
        }

        if (shields != null) {
            body.section("Shield");
            String status = shields.status(f);
            body.text(status == null || status.isBlank() ? "No shield information is available." : status);
            if (shields.graceActive()) {
                body.stat("Grace period", "Ends in " + Time.remaining(shields.graceEndsAt() - now), Msg.INFO);
                body.text("Nobody can be sieged or raided until the grace period ends.", Msg.MUTED);
            }
            if (shields.eotwActive()) {
                body.text("The end of the world is here. Shields are down and every faction can be raided.", Msg.BAD);
            }
            int start = f.shieldStartHour();
            body.text(start < 0
                    ? "Pick the hour your daily shield starts. The hours use the server clock."
                    : "Your shield starts at hour " + start + " of the server clock. Pick another hour to move it, or remove the window below. "
                    + "A change takes " + ctx.settings().integer("shield.changeDelayHours") + " hours to apply.", Msg.MUTED);
            body.hours(start, "shield_hour");
            if (start >= 0) {
                body.buttons(Btn.of("Remove window", "shield_hour", "-1"));
            }
        }
    }

    /** Handles sieges tab buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        switch (e.action) {
            case "siege_start" -> {
                SiegeService s = sieges();
                panel.reply(s == null ? Result.fail("Sieges are not available on this server.") : s.start(panel.uuid()));
            }
            case "siege_cancel" -> {
                SiegeService s = sieges();
                panel.reply(s == null ? Result.fail("Sieges are not available on this server.") : s.cancel(panel.uuid()));
            }
            case "shield_hour" -> {
                ShieldService s = shields();
                if (s == null) {
                    panel.reply(Result.fail("Shields are not available on this server."));
                } else {
                    try {
                        panel.reply(s.setWindow(panel.uuid(), Integer.parseInt(e.value())));
                    } catch (NumberFormatException ex) {
                        panel.reply(Result.fail("That hour could not be read."));
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
            return "The land is open to the attackers. So far " + s.blocksBroken() + " blocks were broken and "
                    + s.containersOpened() + " containers were opened. Attackers lost " + s.attackerDeaths()
                    + " and defenders lost " + s.defenderDeaths() + ".";
        }
        String deaths = " Attackers lost " + s.attackerDeaths() + " and defenders lost " + s.defenderDeaths() + ".";
        if (s.attackerAbsentSince() > 0) {
            long failAt = s.attackerAbsentSince() + ctx.settings().integer("siege.absentSecondsToFail") * 1000L;
            return "No attackers are near the banner. The siege fails in " + Time.remaining(failAt - now)
                    + " unless they return." + deaths;
        }
        if (s.defenderPresentSince() > 0) {
            return "Defenders are standing at the banner and holding up the capture." + deaths;
        }
        return "The attackers hold the banner. The land is breached when the timer runs out." + deaths;
    }

    static String phaseName(Siege.Phase phase) {
        return switch (phase) {
            case CAPTURING -> "Capturing";
            case BREACHED -> "Breached";
            case FAILED -> "Failed";
            case DEFENDED -> "Defended";
            case EXPIRED -> "Expired";
        };
    }

    private static String outcome(Siege.Phase phase, boolean attacked) {
        return switch (phase) {
            case BREACHED, EXPIRED -> attacked ? "Won" : "Lost";
            case FAILED, DEFENDED -> attacked ? "Lost" : "Held";
            case CAPTURING -> "Ongoing";
        };
    }

    private String tag(UUID factionId) {
        Faction f = factionId == null ? null : ctx.service(FactionService.class).byId(factionId);
        return f == null ? "A former faction" : f.tag();
    }
}
