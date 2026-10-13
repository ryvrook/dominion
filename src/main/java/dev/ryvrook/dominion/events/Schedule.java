package dev.ryvrook.dominion.events;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A hill schedule. Either every n minutes, written as "every 30m" or "every 30", or at fixed
 * times of day in the server time zone, written as "at 18:00" or "at 18:00,21:30".
 */
record Schedule(String text, int everyMinutes, List<Integer> times) {

    /** Parses a schedule. Returns null when the text is not understood. */
    static Schedule parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (s.startsWith("every ")) {
            String n = s.substring(6).trim();
            if (n.endsWith("minutes")) {
                n = n.substring(0, n.length() - 7).trim();
            } else if (n.endsWith("m")) {
                n = n.substring(0, n.length() - 1).trim();
            }
            try {
                int minutes = Integer.parseInt(n);
                if (minutes < 5 || minutes > 7 * 24 * 60) {
                    return null;
                }
                return new Schedule("every " + minutes + "m", minutes, List.of());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (s.startsWith("at ")) {
            List<Integer> times = new ArrayList<>();
            for (String part : s.substring(3).split("[, ]+")) {
                if (part.isBlank()) {
                    continue;
                }
                String[] hm = part.split(":");
                if (hm.length != 2) {
                    return null;
                }
                try {
                    int h = Integer.parseInt(hm[0]);
                    int m = Integer.parseInt(hm[1]);
                    if (h < 0 || h > 23 || m < 0 || m > 59) {
                        return null;
                    }
                    int t = h * 60 + m;
                    if (!times.contains(t)) {
                        times.add(t);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (times.isEmpty()) {
                return null;
            }
            times.sort(Integer::compare);
            StringBuilder text = new StringBuilder("at ");
            for (int i = 0; i < times.size(); i++) {
                if (i > 0) {
                    text.append(',');
                }
                text.append(String.format("%02d:%02d", times.get(i) / 60, times.get(i) % 60));
            }
            return new Schedule(text.toString(), 0, List.copyOf(times));
        }
        return null;
    }

    /** The next start strictly after the given time, as epoch millis. */
    long next(long after) {
        if (everyMinutes > 0) {
            return after + everyMinutes * 60_000L;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime from = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(after), zone);
        LocalDate day = from.toLocalDate();
        for (int d = 0; d < 2; d++) {
            for (int t : times) {
                LocalDateTime at = LocalDateTime.of(day.plusDays(d), LocalTime.of(t / 60, t % 60));
                long millis = at.atZone(zone).toInstant().toEpochMilli();
                if (millis > after) {
                    return millis;
                }
            }
        }
        return after + 86_400_000L;
    }

    /** Plain words for the panel and status lines. */
    String describe() {
        if (everyMinutes > 0) {
            return "every " + everyMinutes + " minutes";
        }
        return "daily " + text;
    }
}
