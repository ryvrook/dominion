package dev.ryvrook.dominion.text;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Message helpers so every module speaks with one voice. Plain sentences, no em dashes,
 * no semicolons, no colon reveals in player-facing text.
 */
public final class Msg {
    private Msg() {}

    public static final String GOLD = "#E8A93B";
    public static final String MUTED = "#96a9be";
    public static final String TEXT = "#d3d6db";
    public static final String GOOD = "#5fd37a";
    public static final String BAD = "#ff6b6b";
    public static final String WARN = "#ffb347";
    public static final String INFO = "#7aa7e6";

    public static Message prefix() {
        return Message.raw("Dominion ").color(GOLD).bold(true);
    }

    public static Message info(String text) {
        return Message.join(prefix(), Message.raw(text).color(TEXT));
    }

    public static Message good(String text) {
        return Message.join(prefix(), Message.raw(text).color(GOOD));
    }

    public static Message bad(String text) {
        return Message.join(prefix(), Message.raw(text).color(BAD));
    }

    public static Message warn(String text) {
        return Message.join(prefix(), Message.raw(text).color(WARN));
    }

    public static Message muted(String text) {
        return Message.raw(text).color(MUTED);
    }

    public static Message colored(String text, String color) {
        return Message.raw(text).color(color);
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
