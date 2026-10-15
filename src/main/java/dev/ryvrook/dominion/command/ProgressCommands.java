package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Mission;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.VaultService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Vault pages, upgrades, missions, faction value and the leaderboard. */
final class ProgressCommands extends Section {
    ProgressCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd top = root.sub(new PlayerCmd("top", "Show the faction leaderboard."));
        top.handler(c -> top(c, 1));
        PlayerCmd topPage = new PlayerCmd("Show a page of the faction leaderboard.");
        RequiredArg<Integer> topArg = topPage.withRequiredArg("page", "Page number", ArgTypes.INTEGER);
        topPage.handler(c -> top(c, clampPage(c.arg(topArg))));
        top.variant(topPage);

        PlayerCmd vault = root.sub(new PlayerCmd("vault", "Open the faction vault."));
        vault.handler(c -> openVault(c, 1));
        PlayerCmd vaultPage = new PlayerCmd("Open one page of the faction vault.");
        RequiredArg<Integer> page = vaultPage.withRequiredArg("page", "Page number", ArgTypes.INTEGER);
        vaultPage.handler(c -> openVault(c, c.arg(page)));
        vault.variant(vaultPage);

        root.sub(new PlayerCmd("upgrades", "List faction upgrades.")).handler(this::showUpgrades);

        PlayerCmd upgrade = root.sub(new PlayerCmd("upgrade", "Buy the next level of an upgrade."));
        RequiredArg<String> key = upgrade.withRequiredArg("key", "Upgrade name", ArgTypes.STRING);
        upgrade.handler(c -> {
            ProgressService p = feature(c, ProgressService.class);
            if (p == null || requireFaction(c) == null) {
                return;
            }
            String k = c.arg(key).toLowerCase(Locale.ROOT);
            if (!p.upgradeKeys().contains(k)) {
                c.bad("There is no upgrade called " + c.arg(key) + ". The upgrades are " + String.join(", ", p.upgradeKeys()) + ".");
                return;
            }
            c.reply(p.buyUpgrade(c.uuid(), k));
        });

        root.sub(new PlayerCmd("missions", "Show the daily and weekly missions.")).handler(c -> {
            ProgressService p = feature(c, ProgressService.class);
            Faction f = p == null ? null : requireFaction(c);
            if (f != null) {
                showMissions(c, p, f, false);
                showMissions(c, p, f, true);
            }
        });

        root.sub(new PlayerCmd("value", "Show how your faction's value is worked out.")).handler(c -> {
            ProgressService p = feature(c, ProgressService.class);
            Faction f = p == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            c.send(Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true),
                    Msg.colored("  is worth " + Msg.number(p.value(f)) + " and " + standing(p.rank(f)), Msg.TEXT)));
            for (Map.Entry<String, Double> e : p.valueBreakdown(f).entrySet()) {
                c.send(Message.join(Msg.muted(" " + e.getKey() + "  "), Msg.colored(Msg.number(e.getValue()), Msg.GOLD)));
            }
        });
    }

    /** The /f top handler. One page of the value leaderboard in the classic layout, or the panel without progression. */
    void top(Call c, int page) {
        ProgressService p = ctx.serviceOrNull(ProgressService.class);
        if (p == null) {
            ui().openTop(c.player());
            return;
        }
        List<Faction> all = p.top(Integer.MAX_VALUE);
        if (all.isEmpty()) {
            c.info("No faction has any value yet.");
            return;
        }
        int size = pageSize(ctx.settings().integer("top.pageSize"));
        int pages = pages(all.size(), size);
        int current = Math.min(Math.max(1, page), pages);
        Faction mine = factions().factionOf(c.uuid());
        c.send(Message.join(Msg.colored("____[ ", Msg.GOLD), Msg.colored("Faction Top (Value) " + current + "/" + pages, Msg.TEXT).bold(true),
                Msg.colored(" ]____", Msg.GOLD)));
        int from = firstIndex(current, size);
        int to = Math.min(all.size(), from + size);
        boolean mineShown = false;
        for (int i = from; i < to; i++) {
            Faction f = all.get(i);
            boolean self = mine != null && mine.id().equals(f.id());
            mineShown |= self;
            c.send(Message.join(Msg.muted((i + 1) + ". "), Msg.colored(f.tag(), self ? Msg.GOLD : f.color()).bold(true),
                    Msg.muted(" (" + online(f) + "/" + f.memberCount() + ") "), Msg.colored(Msg.number(Math.round(p.value(f))), Msg.TEXT)));
        }
        if (mine != null && !mine.isSystem() && !mineShown) {
            c.send(Msg.muted(mine.tag() + " " + standing(p.rank(mine)) + " and is worth " + Msg.number(Math.round(p.value(mine))) + "."));
        }
        if (current < pages) {
            c.send(Msg.muted("Type /f top " + (current + 1) + " to see the next page"));
        }
    }

    private static int online(Faction f) {
        int n = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                n++;
            }
        }
        return n;
    }

    /** The configured page size kept between 5 and 25. */
    static int pageSize(int configured) {
        return Math.max(5, Math.min(25, configured));
    }

    /** Number of pages for total rows, at least one. */
    static int pages(int total, int size) {
        return Math.max(1, (total + size - 1) / size);
    }

    /** Index of the first row on a page, pages counted from one. */
    static int firstIndex(int page, int size) {
        return (Math.max(1, page) - 1) * size;
    }

    private void openVault(Call c, int page) {
        VaultService v = feature(c, VaultService.class);
        Faction f = v == null ? null : requireFaction(c);
        if (f == null) {
            return;
        }
        int pages = v.pages(f);
        if (page < 1 || page > pages) {
            c.bad(pages == 1 ? "Your vault has one page." : "Your vault has pages 1 to " + pages + ".");
            return;
        }
        c.reply(v.open(c.uuid(), page));
    }

    private void showUpgrades(Call c) {
        ProgressService p = feature(c, ProgressService.class);
        Faction f = p == null ? null : requireFaction(c);
        if (f == null) {
            return;
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        c.info("Upgrades for " + f.tag() + ". Buy one with /f upgrade and its name.");
        for (String key : p.upgradeKeys()) {
            int level = p.upgradeLevel(f, key);
            int max = p.upgradeMaxLevel(key);
            String next;
            if (level >= max) {
                next = "fully upgraded";
            } else {
                double cost = p.upgradeCost(f, key);
                if (!ctx.settings().bool("upgrades.useCoins")) {
                    next = "next level costs " + Msg.number((long) Math.ceil(cost)) + " faction points";
                } else {
                    next = "next level costs " + (eco == null ? Msg.number(cost) : eco.format(cost)) + " from the bank";
                }
            }
            c.send(Message.join(Msg.colored(" " + key, Msg.GOLD), Msg.colored("  level " + level + " of " + max, Msg.TEXT),
                    Msg.muted(", " + next)));
            String desc = p.upgradeDescription(key, Math.min(max, level + 1));
            if (desc != null && !desc.isBlank()) {
                c.send(Msg.muted("   " + desc));
            }
        }
    }

    private void showMissions(Call c, ProgressService p, Faction f, boolean weekly) {
        List<Mission> missions = p.activeMissions(weekly);
        c.info(weekly ? "Weekly missions" : "Daily missions");
        if (missions.isEmpty()) {
            c.send(Msg.muted(" None right now."));
            return;
        }
        for (Mission m : missions) {
            boolean done = p.missionDone(f, m);
            long progress = Math.min(m.target(), p.missionProgress(f, m));
            String reward = m.rewardPoints() + " points" + (m.rewardCoins() > 0 ? " and " + money(m.rewardCoins()) : "");
            c.send(Message.join(Msg.colored(" " + m.name(), done ? Msg.GOOD : Msg.TEXT).bold(done),
                    Msg.muted("  " + (done ? "complete" : Msg.number(progress) + " of " + Msg.number(m.target())) + ", rewards " + reward)));
            if (m.description() != null && !m.description().isBlank()) {
                c.send(Msg.muted("   " + m.description()));
            }
        }
    }

    private String money(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? Msg.number(amount) : eco.format(amount);
    }

    /** Ranks 3rd, or is not ranked yet. */
    static String standing(int n) {
        return n <= 0 ? "is not ranked yet" : "ranks " + ordinal(n);
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }
}
