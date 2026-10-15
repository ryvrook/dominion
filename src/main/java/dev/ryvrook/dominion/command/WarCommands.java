package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Messages;
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
        PlayerCmd siege = root.sub(new PlayerCmd("siege", Messages.get(CommandText.DESC_PLANT_A_SIEGE_BANNER_ON_THE)));
        siege.handler(c -> {
            SiegeService s = feature(c, SiegeService.class);
            if (s != null) {
                c.reply(s.start(c.uuid()));
            }
        });
        siege.sub(new PlayerCmd("cancel", Messages.get(CommandText.DESC_CALL_OFF_THE_SIEGE_YOUR_FACTION))).handler(c -> {
            SiegeService s = feature(c, SiegeService.class);
            if (s != null) {
                c.reply(s.cancel(c.uuid()));
            }
        });

        root.sub(new PlayerCmd("sieges", Messages.get(CommandText.DESC_LIST_SIEGES_IN_PROGRESS))).handler(this::showActive);

        PlayerCmd raids = root.sub(new PlayerCmd("raids", Messages.get(CommandText.DESC_SHOW_YOUR_FACTIONS_RAID_HISTORY_AND)));
        raids.handler(c -> {
            Faction f = feature(c, SiegeService.class) == null ? null : requireFaction(c);
            if (f != null) {
                showRaids(c, f);
            }
        });
        PlayerCmd raidsTag = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_ANOTHER_FACTIONS_RAID_HISTORY));
        RequiredArg<String> raidsArg = raidsTag.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
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

        PlayerCmd shield = root.sub(new PlayerCmd("shield", Messages.get(CommandText.DESC_SHOW_YOUR_FACTIONS_SHIELD_WINDOW)));
        shield.handler(c -> {
            ShieldService s = feature(c, ShieldService.class);
            Faction f = s == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            c.info(s.status(f));
            int start = f.shieldStartHour();
            c.send(Msg.muted(start < 0
                    ? " " + Messages.get(CommandText.NO_WINDOW_IS_SET_PICK_A)
                    : " " + Messages.get(CommandText.SHIELD_WINDOW, "hour", start, "hours", ctx.settings().integer("shield.hours"))));
        });
        PlayerCmd shieldSet = shield.sub(new PlayerCmd("set", Messages.get(CommandText.DESC_MOVE_THE_DAILY_SHIELD_WINDOW)));
        RequiredArg<Integer> hour = shieldSet.withRequiredArg("hour", Messages.get(CommandText.ARG_START_HOUR_FROM_0_TO_23), ArgTypes.INTEGER);
        shieldSet.handler(c -> {
            ShieldService s = feature(c, ShieldService.class);
            if (s == null) {
                return;
            }
            int h = c.arg(hour);
            if (h < 0 || h > 23) {
                c.bad(Messages.get(CommandText.THE_HOUR_MUST_BE_BETWEEN_0));
                return;
            }
            c.reply(s.setWindow(c.uuid(), h));
        });
        shield.sub(new PlayerCmd("clear", Messages.get(CommandText.DESC_REMOVE_THE_DAILY_SHIELD_WINDOW))).handler(c -> {
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
            c.info(Messages.get(CommandText.NO_SIEGES_ARE_RUNNING_RIGHT_NOW));
            return;
        }
        Faction mine = factions().factionOf(c.uuid());
        c.info(active.size() == 1 ? Messages.get(CommandText.ONE_SIEGE_IS_RUNNING) : Messages.get(CommandText.SIEGES_RUNNING, "count", active.size()));
        for (Siege siege : active) {
            boolean involved = mine != null && (mine.id().equals(siege.attacker()) || mine.id().equals(siege.defender()));
            c.send(Message.join(Msg.colored(" " + tagOf(siege.attacker()), involved ? Msg.GOLD : Msg.TEXT).bold(involved),
                    Msg.muted(" " + Messages.get(CommandText.SIEGE_AGAINST) + " "), Msg.colored(tagOf(siege.defender()), involved ? Msg.GOLD : Msg.TEXT).bold(involved),
                    Msg.muted("  " + Messages.get(CommandText.SIEGE_ROW, "phase", phaseText(siege.phase()), "time", Time.remaining(siege.phaseEndsAt() - Time.now()), "chunk", Keys.describe(siege.chunkIndex()), "world", siege.world()))));
        }
    }

    private void showRaids(Call c, Faction f) {
        SiegeService s = ctx.service(SiegeService.class);
        c.send(Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true),
                Msg.colored("  " + Messages.get(CommandText.RAID_POINTS_HELD, "points", s.raidPoints(f)), Msg.TEXT)));
        for (Siege siege : s.activeFor(f)) {
            c.send(Msg.colored(" " + Messages.get(CommandText.SIEGE_NOW, "siege", describe(f, siege), "phase", phaseText(siege.phase())), Msg.WARN));
        }
        List<Siege> history = s.history(f, HISTORY);
        if (history.isEmpty()) {
            c.send(Msg.muted(" " + Messages.get(CommandText.NO_FINISHED_SIEGES_YET)));
            return;
        }
        for (Siege siege : history) {
            boolean won = wonBy(f, siege);
            c.send(Message.join(Msg.muted(" " + Time.ago(siege.startedAt()) + "  "),
                    Msg.colored(describe(f, siege), Msg.TEXT),
                    Msg.colored("  " + (won ? Messages.get(CommandText.SIEGE_WON) : Messages.get(CommandText.SIEGE_LOST)) + ", " + endText(siege.phase()), won ? Msg.GOOD : Msg.BAD),
                    Msg.muted(", " + Messages.get(CommandText.SIEGE_STATS, "blocks", siege.blocksBroken(), "attackers", siege.attackerDeaths(), "defenders", siege.defenderDeaths()))));
        }
    }

    private String describe(Faction f, Siege siege) {
        return f.id().equals(siege.attacker())
                ? Messages.get(CommandText.SIEGE_ATTACKED, "tag", tagOf(siege.defender()))
                : Messages.get(CommandText.SIEGE_DEFENDED, "tag", tagOf(siege.attacker()));
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
            case EXPIRED -> Messages.get(CommandText.THE_BREACH_RAN_ITS_COURSE);
            case DEFENDED -> Messages.get(CommandText.THE_DEFENDERS_HELD_THE_BANNER);
            case FAILED -> Messages.get(CommandText.THE_SIEGE_FAILED);
            case BREACHED -> Messages.get(CommandText.PHASE_BREACHED);
            case CAPTURING -> Messages.get(CommandText.STILL_CAPTURING);
        };
    }

    static String phaseText(Siege.Phase phase) {
        return switch (phase) {
            case CAPTURING -> Messages.get(CommandText.PHASE_CAPTURING);
            case BREACHED -> Messages.get(CommandText.PHASE_BREACHED);
            case FAILED -> Messages.get(CommandText.PHASE_FAILED);
            case DEFENDED -> Messages.get(CommandText.PHASE_DEFENDED);
            case EXPIRED -> Messages.get(CommandText.PHASE_EXPIRED);
        };
    }

    private String tagOf(UUID factionId) {
        Faction f = factionId == null ? null : factions().byId(factionId);
        return f == null ? Messages.get(CommandText.A_DISBANDED_FACTION) : f.tag();
    }
}
