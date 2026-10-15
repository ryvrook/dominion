package dev.ryvrook.dominion.admin;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCommandsTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);

    @AfterEach
    void forgetTheFile() throws Exception {
        Files.deleteIfExists(dir.resolve("messages.json"));
        Messages.reload();
    }

    @Test
    void searchFindsIdsAndWording() {
        // Loading the text classes registers their ids.
        assertNotNull(ConfigText.SECTIONS);
        assertNotNull(CommonText.FEATURE_OFF);
        List<String> byText = ConfigCommands.search("TURNED OFF on this server");
        assertTrue(byText.contains(CommonText.FEATURE_OFF));
        assertTrue(ConfigCommands.search("admin.messages.").size() >= 5);
        assertTrue(ConfigCommands.search("").isEmpty());
    }

    @Test
    void resetPutsTheDefaultBackAndKeepsEverythingElse() throws Exception {
        Path file = dir.resolve("messages.json");
        Messages.install(files, file, TestContexts.LOGGER);
        Document doc = files.parse(file);
        doc.put(CommonText.FEATURE_OFF, "Nope.");
        doc.put(CommonText.NO_PERMISSION, "Not for you.");
        doc.put("someone.elses.id", "Kept.");
        files.write(file, doc);
        Messages.reload();
        assertTrue(Messages.customised(CommonText.FEATURE_OFF));

        assertTrue(ConfigCommands.resetInFile(files, file, CommonText.FEATURE_OFF));
        assertFalse(Messages.customised(CommonText.FEATURE_OFF));
        assertEquals(Messages.defaultOf(CommonText.FEATURE_OFF), Messages.raw(CommonText.FEATURE_OFF));
        assertTrue(Messages.customised(CommonText.NO_PERMISSION));
        Document after = files.parse(file);
        assertEquals("Kept.", after.getString("someone.elses.id"));
        assertEquals("Not for you.", after.getString(CommonText.NO_PERMISSION));
    }

    @Test
    void resetOfAnUnknownIdFails() {
        assertFalse(ConfigCommands.resetInFile(files, dir.resolve("messages.json"), "no.such.id"));
    }

    @Test
    void keysReadAtStartAreRecognised() {
        Settings s = new Settings(files, dir.resolve("config.json"));
        assertTrue(ConfigCommands.appliesLater(s.definition("commands.root")));
        assertTrue(ConfigCommands.appliesLater(s.definition("integrations.enabled")));
        assertFalse(ConfigCommands.appliesLater(s.definition("faction.tagMax")));
    }
}
