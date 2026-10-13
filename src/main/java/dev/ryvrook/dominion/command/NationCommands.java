package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.NationService;
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
        PlayerCmd nation = root.sub(new PlayerCmd("nation", "Show your nation."));
        nation.aliases("n");
        nation.handler(this::showOwn);

        PlayerCmd info = nation.sub(new PlayerCmd("info", "Show your nation or another one."));
        info.handler(this::showOwn);
        PlayerCmd infoTag = new PlayerCmd("Show a nation by its tag.");
        RequiredArg<String> infoArg = infoTag.withRequiredArg("tag", "Nation tag", ArgTypes.STRING);
        infoTag.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns == null) {
                return;
            }
            Nation n = ns.byTag(c.arg(infoArg));
            if (n == null) {
                c.bad("No nation uses the tag " + c.arg(infoArg) + ".");
                return;
            }
            show(c, n);
        });
        info.variant(infoTag);

        PlayerCmd create = nation.sub(new PlayerCmd("create", "Found a nation led by your faction."));
        RequiredArg<String> createTag = create.withRequiredArg("tag", "Nation tag", ArgTypes.STRING);
        create.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(ns.create(c.uuid(), c.arg(createTag), null));
            }
        });
        PlayerCmd createNamed = new PlayerCmd("Found a nation with a display name.");
        RequiredArg<String> namedTag = createNamed.withRequiredArg("tag", "Nation tag", ArgTypes.STRING);
        RequiredArg<String> namedName = createNamed.withRequiredArg("name", "Display name", ArgTypes.GREEDY_STRING);
        createNamed.handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(ns.create(c.uuid(), c.arg(namedTag), c.arg(namedName).trim()));
            }
        });
        create.variant(createNamed);

        tagAction(nation, "invite", "Invite a faction into your nation.", "Faction tag", NationService::invite);
        tagAction(nation, "join", "Join a nation that invited your faction.", "Nation tag", NationService::join);
        tagAction(nation, "kick", "Remove a faction from your nation.", "Faction tag", NationService::kick);
        tagAction(nation, "leader", "Make another member faction the nation leader.", "Faction tag", NationService::setLeaderFaction);

        nation.sub(new PlayerCmd("leave", "Take your faction out of its nation.")).handler(c -> {
            NationService ns = feature(c, NationService.class);
            if (ns != null) {
                c.reply(ns.leave(c.uuid()));
            }
        });

        nation.sub(new PlayerCmd("disband", "Disband the nation your faction leads.")).handler(c -> {
            NationService ns = feature(c, NationService.class);
            Faction f = ns == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            Nation n = ns.nationOf(f);
            if (n == null) {
                c.bad("Your faction is not in a nation.");
                return;
            }
            UUID uuid = c.uuid();
            if (!f.id().equals(n.leaderFaction()) || !uuid.equals(f.leader())) {
                c.bad("Only the leader of the leading faction can disband the nation " + n.tag() + ".");
                return;
            }
            confirmations.put(uuid, "nation disband", () -> ns.disband(uuid, true));
            c.send(Msg.warn("This disbands the nation " + n.tag() + " and frees every member faction. Type /f confirm within 30 seconds to go ahead."));
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
            c.info("Your faction is not in a nation. Found one with /f nation create and a tag, or join one that invited you.");
            List<String> invites = new ArrayList<>();
            for (Nation other : ns.all()) {
                if (other.invites().contains(f.id())) {
                    invites.add(other.tag());
                }
            }
            if (!invites.isEmpty()) {
                c.send(Msg.muted(" Invited by " + String.join(", ", invites) + ". Use /f nation join and the tag."));
            }
            return;
        }
        show(c, n);
    }

    private void show(Call c, Nation n) {
        String color = n.color() != null && n.color().matches("#[0-9a-fA-F]{6}") ? n.color() : Msg.GOLD;
        c.send(Message.join(Msg.prefix(), Msg.colored(n.tag(), color).bold(true), Msg.colored("  " + n.name(), Msg.TEXT)));
        Faction leader = n.leaderFaction() == null ? null : factions().byId(n.leaderFaction());
        c.send(Msg.muted(" Led by " + (leader == null ? "nobody" : leader.tag()) + ", founded " + Time.date(n.createdAt())));
        List<String> members = new ArrayList<>();
        for (UUID id : n.members()) {
            Faction m = factions().byId(id);
            if (m != null) {
                members.add(m.tag());
            }
        }
        c.send(Message.join(Msg.muted(" Members "), Msg.colored(members.isEmpty() ? "none" : String.join(", ", members), Msg.TEXT)));
        List<String> invited = new ArrayList<>();
        for (UUID id : n.invites()) {
            Faction m = factions().byId(id);
            if (m != null) {
                invited.add(m.tag());
            }
        }
        if (!invited.isEmpty()) {
            c.send(Message.join(Msg.muted(" Invited "), Msg.colored(String.join(", ", invited), Msg.TEXT)));
        }
    }
}
