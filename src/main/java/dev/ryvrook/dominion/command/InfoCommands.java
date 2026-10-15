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
                if (t == null) {
                    t = targets.resolveAnywhere(text);
                }
                f = t == null ? null : factions().factionOf(t.uuid());
                if (f == null && t != null) {
                    c.info(t.name() + " is not in a faction.");
                    return;
                }
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
        Relation rel = relations().playerToFaction(c.uuid(), f);
        String relColor = rel == null ? Relation.NEUTRAL.color() : rel.color();
        c.send(Message.join(Msg.colored("____[ ", Msg.GOLD), Msg.colored(f.label(), relColor).bold(true),
                Msg.colored(" ]____", Msg.GOLD)));
        if (f.type() != FactionType.NORMAL) {
            c.send(Msg.muted(f.type() == FactionType.SAFEZONE ? " A safe zone run by the server." : " A war zone run by the server."));
            return;
        }
        if (!f.description().isBlank()) {
            c.send(line("Description", Msg.colored(f.description(), Msg.TEXT)));
        }
        c.send(line("Age", Msg.colored(age(Time.now() - f.createdAt()), Msg.TEXT)));
        boolean open = f.setting("open", false);
        c.send(line("Open", Msg.colored(open ? "Yes, anyone can join" : "No, only invited players can join", Msg.TEXT)));
        c.send(line("Land / Power / Maxpower", Msg.colored(claims().claimCount(f) + " / " + Msg.number(power().factionPower(f))
                + " / " + Msg.number(power().factionMaxPower(f)), Msg.TEXT)));
        if (claims().raidable(f)) {
            c.send(Msg.colored("Raidable now", Msg.BAD).bold(true));
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco != null && eco.enabled()) {
            c.send(line("Bank", Msg.colored(eco.format(f.bank()), Msg.TEXT)));
        }
        NationService nations = ctx.serviceOrNull(NationService.class);
        Nation nation = nations == null ? null : nations.nationOf(f);
        if (nation != null) {
            c.send(Message.join(Msg.muted("Member of the nation "), Msg.colored(nation.label(), Msg.GOLD)));
        }
        relationLine(c, f, Relation.TRUCE, "In truce with");
        relationLine(c, f, Relation.ALLY, "Allies");
        relationLine(c, f, Relation.ENEMY, "Enemies");

        List<Member> on = new ArrayList<>();
        List<Member> off = new ArrayList<>();
        for (Member m : Faction.sortedByRank(f.members())) {
            (Players.isOnline(m.uuid()) ? on : off).add(m);
        }
        c.send(roster(c, f, "Members online (" + on.size() + ")", on, relColor));
        c.send(roster(c, f, "Members offline (" + off.size() + ")", off, Msg.MUTED));
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
            out = Message.join(out, Msg.muted("none"));
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
            return hours > 0 ? unit(days, "day") + ", " + unit(hours, "hour") : unit(days, "day");
        }
        if (hours > 0) {
            return mins > 0 ? unit(hours, "hour") + ", " + unit(mins, "minute") : unit(hours, "hour");
        }
        return mins > 0 ? unit(mins, "minute") : "less than a minute";
    }

    private static String unit(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
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
