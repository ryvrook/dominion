package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Leaderboard of factions by a blended score. The viewer's faction is pinned at the bottom. */
public final class TopPage extends DominionPage {
    private static final int LIMIT = 30;
    private static final int TOP_FETCH = 500;

    record Scored(Faction faction, double score, int claims, double power) {}

    public TopPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui) {
        super(player, ctx, ui);
    }

    /**
     * Ranking by faction value when the progress service is installed, otherwise by the built in
     * blend of claims times 10, members times 5, power, kills times 2 and bank over 100.
     */
    static List<Scored> ranking(DominionContext ctx) {
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress != null) {
            ClaimService claims = ctx.service(ClaimService.class);
            PowerService power = ctx.service(PowerService.class);
            List<Scored> out = new ArrayList<>();
            for (Faction f : progress.top(TOP_FETCH)) {
                if (!f.isSystem()) {
                    out.add(new Scored(f, progress.value(f), claims.claimCount(f), power.factionPower(f)));
                }
            }
            return out;
        }
        return blended(ctx);
    }

    private static List<Scored> blended(DominionContext ctx) {
        ClaimService claims = ctx.service(ClaimService.class);
        PowerService power = ctx.service(PowerService.class);
        List<Scored> out = new ArrayList<>();
        for (Faction f : ctx.service(dev.ryvrook.dominion.service.FactionService.class).all()) {
            if (f.isSystem()) {
                continue;
            }
            int c = claims.claimCount(f);
            double p = power.factionPower(f);
            double score = c * 10.0 + f.memberCount() * 5.0 + p + f.stat("kills") * 2.0 + f.bank() / 100.0;
            out.add(new Scored(f, score, c, p));
        }
        out.sort(Comparator.comparingDouble(Scored::score).reversed());
        return out;
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(FactionInfoPage.PAGE);
        c.set("#PageTitle.Text", "Top factions");
        c.set("#Heading.Text", "Top factions");
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        c.set("#Subheading.Text", progress != null ? "Ranked by faction value" : "Score blends land, members, power, kills and bank");
        c.set("#F1.Visible", true);
        c.set("#F1.Text", "Your panel");
        Body.on(events, "#F1", "panel", "");

        Body body = new Body(c, events, "#Body");
        List<Scored> ranking = ranking(ctx);
        body.row("Faction", Msg.MUTED, new String[] {progress != null ? "Value" : "Score", "Members", "Land", "Power"});
        Faction mine = factions().factionOf(uuid());
        int myPlace = -1;
        for (int i = 0; i < ranking.size(); i++) {
            Scored s = ranking.get(i);
            if (mine != null && s.faction().id().equals(mine.id())) {
                myPlace = i;
            }
            if (i < LIMIT) {
                row(body, i + 1, s);
            }
        }
        if (ranking.isEmpty()) {
            body.text("No factions yet.", Msg.MUTED);
        }
        if (mine != null && myPlace >= 0) {
            body.section("Your faction");
            row(body, myPlace + 1, ranking.get(myPlace));
        } else if (mine != null && !mine.isSystem() && progress != null) {
            body.section("Your faction");
            row(body, progress.rank(mine), new Scored(mine, progress.value(mine),
                    ctx.service(ClaimService.class).claimCount(mine), ctx.service(PowerService.class).factionPower(mine)));
        }
        if (mine != null && progress != null) {
            Map<String, Double> parts = progress.valueBreakdown(mine);
            if (parts != null && !parts.isEmpty()) {
                body.section("Where your value comes from");
                for (Map.Entry<String, Double> e : parts.entrySet()) {
                    body.stat(partName(e.getKey()), Msg.number(Math.round(e.getValue() == null ? 0 : e.getValue())));
                }
            }
        }
    }

    private void row(Body body, int place, Scored s) {
        Faction f = s.faction();
        String nation = nationTag(f);
        body.row((place > 0 ? place + ".  " : "") + f.tag() + "  " + f.name() + (nation == null ? "" : "  of " + nation),
                safeColor(f.color(), null),
                new String[] {Msg.number(Math.round(s.score())), String.valueOf(f.memberCount()), String.valueOf(s.claims()),
                        Msg.number(s.power())},
                Btn.of("Info", "info", f.tag()));
    }

    /** The tag of the faction's nation, or null without one or without the nation service. */
    private String nationTag(Faction f) {
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations == null) {
            return null;
        }
        try {
            Nation n = nations.nationOf(f);
            return n == null ? null : n.tag();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Turns a breakdown key such as claims or bankPer100 into a readable label. */
    static String partName(String key) {
        if (key == null || key.isBlank()) {
            return "Other";
        }
        String k = key.startsWith("weight") ? key.substring(6) : key;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < k.length(); i++) {
            char ch = k.charAt(i);
            if (i > 0 && (Character.isUpperCase(ch) || (Character.isDigit(ch) && !Character.isDigit(k.charAt(i - 1))))) {
                out.append(' ');
            }
            out.append(i == 0 ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
        }
        return out.toString().replace('_', ' ').replace('.', ' ');
    }

    @Override
    protected boolean onAction(PageEvent e) {
        switch (e.action) {
            case "panel" -> ui.openPanel(player(), DominionPanel.OVERVIEW);
            case "info" -> ui.openFactionInfo(player(), e.value());
            default -> {
                return false;
            }
        }
        return false;
    }
}
