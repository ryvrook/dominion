package dev.ryvrook.dominion.service;

import java.util.UUID;

/**
 * The one place that answers whether a player is combat tagged. The answer is the union of
 * Dominion's own tag and, when it is installed and allowed, the tag kept by the CombatLog mod.
 * Every combat rule in Dominion reads it here.
 */
public interface CombatTagService {
    /** True when either source has the player tagged. */
    boolean tagged(UUID player);

    /** Milliseconds until the longer of the two tags ends, zero when the player is not tagged. */
    long remainingMillis(UUID player);

    /** One sentence for the start log and /fa diag that names the tag source in use. */
    String status();
}
