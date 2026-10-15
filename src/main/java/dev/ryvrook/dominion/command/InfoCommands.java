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
import dev.ryvrook.dominion.text.Messages;
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
        PlayerCmd help = root.sub(new PlayerCmd("help", Messages.get(CommandText.DESC_SHOW_WHAT_YOU_CAN_DO)));
        help.handler(c -> HelpTopics.show(c, null));
        PlayerCmd helpTopic = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_HELP_FOR_ONE_TOPIC));
        RequiredArg<String> topic = helpTopic.withRequiredArg("topic", Messages.get(CommandText.ARG_HELP_TOPIC), ArgTypes.STRING);
        helpTopic.handler(c -> HelpTopics.show(c, c.arg(topic)));
        help.variant(helpTopic);

        PlayerCmd who = root.sub(new PlayerCmd("who", Messages.get(CommandText.DESC_SHOW_DETAILS_ABOUT_YOUR_FACTION)));
        who.addAliases("info", "show", "f");
        who.handler(c -> {
            Faction f = requireFaction(c);
            if (f != null) {
                showFaction(c, f);
            }
        });
        PlayerCmd whoTag = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_DETAILS_ABOUT_ANOTHER_FACTION));
        RequiredArg<String> whoArg = whoTag.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG_OR_PLAYER_NAME), ArgTypes.STRING);
        whoTag.handler(c -> {
            String text = c.arg(whoArg);
            Faction f = factions().byTag(text);
            if (f == null) {
                Targets.Target t = targets.resolve(c.uuid(), text);
                if (t == null) {
                    t = targets.resolveAnywhere(text);
                }
                f = t == null ? null : factions().factionOf(t.uuid());
                if (f == null && t != null) {
                    c.info(Messages.get(CommandText.PLAYER_NOT_IN_A_FACTION, "name", t.name()));
                    return;
                }
            }
            if (f == null) {
                c.bad(Messages.get(CommandText.NO_FACTION_OR_PLAYER, "text", text));
                return;
            }
            showFaction(c, f);
        });
        who.variant(whoTag);

        PlayerCmd list = root.sub(new PlayerCmd("list", Messages.get(CommandText.DESC_LIST_FACTIONS)));
        list.handler(c -> showList(c, 1));
        PlayerCmd listPage = new PlayerCmd(Messages.get(CommandText.DESC_LIST_A_PAGE_OF_FACTIONS));
        RequiredArg<Integer> listArg = listPage.withRequiredArg("page", Messages.get(CommandText.ARG_PAGE_NUMBER), ArgTypes.INTEGER);
        listPage.handler(c -> showList(c, clampPage(c.arg(listArg))));
        list.variant(listPage);

        PlayerCmd player = root.sub(new PlayerCmd("player", Messages.get(CommandText.DESC_SHOW_YOUR_PLAYER_PROFILE)));
        player.addAliases("p");
        player.handler(c -> showPlayer(c, c.uuid(), c.name()));
        PlayerCmd playerName = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_ANOTHER_PLAYERS_PROFILE));
        RequiredArg<String> playerArg = playerName.withRequiredArg("name", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        playerName.handler(c -> {
            Targets.Target t = target(c, c.arg(playerArg));
            if (t != null) {
                showPlayer(c, t.uuid(), t.name());
            }
        });
        player.variant(playerName);

        root.sub(new PlayerCmd("online", Messages.get(CommandText.DESC_LIST_MEMBERS_WHO_ARE_ONLINE))).handler(c -> {
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
            c.info(Messages.get(CommandText.MEMBERS_ONLINE, "online", on.size(), "members", f.memberCount(), "tag", f.tag()));
            for (Member m : on) {
                c.send(Message.join(Msg.muted(" " + rankOf(f, m).prefix() + " "), Msg.colored(m.name(), Relation.OWN.color())));
            }
        });

        PlayerCmd log = root.sub(new PlayerCmd("log", Messages.get(CommandText.DESC_SHOW_YOUR_FACTION_AUDIT_LOG)));
        log.handler(c -> showLog(c, 1));
        PlayerCmd logPage = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_A_PAGE_OF_THE_AUDIT));
        RequiredArg<Integer> logArg = logPage.withRequiredArg("page", Messages.get(CommandText.ARG_PAGE_NUMBER), ArgTypes.INTEGER);
        logPage.handler(c -> showLog(c, clampPage(c.arg(logArg))));
        log.variant(logPage);

        PlayerCmd stats = root.sub(new PlayerCmd("stats", Messages.get(CommandText.DESC_SHOW_YOUR_FACTION_STATISTICS)));
        stats.handler(c -> {
            Faction f = requireFaction(c);
            if (f != null) {
                showStats(c, f);
            }
        });
        PlayerCmd statsTag = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_ANOTHER_FACTIONS_STATISTICS));
        RequiredArg<String> statsArg = statsTag.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
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
        Relation rel = relations().playerToFaction(c.uuid(), f);
        String relColor = rel == null ? Relation.NEUTRAL.color() : rel.color();
        c.send(Message.join(Msg.colored("____[ ", Msg.GOLD), Msg.colored(f.label(), relColor).bold(true),
                Msg.colored(" ]____", Msg.GOLD)));
        if (f.type() != FactionType.NORMAL) {
            c.send(Msg.muted(f.type() == FactionType.SAFEZONE ? " " + Messages.get(CommandText.A_SAFE_ZONE_RUN_BY_THE) : " " + Messages.get(CommandText.A_WAR_ZONE_RUN_BY_THE)));
            return;
        }
        if (!f.description().isBlank()) {
            c.send(line(Messages.get(CommandText.LABEL_DESCRIPTION), Msg.colored(f.description(), Msg.TEXT)));
        }
        c.send(line(Messages.get(CommandText.LABEL_AGE), Msg.colored(age(Time.now() - f.createdAt()), Msg.TEXT)));
        boolean open = f.setting("open", false);
        c.send(line(Messages.get(CommandText.LABEL_OPEN), Msg.colored(open ? Messages.get(CommandText.YES_ANYONE_CAN_JOIN) : Messages.get(CommandText.NO_ONLY_INVITED_PLAYERS_CAN_JOIN), Msg.TEXT)));
        c.send(line(Messages.get(CommandText.LABEL_LAND_POWER_MAXPOWER), Msg.colored(claims().claimCount(f) + " / " + Msg.number(power().factionPower(f))
                + " / " + Msg.number(power().factionMaxPower(f)), Msg.TEXT)));
        if (claims().raidable(f)) {
            c.send(Msg.colored(Messages.get(CommandText.RAIDABLE_NOW), Msg.BAD).bold(true));
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco != null && eco.enabled()) {
            c.send(line(Messages.get(CommandText.LABEL_BANK), Msg.colored(eco.format(f.bank()), Msg.TEXT)));
        }
        NationService nations = ctx.serviceOrNull(NationService.class);
        Nation nation = nations == null ? null : nations.nationOf(f);
        if (nation != null) {
            c.send(Message.join(Msg.muted(Messages.get(CommandText.MEMBER_OF_THE_NATION) + " "), Msg.colored(nation.label(), Msg.GOLD)));
        }
        relationLine(c, f, Relation.TRUCE, Messages.get(CommandText.LABEL_TRUCE));
        relationLine(c, f, Relation.ALLY, Messages.get(CommandText.LABEL_ALLIES));
        relationLine(c, f, Relation.ENEMY, Messages.get(CommandText.LABEL_ENEMIES));

        List<Member> on = new ArrayList<>();
        List<Member> off = new ArrayList<>();
        for (Member m : Faction.sortedByRank(f.members())) {
            (Players.isOnline(m.uuid()) ? on : off).add(m);
        }
        c.send(roster(c, f, Messages.get(CommandText.ROSTER_ONLINE, "count", on.size()), on, relColor));
        c.send(roster(c, f, Messages.get(CommandText.ROSTER_OFFLINE, "count", off.size()), off, Msg.MUTED));
    }

    /** A muted label followed by its value, the classic who layout without a colon. */
    private static Message line(String label, Message value) {
        return Message.join(Msg.muted(label + "  "), value);
    }

    /** One comma list of the factions in the given relation with f, in that relation's colour. */
    private void relationLine(Call c, Faction f, Relation wanted, String label) {
        List<String> tags = new ArrayList<>();
        // Every faction, since an enemy declaration and a shared nation are not stored on f itself.
        for (Faction other : factions().all()) {
            if (other != null && !other.isSystem() && !other.id().equals(f.id())
                    && relations().between(f, other) == wanted && !tags.contains(other.tag())) {
                tags.add(other.tag());
            }
        }
        if (tags.isEmpty()) {
            return;
        }
        tags.sort(String.CASE_INSENSITIVE_ORDER);
        c.send(line(label, Msg.colored(String.join(", ", tags), wanted.color())));
    }

    /** Classic roster line. Leader **, officers *, a custom rank name in muted text, the viewer in gold. */
    private Message roster(Call c, Faction f, String label, List<Member> members, String color) {
        Message out = Msg.muted(label + "  ");
        boolean first = true;
        for (Member m : members) {
            Rank r = rankOf(f, m);
            List<Message> parts = new ArrayList<>();
            parts.add(out);
            if (!first) {
                parts.add(Msg.muted(", "));
            }
            String mark = marker(r);
            if (!mark.isEmpty()) {
                parts.add(Msg.colored(mark, color));
            }
            String rankName = f.rankName(r);
            if (rankName != null && !rankName.isBlank() && !rankName.equalsIgnoreCase(r.displayName())) {
                parts.add(Msg.muted(rankName + " "));
            }
            boolean self = m.uuid().equals(c.uuid());
            parts.add(self ? Msg.colored(m.name(), Msg.GOLD).bold(true) : Msg.colored(m.name(), color));
            out = Message.join(parts.toArray(new Message[0]));
            first = false;
        }
        if (first) {
            out = Message.join(out, Msg.muted(Messages.get(CommandText.NONE)));
        }
        return out;
    }

    /** ** for the leader and * for officers, as classic factions marks them. Others have no mark. */
    static String marker(Rank r) {
        return r == Rank.LEADER || r == Rank.OFFICER ? r.prefix() : "";
    }

    /** Faction age such as 3 days, 4 hours or 2 hours, 17 minutes, the two largest units. */
    static String age(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        if (days > 0) {
            return hours > 0 ? days(days) + ", " + hours(hours) : days(days);
        }
        if (hours > 0) {
            return mins > 0 ? hours(hours) + ", " + minutes(mins) : hours(hours);
        }
        return mins > 0 ? minutes(mins) : Messages.get(CommandText.LESS_THAN_A_MINUTE);
    }

    private static String days(long n) {
        return Messages.get(n == 1 ? CommandText.AGE_DAY : CommandText.AGE_DAYS, "n", n);
    }

    private static String hours(long n) {
        return Messages.get(n == 1 ? CommandText.AGE_HOUR : CommandText.AGE_HOURS, "n", n);
    }

    private static String minutes(long n) {
        return Messages.get(n == 1 ? CommandText.AGE_MINUTE : CommandText.AGE_MINUTES, "n", n);
    }

    private void showList(Call c, int page) {
        List<Faction> all = new ArrayList<>();
        for (Faction f : factions().all()) {
            if (f.type() == FactionType.NORMAL) {
                all.add(f);
            }
        }
        if (all.isEmpty()) {
            c.info(Messages.get(CommandText.THERE_ARE_NO_FACTIONS_YET_START));
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
        c.info(Messages.get(CommandText.FACTIONS_PAGE, "page", p, "pages", pages));
        for (int i = (p - 1) * LIST_PAGE; i < Math.min(all.size(), p * LIST_PAGE); i++) {
            Faction f = all.get(i);
            Relation rel = relations().playerToFaction(c.uuid(), f);
            c.send(Message.join(Msg.muted(" " + (i + 1) + ". "), Msg.colored(f.tag(), rel.color()).bold(true),
                    Msg.muted("  " + Messages.get(CommandText.LIST_ROW, "online", online.get(f.id()), "members", f.memberCount(), "power", Msg.number(power().factionPower(f)), "claims", claims().claimCount(f)))));
        }
    }

    private void showPlayer(Call c, UUID uuid, String name) {
        PlayerData pd = ctx.player(uuid);
        Faction f = factions().factionOf(uuid);
        Relation rel = relations().betweenPlayers(c.uuid(), uuid);
        c.send(Message.join(Msg.prefix(), Msg.colored(name, rel.color()).bold(true)));
        if (f == null) {
            c.send(Msg.muted(" " + Messages.get(CommandText.PLAYER_NOT_IN_FACTION)));
        } else {
            Rank r = f.rankOf(uuid);
            c.send(Msg.colored(" " + Messages.get(CommandText.PLAYER_RANK_OF, "rank", r == null ? f.rankName(Rank.MEMBER) : f.rankName(r), "tag", f.tag()), Msg.TEXT));
        }
        c.send(Msg.colored(" " + Messages.get(CommandText.PLAYER_POWER, "power", Msg.number(power().powerOf(uuid)), "max", Msg.number(power().maxPowerOf(uuid)), "kills", pd.kills(), "deaths", pd.deaths()), Msg.TEXT));
        c.send(Msg.muted(Players.isOnline(uuid) ? " " + Messages.get(CommandText.ONLINE_NOW) : " " + Messages.get(CommandText.LAST_SEEN, "ago", Time.ago(pd.lastSeen()))));
    }

    private void showLog(Call c, int page) {
        Faction f = requireFaction(c);
        if (f == null) {
            return;
        }
        List<AuditEntry> entries = f.audit();
        if (entries.isEmpty()) {
            c.info(Messages.get(CommandText.NOTHING_HAS_BEEN_LOGGED_YET));
            return;
        }
        int pages = (entries.size() + LOG_PAGE - 1) / LOG_PAGE;
        int p = Math.min(page, pages);
        c.info(Messages.get(CommandText.AUDIT_PAGE, "tag", f.tag(), "page", p, "pages", pages));
        for (int i = (p - 1) * LOG_PAGE; i < Math.min(entries.size(), p * LOG_PAGE); i++) {
            AuditEntry e = entries.get(i);
            String who = e.whoName() == null || e.whoName().isBlank() ? Messages.get(CommandText.AUDIT_SERVER) : e.whoName();
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
        c.send(Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true), Msg.colored("  " + Messages.get(CommandText.STATS_TITLE), Msg.TEXT)));
        c.send(Msg.colored(" " + Messages.get(CommandText.STATS_KILLS, "kills", kills, "deaths", deaths), Msg.TEXT));
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress == null) {
            c.send(Msg.colored(" " + Messages.get(CommandText.STATS_POINTS, "points", f.points(), "date", Time.date(f.createdAt())), Msg.TEXT));
        } else {
            c.send(Msg.colored(" " + Messages.get(CommandText.STATS_LEVEL, "level", progress.level(f), "points", progress.points(f), "value", Msg.number(progress.value(f)), "standing", ProgressCommands.standing(progress.rank(f))), Msg.TEXT));
            c.send(Msg.colored(" " + Messages.get(CommandText.STATS_FOUNDED, "date", Time.date(f.createdAt())), Msg.TEXT));
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
            String raids = active > 0
                    ? Messages.get(CommandText.STATS_RAIDS_RUNNING, "points", sieges.raidPoints(f), "won", won, "lost", lost, "running", active)
                    : Messages.get(CommandText.STATS_RAIDS, "points", sieges.raidPoints(f), "won", won, "lost", lost);
            c.send(Msg.colored(" " + raids, Msg.TEXT));
        }
        Map<String, Long> stats = new TreeMap<>(f.stats());
        for (Map.Entry<String, Long> e : stats.entrySet()) {
            c.send(Msg.muted(" " + e.getKey() + " " + Msg.number(e.getValue())));
        }
    }
}
