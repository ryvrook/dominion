package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.text.Messages;
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
        PlayerCmd create = root.sub(new PlayerCmd("create", Messages.get(CommandText.DESC_FOUND_A_NEW_FACTION)));
        RequiredArg<String> createTag = create.withRequiredArg("tag", Messages.get(CommandText.ARG_SHORT_FACTION_TAG), ArgTypes.STRING);
        create.handler(c -> c.reply(factions().create(c.uuid(), c.name(), c.arg(createTag), null)));
        PlayerCmd createNamed = new PlayerCmd(Messages.get(CommandText.DESC_FOUND_A_NEW_FACTION_WITH_A));
        RequiredArg<String> namedTag = createNamed.withRequiredArg("tag", Messages.get(CommandText.ARG_SHORT_FACTION_TAG), ArgTypes.STRING);
        // Greedy like /f name and /f nation create, so a display name of several words needs no quotes.
        RequiredArg<String> namedName = createNamed.withRequiredArg("name", Messages.get(CommandText.ARG_DISPLAY_NAME), ArgTypes.GREEDY_STRING);
        createNamed.handler(c -> c.reply(factions().create(c.uuid(), c.name(), c.arg(namedTag), c.arg(namedName))));
        create.variant(createNamed);

        root.sub(new PlayerCmd("disband", Messages.get(CommandText.DESC_DISBAND_YOUR_FACTION))).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            UUID uuid = c.uuid();
            confirmations.put(uuid, "disband", () -> factions().disband(uuid, true));
            c.send(Msg.warn(Messages.get(CommandText.DISBAND_CONFIRM, "tag", f.tag())));
        });

        root.sub(new PlayerCmd("confirm", Messages.get(CommandText.DESC_CONFIRM_A_PENDING_DANGEROUS_ACTION)))
                .handler(c -> c.reply(confirmations.confirm(c.uuid())));

        PlayerCmd invite = root.sub(new PlayerCmd("invite", Messages.get(CommandText.DESC_INVITE_A_PLAYER_OR_ACCEPT_THEIR)));
        RequiredArg<String> inviteWho = invite.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
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

        PlayerCmd uninvite = root.sub(new PlayerCmd("uninvite", Messages.get(CommandText.DESC_WITHDRAW_AN_INVITE_OR_DECLINE_A)));
        RequiredArg<String> uninviteWho = uninvite.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
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

        PlayerCmd join = root.sub(new PlayerCmd("join", Messages.get(CommandText.DESC_JOIN_A_FACTION_THAT_INVITED_YOU)));
        RequiredArg<String> joinTag = join.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        join.handler(c -> c.reply(factions().join(c.uuid(), c.name(), c.arg(joinTag))));

        PlayerCmd request = root.sub(new PlayerCmd("request", Messages.get(CommandText.DESC_ASK_TO_JOIN_A_FACTION)));
        RequiredArg<String> requestTag = request.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        request.handler(c -> c.reply(factions().requestJoin(c.uuid(), c.name(), c.arg(requestTag))));

        root.sub(new PlayerCmd("requests", Messages.get(CommandText.DESC_LIST_PLAYERS_ASKING_TO_JOIN))).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            List<UUID> requests = f.requests();
            if (requests.isEmpty()) {
                c.info(Messages.get(CommandText.NOBODY_IS_ASKING_TO_JOIN_RIGHT));
                return;
            }
            c.send(Msg.info(Messages.get(CommandText.JOIN_REQUESTS_FOR, "tag", f.tag())));
            for (UUID uuid : requests) {
                c.send(Message.join(Msg.muted(" - "), Msg.colored(targets.nameOf(uuid), Msg.TEXT)));
            }
            c.send(Msg.muted(Messages.get(CommandText.USE_F_INVITE_WITH_A_NAME)));
        });

        root.sub(new PlayerCmd("leave", Messages.get(CommandText.DESC_LEAVE_YOUR_FACTION))).handler(c -> c.reply(factions().leave(c.uuid())));

        playerAction(root, "kick", Messages.get(CommandText.DESC_REMOVE_A_MEMBER_FROM_YOUR_FACTION), (actor, t) -> factions().kick(actor, t.uuid()));
        playerAction(root, "ban", Messages.get(CommandText.DESC_BAN_A_PLAYER_FROM_YOUR_FACTION), (actor, t) -> factions().ban(actor, t.uuid()));
        playerAction(root, "unban", Messages.get(CommandText.DESC_LIFT_A_BAN), (actor, t) -> factions().unban(actor, t.uuid()));
        playerAction(root, "promote", Messages.get(CommandText.DESC_RAISE_A_MEMBER_ONE_RANK), (actor, t) -> factions().promote(actor, t.uuid()));
        playerAction(root, "demote", Messages.get(CommandText.DESC_LOWER_A_MEMBER_ONE_RANK), (actor, t) -> factions().demote(actor, t.uuid()));

        PlayerCmd rank = root.sub(new PlayerCmd("rank", Messages.get(CommandText.DESC_SET_A_MEMBERS_RANK_DIRECTLY)));
        RequiredArg<String> rankWho = rank.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        RequiredArg<String> rankName = rank.withRequiredArg("rank", Messages.get(CommandText.ARG_RECRUIT_MEMBER_OR_OFFICER_OR_YOUR), ArgTypes.STRING);
        rank.handler(c -> {
            Faction own = factions().factionOf(c.uuid());
            Rank r = ZoneCommands.parseRank(own, c.arg(rankName));
            if (r == null || r == Rank.LEADER) {
                String names = own == null ? Messages.get(CommandText.RANK_LIST_DEFAULT)
                        : Messages.get(CommandText.RANK_LIST, "recruit", own.rankName(Rank.RECRUIT), "member", own.rankName(Rank.MEMBER), "officer", own.rankName(Rank.OFFICER));
                c.bad(Messages.get(CommandText.RANKS_ARE, "ranks", names));
                return;
            }
            Targets.Target t = target(c, c.arg(rankWho));
            if (t != null) {
                c.reply(factions().setRank(c.uuid(), t.uuid(), r));
            }
        });

        PlayerCmd leader = root.sub(new PlayerCmd("leader", Messages.get(CommandText.DESC_HAND_LEADERSHIP_TO_ANOTHER_MEMBER)));
        RequiredArg<String> leaderWho = leader.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
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
                c.bad(Messages.get(CommandText.NOT_MEMBER_OF, "name", t.name(), "tag", f.tag()));
                return;
            }
            UUID actor = c.uuid();
            confirmations.put(actor, "leader", () -> factions().transferLeader(actor, t.uuid(), true));
            c.send(Msg.warn(Messages.get(CommandText.LEADER_CONFIRM, "name", t.name(), "tag", f.tag())));
        });

        PlayerCmd title = root.sub(new PlayerCmd("title", Messages.get(CommandText.DESC_GIVE_A_MEMBER_A_TITLE)));
        RequiredArg<String> titleWho = title.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        RequiredArg<String> titleText = title.withRequiredArg("title", Messages.get(CommandText.ARG_TITLE_TEXT), ArgTypes.GREEDY_STRING);
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
        RequiredArg<String> who = cmd.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        cmd.handler(c -> {
            Targets.Target t = target(c, c.arg(who));
            if (t != null) {
                c.reply(action.apply(c.uuid(), t));
            }
        });
    }
}
