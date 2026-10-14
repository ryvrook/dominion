package dev.ryvrook.dominion.shield;

/**
 * The pure arithmetic behind shield windows and grace, kept free of engine and settings types so
 * it can be tested. Minutes of the day run from 0 to 1439 in the server's local time.
 */
final class ShieldWindow {
    static final int DAY_MINUTES = 1440;

    private ShieldWindow() {}

    /**
     * Minutes left in the window that starts at startHour and lasts hours, seen at minuteOfDay, or
     * zero when the window is not running. A window that starts late in the day runs on past
     * midnight. A length of 24 hours or more covers the whole day.
     */
    static long minutesLeft(int startHour, int hours, int minuteOfDay) {
        if (startHour < 0 || startHour > 23 || hours <= 0) {
            return 0;
        }
        int length = Math.min(24, hours) * 60;
        int since = Math.floorMod(minuteOfDay - startHour * 60, DAY_MINUTES);
        return since < length ? length - since : 0;
    }

    /** True when a chunk dx and dz chunks away from the home chunk lies inside the shielded core. */
    static boolean covers(int coreRadius, int upgradeLevel, int dx, int dz) {
        int radius = Math.max(0, coreRadius) + Math.max(0, upgradeLevel);
        return Math.max(Math.abs(dx), Math.abs(dz)) <= radius;
    }

    /** Grace runs while the flag is on and the end time, when one is set, has not passed. */
    static boolean graceActive(boolean flag, long endsAt, long now) {
        return flag && (endsAt <= 0 || now < endsAt);
    }

    /** True when a pending window change asked for at pendingAt is due to apply. */
    static boolean pendingDue(long pendingAt, long delayMillis, long now) {
        return pendingAt > 0 && now >= pendingAt + Math.max(0, delayMillis);
    }
}
