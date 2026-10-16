package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.PlayerData;

/**
 * Opens the tutorial on join while misc.tutorial is on and the player has not finished or skipped it.
 * It opens at the furthest step reached, so closing it early brings it back where the player left off.
 * A player who already belongs to a faction and never opened the tutorial is a returning player and is
 * only marked as finished.
 */
final class TutorialOnJoin {
    private final DominionContext ctx;
    private final UiServiceImpl ui;

    TutorialOnJoin(DominionContext ctx, UiServiceImpl ui) {
        this.ctx = ctx;
        this.ui = ui;
    }

    void onPlayerReady(PlayerRef player) {
        if (!ctx.settings().bool("misc.tutorial")) {
            return;
        }
        PlayerData data = ctx.player(player.getUuid());
        if (!shouldOpen(data)) {
            return;
        }
        ui.resumeTutorial(player);
    }

    /** True when the opener shows the page. Marks a returning faction member as finished instead. */
    static boolean shouldOpen(PlayerData data) {
        if (data.tutorialFinished()) {
            return false;
        }
        if (data.tutorialStep() == null && data.factionId() != null) {
            data.setTutorialFinished(true);
            return false;
        }
        return true;
    }
}
