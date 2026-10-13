package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;
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
        relation(root, "ally", "Ask a faction to become allies.", Relation.ALLY);
        relation(root, "truce", "Ask a faction for a truce.", Relation.TRUCE);
        relation(root, "neutral", "Set a faction to neutral.", Relation.NEUTRAL);
        relation(root, "enemy", "Declare a faction your enemy.", Relation.ENEMY);

        PlayerCmd accept = root.sub(new PlayerCmd("accept", "Accept a relation request."));
        RequiredArg<String> acceptTag = accept.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        accept.handler(c -> c.reply(relations().accept(c.uuid(), c.arg(acceptTag))));

        PlayerCmd decline = root.sub(new PlayerCmd("decline", "Decline a relation request."));
        RequiredArg<String> declineTag = decline.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        decline.handler(c -> c.reply(relations().decline(c.uuid(), c.arg(declineTag))));

        root.sub(new PlayerCmd("relations", "List your allies, truces and enemies.")).handler(this::list);
    }

    private void relation(PlayerCmd root, String name, String description, Relation relation) {
        PlayerCmd cmd = root.sub(new PlayerCmd(name, description));
        RequiredArg<String> tag = cmd.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        cmd.handler(c -> c.reply(relations().request(c.uuid(), c.arg(tag), relation)));
    }

    private void list(Call c) {
        Faction f = requireFaction(c);
        if (f == null) {
            return;
        }
        Map<UUID, Relation> rel = f.relations();
        c.send(Msg.info("Relations of " + f.tag()));
        if (rel.isEmpty()) {
            c.send(Msg.muted(" Everyone is neutral to you."));
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
                    ? other.tag() + " wants " + req.type().displayName().toLowerCase() + ". Use /f accept " + other.tag()
                    : "You asked " + other.tag() + " for " + req.type().displayName().toLowerCase()
                    + ", expires in " + Time.remaining(req.expiresAt() - now);
            c.send(Msg.colored(" " + text, incoming ? Msg.WARN : Msg.MUTED));
        }
    }
}
