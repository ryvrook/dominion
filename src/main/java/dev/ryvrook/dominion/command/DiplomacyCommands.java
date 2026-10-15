package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.Map;
import java.util.UUID;

/** Alliances, truces, enemies and pending relation requests. */
final class DiplomacyCommands extends Section {

    DiplomacyCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        relation(root, "ally", Messages.get(CommandText.DESC_ASK_A_FACTION_TO_BECOME_ALLIES), Relation.ALLY);
        relation(root, "truce", Messages.get(CommandText.DESC_ASK_A_FACTION_FOR_A_TRUCE), Relation.TRUCE);
        relation(root, "neutral", Messages.get(CommandText.DESC_SET_A_FACTION_TO_NEUTRAL), Relation.NEUTRAL);
        relation(root, "enemy", Messages.get(CommandText.DESC_DECLARE_A_FACTION_YOUR_ENEMY), Relation.ENEMY);

        PlayerCmd accept = root.sub(new PlayerCmd("accept", Messages.get(CommandText.DESC_ACCEPT_A_RELATION_REQUEST)));
        RequiredArg<String> acceptTag = accept.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        accept.handler(c -> c.reply(relations().accept(c.uuid(), c.arg(acceptTag))));

        PlayerCmd decline = root.sub(new PlayerCmd("decline", Messages.get(CommandText.DESC_DECLINE_A_RELATION_REQUEST)));
        RequiredArg<String> declineTag = decline.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        decline.handler(c -> c.reply(relations().decline(c.uuid(), c.arg(declineTag))));

        root.sub(new PlayerCmd("relations", Messages.get(CommandText.DESC_LIST_YOUR_ALLIES_TRUCES_AND_ENEMIES))).handler(this::list);
    }

    private void relation(PlayerCmd root, String name, String description, Relation relation) {
        PlayerCmd cmd = root.sub(new PlayerCmd(name, description));
        RequiredArg<String> tag = cmd.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        cmd.handler(c -> c.reply(relations().request(c.uuid(), c.arg(tag), relation)));
    }

    private void list(Call c) {
        Faction f = requireFaction(c);
        if (f == null) {
            return;
        }
        Map<UUID, Relation> rel = f.relations();
        c.send(Msg.info(Messages.get(CommandText.RELATIONS_OF, "tag", f.tag())));
        if (rel.isEmpty()) {
            c.send(Msg.muted(" " + Messages.get(CommandText.EVERYONE_IS_NEUTRAL_TO_YOU)));
        }
        for (Relation r : new Relation[] {Relation.ALLY, Relation.TRUCE, Relation.ENEMY}) {
            StringBuilder tags = new StringBuilder();
            for (Map.Entry<UUID, Relation> e : rel.entrySet()) {
                if (e.getValue() != r) {
                    continue;
                }
                Faction other = factions().byId(e.getKey());
                if (other == null) {
                    continue;
                }
                if (!tags.isEmpty()) {
                    tags.append(", ");
                }
                tags.append(other.tag());
            }
            if (!tags.isEmpty()) {
                c.send(Message.join(Msg.colored(" " + r.displayName() + " ", r.color()).bold(true),
                        Msg.colored(tags.toString(), Msg.TEXT)));
            }
        }
        Map<UUID, Faction.RelationRequest> requests = f.relationRequests();
        long now = Time.now();
        for (Map.Entry<UUID, Faction.RelationRequest> e : requests.entrySet()) {
            Faction.RelationRequest req = e.getValue();
            if (req.expiresAt() < now) {
                continue;
            }
            Faction other = factions().byId(e.getKey());
            if (other == null) {
                continue;
            }
            boolean incoming = req.from() == null || !(f.id().equals(req.from()) || f.isMember(req.from()));
            String text = incoming
                    ? Messages.get(CommandText.RELATION_REQUEST_INCOMING, "tag", other.tag(), "relation", req.type().displayName().toLowerCase())
                    : Messages.get(CommandText.RELATION_REQUEST_OUTGOING, "tag", other.tag(), "relation", req.type().displayName().toLowerCase(), "time", Time.remaining(req.expiresAt() - now));
            c.send(Msg.colored(" " + text, incoming ? Msg.WARN : Msg.MUTED));
        }
    }
}
