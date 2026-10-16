package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.PlayerData;

/**
 * The one counter that has no bus event behind it. Finishing or skipping the tutorial is a change to the player's
 * own record, so the pages that set the flag call this, and it sends the credit through the same fanout the bus
 * events use.
 */
public final class TutorialCredit {
    private TutorialCredit() {}

    /** Marks the tutorial finished and credits the player once, the first time the flag turns true. */
    public static void finished(DominionContext ctx, PlayerData data) {
        if (data == null || !data.setTutorialFinished(true)) {
            return;
        }
        StatFanout stats = ctx == null ? null : ctx.serviceOrNull(StatFanout.class);
        if (stats != null) {
            stats.credit(data.uuid(), StatKeys.TUTORIAL_DONE, 1);
        }
    }
}
