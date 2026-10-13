package dev.ryvrook.dominion.service;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.function.Consumer;

/** Opens pages and refreshes the HUD. Implemented by the ui module. */
public interface UiService {
    void openPanel(PlayerRef player, String tab);

    void openPermissions(PlayerRef player);

    void openFactionInfo(PlayerRef player, String tag);

    void openTop(PlayerRef player);

    void openTutorial(PlayerRef player);

    void openAdminSettings(PlayerRef player, String group);

    /** Shows a confirmation page and runs the action if confirmed. */
    void confirm(PlayerRef player, String title, String consequence, Consumer<Boolean> onDecision);

    void refreshHud(PlayerRef player);

    void setHudVisible(PlayerRef player, boolean visible);

    /** Territory banner shown on chunk owner change. */
    void territoryBanner(PlayerRef player, String title, String subtitle, String color);
}
