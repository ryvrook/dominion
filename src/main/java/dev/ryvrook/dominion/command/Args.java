package dev.ryvrook.dominion.command;

import java.util.Locale;

/**
 * Text helpers for command arguments. The engine tokenizer splits on spaces, keeps the quote
 * characters of a quoted word in the value and treats a word that starts with an apostrophe as
 * the start of a quote. These helpers turn what arrives into what the player meant.
 */
public final class Args {
    private Args() {}

    /**
     * Removes one pair of matching quotes around the whole value and the escapes the engine leaves
     * inside it. Text without surrounding quotes is returned trimmed, apostrophes inside words such
     * as O'Neil stay as typed.
     */
    public static String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' || first == '\'') && last == first) {
                s = s.substring(1, s.length() - 1).replace("\\" + first, String.valueOf(first)).strip();
            }
        }
        return s;
    }

    /** Parses a whole number, allowing a leading minus or plus. Null when the text is not one. */
    public static Integer integer(String raw) {
        String s = unquote(raw);
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.replace("_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a money amount such as 1500, 1,500, 1_500 or 2.5k. Returns NaN when the text is not a
     * finite number.
     */
    public static double amount(String raw) {
        String s = unquote(raw);
        if (s == null || s.isEmpty()) {
            return Double.NaN;
        }
        s = s.toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        double scale = 1;
        if (s.endsWith("k")) {
            scale = 1_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            scale = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        if (!s.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")) {
            return Double.NaN;
        }
        double d = Double.parseDouble(s) * scale;
        return Double.isFinite(d) ? d : Double.NaN;
    }
}
