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
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;

import java.util.Locale;

/** Grid of faction actions against ranks and relations. Each cell toggles one permission. */
public final class PermissionsPage extends DominionPage {
    private static final String PAGE = "Pages/Dominion_Perms.ui";
    private static final String HEAD = "Pages/Dominion_PermHead.ui";
    private static final String ROW = "Pages/Dominion_PermRow.ui";

    static final Rank[] RANKS = {Rank.RECRUIT, Rank.MEMBER, Rank.OFFICER};
    static final Relation[] RELATIONS = {Relation.ALLY, Relation.TRUCE, Relation.NEUTRAL, Relation.ENEMY};

    public PermissionsPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui) {
        super(player, ctx, ui);
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(PAGE);
        Body.on(events, "#Back", "back", "");
        Body.on(events, "#Reset", "reset", "");
        Faction f = factions().factionOf(uuid());
        if (f == null) {
            c.set("#Subtitle.Text", Messages.get(CommonText.NOT_IN_FACTION));
            return;
        }
        boolean canEdit = factions().can(uuid(), FactionAction.PERMS);
        c.set("#Reset.Visible", canEdit);
        c.set("#Subtitle.Text", f.label() + (canEdit
                ? "    " + Messages.get(UiText.PERMS_CAN_EDIT)
                : "    " + Messages.get(UiText.PERMS_READ_ONLY)));
        c.clear("#Head");
        c.append("#Head", HEAD);
        rankHeads(c, f);

        c.clear("#List");
        int index = 0;
        for (FactionAction action : FactionAction.values()) {
            String row = "#List[" + index++ + "]";
            c.append("#List", ROW);
            c.set(row + " #Name.Text", pretty(action));
            c.set(row + " #Desc.Text", action.description());
            for (int i = 0; i < RANKS.length; i++) {
                if (action.rankScoped()) {
                    cell(c, events, row, i, f.rankCan(RANKS[i], action), "rank:" + RANKS[i].name() + ":" + action.name());
                }
            }
            for (int i = 0; i < RELATIONS.length; i++) {
                if (action.relationScoped()) {
                    cell(c, events, row, RANKS.length + i, f.relationCan(RELATIONS[i], action),
                            "rel:" + RELATIONS[i].name() + ":" + action.name());
                }
            }
        }
    }

    /** Writes the faction's rank names into the grid header. */
    static void rankHeads(UICommandBuilder c, Faction f) {
        for (int i = 0; i < RANKS.length; i++) {
            // Custom names can run to 16 characters but a grid column fits about ten.
            c.set("#Head[0] #R" + i + ".Text", DominionHud.fit(f.rankName(RANKS[i]), 10));
        }
    }

    private static void cell(UICommandBuilder c, UIEventBuilder events, String row, int i, boolean allowed, String value) {
        String on = row + " #Y" + i;
        String off = row + " #N" + i;
        c.set(on + ".Visible", allowed);
        c.set(off + ".Visible", !allowed);
        Body.on(events, allowed ? on : off, "toggle", value);
    }

    static String pretty(FactionAction action) {
        String n = action.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    @Override
    protected boolean onAction(PageEvent e) {
        switch (e.action) {
            case "back" -> {
                ui.openPanel(player(), DominionPanel.SETTINGS);
                return false;
            }
            case "reset" -> {
                reply(factions().resetPerms(uuid()));
                return true;
            }
            case "toggle" -> {
                Faction f = factions().factionOf(uuid());
                if (f == null) {
                    reply(Result.fail(Messages.get(CommonText.NOT_IN_FACTION)));
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
                        reply(factions().setRankPerm(uuid(), rank, action, !f.rankCan(rank, action)));
                    }
                } else {
                    Relation rel = Relation.parse(parts[1]);
                    if (rel != null) {
                        reply(factions().setRelationPerm(uuid(), rel, action, !f.relationCan(rel, action)));
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
