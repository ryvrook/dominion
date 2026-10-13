package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.chat.ChatServiceImpl;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.NationService;

import java.util.Locale;

/** Chat channels, announcements and personal toggles. */
final class SocialCommands extends Section {

    SocialCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd chat = root.sub(new PlayerCmd("chat", "Cycle your chat channel."));
        chat.addAliases("c");
        chat.handler(c -> {
            PlayerData.Channel ch = chat().cycleChannel(c.uuid());
            c.good("You are now talking in " + currentChannel(c.uuid(), ch) + ".");
        });
        PlayerCmd chatSet = new PlayerCmd("Pick a chat channel.");
        RequiredArg<String> channel = chatSet.withRequiredArg("channel", "public, faction, ally, officer or nation", ArgTypes.STRING);
        chatSet.handler(c -> {
            String typed = c.arg(channel).toLowerCase(Locale.ROOT);
            PlayerData.Channel ch = parseChannel(typed);
            if (ch == PlayerData.Channel.NATION && ctx.serviceOrNull(NationService.class) == null) {
                c.bad(NOT_INSTALLED);
                return;
            }
            if (ch == null) {
                c.bad(ctx.serviceOrNull(NationService.class) != null
                        ? "Channels are public, faction, ally, officer and nation."
                        : "Channels are public, faction, ally and officer.");
                return;
            }
            c.reply(chat().setChannel(c.uuid(), ch));
        });
        chat.variant(chatSet);

        PlayerCmd announce = root.sub(new PlayerCmd("announce", "Post an announcement to your faction."));
        RequiredArg<String> text = announce.withRequiredArg("text", "Announcement", ArgTypes.GREEDY_STRING);
        announce.handler(c -> c.reply(chat().announce(c.uuid(), c.arg(text).trim())));

        root.sub(new PlayerCmd("alerts", "Toggle faction alerts.")).handler(c -> {
            PlayerData pd = ctx.player(c.uuid());
            boolean on = !pd.alerts();
            pd.setAlerts(on);
            c.good(on ? "Faction alerts are on." : "Faction alerts are off.");
        });

        root.sub(new PlayerCmd("hud", "Toggle the faction sidebar.")).handler(c -> {
            PlayerData pd = ctx.player(c.uuid());
            boolean on = !pd.hud();
            pd.setHud(on);
            ui().setHudVisible(c.player(), on);
            c.good(on ? "The sidebar is visible." : "The sidebar is hidden.");
        });

        root.sub(new PlayerCmd("tutorial", "Open the factions tutorial.")).handler(c -> ui().openTutorial(c.player()));
    }

    /** The readable channel name, which includes nation chat when the chat module reports it. */
    private String currentChannel(java.util.UUID player, PlayerData.Channel fallback) {
        return chat() instanceof ChatServiceImpl impl ? impl.currentChannelName(player) : channelName(fallback);
    }

    static PlayerData.Channel parseChannel(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "p", "public", "global", "g" -> PlayerData.Channel.PUBLIC;
            case "f", "faction", "fac" -> PlayerData.Channel.FACTION;
            case "a", "ally", "allies" -> PlayerData.Channel.ALLY;
            case "o", "officer", "officers", "mod" -> PlayerData.Channel.OFFICER;
            case "n", "nation", "nations" -> PlayerData.Channel.NATION;
            default -> null;
        };
    }

    static String channelName(PlayerData.Channel ch) {
        return switch (ch) {
            case PUBLIC -> "public chat";
            case FACTION -> "faction chat";
            case ALLY -> "ally chat";
            case NATION -> "nation chat";
            case OFFICER -> "officer chat";
        };
    }
}
