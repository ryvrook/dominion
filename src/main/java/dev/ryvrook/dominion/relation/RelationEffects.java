package dev.ryvrook.dominion.relation;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Msg;

import java.util.UUID;

/** Chat lines, audit entries and events that follow a relation change. */
final class RelationEffects {
    private final DominionContext ctx;

    RelationEffects(DominionContext ctx) {
        this.ctx = ctx;
    }

    /** Announces a settled relation to both factions. */
    void changed(Faction a, Faction b, Relation relation, UUID actor) {
        String who = ctx.player(actor).name();
        String state = phrase(relation);
        audit(a, actor, who, "Set " + b.tag() + " to " + relation.displayName());
        audit(b, actor, who, a.tag() + " set the relation to " + relation.displayName());
        send(a, Message.join(Msg.prefix(), Msg.colored("Your faction is now " + state + " " + b.tag(), relation.color())));
        send(b, Message.join(Msg.prefix(), Msg.colored(a.tag() + " is now " + state + " your faction", relation.color())));
        ctx.bus().publish(new DominionEvent.RelationChanged(a.id(), b.id(), relation.name()));
    }

    void requested(Faction from, Faction to, Relation relation, UUID actor) {
        String who = ctx.player(actor).name();
        String noun = relation.displayName().toLowerCase();
        audit(from, actor, who, "Asked " + to.tag() + " for " + article(relation) + " " + noun);
        audit(to, actor, who, from.tag() + " asked for " + article(relation) + " " + noun);
        send(from, Msg.info("Your faction asked " + to.tag() + " for " + article(relation) + " " + noun));
        send(to, Msg.info(from.tag() + " wants " + article(relation) + " " + noun + ". Use /f relation accept " + from.tag()));
    }

    void declined(Faction by, Faction requester, Relation relation, UUID actor) {
        String who = ctx.player(actor).name();
        String noun = relation.displayName().toLowerCase();
        audit(by, actor, who, "Declined the " + noun + " request from " + requester.tag());
        audit(requester, actor, who, by.tag() + " declined the " + noun + " request");
        send(by, Msg.info("Your faction declined the " + noun + " request from " + requester.tag()));
        send(requester, Msg.warn(by.tag() + " declined your " + noun + " request"));
    }

    void expired(Faction to, Faction from, Relation relation) {
        String noun = relation.displayName().toLowerCase();
        audit(to, null, "Server", "The " + noun + " request from " + from.tag() + " expired");
        audit(from, null, "Server", "The " + noun + " request to " + to.tag() + " expired");
    }

    private void audit(Faction f, UUID who, String whoName, String text) {
        ctx.service(FactionService.class).audit(f, who, whoName, "relation", text);
    }

    private void send(Faction f, Message message) {
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat != null) {
            chat.sendToFaction(f, message, false);
        }
    }

    private static String phrase(Relation relation) {
        return switch (relation) {
            case ALLY -> "allied with";
            case TRUCE -> "in a truce with";
            case ENEMY -> "an enemy of";
            default -> "neutral with";
        };
    }

    private static String article(Relation relation) {
        return relation == Relation.ALLY ? "an" : "a";
    }
}
