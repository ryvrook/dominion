package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;

import java.util.List;
import java.util.UUID;

/** Sieges, the raid log and shield windows. */
final class WarCommands extends Section {
    private static final int HISTORY = 10;

    WarCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd siege = root.sub(new PlayerCmd("siege", "Plant a siege banner on the enemy chunk next to you."));
        siege.handler(c -> {
            SiegeService s = feature(c, SiegeService.class);
            if (s != null) {
                c.reply(s.start(c.uuid()));
            }
        });
        siege.sub(new PlayerCmd("cancel", "Call off the siege your faction is running.")).handler(c -> {
            SiegeService s = feature(c, SiegeService.class);
            if (s != null) {
                c.reply(s.cancel(c.uuid()));
            }
        });

        root.sub(new PlayerCmd("sieges", "List sieges in progress.")).handler(this::showActive);

        PlayerCmd raids = root.sub(new PlayerCmd("raids", "Show your faction's raid history and raid points."));
        raids.handler(c -> {
            Faction f = feature(c, SiegeService.class) == null ? null : requireFaction(c);
            if (f != null) {
                showRaids(c, f);
            }
        });
        PlayerCmd raidsTag = new PlayerCmd("Show another faction's raid history.");
        RequiredArg<String> raidsArg = raidsTag.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        raidsTag.handler(c -> {
            if (feature(c, SiegeService.class) == null) {
                return;
            }
            Faction f = factionByTag(c, c.arg(raidsArg));
            if (f != null) {
                showRaids(c, f);
            }
        });
        raids.variant(raidsTag);

        PlayerCmd shield = root.sub(new PlayerCmd("shield", "Show your faction's shield window."));
        shield.handler(c -> {
            ShieldService s = feature(c, ShieldService.class);
            Faction f = s == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            c.info(s.status(f));
            int start = f.shieldStartHour();
            c.send(Msg.muted(start < 0
                    ? " No window is set. Pick a start hour with /f shield set and an hour from 0 to 23."
                    : " The window starts at hour " + start + " and lasts " + ctx.settings().integer("shield.hours")
                    + " hours. Change it with /f shield set and an hour, or remove it with /f shield clear."));
        });
        PlayerCmd shieldSet = shield.sub(new PlayerCmd("set", "Move the daily shield window."));
        RequiredArg<Integer> hour = shieldSet.withRequiredArg("hour", "Start hour from 0 to 23", ArgTypes.INTEGER);
        shieldSet.handler(c -> {
            ShieldService s = feature(c, ShieldService.class);
            if (s == null) {
                return;
            }
            int h = c.arg(hour);
            if (h < 0 || h > 23) {
                c.bad("The hour must be between 0 and 23.");
                return;
            }
            c.reply(s.setWindow(c.uuid(), h));
        });
        shield.sub(new PlayerCmd("clear", "Remove the daily shield window.")).handler(c -> {
            ShieldService s = feature(c, ShieldService.class);
            if (s != null) {
                c.reply(s.setWindow(c.uuid(), -1));
            }
        });
    }

    private void showActive(Call c) {
        SiegeService s = feature(c, SiegeService.class);
        if (s == null) {
            return;
        }
        List<Siege> active = s.active();
        if (active.isEmpty()) {
            c.info("No sieges are running right now.");
            return;
        }
        Faction mine = factions().factionOf(c.uuid());
        c.info(active.size() == 1 ? "One siege is running" : active.size() + " sieges are running");
        for (Siege siege : active) {
            boolean involved = mine != null && (mine.id().equals(siege.attacker()) || mine.id().equals(siege.defender()));
            c.send(Message.join(Msg.colored(" " + tagOf(siege.attacker()), involved ? Msg.GOLD : Msg.TEXT).bold(involved),
                    Msg.muted(" against "), Msg.colored(tagOf(siege.defender()), involved ? Msg.GOLD : Msg.TEXT).bold(involved),
                    Msg.muted("  " + phaseText(siege.phase()) + ", " + Time.remaining(siege.phaseEndsAt() - Time.now())
                            + " left at chunk " + Keys.describe(siege.chunkIndex()) + " in " + siege.world())));
        }
    }

    private void showRaids(Call c, Faction f) {
        SiegeService s = ctx.service(SiegeService.class);
        c.send(Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true),
                Msg.colored("  holds " + s.raidPoints(f) + " raid points", Msg.TEXT)));
        for (Siege siege : s.activeFor(f)) {
            c.send(Msg.colored(" " + describe(f, siege) + ", " + phaseText(siege.phase()) + " now.", Msg.WARN));
        }
        List<Siege> history = s.history(f, HISTORY);
        if (history.isEmpty()) {
            c.send(Msg.muted(" No finished sieges yet."));
            return;
        }
        for (Siege siege : history) {
            boolean won = wonBy(f, siege);
            c.send(Message.join(Msg.muted(" " + Time.ago(siege.startedAt()) + "  "),
                    Msg.colored(describe(f, siege), Msg.TEXT),
                    Msg.colored("  " + (won ? "won" : "lost") + ", " + endText(siege.phase()), won ? Msg.GOOD : Msg.BAD),
                    Msg.muted(", " + siege.blocksBroken() + " blocks broken, " + siege.attackerDeaths() + " attacker and "
                            + siege.defenderDeaths() + " defender deaths")));
        }
    }

    private String describe(Faction f, Siege siege) {
        return f.id().equals(siege.attacker())
                ? "Attacked " + tagOf(siege.defender())
                : "Defended against " + tagOf(siege.attacker());
    }

    /**
     * A breach that ran its course ends as EXPIRED and is a win for the attacker. FAILED and
     * DEFENDED are wins for the defender.
     */
    private static boolean wonBy(Faction f, Siege siege) {
        boolean attackerWon = siege.phase() == Siege.Phase.BREACHED || siege.phase() == Siege.Phase.EXPIRED;
        return f.id().equals(siege.attacker()) == attackerWon;
    }

    /** How a finished siege ended, in words. */
    static String endText(Siege.Phase phase) {
        return switch (phase) {
            case EXPIRED -> "the breach ran its course";
            case DEFENDED -> "the defenders held the banner";
            case FAILED -> "the siege failed";
            case BREACHED -> "breached";
            case CAPTURING -> "still capturing";
        };
    }

    static String phaseText(Siege.Phase phase) {
        return switch (phase) {
            case CAPTURING -> "capturing";
            case BREACHED -> "breached";
            case FAILED -> "failed";
            case DEFENDED -> "defended";
            case EXPIRED -> "expired";
        };
    }

    private String tagOf(UUID factionId) {
        Faction f = factionId == null ? null : factions().byId(factionId);
        return f == null ? "a disbanded faction" : f.tag();
    }
}
