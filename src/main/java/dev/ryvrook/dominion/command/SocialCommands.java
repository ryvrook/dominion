package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.chat.ChatServiceImpl;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.text.Messages;

import java.util.Locale;

/** Chat channels, announcements and personal toggles. */
final class SocialCommands extends Section {

    SocialCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd chat = root.sub(new PlayerCmd("chat", Messages.get(CommandText.DESC_CYCLE_YOUR_CHAT_CHANNEL)));
        chat.addAliases("c");
        chat.handler(c -> {
            PlayerData.Channel ch = chat().cycleChannel(c.uuid());
            c.good(Messages.get(CommandText.NOW_TALKING_IN, "channel", currentChannel(c.uuid(), ch)));
        });
        PlayerCmd chatSet = new PlayerCmd(Messages.get(CommandText.DESC_PICK_A_CHAT_CHANNEL));
        RequiredArg<String> channel = chatSet.withRequiredArg("channel", Messages.get(CommandText.ARG_PUBLIC_FACTION_ALLY_OFFICER_OR_NATION), ArgTypes.STRING);
        chatSet.handler(c -> {
            String typed = c.arg(channel).toLowerCase(Locale.ROOT);
            PlayerData.Channel ch = parseChannel(typed);
            if (ch == PlayerData.Channel.NATION && ctx.serviceOrNull(NationService.class) == null) {
                c.bad(Messages.get(NOT_INSTALLED));
                return;
            }
            if (ch == null) {
                c.bad(ctx.serviceOrNull(NationService.class) != null
                        ? Messages.get(CommandText.CHANNELS_ARE_PUBLIC_FACTION_ALLY_OFFICER)
                        : Messages.get(CommandText.CHANNELS_ARE_PUBLIC_FACTION_ALLY_AND));
                return;
            }
            c.reply(chat().setChannel(c.uuid(), ch));
        });
        chat.variant(chatSet);

        PlayerCmd announce = root.sub(new PlayerCmd("announce", Messages.get(CommandText.DESC_POST_AN_ANNOUNCEMENT_TO_YOUR_FACTION)));
        RequiredArg<String> text = announce.withRequiredArg("text", Messages.get(CommandText.ARG_ANNOUNCEMENT), ArgTypes.GREEDY_STRING);
        announce.handler(c -> c.reply(chat().announce(c.uuid(), c.arg(text).trim())));

        root.sub(new PlayerCmd("alerts", Messages.get(CommandText.DESC_TOGGLE_FACTION_ALERTS))).handler(c -> {
            PlayerData pd = ctx.player(c.uuid());
            boolean on = !pd.alerts();
            pd.setAlerts(on);
            c.good(on ? Messages.get(CommandText.FACTION_ALERTS_ARE_ON) : Messages.get(CommandText.FACTION_ALERTS_ARE_OFF));
        });

        root.sub(new PlayerCmd("hud", Messages.get(CommandText.DESC_TOGGLE_THE_FACTION_SIDEBAR))).handler(c -> {
            PlayerData pd = ctx.player(c.uuid());
            boolean on = !pd.hud();
            pd.setHud(on);
            ui().setHudVisible(c.player(), on);
            c.good(on ? Messages.get(CommandText.THE_SIDEBAR_IS_VISIBLE) : Messages.get(CommandText.THE_SIDEBAR_IS_HIDDEN));
        });

        root.sub(new PlayerCmd("tutorial", Messages.get(CommandText.DESC_OPEN_THE_FACTIONS_TUTORIAL))).handler(c -> ui().openTutorial(c.player()));
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
            case PUBLIC -> Messages.get(CommandText.PUBLIC_CHAT);
            case FACTION -> Messages.get(CommandText.FACTION_CHAT);
            case ALLY -> Messages.get(CommandText.ALLY_CHAT);
            case NATION -> Messages.get(CommandText.NATION_CHAT);
            case OFFICER -> Messages.get(CommandText.OFFICER_CHAT);
        };
    }
}
