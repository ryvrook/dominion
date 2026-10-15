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
import dev.ryvrook.dominion.text.Messages;
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
        PlayerCmd top = root.sub(new PlayerCmd("top", Messages.get(CommandText.DESC_SHOW_THE_FACTION_LEADERBOARD)));
        top.handler(c -> top(c, 1));
        PlayerCmd topPage = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_A_PAGE_OF_THE_FACTION));
        RequiredArg<Integer> topArg = topPage.withRequiredArg("page", Messages.get(CommandText.ARG_PAGE_NUMBER), ArgTypes.INTEGER);
        topPage.handler(c -> top(c, clampPage(c.arg(topArg))));
        top.variant(topPage);

        PlayerCmd vault = root.sub(new PlayerCmd("vault", Messages.get(CommandText.DESC_OPEN_THE_FACTION_VAULT)));
        vault.handler(c -> openVault(c, 1));
        PlayerCmd vaultPage = new PlayerCmd(Messages.get(CommandText.DESC_OPEN_ONE_PAGE_OF_THE_FACTION));
        RequiredArg<Integer> page = vaultPage.withRequiredArg("page", Messages.get(CommandText.ARG_PAGE_NUMBER), ArgTypes.INTEGER);
        vaultPage.handler(c -> openVault(c, c.arg(page)));
        vault.variant(vaultPage);

        root.sub(new PlayerCmd("upgrades", Messages.get(CommandText.DESC_LIST_FACTION_UPGRADES))).handler(this::showUpgrades);

        PlayerCmd upgrade = root.sub(new PlayerCmd("upgrade", Messages.get(CommandText.DESC_BUY_THE_NEXT_LEVEL_OF_AN)));
        RequiredArg<String> key = upgrade.withRequiredArg("key", Messages.get(CommandText.ARG_UPGRADE_NAME), ArgTypes.STRING);
        upgrade.handler(c -> {
            ProgressService p = feature(c, ProgressService.class);
            if (p == null || requireFaction(c) == null) {
                return;
            }
            String k = c.arg(key).toLowerCase(Locale.ROOT);
            if (!p.upgradeKeys().contains(k)) {
                c.bad(Messages.get(CommandText.NO_UPGRADE_CALLED, "name", c.arg(key), "upgrades", String.join(", ", p.upgradeKeys())));
                return;
            }
            c.reply(p.buyUpgrade(c.uuid(), k));
        });

        root.sub(new PlayerCmd("missions", Messages.get(CommandText.DESC_SHOW_THE_DAILY_AND_WEEKLY_MISSIONS))).handler(c -> {
            ProgressService p = feature(c, ProgressService.class);
            Faction f = p == null ? null : requireFaction(c);
            if (f != null) {
                showMissions(c, p, f, false);
                showMissions(c, p, f, true);
            }
        });

        root.sub(new PlayerCmd("value", Messages.get(CommandText.DESC_SHOW_HOW_YOUR_FACTIONS_VALUE_IS))).handler(c -> {
            ProgressService p = feature(c, ProgressService.class);
            Faction f = p == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            c.send(Message.join(Msg.prefix(), Msg.colored(f.tag(), f.color()).bold(true),
                    Msg.colored("  " + Messages.get(CommandText.VALUE_WORTH, "value", Msg.number(p.value(f)), "standing", standing(p.rank(f))), Msg.TEXT)));
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
            c.info(Messages.get(CommandText.NO_FACTION_HAS_ANY_VALUE_YET));
            return;
        }
        int size = pageSize(ctx.settings().integer("top.pageSize"));
        int pages = pages(all.size(), size);
        int current = Math.min(Math.max(1, page), pages);
        Faction mine = factions().factionOf(c.uuid());
        c.send(Message.join(Msg.colored("____[ ", Msg.GOLD), Msg.colored(Messages.get(CommandText.TOP_TITLE, "page", current, "pages", pages), Msg.TEXT).bold(true),
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
            c.send(Msg.muted(Messages.get(CommandText.TOP_MINE, "tag", mine.tag(), "standing", standing(p.rank(mine)), "value", Msg.number(Math.round(p.value(mine))))));
        }
        if (current < pages) {
            c.send(Msg.muted(Messages.get(CommandText.TOP_NEXT, "page", current + 1)));
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
            c.bad(pages == 1 ? Messages.get(CommandText.YOUR_VAULT_HAS_ONE_PAGE) : Messages.get(CommandText.VAULT_PAGES, "pages", pages));
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
        c.info(Messages.get(CommandText.UPGRADES_FOR, "tag", f.tag()));
        for (String key : p.upgradeKeys()) {
            int level = p.upgradeLevel(f, key);
            int max = p.upgradeMaxLevel(key);
            String next;
            if (level >= max) {
                next = Messages.get(CommandText.FULLY_UPGRADED);
            } else {
                double cost = p.upgradeCost(f, key);
                if (!ctx.settings().bool("upgrades.useCoins")) {
                    next = Messages.get(CommandText.NEXT_COST_POINTS, "points", Msg.number((long) Math.ceil(cost)));
                } else {
                    next = Messages.get(CommandText.NEXT_COST_COINS, "cost", eco == null ? Msg.number(cost) : eco.format(cost));
                }
            }
            c.send(Message.join(Msg.colored(" " + key, Msg.GOLD), Msg.colored("  " + Messages.get(CommandText.UPGRADE_LEVEL, "level", level, "max", max), Msg.TEXT),
                    Msg.muted(", " + next)));
            String desc = p.upgradeDescription(key, Math.min(max, level + 1));
            if (desc != null && !desc.isBlank()) {
                c.send(Msg.muted("   " + desc));
            }
        }
    }

    private void showMissions(Call c, ProgressService p, Faction f, boolean weekly) {
        List<Mission> missions = p.activeMissions(weekly);
        c.info(weekly ? Messages.get(CommandText.WEEKLY_MISSIONS) : Messages.get(CommandText.DAILY_MISSIONS));
        if (missions.isEmpty()) {
            c.send(Msg.muted(" " + Messages.get(CommandText.NONE_RIGHT_NOW)));
            return;
        }
        for (Mission m : missions) {
            boolean done = p.missionDone(f, m);
            long progress = Math.min(m.target(), p.missionProgress(f, m));
            String reward = m.rewardCoins() > 0
                    ? Messages.get(CommandText.REWARD_POINTS_AND_COINS, "points", m.rewardPoints(), "coins", money(m.rewardCoins()))
                    : Messages.get(CommandText.REWARD_POINTS, "points", m.rewardPoints());
            c.send(Message.join(Msg.colored(" " + m.name(), done ? Msg.GOOD : Msg.TEXT).bold(done),
                    Msg.muted("  " + (done ? Messages.get(CommandText.MISSION_COMPLETE) : Messages.get(CommandText.MISSION_PROGRESS, "progress", Msg.number(progress), "target", Msg.number(m.target())))
                            + ", " + Messages.get(CommandText.MISSION_REWARDS, "reward", reward))));
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
        return n <= 0 ? Messages.get(CommandText.IS_NOT_RANKED_YET) : Messages.get(CommandText.RANKS_ORDINAL, "place", ordinal(n));
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        String id = mod100 >= 11 && mod100 <= 13 ? CommandText.ORDINAL_TH : switch (n % 10) {
            case 1 -> CommandText.ORDINAL_ST;
            case 2 -> CommandText.ORDINAL_ND;
            case 3 -> CommandText.ORDINAL_RD;
            default -> CommandText.ORDINAL_TH;
        };
        return Messages.get(id, "n", n);
    }
}
