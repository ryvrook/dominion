package dev.ryvrook.dominion.util;

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
            return "now";
        }
        long s = millis / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m " + (s % 60) + "s";
        }
        return s + "s";
    }

    public static String ago(long epochMillis) {
        return remaining(now() - epochMillis) + " ago";
    }

    /** Parses 7d, 12h, 30m, 90s or combinations such as 1d12h. Returns null if invalid. */
    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : text.trim().toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
                continue;
            }
            if (num.isEmpty()) {
                return null;
            }
            long n = Long.parseLong(num.toString());
            num.setLength(0);
            switch (c) {
                case 'd' -> total += n * 86400;
                case 'h' -> total += n * 3600;
                case 'm' -> total += n * 60;
                case 's' -> total += n;
                default -> { return null; }
            }
        }
        if (!num.isEmpty()) {
            total += Long.parseLong(num.toString());
        }
        return Duration.ofSeconds(total);
    }
}
