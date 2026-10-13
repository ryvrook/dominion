package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;

/** Tag, name, description, message of the day, colour and open state. */
final class IdentityCommands extends Section {

    IdentityCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd tag = root.sub(new PlayerCmd("tag", "Change your faction tag."));
        RequiredArg<String> tagArg = tag.withRequiredArg("tag", "New tag", ArgTypes.STRING);
        // A null name keeps the current display name.
        tag.handler(c -> c.reply(factions().rename(c.uuid(), c.arg(tagArg), null)));

        PlayerCmd name = root.sub(new PlayerCmd("name", "Change your faction display name."));
        RequiredArg<String> nameArg = name.withRequiredArg("name", "New display name", ArgTypes.GREEDY_STRING);
        // A null tag keeps the current tag.
        name.handler(c -> c.reply(factions().rename(c.uuid(), null, c.arg(nameArg).trim())));

        PlayerCmd desc = root.sub(new PlayerCmd("desc", "Set your faction description."));
        RequiredArg<String> descArg = desc.withRequiredArg("text", "Description", ArgTypes.GREEDY_STRING);
        desc.handler(c -> c.reply(factions().setDescription(c.uuid(), c.arg(descArg).trim())));

        PlayerCmd motd = root.sub(new PlayerCmd("motd", "Set the message members see when they log in."));
        RequiredArg<String> motdArg = motd.withRequiredArg("text", "Message of the day", ArgTypes.GREEDY_STRING);
        motd.handler(c -> c.reply(factions().setMotd(c.uuid(), c.arg(motdArg).trim())));

        PlayerCmd color = root.sub(new PlayerCmd("color", "Set your faction colour as a hex code."));
        color.addAliases("colour");
        RequiredArg<String> colorArg = color.withRequiredArg("hex", "Colour such as #ff8800", ArgTypes.STRING);
        color.handler(c -> {
            String hex = c.arg(colorArg).trim();
            if (!hex.startsWith("#")) {
                hex = "#" + hex;
            }
            if (!hex.matches("#[0-9a-fA-F]{6}")) {
                c.bad("Use a six digit hex colour such as #ff8800.");
                return;
            }
            c.reply(factions().setColor(c.uuid(), hex.toLowerCase()));
        });

        root.sub(new PlayerCmd("open", "Let anyone join without an invite."))
                .handler(c -> c.reply(factions().setOpen(c.uuid(), true)));
        root.sub(new PlayerCmd("close", "Require an invite to join."))
                .handler(c -> c.reply(factions().setOpen(c.uuid(), false)));

        root.sub(new PlayerCmd("perms", "Edit rank and relation permissions.")).handler(c -> {
            if (requireFaction(c) != null) {
                ui().openPermissions(c.player());
            }
        });
        root.sub(new PlayerCmd("settings", "Open your faction settings.")).handler(c -> {
            if (requireFaction(c) != null) {
                ui().openPanel(c.player(), "settings");
            }
        });
    }
}
