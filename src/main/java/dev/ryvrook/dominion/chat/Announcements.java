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
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.util.ArrayList;
import java.util.List;
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
            Msg.send(p, Message.join(Msg.prefix(), coloured(before, after, color)));
        }
    }

    /** The shared faction label, so the announcement reads like every other place a faction is named. */
    static String label(String tag, String name) {
        return Faction.label(tag, name);
    }

    /** The plain announcement as players read it. */
    static String line(String before, String after) {
        return Messages.get(ChatText.RENAMED, "before", before, "after", after);
    }

    /** The announcement with the two labels in bold relation colour and the rest in text colour, in the order the wording puts them. */
    private static Message coloured(String before, String after, String color) {
        String text = Messages.raw(ChatText.RENAMED);
        List<Message> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int b = text.indexOf("{before}", i);
            int a = text.indexOf("{after}", i);
            int next = b < 0 ? a : a < 0 ? b : Math.min(a, b);
            if (next < 0) {
                parts.add(Msg.colored(text.substring(i), Msg.TEXT));
                break;
            }
            if (next > i) {
                parts.add(Msg.colored(text.substring(i, next), Msg.TEXT));
            }
            boolean isBefore = next == b;
            parts.add(Msg.colored(isBefore ? before : after, color).bold(true));
            i = next + (isBefore ? "{before}" : "{after}").length();
        }
        return Message.join(parts.toArray(new Message[0]));
    }
}
