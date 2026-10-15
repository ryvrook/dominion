package dev.ryvrook.dominion.chat;

import dev.ryvrook.dominion.config.CommandNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VerbatimChatTest {
    @AfterEach
    void defaultNames() {
        CommandNames.registeredRoot(CommandNames.DEFAULT_ROOT, Set.of());
    }

    @Test
    void whatAPlayerTypesIsNotRenamedWithTheRoot() {
        CommandNames.registeredRoot("clan", Set.of());
        assertEquals("/clan home", CommandNames.localize("/f home"));
        assertEquals("type /f home to get back", ChatServiceImpl.verbatim("type /f home to get back", "#ffffff").getRawText());
        assertEquals("", ChatServiceImpl.verbatim(null, "#ffffff").getRawText());
    }
}
