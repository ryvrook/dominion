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
import dev.ryvrook.dominion.text.Messages;
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
        c.set("#PageTitle.Text", Messages.get(UiText.FACTION_INFO_TITLE));
        c.set("#F1.Visible", true);
        c.set("#F1.Text", Messages.get(UiText.TOP_FACTIONS));
        Body.on(events, "#F1", "top", "");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", Messages.get(UiText.YOUR_PANEL));
        Body.on(events, "#F2", "panel", "");

        Body body = new Body(c, events, "#Body");
        Faction f = factions().byTag(tag);
        if (f == null) {
            c.set("#Heading.Text", Messages.get(UiText.UNKNOWN_FACTION));
            body.text(Messages.get(UiText.NO_FACTION_WITH_TAG, "tag", tag), Msg.MUTED);
            return;
        }
        PowerService power = ctx.service(PowerService.class);
        ClaimService claims = ctx.service(ClaimService.class);
        c.set("#Heading.Text", f.label());
        c.set("#Heading.Style.TextColor", safeColor(f.color(), Msg.TEXT));
        ShopService shops = ctx.serviceOrNull(ShopService.class);
        if (shops != null && !f.isSystem()) {
            c.set("#F3.Visible", true);
            c.set("#F3.Text", Messages.get(UiText.OPEN_SHOP));
            Body.on(events, "#F3", "shop", f.tag());
        }
        Relation rel = ctx.service(RelationService.class).playerToFaction(uuid(), f);
        c.set("#Subheading.Text", rel == null ? "" : rel.displayName());
        if (rel != null) {
            c.set("#Subheading.Style.TextColor", rel.color());
        }

        body.text(f.description().isBlank() ? Messages.get(UiText.NO_DESCRIPTION) : f.description());
        body.stat(Messages.get(UiText.LABEL_FOUNDED), Time.date(f.createdAt()));
        NationService nations = ctx.serviceOrNull(NationService.class);
        Nation nation = nations == null ? null : nations.nationOf(f);
        if (nation != null) {
            body.stat(Messages.get(UiText.LABEL_NATION), nation.label(), safeColor(nation.color(), Msg.GOLD));
        }
        body.stat(Messages.get(UiText.LABEL_POWER), Messages.get(UiText.POWER_OF_MAX,
                "power", Msg.number(power.factionPower(f)), "max", Msg.number(power.factionMaxPower(f))));
        body.stat(Messages.get(UiText.LABEL_LAND), Messages.get(UiText.CHUNKS, "count", claims.claimCount(f)));
        int online = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                online++;
            }
        }
        body.stat(Messages.get(UiText.LABEL_MEMBERS), Messages.get(UiText.ONLINE_OF, "online", online, "total", f.memberCount()));
        body.stat(Messages.get(UiText.LABEL_JOINING), f.setting("open", false)
                ? Messages.get(UiText.OPEN_TO_EVERYONE) : Messages.get(UiText.INVITE_ONLY));
        if (claims.raidable(f)) {
            body.stat(Messages.get(UiText.LABEL_STATUS), Messages.get(UiText.RAIDABLE), Msg.BAD);
        }

        body.section(Messages.get(UiText.LABEL_RELATIONS));
        boolean any = false;
        for (Map.Entry<UUID, Relation> e : f.relations().entrySet()) {
            Faction o = factions().byId(e.getKey());
            if (o == null) {
                continue;
            }
            any = true;
            body.row(o.label(), e.getValue().color(),
                    new String[] {e.getValue().displayName(), "", "", ""}, Btn.of(Messages.get(UiText.INFO), "info", o.tag()));
        }
        if (!any) {
            body.text(Messages.get(UiText.NEUTRAL_WITH_EVERYONE), Msg.MUTED);
        }

        body.section(Messages.get(UiText.LABEL_MEMBERS));
        List<Member> members = new ArrayList<>(f.members());
        UUID leader = f.leader();
        members.sort(Comparator.comparing((Member m) -> m.uuid().equals(leader) ? Rank.LEADER : m.rank()).reversed()
                .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
        for (Member m : members) {
            boolean on = Players.isOnline(m.uuid());
            Rank rank = m.uuid().equals(leader) ? Rank.LEADER : m.rank();
            String row = body.row(m.name(), rank == Rank.LEADER ? Msg.GOLD : null,
                    new String[] {f.rankName(rank), Messages.get(UiText.POWER_AMOUNT, "power", Msg.number(power.powerOf(m.uuid()))),
                            on ? Messages.get(UiText.ONLINE) : Time.ago(m.lastSeen()), m.title()});
            body.dot(row, on);
        }

        MarketService market = ctx.serviceOrNull(MarketService.class);
        if (market != null && market.enabled()) {
            List<Listing> shelf = market.storefront(f);
            if (!shelf.isEmpty()) {
                body.section(Messages.get(UiText.LABEL_STOREFRONT));
                EconomyService eco = ctx.serviceOrNull(EconomyService.class);
                for (Listing l : shelf) {
                    if (l.closed()) {
                        continue;
                    }
                    String price = eco != null ? eco.format(l.price()) : Msg.number(l.price());
                    body.itemRow(l.itemId(), ItemNames.of(l.itemId()), null,
                            new String[] {String.valueOf(l.quantity()), Messages.get(UiText.PRICE_EACH, "price", price), l.sellerName()},
                            "", Btn.of(Messages.get(UiText.BUY), "store_buy", l.id().toString()), null);
                }
                body.text(Messages.get(UiText.STOREFRONT_HINT), Msg.MUTED);
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
                    reply(Result.fail(Messages.get(UiText.SHOPS_UNAVAILABLE)));
                } else if (f == null) {
                    reply(Result.fail(Messages.get(UiText.FACTION_GONE)));
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
                    reply(Result.fail(Messages.get(UiText.ITEM_NOT_FOR_SALE)));
                } else {
                    int amount;
                    try {
                        amount = e.text().isEmpty() ? l.quantity() : Integer.parseInt(e.text().replace(",", "").replace("_", "").trim());
                    } catch (NumberFormatException ex) {
                        amount = -1;
                    }
                    reply(amount <= 0 ? Result.fail(Messages.get(UiText.WHOLE_NUMBER_TO_BUY)) : market.buy(uuid(), l.id(), amount));
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
