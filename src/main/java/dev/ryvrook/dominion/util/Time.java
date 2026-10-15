package dev.ryvrook.dominion.util;

import dev.ryvrook.dominion.text.Messages;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Time {
    private Time() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    public static long now() {
        return System.currentTimeMillis();
    }

    public static String date(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /** Compact remaining-time text such as 2d 4h, 3h 12m, 45s. */
    public static String remaining(long millis) {
        if (millis <= 0) {
            return Messages.get(UtilText.TIME_NOW);
        }
        long s = millis / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) {
            return Messages.get(UtilText.TIME_DAYS_HOURS, "days", d, "hours", h);
        }
        if (h > 0) {
            return Messages.get(UtilText.TIME_HOURS_MINUTES, "hours", h, "minutes", m);
        }
        if (m > 0) {
            return Messages.get(UtilText.TIME_MINUTES_SECONDS, "minutes", m, "seconds", s % 60);
        }
        return Messages.get(UtilText.TIME_SECONDS, "seconds", s);
    }

    public static String ago(long epochMillis) {
        return Messages.get(UtilText.TIME_AGO, "time", remaining(now() - epochMillis));
    }

    /** Longest duration parse accepts, about 100 years, so later millisecond maths never overflows. */
    private static final long MAX_SECONDS = 100L * 365 * 86400;

    /**
     * Parses 7d, 12h, 30m, 90s or combinations such as 1d12h or 1d 12h. A plain number counts as
     * seconds. Returns null for anything else, including a unit without a number, a number without a
     * unit after another part such as 1d12, and lengths over about a century.
     */
    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.strip().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        if (t.chars().allMatch(Character::isDigit)) {
            return seconds(t, 1);
        }
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
                continue;
            }
            if (num.isEmpty()) {
                return null;
            }
            long unit = switch (c) {
                case 'd' -> 86400;
                case 'h' -> 3600;
                case 'm' -> 60;
                case 's' -> 1;
                default -> -1;
            };
            if (unit < 0 || num.length() > 12) {
                return null;
            }
            total += Long.parseLong(num.toString()) * unit;
            num.setLength(0);
            if (total > MAX_SECONDS) {
                return null;
            }
        }
        return num.isEmpty() ? Duration.ofSeconds(total) : null;
    }

    private static Duration seconds(String digits, long unit) {
        if (digits.length() > 12) {
            return null;
        }
        long s = Long.parseLong(digits) * unit;
        return s > MAX_SECONDS ? null : Duration.ofSeconds(s);
    }
}
