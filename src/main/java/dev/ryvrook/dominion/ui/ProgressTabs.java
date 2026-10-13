package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Mission;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.VaultService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The vault, upgrades and missions tabs of the panel, plus the progression lines on the overview. */
final class ProgressTabs {
    static final String VAULT = "vault";
    static final String UPGRADES = "upgrades";
    static final String MISSIONS = "missions";
    private static final int MAX_VAULT_PAGES = 30;

    private final DominionContext ctx;
    private final DominionPanel panel;

    ProgressTabs(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    ProgressService progress() {
        return ctx.serviceOrNull(ProgressService.class);
    }

    VaultService vault() {
        return ctx.serviceOrNull(VaultService.class);
    }

    // ---- overview --------------------------------------------------------

    /** Value, leaderboard place and level for the overview tab. Does nothing without the service. */
    void overviewStats(Body body, Faction f) {
        ProgressService p = progress();
        if (p == null) {
            return;
        }
        body.stat("Faction value", Msg.number(Math.round(p.value(f))), Msg.GOLD);
        int rank = p.rank(f);
        body.stat("Leaderboard", rank > 0 ? "Place " + rank : "Not ranked yet");
        body.stat("Level", "Level " + p.level(f) + " with " + Msg.number(p.points(f)) + " points");
    }

    // ---- vault -----------------------------------------------------------

    void buildVault(UICommandBuilder c, Body body, Faction f) {
        VaultService v = vault();
        int pages = v == null ? 0 : Math.max(0, Math.min(MAX_VAULT_PAGES, v.pages(f)));
        panel.title(c, "Vault", pages == 1 ? "1 page" : pages + " pages");
        body.text("The vault is a shared chest for your faction. Pick a page to open it. "
                + "Opening the vault needs the vault permission.");
        if (pages == 0) {
            body.text("Your faction has no vault pages yet. Buy the vault upgrade to unlock more.", Msg.MUTED);
            return;
        }
        List<Btn> row = new ArrayList<>();
        for (int i = 1; i <= pages; i++) {
            row.add(Btn.of("Page " + i, "vault_open", String.valueOf(i)));
            if (row.size() == 6) {
                body.buttons(row.toArray(new Btn[0]));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            body.buttons(row.toArray(new Btn[0]));
        }
        body.text("Each page holds " + ctx.settings().integer("vault.slotsPerPage") + " slots.", Msg.MUTED);
    }

    // ---- upgrades --------------------------------------------------------

    void buildUpgrades(UICommandBuilder c, Body body, Faction f) {
        ProgressService p = progress();
        boolean coins = ctx.settings().bool("upgrades.useCoins");
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        panel.title(c, "Upgrades", coins && eco != null ? "Bank " + eco.format(f.bank())
                : Msg.number(p.points(f)) + " points to spend");
        body.text(coins ? "Upgrades are paid from the faction bank and need the settings permission."
                : "Upgrades are paid with faction points and need the settings permission.", Msg.MUTED);
        for (String key : p.upgradeKeys()) {
            int level = p.upgradeLevel(f, key);
            int max = p.upgradeMaxLevel(key);
            boolean maxed = level >= max;
            String desc = p.upgradeDescription(key, level);
            String cost;
            if (maxed) {
                cost = "Maxed";
            } else {
                double amount = p.upgradeCost(f, key);
                // Points are charged rounded up, matching the progress service.
                cost = coins ? (eco != null ? eco.format(amount) : Msg.number(amount)) : Msg.number((long) Math.ceil(amount)) + " points";
            }
            body.upgrade(prettyKey(key), desc, level, max, cost,
                    maxed ? null : Btn.of("Buy", "upgrade_buy", key));
        }
    }

    // ---- missions --------------------------------------------------------

    void buildMissions(UICommandBuilder c, Body body, Faction f) {
        ProgressService p = progress();
        List<Mission> daily = p.activeMissions(false);
        List<Mission> weekly = p.activeMissions(true);
        int done = 0;
        for (Mission m : daily) {
            if (p.missionDone(f, m)) {
                done++;
            }
        }
        for (Mission m : weekly) {
            if (p.missionDone(f, m)) {
                done++;
            }
        }
        panel.title(c, "Missions", done + " of " + (daily.size() + weekly.size()) + " complete");
        // Missions rotate at midnight UTC and weekly on Monday at midnight UTC.
        ZoneId zone = ZoneOffset.UTC;
        LocalDate today = LocalDate.now(zone);
        long now = Time.now();
        long dayEnds = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long weekEnds = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli();

        body.section("Daily missions");
        cards(body, f, p, daily, "New missions in " + Time.remaining(dayEnds - now));
        body.section("Weekly missions");
        cards(body, f, p, weekly, "New missions in " + Time.remaining(weekEnds - now));
        body.text("Every member helps. Progress counts while you play and rewards go to the faction when a mission is done.",
                Msg.MUTED);
    }

    private void cards(Body body, Faction f, ProgressService p, List<Mission> missions, String timer) {
        if (missions.isEmpty()) {
            body.text("No missions are active right now.", Msg.MUTED);
            return;
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        for (Mission m : missions) {
            long target = Math.max(1, m.target());
            long progress = Math.min(target, Math.max(0, p.missionProgress(f, m)));
            boolean done = p.missionDone(f, m);
            List<String> rewards = new ArrayList<>();
            if (m.rewardCoins() > 0) {
                rewards.add(eco != null ? eco.format(m.rewardCoins()) : Msg.number(m.rewardCoins()) + " coins");
            }
            if (m.rewardPoints() > 0) {
                rewards.add(m.rewardPoints() + " points");
            }
            String reward = rewards.isEmpty() ? "" : "Reward " + String.join(" and ", rewards);
            String card = body.card(m.icon(), m.name(), m.description(), reward, progress / (float) target,
                    done ? "Complete" : Msg.number(progress) + " of " + Msg.number(target), done ? "" : timer);
            if (done) {
                body.commands().set(card + " #Value.Style.TextColor", Msg.GOOD);
            }
        }
    }

    // ---- actions ---------------------------------------------------------

    /** Handles vault and upgrade buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        switch (e.action) {
            case "vault_open" -> {
                VaultService v = vault();
                if (v == null) {
                    panel.reply(Result.fail("The vault is not available on this server."));
                    return true;
                }
                int page;
                try {
                    page = Integer.parseInt(e.value());
                } catch (NumberFormatException ex) {
                    panel.reply(Result.fail("That page could not be read."));
                    return true;
                }
                Result r = v.open(panel.uuid(), page);
                if (r.success()) {
                    if (!r.message().isBlank()) {
                        panel.reply(r);
                    }
                    return false;
                }
                panel.reply(r);
            }
            case "upgrade_buy" -> {
                ProgressService p = progress();
                panel.reply(p == null ? Result.fail("Upgrades are not available on this server.") : p.buyUpgrade(panel.uuid(), e.value()));
            }
            default -> {
                return null;
            }
        }
        return true;
    }

    static String prettyKey(String key) {
        String k = switch (key == null ? "" : key.toLowerCase(Locale.ROOT)) {
            case "members" -> "Member slots";
            case "regen" -> "Power regeneration";
            case "maxpower" -> "Max power";
            case "warps" -> "Warps";
            case "vault" -> "Vault pages";
            case "shield" -> "Shield";
            case "immunity" -> "Siege immunity";
            default -> null;
        };
        if (k != null) {
            return k;
        }
        return key == null || key.isEmpty() ? "Upgrade" : Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
