package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.text.Msg;

import java.util.List;
import java.util.UUID;

/** Creating, joining, leaving and managing members. */
final class MembershipCommands extends Section {

    MembershipCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd create = root.sub(new PlayerCmd("create", "Found a new faction."));
        RequiredArg<String> createTag = create.withRequiredArg("tag", "Short faction tag", ArgTypes.STRING);
        create.handler(c -> c.reply(factions().create(c.uuid(), c.name(), c.arg(createTag), null)));
        PlayerCmd createNamed = new PlayerCmd("Found a new faction with a display name.");
        RequiredArg<String> namedTag = createNamed.withRequiredArg("tag", "Short faction tag", ArgTypes.STRING);
        RequiredArg<String> namedName = createNamed.withRequiredArg("name", "Display name", ArgTypes.STRING);
        createNamed.handler(c -> c.reply(factions().create(c.uuid(), c.name(), c.arg(namedTag), c.arg(namedName))));
        create.variant(createNamed);

        root.sub(new PlayerCmd("disband", "Disband your faction.")).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            UUID uuid = c.uuid();
            confirmations.put(uuid, "disband", () -> factions().disband(uuid, true));
            c.send(Msg.warn("This disbands " + f.tag() + " and releases all of its land. Type /f confirm within 30 seconds to go ahead."));
        });

        root.sub(new PlayerCmd("confirm", "Confirm a pending dangerous action."))
                .handler(c -> c.reply(confirmations.confirm(c.uuid())));

        PlayerCmd invite = root.sub(new PlayerCmd("invite", "Invite a player, or accept their join request."));
        RequiredArg<String> inviteWho = invite.withRequiredArg("player", "Player name", ArgTypes.STRING);
        invite.handler(c -> {
            Targets.Target t = target(c, c.arg(inviteWho));
            if (t == null) {
                return;
            }
            Faction f = factions().factionOf(c.uuid());
            if (f != null && f.requests().contains(t.uuid())) {
                c.reply(factions().acceptRequest(c.uuid(), t.uuid()));
                return;
            }
            c.reply(factions().invite(c.uuid(), t.uuid(), t.name()));
        });

        PlayerCmd uninvite = root.sub(new PlayerCmd("uninvite", "Withdraw an invite or decline a join request."));
        RequiredArg<String> uninviteWho = uninvite.withRequiredArg("player", "Player name", ArgTypes.STRING);
        uninvite.handler(c -> {
            Targets.Target t = target(c, c.arg(uninviteWho));
            if (t == null) {
                return;
            }
            Faction f = factions().factionOf(c.uuid());
            if (f != null && f.requests().contains(t.uuid())) {
                c.reply(factions().declineRequest(c.uuid(), t.uuid()));
                return;
            }
            c.reply(factions().uninvite(c.uuid(), t.uuid()));
        });

        PlayerCmd join = root.sub(new PlayerCmd("join", "Join a faction that invited you or is open."));
        RequiredArg<String> joinTag = join.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        join.handler(c -> c.reply(factions().join(c.uuid(), c.name(), c.arg(joinTag))));

        PlayerCmd request = root.sub(new PlayerCmd("request", "Ask to join a faction."));
        RequiredArg<String> requestTag = request.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        request.handler(c -> c.reply(factions().requestJoin(c.uuid(), c.name(), c.arg(requestTag))));

        root.sub(new PlayerCmd("requests", "List players asking to join.")).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            List<UUID> requests = f.requests();
            if (requests.isEmpty()) {
                c.info("Nobody is asking to join right now.");
                return;
            }
            c.send(Msg.info("Join requests for " + f.tag()));
            for (UUID uuid : requests) {
                c.send(Message.join(Msg.muted(" - "), Msg.colored(targets.nameOf(uuid), Msg.TEXT)));
            }
            c.send(Msg.muted("Use /f invite with a name to accept or /f uninvite to decline."));
        });

        root.sub(new PlayerCmd("leave", "Leave your faction.")).handler(c -> c.reply(factions().leave(c.uuid())));

        playerAction(root, "kick", "Remove a member from your faction.", (actor, t) -> factions().kick(actor, t.uuid()));
        playerAction(root, "ban", "Ban a player from your faction.", (actor, t) -> factions().ban(actor, t.uuid()));
        playerAction(root, "unban", "Lift a ban.", (actor, t) -> factions().unban(actor, t.uuid()));
        playerAction(root, "promote", "Raise a member one rank.", (actor, t) -> factions().promote(actor, t.uuid()));
        playerAction(root, "demote", "Lower a member one rank.", (actor, t) -> factions().demote(actor, t.uuid()));

        PlayerCmd rank = root.sub(new PlayerCmd("rank", "Set a member's rank directly."));
        RequiredArg<String> rankWho = rank.withRequiredArg("player", "Player name", ArgTypes.STRING);
        RequiredArg<String> rankName = rank.withRequiredArg("rank", "Recruit, member or officer, or your custom rank name", ArgTypes.STRING);
        rank.handler(c -> {
            Faction own = factions().factionOf(c.uuid());
            Rank r = ZoneCommands.parseRank(own, c.arg(rankName));
            if (r == null || r == Rank.LEADER) {
                String names = own == null ? "recruit, member and officer"
                        : own.rankName(Rank.RECRUIT) + ", " + own.rankName(Rank.MEMBER) + " and " + own.rankName(Rank.OFFICER);
                c.bad("Ranks are " + names + ". Use /f leader to hand over leadership.");
                return;
            }
            Targets.Target t = target(c, c.arg(rankWho));
            if (t != null) {
                c.reply(factions().setRank(c.uuid(), t.uuid(), r));
            }
        });

        PlayerCmd leader = root.sub(new PlayerCmd("leader", "Hand leadership to another member."));
        RequiredArg<String> leaderWho = leader.withRequiredArg("player", "Player name", ArgTypes.STRING);
        leader.handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Targets.Target t = target(c, c.arg(leaderWho));
            if (t == null) {
                return;
            }
            if (!f.isMember(t.uuid())) {
                c.bad(t.name() + " is not a member of " + f.tag() + ".");
                return;
            }
            UUID actor = c.uuid();
            confirmations.put(actor, "leader", () -> factions().transferLeader(actor, t.uuid(), true));
            c.send(Msg.warn("This makes " + t.name() + " the leader of " + f.tag() + ". Type /f confirm within 30 seconds to go ahead."));
        });

        PlayerCmd title = root.sub(new PlayerCmd("title", "Give a member a title."));
        RequiredArg<String> titleWho = title.withRequiredArg("player", "Player name", ArgTypes.STRING);
        RequiredArg<String> titleText = title.withRequiredArg("title", "Title text", ArgTypes.GREEDY_STRING);
        title.handler(c -> {
            Targets.Target t = target(c, c.arg(titleWho));
            if (t != null) {
                c.reply(factions().setTitle(c.uuid(), t.uuid(), c.arg(titleText).trim()));
            }
        });
    }

    private interface TargetAction {
        Result apply(UUID actor, Targets.Target target);
    }

    private void playerAction(PlayerCmd root, String name, String description, TargetAction action) {
        PlayerCmd cmd = root.sub(new PlayerCmd(name, description));
        RequiredArg<String> who = cmd.withRequiredArg("player", "Player name", ArgTypes.STRING);
        cmd.handler(c -> {
            Targets.Target t = target(c, c.arg(who));
            if (t != null) {
                c.reply(action.apply(c.uuid(), t));
            }
        });
    }
}
