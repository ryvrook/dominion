package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.model.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTogglesTest {
    @Test
    void webMapHideAndShowFlipTheFlagAndConfirm() {
        PlayerData p = new PlayerData(UUID.randomUUID(), "Ann", 10, 0);
        p.clearDirty();
        String hide = PlayerToggles.webMap(p, true);
        assertTrue(p.mapHidden());
        assertTrue(p.isDirty(), "the change is saved with the next autosave");
        assertTrue(hide.startsWith("You are hidden from the web map"));
        String show = PlayerToggles.webMap(p, false);
        assertFalse(p.mapHidden());
        assertTrue(show.startsWith("You are visible on the web map"));
        for (String line : new String[] {hide, show}) {
            assertFalse(line.contains(";") || line.contains("—") || line.contains(":"), line);
        }
    }
}
