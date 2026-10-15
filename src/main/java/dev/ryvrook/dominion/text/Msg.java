package dev.ryvrook.dominion.text;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.config.CommandNames;

/**
 * Message helpers so every module speaks with one voice. Plain sentences, no em dashes,
 * no semicolons, no colon reveals in player-facing text.
 */
public final class Msg {
    private Msg() {}

    // Not final on purpose. Palette refreshes them from the colors settings, and javac would inline a
    // final String constant into every caller, which would keep the old colour there.
    public static volatile String GOLD = Palette.DEFAULTS.get("colors.accent");
    public static volatile String MUTED = Palette.DEFAULTS.get("colors.muted");
    public static volatile String TEXT = Palette.DEFAULTS.get("colors.text");
    public static volatile String GOOD = Palette.DEFAULTS.get("colors.good");
    public static volatile String BAD = Palette.DEFAULTS.get("colors.bad");
    public static volatile String WARN = Palette.DEFAULTS.get("colors.warn");
    public static volatile String INFO = Palette.DEFAULTS.get("colors.info");

    // root command name comes from commands.root. Sentences keep /f, /fa, /fe and /fashop, and the helpers
    // below rewrite them to the names registered at start through CommandNames.localize.

    public static Message prefix() {
        return Message.raw("Dominion ").color(GOLD).bold(true);
    }

    public static Message info(String text) {
        return Message.join(prefix(), Message.raw(CommandNames.localize(text)).color(TEXT));
    }

    public static Message good(String text) {
        return Message.join(prefix(), Message.raw(CommandNames.localize(text)).color(GOOD));
    }

    public static Message bad(String text) {
        return Message.join(prefix(), Message.raw(CommandNames.localize(text)).color(BAD));
    }

    public static Message warn(String text) {
        return Message.join(prefix(), Message.raw(CommandNames.localize(text)).color(WARN));
    }

    public static Message muted(String text) {
        return Message.raw(CommandNames.localize(text)).color(MUTED);
    }

    public static Message colored(String text, String color) {
        return Message.raw(CommandNames.localize(text)).color(color);
    }

    public static void send(PlayerRef player, Message message) {
        if (player != null && message != null) {
            player.sendMessage(message);
        }
    }

    public static String number(long value) {
        return String.format("%,d", value);
    }

    public static String number(double value) {
        return value == Math.rint(value) ? String.format("%,d", (long) value) : String.format("%,.1f", value);
    }
}
