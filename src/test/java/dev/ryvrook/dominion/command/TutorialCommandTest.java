package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.ryvrook.dominion.config.Perms;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TutorialCommandTest {
    @Test
    void tutorialOpensForEveryoneAndResetNeedsTheAdminNodeAndAPlayer() {
        PlayerCmd root = new PlayerCmd("f", "Root.");
        PlayerCmd tutorial = root.sub(SocialCommands.tutorialCommand(c -> { }, (c, name) -> { }));
        root.seal();
        assertSame(tutorial, root.getSubCommands().get("tutorial"));
        assertNull(tutorial.getPermission());
        AbstractCommand reset = tutorial.getSubCommands().get("reset");
        assertNotNull(reset);
        assertEquals(Perms.admin(), reset.getPermission());
        assertEquals(1, reset.getRequiredArguments().size());
        assertEquals("player", reset.getRequiredArguments().get(0).getName());
    }
}
