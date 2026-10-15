package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.text.Messages;

/** Tag, name, description, message of the day, colour and open state. */
final class IdentityCommands extends Section {

    IdentityCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd tag = root.sub(new PlayerCmd("tag", Messages.get(CommandText.DESC_CHANGE_YOUR_FACTION_TAG)));
        RequiredArg<String> tagArg = tag.withRequiredArg("tag", Messages.get(CommandText.ARG_NEW_TAG), ArgTypes.STRING);
        // A null name keeps the current display name.
        tag.handler(c -> c.reply(factions().rename(c.uuid(), c.arg(tagArg), null)));

        PlayerCmd name = root.sub(new PlayerCmd("name", Messages.get(CommandText.DESC_CHANGE_YOUR_FACTION_DISPLAY_NAME)));
        RequiredArg<String> nameArg = name.withRequiredArg("name", Messages.get(CommandText.ARG_NEW_DISPLAY_NAME), ArgTypes.GREEDY_STRING);
        // A null tag keeps the current tag.
        name.handler(c -> c.reply(factions().rename(c.uuid(), null, c.arg(nameArg).trim())));

        PlayerCmd desc = root.sub(new PlayerCmd("desc", Messages.get(CommandText.DESC_SET_YOUR_FACTION_DESCRIPTION)));
        RequiredArg<String> descArg = desc.withRequiredArg("text", Messages.get(CommandText.ARG_DESCRIPTION), ArgTypes.GREEDY_STRING);
        desc.handler(c -> c.reply(factions().setDescription(c.uuid(), c.arg(descArg).trim())));

        PlayerCmd motd = root.sub(new PlayerCmd("motd", Messages.get(CommandText.DESC_SET_THE_MESSAGE_MEMBERS_SEE_WHEN)));
        RequiredArg<String> motdArg = motd.withRequiredArg("text", Messages.get(CommandText.ARG_MESSAGE_OF_THE_DAY), ArgTypes.GREEDY_STRING);
        motd.handler(c -> c.reply(factions().setMotd(c.uuid(), c.arg(motdArg).trim())));

        PlayerCmd color = root.sub(new PlayerCmd("color", Messages.get(CommandText.DESC_SET_YOUR_FACTION_COLOUR_AS_A)));
        color.addAliases("colour");
        RequiredArg<String> colorArg = color.withRequiredArg("hex", Messages.get(CommandText.ARG_COLOUR_SUCH_AS_FF_8800), ArgTypes.STRING);
        color.handler(c -> {
            String hex = c.arg(colorArg).trim();
            if (!hex.startsWith("#")) {
                hex = "#" + hex;
            }
            if (!hex.matches("#[0-9a-fA-F]{6}")) {
                c.bad(Messages.get(CommandText.USE_A_SIX_DIGIT_HEX_COLOUR));
                return;
            }
            c.reply(factions().setColor(c.uuid(), hex.toLowerCase()));
        });

        root.sub(new PlayerCmd("open", Messages.get(CommandText.DESC_LET_ANYONE_JOIN_WITHOUT_AN_INVITE)))
                .handler(c -> c.reply(factions().setOpen(c.uuid(), true)));
        root.sub(new PlayerCmd("close", Messages.get(CommandText.DESC_REQUIRE_AN_INVITE_TO_JOIN)))
                .handler(c -> c.reply(factions().setOpen(c.uuid(), false)));

        root.sub(new PlayerCmd("perms", Messages.get(CommandText.DESC_EDIT_RANK_AND_RELATION_PERMISSIONS))).handler(c -> {
            if (requireFaction(c) != null) {
                ui().openPermissions(c.player());
            }
        });
        root.sub(new PlayerCmd("settings", Messages.get(CommandText.DESC_OPEN_YOUR_FACTION_SETTINGS))).handler(c -> {
            if (requireFaction(c) != null) {
                ui().openPanel(c.player(), "settings");
            }
        });
    }
}
