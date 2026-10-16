package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The /f nation tree. Every handler resolves the nation service at use time. */
final class NationCommands extends Section {

    NationCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd nation = root.sub(new PlayerCmd("nation", Messages.get(CommandText.DESC_SHOW_YOUR_NATION)));
        nation.aliases("n");
        nation.handler(this::showOwn);

        PlayerCmd info = nation.sub(new PlayerCmd("info", Messages.get(CommandText.DESC_SHOW_YOUR_NATION_OR_ANOTHER_ONE)));
        info.handler(this::showOwn);
        PlayerCmd infoTag = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_A_NATION_BY_ITS_TAG));
        RequiredArg<String> infoArg = infoTag.withRequiredArg("tag", Messages.get(CommandText.ARG_NATION_TAG), ArgTypes.STRING);
        infoTag.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns == null) {
                return;
            }
            Nation n = ns.byTag(c.arg(infoArg));
            if (n == null) {
                c.bad(Messages.get(CommandText.NO_NATION_WITH_TAG, "tag", c.arg(infoArg)));
                return;
            }
            show(c, n);
        });
        info.variant(infoTag);

        PlayerCmd create = nation.sub(new PlayerCmd("create", Messages.get(CommandText.DESC_FOUND_A_NATION_LED_BY_YOUR)));
        RequiredArg<String> createTag = create.withRequiredArg("tag", Messages.get(CommandText.ARG_NATION_TAG), ArgTypes.STRING);
        create.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(ns.create(c.uuid(), c.arg(createTag), null));
            }
        });
        PlayerCmd createNamed = new PlayerCmd(Messages.get(CommandText.DESC_FOUND_A_NATION_WITH_A_DISPLAY));
        RequiredArg<String> namedTag = createNamed.withRequiredArg("tag", Messages.get(CommandText.ARG_NATION_TAG), ArgTypes.STRING);
        RequiredArg<String> namedName = createNamed.withRequiredArg("name", Messages.get(CommandText.ARG_DISPLAY_NAME), ArgTypes.GREEDY_STRING);
        createNamed.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(ns.create(c.uuid(), c.arg(namedTag), c.arg(namedName).trim()));
            }
        });
        create.variant(createNamed);

        tagAction(nation, "invite", Messages.get(CommandText.DESC_INVITE_A_FACTION_INTO_YOUR_NATION), Messages.get(CommandText.ARG_FACTION_TAG), NationService::invite);
        tagAction(nation, "join", Messages.get(CommandText.DESC_JOIN_A_NATION_THAT_INVITED_YOUR), Messages.get(CommandText.ARG_NATION_TAG), NationService::join);
        tagAction(nation, "kick", Messages.get(CommandText.DESC_REMOVE_A_FACTION_FROM_YOUR_NATION), Messages.get(CommandText.ARG_FACTION_TAG), NationService::kick);
        tagAction(nation, "leader", Messages.get(CommandText.DESC_MAKE_ANOTHER_MEMBER_FACTION_THE_NATION), Messages.get(CommandText.ARG_FACTION_TAG), NationService::setLeaderFaction);

        nation.sub(new PlayerCmd("leave", Messages.get(CommandText.DESC_TAKE_YOUR_FACTION_OUT_OF_ITS))).handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(ns.leave(c.uuid()));
            }
        });

        nation.sub(new PlayerCmd("disband", Messages.get(CommandText.DESC_DISBAND_THE_NATION_YOUR_FACTION_LEADS))).handler(c -> {
            NationService ns = feature(c, NationService.class);
            Faction f = ns == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            Nation n = ns.nationOf(f);
            if (n == null) {
                c.bad(Messages.get(CommandText.YOUR_FACTION_IS_NOT_IN_A));
                return;
            }
            UUID uuid = c.uuid();
            if (!f.id().equals(n.leaderFaction()) || !uuid.equals(f.leader())) {
                c.bad(Messages.get(CommandText.NATION_DISBAND_ONLY_LEADER, "tag", n.tag()));
                return;
            }
            confirmations.put(uuid, "nation disband", () -> ns.disband(uuid, true));
            c.send(Msg.warn(Messages.get(CommandText.NATION_DISBAND_CONFIRM, "tag", n.tag())));
        });
    }

    private void tagAction(PlayerCmd parent, String name, String description, String argDescription,
                           TagCall action) {
        PlayerCmd cmd = parent.sub(new PlayerCmd(name, description));
        RequiredArg<String> tag = cmd.withRequiredArg("tag", argDescription, ArgTypes.STRING);
        cmd.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(action.apply(ns, c.uuid(), c.arg(tag)));
            }
        });
    }

    private interface TagCall {
        dev.ryvrook.dominion.api.Result apply(NationService ns, UUID actor, String tag);
    }

    private void showOwn(Call c) {
        NationService ns = feature(c, NationService.class);
        Faction f = ns == null ? null : requireFaction(c);
        if (f == null) {
            return;
        }
        Nation n = ns.nationOf(f);
        if (n == null) {
            c.info(Messages.get(CommandText.YOUR_FACTION_IS_NOT_IN_A_NATION));
            List<String> invites = new ArrayList<>();
            for (Nation other : ns.all()) {
                if (other.invites().contains(f.id())) {
                    invites.add(other.tag());
                }
            }
            if (!invites.isEmpty()) {
                c.send(Msg.muted(" " + Messages.get(CommandText.NATION_INVITED_BY, "tags", String.join(", ", invites))));
            }
            return;
        }
        show(c, n);
    }

    private void show(Call c, Nation n) {
        String color = n.color() != null && n.color().matches("#[0-9a-fA-F]{6}") ? n.color() : Msg.GOLD;
        c.send(n.tagDiffers()
                ? Message.join(Msg.prefix(), Msg.colored(Faction.bracketed(n.tag()), color).bold(true), Msg.colored(" " + n.name(), Msg.TEXT))
                : Message.join(Msg.prefix(), Msg.colored(n.label(), color).bold(true)));
        Faction leader = n.leaderFaction() == null ? null : factions().byId(n.leaderFaction());
        c.send(Msg.muted(" " + Messages.get(CommandText.NATION_LED_BY, "leader", leader == null ? Messages.get(CommandText.NOBODY) : leader.tag(), "date", Time.date(n.createdAt()))));
        List<String> members = new ArrayList<>();
        for (UUID id : n.members()) {
            Faction m = factions().byId(id);
            if (m != null) {
                members.add(m.tag());
            }
        }
        c.send(Message.join(Msg.muted(" " + Messages.get(CommandText.NATION_MEMBERS) + " "), Msg.colored(members.isEmpty() ? Messages.get(CommandText.NONE) : String.join(", ", members), Msg.TEXT)));
        List<String> invited = new ArrayList<>();
        for (UUID id : n.invites()) {
            Faction m = factions().byId(id);
            if (m != null) {
                invited.add(m.tag());
            }
        }
        if (!invited.isEmpty()) {
            c.send(Message.join(Msg.muted(" " + Messages.get(CommandText.NATION_INVITED) + " "), Msg.colored(String.join(", ", invited), Msg.TEXT)));
        }
    }
}
