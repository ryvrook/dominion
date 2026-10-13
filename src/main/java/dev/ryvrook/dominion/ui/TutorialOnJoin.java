package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.PlayerData;

/**
 * Opens the tutorial once for a new player when misc.tutorial is on. The page is marked as seen
 * as soon as it opens, so closing it early does not bring it back on the next join. Players who
 * already belong to a faction are treated as returning players and are only marked as seen.
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
        if (data.tutorialSeen()) {
            return;
        }
        data.setTutorialSeen(true);
        if (data.factionId() != null) {
            return;
        }
        ui.openTutorial(player);
    }
}
