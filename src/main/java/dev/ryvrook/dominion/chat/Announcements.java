package dev.ryvrook.dominion.chat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.Msg;

import java.util.ArrayList;
import java.util.logging.Level;

/** Server wide chat lines for faction events, such as a faction taking a new tag or name. */
final class Announcements {
    private final DominionContext ctx;

    Announcements(DominionContext ctx) {
        this.ctx = ctx;
    }

    void onEvent(DominionEvent event) {
        if (event instanceof DominionEvent.FactionRenamed renamed) {
            onRenamed(renamed);
        }
    }

    private void onRenamed(DominionEvent.FactionRenamed e) {
        if (!ctx.settings().bool("chat.announceRenames")) {
            return;
        }
        String before = label(e.oldTag(), e.oldName());
        String after = label(e.newTag(), e.newName());
        FactionService factions = ctx.serviceOrNull(FactionService.class);
        Faction faction = factions == null ? null : factions.byId(e.factionId());
        if (faction != null) {
            // Members who were offline read it later in the log tab.
            factions.audit(faction, null, "Server", "faction", line(before, after));
        }
        ctx.logger().at(Level.INFO).log("%s", line(before, after));
        Universe u = Universe.get();
        if (u == null) {
            return;
        }
        RelationService relations = ctx.serviceOrNull(RelationService.class);
        for (PlayerRef p : new ArrayList<>(u.getPlayers())) {
            if (p == null) {
                continue;
            }
            Relation rel = faction == null || relations == null ? Relation.NEUTRAL : relations.playerToFaction(p.getUuid(), faction);
            String color = (rel == null ? Relation.NEUTRAL : rel).color();
            Msg.send(p, Message.join(Msg.prefix(), Msg.colored("The faction ", Msg.TEXT), Msg.colored(before, color).bold(true),
                    Msg.colored(" is now ", Msg.TEXT), Msg.colored(after, color).bold(true), Msg.colored(".", Msg.TEXT)));
        }
    }

    /** The shared faction label, so the announcement reads like every other place a faction is named. */
    static String label(String tag, String name) {
        return Faction.label(tag, name);
    }

    /** The plain announcement as players read it. */
    static String line(String before, String after) {
        return "The faction " + before + " is now " + after + ".";
    }
}
