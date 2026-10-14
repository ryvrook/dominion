package dev.ryvrook.dominion.combat;

import java.util.UUID;

/**
 * One place that knows combat tags. Dominion's own tag and the CombatLog bridge both implement it,
 * and tests stand in for either with a small fake.
 */
interface CombatTagSource {
    boolean tagged(UUID player);

    /** Milliseconds left on the tag, zero when there is none. */
    long remainingMillis(UUID player);

    /** False when the source is not installed, so it is never asked. */
    default boolean present() {
        return true;
    }

    /** Version text for the log, or null when it is not known. */
    default String version() {
        return null;
    }
}
