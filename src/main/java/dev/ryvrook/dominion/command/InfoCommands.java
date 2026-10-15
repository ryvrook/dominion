package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Read only views of factions, players and the audit log. */
final class InfoCommands extends Section {
    private static final int LIST_PAGE = 8;
    private static final int LOG_PAGE = 10;
    private static final int SIEGE_HISTORY = 50;

    InfoCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd help = root.sub(new PlayerCmd("help", "Show what you can do."));
        help.handler(c -> HelpTopics.show(c, null));
        PlayerCmd helpTopic = new PlayerCmd("Show help for one topic.");
        RequiredArg<String> topic = helpTopic.withRequiredArg("topic", "Help topic", ArgTypes.STRING);
        helpTopic.handler(c -> HelpTopics.show(c, c.arg(topic)));
        help.variant(helpTopic);

        PlayerCmd who = root.sub(new PlayerCmd("who", "Show details about your faction."));
        who.addAliases("info", "show", "f");
        who.handler(c -> {
            Faction f = requireFaction(c);
            if (f != null) {
                showFaction(c, f);
            }
        });
        PlayerCmd whoTag = new PlayerCmd("Show details about another faction.");
        RequiredArg<String> whoArg = whoTag.withRequiredArg("tag", "Faction tag or player name", ArgTypes.STRING);
        whoTag.handler(c -> {
            String text = c.arg(whoArg);
            Faction f = factions().byTag(text);
            if (f == null) {
                Targets.Target t = targets.resolve(c.uuid(), text);
                f = t == null ? null : factions().factionOf(t.uuid());
            }
            if (f == null) {
                c.bad("No faction or player matches " + text + ".");
                return;
            }
            showFaction(c, f);
        });
        who.variant(whoTag);

        PlayerCmd list = root.sub(new PlayerCmd("list", "List factions."));
        list.handler(c -> showList(c, 1));
        PlayerCmd listPage = new PlayerCmd("List a page of factions.");
        RequiredArg<Integer> listArg = listPage.withRequiredArg("page", "Page number", ArgTypes.INTEGER);
        listPage.handler(c -> showList(c, clampPage(c.arg(listArg))));
        list.variant(listPage);

        PlayerCmd player = root.sub(new PlayerCmd("player", "Show your player profile."));
        player.addAliases("p");
        player.handler(c -> showPlayer(c, c.uuid(), c.name()));
        PlayerCmd playerName = new PlayerCmd("Show another player's profile.");
        RequiredArg<String> playerArg = playerName.withRequiredArg("name", "Player name", ArgTypes.STRING);
        playerName.handler(c -> {
            Targets.Target t = target(c, c.arg(playerArg));
            if (t != null) {
                showPlayer(c, t.uuid(), t.name());
            }
        });
        player.variant(playerName);

        root.sub(new PlayerCmd("online", "List members who are online.")).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            List<Member> on = new ArrayList<>();
            for (Member m : Faction.sortedByRank(f.members())) {
                if (Players.isOnline(m.uuid())) {
                    on.add(m);
                }
            }
            c.info(on.size() + " of " + f.memberCount() + " members of " + f.tag() + " are online");
            for (Member m : on) {
                c.send(Message.join(Msg.muted(" " + rankOf(f, m).prefix() + " "), Msg.colored(m.name(), Relation.OWN.color())));
            }
        });

        PlayerCmd log = root.sub(new PlayerCmd("log", "Show your faction audit log."));
        log.handler(c -> showLog(c, 1));
        PlayerCmd logPage = new PlayerCmd("Show a page of the audit log.");
        RequiredArg<Integer> logArg = logPage.withRequiredArg("page", "Page number", ArgTypes.INTEGER);
        logPage.handler(c -> showLog(c, clampPage(c.arg(logArg))));
        log.variant(logPage);

        PlayerCmd stats = root.sub(new PlayerCmd("stats", "Show your faction statistics."));
        stats.handler(c -> {
            Faction f = requireFaction(c);
            if (f != null) {
                showStats(c, f);
            }
        });
        PlayerCmd statsTag = new PlayerCmd("Show another faction's statistics.");
        RequiredArg<String> statsArg = statsTag.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        statsTag.handler(c -> {
            Faction f = factionByTag(c, c.arg(statsArg));
            if (f != null) {
                showStats(c, f);
            }
        });
        stats.variant(statsTag);
    }

    private static Rank rankOf(Faction f, Member m) {
        Rank r = f.rankOf(m.uuid());
        return r == null ? m.rank() : r;
    }

    void showFaction(Call c, Faction f) {
        c.send(f.tagDiffers()
                ? Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true), Msg.colored("  " + f.name(), Msg.TEXT))
                : Message.join(Msg.prefix(), Msg.colored(f.label(), f.color()).bold(true)));
        if (f.type() != FactionType.NORMAL) {
            c.send(Msg.muted(f.type() == FactionType.SAFEZONE ? " A safe zone run by the server." : " A war zone run by the server."));
            return;
        }
        if (!f.description().isBlank()) {
            c.send(Msg.colored(" " + f.description(), Msg.MUTED));
        }
        Relation rel = relations().playerToFaction(c.uuid(), f);
        c.send(Message.join(Msg.muted(" Your relation is "), Msg.colored(rel.displayName(), rel.color())));
        NationService nations = ctx.serviceOrNull(NationService.class);
        Nation nation = nations == null ? null : nations.nationOf(f);
        if (nation != null) {
            c.send(Message.join(Msg.muted(" Member of the nation "), Msg.colored(nation.label(), Msg.GOLD)));
        }
        List<Member> members = Faction.sortedByRank(f.members());
        int online = 0;
        for (Member m : members) {
            if (Players.isOnline(m.uuid())) {
                online++;
            }
        }
        c.send(Msg.colored(" Led by " + targets.nameOf(f.leader()) + " with " + members.size() + " members, "
                + online + " online.", Msg.TEXT));
        c.send(Msg.colored(" Power " + Msg.number(power().factionPower(f)) + " of " + Msg.number(power().factionMaxPower(f))
                + " holding " + claims().claimCount(f) + " of " + claims().maxClaims(f) + " claims.", Msg.TEXT));
        if (claims().raidable(f)) {
            c.send(Msg.colored(" This faction can be raided right now.", Msg.BAD));
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco != null && eco.enabled()) {
            c.send(Msg.colored(" The bank holds " + eco.format(f.bank()) + ".", Msg.TEXT));
        }
        Message roster = Message.join(Msg.muted(" Members "));
        boolean first = true;
        for (Member m : members) {
            boolean on = Players.isOnline(m.uuid());
            String text = (first ? "" : ", ") + rankOf(f, m).prefix() + m.name();
            roster = Message.join(roster, Msg.colored(text, on ? Relation.OWN.color() : Msg.MUTED));
            first = false;
        }
        c.send(roster);
    }

    private void showList(Call c, int page) {
        List<Faction> all = new ArrayList<>();
        for (Faction f : factions().all()) {
            if (f.type() == FactionType.NORMAL) {
                all.add(f);
            }
        }
        if (all.isEmpty()) {
            c.info("There are no factions yet. Start one with /f create.");
            return;
        }
        Map<UUID, Integer> online = new HashMap<>();
        for (Faction f : all) {
            int n = 0;
            for (Member m : f.members()) {
                if (Players.isOnline(m.uuid())) {
                    n++;
                }
            }
            online.put(f.id(), n);
        }
        all.sort(Comparator.comparingInt((Faction f) -> online.get(f.id())).reversed()
                .thenComparing(Comparator.comparingDouble((Faction f) -> power().factionPower(f)).reversed()));
        int pages = (all.size() + LIST_PAGE - 1) / LIST_PAGE;
        int p = Math.min(page, pages);
        c.info("Factions, page " + p + " of " + pages);
        for (int i = (p - 1) * LIST_PAGE; i < Math.min(all.size(), p * LIST_PAGE); i++) {
            Faction f = all.get(i);
            Relation rel = relations().playerToFaction(c.uuid(), f);
            c.send(Message.join(Msg.muted(" " + (i + 1) + ". "), Msg.colored(f.tag(), rel.color()).bold(true),
                    Msg.muted("  " + online.get(f.id()) + " of " + f.memberCount() + " online, power "
                            + Msg.number(power().factionPower(f)) + ", " + claims().claimCount(f) + " claims")));
        }
    }

    private void showPlayer(Call c, UUID uuid, String name) {
        PlayerData pd = ctx.player(uuid);
        Faction f = factions().factionOf(uuid);
        Relation rel = relations().betweenPlayers(c.uuid(), uuid);
        c.send(Message.join(Msg.prefix(), Msg.colored(name, rel.color()).bold(true)));
        if (f == null) {
            c.send(Msg.muted(" Not in a faction."));
        } else {
            Rank r = f.rankOf(uuid);
            c.send(Msg.colored(" " + (r == null ? f.rankName(Rank.MEMBER) : f.rankName(r)) + " of " + f.tag(), Msg.TEXT));
        }
        c.send(Msg.colored(" Power " + Msg.number(power().powerOf(uuid)) + " of " + Msg.number(power().maxPowerOf(uuid))
                + ", " + pd.kills() + " kills and " + pd.deaths() + " deaths.", Msg.TEXT));
        c.send(Msg.muted(Players.isOnline(uuid) ? " Online now." : " Last seen " + Time.ago(pd.lastSeen()) + "."));
    }

    private void showLog(Call c, int page) {
        Faction f = requireFaction(c);
        if (f == null) {
            return;
        }
        List<AuditEntry> entries = f.audit();
        if (entries.isEmpty()) {
            c.info("Nothing has been logged yet.");
            return;
        }
        int pages = (entries.size() + LOG_PAGE - 1) / LOG_PAGE;
        int p = Math.min(page, pages);
        c.info("Audit log of " + f.tag() + ", page " + p + " of " + pages);
        for (int i = (p - 1) * LOG_PAGE; i < Math.min(entries.size(), p * LOG_PAGE); i++) {
            AuditEntry e = entries.get(i);
            String who = e.whoName() == null || e.whoName().isBlank() ? "Server" : e.whoName();
            c.send(Message.join(Msg.muted(" " + Time.ago(e.at()) + "  "), Msg.colored(who + " ", Msg.INFO),
                    Msg.colored(e.text() == null ? "" : e.text(), Msg.TEXT)));
        }
    }

    private void showStats(Call c, Faction f) {
        int kills = 0;
        int deaths = 0;
        for (Member m : f.members()) {
            kills += m.kills();
            deaths += m.deaths();
        }
        c.send(Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true), Msg.colored("  statistics", Msg.TEXT)));
        c.send(Msg.colored(" " + kills + " kills and " + deaths + " deaths by current members.", Msg.TEXT));
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress == null) {
            c.send(Msg.colored(" " + f.points() + " points, founded " + Time.date(f.createdAt()) + ".", Msg.TEXT));
        } else {
            c.send(Msg.colored(" Level " + progress.level(f) + " with " + progress.points(f) + " points, worth "
                    + Msg.number(progress.value(f)) + " and " + ProgressCommands.standing(progress.rank(f)) + ".", Msg.TEXT));
            c.send(Msg.colored(" Founded " + Time.date(f.createdAt()) + ".", Msg.TEXT));
        }
        SiegeService sieges = ctx.serviceOrNull(SiegeService.class);
        if (sieges != null) {
            int won = 0;
            int lost = 0;
            for (Siege s : sieges.history(f, SIEGE_HISTORY)) {
                boolean attackerWon = s.phase() == Siege.Phase.BREACHED;
                if (f.id().equals(s.attacker()) == attackerWon) {
                    won++;
                } else {
                    lost++;
                }
            }
            int active = sieges.activeFor(f).size();
            c.send(Msg.colored(" " + sieges.raidPoints(f) + " raid points, " + won + " sieges won and " + lost + " lost recently"
                    + (active > 0 ? ", " + active + " running now." : "."), Msg.TEXT));
        }
        Map<String, Long> stats = new TreeMap<>(f.stats());
        for (Map.Entry<String, Long> e : stats.entrySet()) {
            c.send(Msg.muted(" " + e.getKey() + " " + Msg.number(e.getValue())));
        }
    }
}
