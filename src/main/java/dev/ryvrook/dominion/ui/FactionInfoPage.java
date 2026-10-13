package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.ShopService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Public profile of any faction by tag. */
public final class FactionInfoPage extends DominionPage {
    static final String PAGE = "Pages/Dominion_Simple.ui";

    private final String tag;

    public FactionInfoPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui, String tag) {
        super(player, ctx, ui);
        this.tag = tag == null ? "" : tag.trim();
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(PAGE);
        c.set("#PageTitle.Text", "Faction info");
        c.set("#F1.Visible", true);
        c.set("#F1.Text", "Top factions");
        Body.on(events, "#F1", "top", "");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", "Your panel");
        Body.on(events, "#F2", "panel", "");

        Body body = new Body(c, events, "#Body");
        Faction f = factions().byTag(tag);
        if (f == null) {
            c.set("#Heading.Text", "Unknown faction");
            body.text("No faction uses the tag " + tag + ".", Msg.MUTED);
            return;
        }
        PowerService power = ctx.service(PowerService.class);
        ClaimService claims = ctx.service(ClaimService.class);
        c.set("#Heading.Text", f.tag() + "  " + f.name());
        c.set("#Heading.Style.TextColor", safeColor(f.color(), Msg.TEXT));
        ShopService shops = ctx.serviceOrNull(ShopService.class);
        if (shops != null && !f.isSystem()) {
            c.set("#F3.Visible", true);
            c.set("#F3.Text", "Open shop");
            Body.on(events, "#F3", "shop", f.tag());
        }
        Relation rel = ctx.service(RelationService.class).playerToFaction(uuid(), f);
        c.set("#Subheading.Text", rel == null ? "" : rel.displayName());
        if (rel != null) {
            c.set("#Subheading.Style.TextColor", rel.color());
        }

        body.text(f.description().isBlank() ? "This faction has no description." : f.description());
        body.stat("Founded", Time.date(f.createdAt()));
        NationService nations = ctx.serviceOrNull(NationService.class);
        Nation nation = nations == null ? null : nations.nationOf(f);
        if (nation != null) {
            body.stat("Nation", nation.tag() + "  " + nation.name(), safeColor(nation.color(), Msg.GOLD));
        }
        body.stat("Power", Msg.number(power.factionPower(f)) + " / " + Msg.number(power.factionMaxPower(f)));
        body.stat("Land", claims.claimCount(f) + " chunks");
        int online = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                online++;
            }
        }
        body.stat("Members", online + " online of " + f.memberCount());
        body.stat("Joining", f.setting("open", false) ? "Open to everyone" : "Invite only");
        if (claims.raidable(f)) {
            body.stat("Status", "Raidable", Msg.BAD);
        }

        body.section("Relations");
        boolean any = false;
        for (Map.Entry<UUID, Relation> e : f.relations().entrySet()) {
            Faction o = factions().byId(e.getKey());
            if (o == null) {
                continue;
            }
            any = true;
            body.row(o.tag() + "  " + o.name(), e.getValue().color(),
                    new String[] {e.getValue().displayName(), "", "", ""}, Btn.of("Info", "info", o.tag()));
        }
        if (!any) {
            body.text("Neutral with everyone.", Msg.MUTED);
        }

        body.section("Members");
        List<Member> members = new ArrayList<>(f.members());
        UUID leader = f.leader();
        members.sort(Comparator.comparing((Member m) -> m.uuid().equals(leader) ? Rank.LEADER : m.rank()).reversed()
                .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
        for (Member m : members) {
            boolean on = Players.isOnline(m.uuid());
            Rank rank = m.uuid().equals(leader) ? Rank.LEADER : m.rank();
            String row = body.row(m.name(), rank == Rank.LEADER ? Msg.GOLD : null,
                    new String[] {f.rankName(rank), Msg.number(power.powerOf(m.uuid())) + " power",
                            on ? "Online" : Time.ago(m.lastSeen()), m.title()});
            body.dot(row, on);
        }

        MarketService market = ctx.serviceOrNull(MarketService.class);
        if (market != null && market.enabled()) {
            List<Listing> shelf = market.storefront(f);
            if (!shelf.isEmpty()) {
                body.section("Storefront");
                EconomyService eco = ctx.serviceOrNull(EconomyService.class);
                for (Listing l : shelf) {
                    if (l.closed()) {
                        continue;
                    }
                    String price = eco != null ? eco.format(l.price()) : Msg.number(l.price());
                    body.itemRow(l.itemId(), ItemNames.of(l.itemId()), null,
                            new String[] {String.valueOf(l.quantity()), price + " each", l.sellerName()},
                            "", Btn.of("Buy", "store_buy", l.id().toString()), null);
                }
                body.text("Type how many you want next to an item and press Buy. Leave it empty to buy them all.", Msg.MUTED);
            }
        }
    }

    @Override
    protected boolean onAction(PageEvent e) {
        switch (e.action) {
            case "top" -> ui.openTop(player());
            case "shop" -> {
                ShopService shops = ctx.serviceOrNull(ShopService.class);
                Faction f = factions().byTag(e.value());
                if (shops == null) {
                    reply(Result.fail("Shops are not available on this server."));
                } else if (f == null) {
                    reply(Result.fail("That faction no longer exists."));
                } else {
                    shops.openFactionShop(player(), f);
                }
            }
            case "panel" -> ui.openPanel(player(), DominionPanel.OVERVIEW);
            case "info" -> ui.openFactionInfo(player(), e.value());
            case "store_buy" -> {
                MarketService market = ctx.serviceOrNull(MarketService.class);
                Listing l = null;
                try {
                    l = market == null ? null : market.byId(UUID.fromString(e.value()));
                } catch (IllegalArgumentException ex) {
                    l = null;
                }
                if (l == null || l.closed()) {
                    reply(Result.fail("That item is no longer for sale."));
                } else {
                    int amount;
                    try {
                        amount = e.text().isEmpty() ? l.quantity() : Integer.parseInt(e.text().replace(",", "").replace("_", "").trim());
                    } catch (NumberFormatException ex) {
                        amount = -1;
                    }
                    reply(amount <= 0 ? Result.fail("Type a whole number of items to buy.") : market.buy(uuid(), l.id(), amount));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
        return false;
    }
}
