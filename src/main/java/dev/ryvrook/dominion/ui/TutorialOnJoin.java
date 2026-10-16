package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.integration.TutorialCredit;
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
        if (!shouldOpen(data, () -> TutorialCredit.finished(ctx, data))) {
            return;
        }
        ui.resumeTutorial(player);
    }

    /** True when the opener shows the page. Marks a returning faction member as finished instead. */
    static boolean shouldOpen(PlayerData data) {
        return shouldOpen(data, () -> data.setTutorialFinished(true));
    }

    /** As above, with the step that marks a returning member as finished, so the opener can credit it. */
    static boolean shouldOpen(PlayerData data, Runnable finish) {
        if (data.tutorialFinished()) {
            return false;
        }
        if (data.tutorialStep() == null && data.factionId() != null) {
            finish.run();
            return false;
        }
        return true;
    }
}
