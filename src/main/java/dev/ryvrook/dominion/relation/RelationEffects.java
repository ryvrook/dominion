package dev.ryvrook.dominion.relation;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
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
        audit(a, actor, who, Messages.get(RelationText.AUDIT_SET, "faction", b.tag(), "relation", relation.displayName()));
        audit(b, actor, who, Messages.get(RelationText.AUDIT_SET_BY, "faction", a.tag(), "relation", relation.displayName()));
        send(a, Message.join(Msg.prefix(), Msg.colored(Messages.get(RelationText.NOW_OURS, "state", state, "faction", b.tag()), relation.color())));
        send(b, Message.join(Msg.prefix(), Msg.colored(Messages.get(RelationText.NOW_THEIRS, "faction", a.tag(), "state", state), relation.color())));
        ctx.bus().publish(new DominionEvent.RelationChanged(a.id(), b.id(), relation.name()));
    }

    void requested(Faction from, Faction to, Relation relation, UUID actor) {
        String who = ctx.player(actor).name();
        String request = withArticle(relation);
        audit(from, actor, who, Messages.get(RelationText.AUDIT_ASKED, "faction", to.tag(), "request", request));
        audit(to, actor, who, Messages.get(RelationText.AUDIT_ASKED_BY, "faction", from.tag(), "request", request));
        send(from, Msg.info(Messages.get(RelationText.ASKED_NOTICE, "faction", to.tag(), "request", request)));
        send(to, Msg.info(Messages.get(RelationText.WANTS_NOTICE, "faction", from.tag(), "request", request)));
    }

    void declined(Faction by, Faction requester, Relation relation, UUID actor) {
        String who = ctx.player(actor).name();
        String noun = noun(relation);
        audit(by, actor, who, Messages.get(RelationText.AUDIT_DECLINED, "noun", noun, "faction", requester.tag()));
        audit(requester, actor, who, Messages.get(RelationText.AUDIT_DECLINED_BY, "faction", by.tag(), "noun", noun));
        send(by, Msg.info(Messages.get(RelationText.DECLINED_NOTICE, "noun", noun, "faction", requester.tag())));
        send(requester, Msg.warn(Messages.get(RelationText.DECLINED_YOURS, "faction", by.tag(), "noun", noun)));
    }

    void expired(Faction to, Faction from, Relation relation) {
        String noun = noun(relation);
        String server = Messages.get(CommonText.SERVER);
        audit(to, null, server, Messages.get(RelationText.AUDIT_EXPIRED_FROM, "noun", noun, "faction", from.tag()));
        audit(from, null, server, Messages.get(RelationText.AUDIT_EXPIRED_TO, "noun", noun, "faction", to.tag()));
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
        return Messages.get(switch (relation) {
            case ALLY -> RelationText.STATE_ALLY;
            case TRUCE -> RelationText.STATE_TRUCE;
            case ENEMY -> RelationText.STATE_ENEMY;
            default -> RelationText.STATE_NEUTRAL;
        });
    }

    /** Requests are only ever for an alliance or a truce. */
    private static String withArticle(Relation relation) {
        return Messages.get(relation == Relation.ALLY ? RelationText.AN_ALLY : RelationText.A_TRUCE);
    }

    private static String noun(Relation relation) {
        return Messages.get(relation == Relation.ALLY ? RelationText.NOUN_ALLY : RelationText.NOUN_TRUCE);
    }
}
