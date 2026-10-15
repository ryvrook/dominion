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
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;

/**
 * Permission grid for one zone, built like the faction grid. A rank or relation without a zone
 * override shows the faction value as Inherit. Clicking a cell sets the opposite of what it shows.
 */
public final class ZonePermsPage extends DominionPage {
    private static final String PAGE = "Pages/Dominion_ZonePerms.ui";
    private static final String HEAD = "Pages/Dominion_PermHead.ui";
    private static final String ROW = "Pages/Dominion_PermRow.ui";

    private final String zoneId;

    public ZonePermsPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui, String zoneId) {
        super(player, ctx, ui);
        this.zoneId = zoneId == null ? "" : zoneId;
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(PAGE);
        Body.on(events, "#Back", "back", "");
        Body.on(events, "#Reset", "reset", "");
        Faction f = factions().factionOf(uuid());
        Zone zone = f == null ? null : f.zone(zoneId);
        if (zone == null) {
            c.set("#Subtitle.Text", f == null ? Messages.get(CommonText.NOT_IN_FACTION) : Messages.get(UiText.ZONE_GONE));
            return;
        }
        // Zone permission changes are gated on the permissions right, like the faction grid.
        boolean canEdit = factions().can(uuid(), FactionAction.PERMS);
        c.set("#Subtitle.Text", Messages.get(UiText.ZONE_OF, "zone", zone.name(), "tag", f.tag()) + "    " + (canEdit
                ? Messages.get(UiText.ZONE_PERMS_CAN_EDIT)
                : Messages.get(UiText.PERMS_READ_ONLY)));
        c.clear("#Head");
        c.append("#Head", HEAD);
        PermissionsPage.rankHeads(c, f);

        c.clear("#List");
        int index = 0;
        Rank[] ranks = PermissionsPage.RANKS;
        Relation[] relations = PermissionsPage.RELATIONS;
        for (FactionAction action : FactionAction.values()) {
            String row = "#List[" + index++ + "]";
            c.append("#List", ROW);
            c.set(row + " #Name.Text", PermissionsPage.pretty(action));
            c.set(row + " #Desc.Text", action.description());
            for (int i = 0; i < ranks.length; i++) {
                if (action.rankScoped()) {
                    Boolean own = zone.hasRankOverride(ranks[i]) ? zone.rankCan(ranks[i], action) : null;
                    boolean effective = own != null ? own : f.rankCan(ranks[i], action);
                    cell(c, events, row, i, effective, own == null, "rank:" + ranks[i].name() + ":" + action.name());
                }
            }
            for (int i = 0; i < relations.length; i++) {
                if (action.relationScoped()) {
                    Boolean own = zone.hasRelationOverride(relations[i]) ? zone.relationCan(relations[i], action) : null;
                    boolean effective = own != null ? own : f.relationCan(relations[i], action);
                    cell(c, events, row, ranks.length + i, effective, own == null,
                            "rel:" + relations[i].name() + ":" + action.name());
                }
            }
        }
    }

    private static void cell(UICommandBuilder c, UIEventBuilder events, String row, int i, boolean allowed,
                             boolean inherited, String value) {
        String on = row + " #Y" + i;
        String off = row + " #N" + i;
        c.set(on + ".Visible", allowed);
        c.set(off + ".Visible", !allowed);
        if (inherited) {
            c.set((allowed ? on : off) + ".Text", Messages.get(UiText.INHERIT));
        }
        Body.on(events, allowed ? on : off, "toggle", value);
    }

    @Override
    protected boolean onAction(PageEvent e) {
        switch (e.action) {
            case "back" -> {
                ui.openPanel(player(), DominionPanel.SETTINGS);
                return false;
            }
            case "reset" -> {
                Faction f = factions().factionOf(uuid());
                Zone zone = f == null ? null : f.zone(zoneId);
                if (zone == null) {
                    reply(Result.fail(Messages.get(UiText.ZONE_GONE)));
                    return true;
                }
                if (!factions().can(uuid(), FactionAction.PERMS)) {
                    reply(Result.fail(Messages.get(UiText.ZONE_PERMS_NEED_RIGHT)));
                    return true;
                }
                Result res = factions().zoneReset(uuid(), zoneId);
                reply(res.success() && res.message().isBlank()
                        ? Result.pass(Messages.get(UiText.ZONE_PERMS_RESET)) : res);
                return true;
            }
            case "toggle" -> {
                Faction f = factions().factionOf(uuid());
                Zone zone = f == null ? null : f.zone(zoneId);
                if (zone == null) {
                    reply(Result.fail(Messages.get(UiText.ZONE_GONE)));
                    return true;
                }
                String[] parts = e.value().split(":");
                if (parts.length != 3) {
                    return true;
                }
                FactionAction action;
                try {
                    action = FactionAction.valueOf(parts[2]);
                } catch (IllegalArgumentException ex) {
                    return true;
                }
                if ("rank".equals(parts[0])) {
                    Rank rank = Rank.parse(parts[1]);
                    if (rank != null) {
                        boolean now = zone.hasRankOverride(rank) ? Boolean.TRUE.equals(zone.rankCan(rank, action))
                                : f.rankCan(rank, action);
                        reply(factions().zoneSetPerm(uuid(), zoneId, rank, null, action, !now));
                    }
                } else {
                    Relation rel = Relation.parse(parts[1]);
                    if (rel != null) {
                        boolean now = zone.hasRelationOverride(rel) ? Boolean.TRUE.equals(zone.relationCan(rel, action))
                                : f.relationCan(rel, action);
                        reply(factions().zoneSetPerm(uuid(), zoneId, null, rel, action, !now));
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
