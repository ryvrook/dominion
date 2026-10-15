package dev.ryvrook.dominion.util;

import dev.ryvrook.dominion.text.Messages;

/** Time words that reach players through countdowns, cooldowns and "ago" stamps. */
public final class UtilText {
    private UtilText() {}

    public static final String TIME_NOW = Messages.define("util.timeNow", "now");
    public static final String TIME_DAYS_HOURS = Messages.define("util.timeDaysHours", "{days}d {hours}h");
    public static final String TIME_HOURS_MINUTES = Messages.define("util.timeHoursMinutes", "{hours}h {minutes}m");
    public static final String TIME_MINUTES_SECONDS = Messages.define("util.timeMinutesSeconds", "{minutes}m {seconds}s");
    public static final String TIME_SECONDS = Messages.define("util.timeSeconds", "{seconds}s");
    public static final String TIME_AGO = Messages.define("util.timeAgo", "{time} ago");
}
