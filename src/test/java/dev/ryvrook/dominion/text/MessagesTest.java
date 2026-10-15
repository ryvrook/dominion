package dev.ryvrook.dominion.text;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {
    @TempDir Path dir;

    @AfterEach
    void reset() {
        Messages.resetForTests();
    }

    @Test
    void placeholdersAreFilledInPairs() {
        assertEquals("No player called Ann is known.", Messages.get(CommonText.NO_SUCH_PLAYER, "name", "Ann"));
        assertEquals("No player called  is known.", Messages.get(CommonText.NO_SUCH_PLAYER, "name", null));
        assertEquals("You are not in a faction.", Messages.get(CommonText.NOT_IN_FACTION));
        assertEquals("No player called {name} is known.", Messages.get(CommonText.NO_SUCH_PLAYER, "name", "{name}"),
                "a value that holds a placeholder is filled once");
        assertEquals("No player called Ann is known.", Messages.get(CommonText.NO_SUCH_PLAYER, "name", "Ann", "name", "Bo"),
                "the first value for a name wins");
        assertEquals("{name}", Messages.raw(CommonText.NO_SUCH_PLAYER).substring(17, 23));
    }

    @Test
    void anUnknownIdShowsItselfAndADoubleDefinitionIsRefused() {
        assertEquals("land.noSuchThing", Messages.get("land.noSuchThing"));
        assertFalse(Messages.has("land.noSuchThing"));
        assertThrows(IllegalStateException.class, () -> Messages.define(CommonText.NOT_IN_FACTION, "Something else."));
        assertEquals(CommonText.NOT_IN_FACTION, Messages.define(CommonText.NOT_IN_FACTION, "You are not in a faction."));
    }

    @Test
    void theFileIsWrittenWithEveryDefaultAndCustomWordingWins() throws Exception {
        JsonFiles files = new JsonFiles(TestContexts.LOGGER);
        Path file = dir.resolve("messages.json");
        Messages.LoadReport first = Messages.install(files, file, TestContexts.LOGGER);
        assertFalse(first.fileRead());
        assertTrue(Files.exists(file), "the defaults are written on the first start");
        Document doc = files.parse(file);
        assertEquals("You are not in a faction.", doc.getString(CommonText.NOT_IN_FACTION));
        assertTrue(doc.keySet().containsAll(Messages.ids()));

        doc.put(CommonText.NOT_IN_FACTION, "Join a faction first, {name}.");
        doc.put("future.id", "Kept for a later version.");
        doc.remove(CommonText.NO_SUCH_FACTION);
        doc.put("broken.id", 5);
        files.write(file, doc);

        Messages.LoadReport again = Messages.install(files, file, TestContexts.LOGGER);
        assertTrue(again.fileRead());
        assertEquals(1, again.customised());
        assertEquals(1, again.added(), "the removed id is added back");
        assertEquals(1, again.problems().size(), "a non text entry is reported");
        assertEquals("Join a faction first, Ann.", Messages.get(CommonText.NOT_IN_FACTION, "name", "Ann"));
        assertTrue(Messages.customised(CommonText.NOT_IN_FACTION));
        assertEquals("That faction does not exist.", Messages.get(CommonText.NO_SUCH_FACTION));

        Document written = files.parse(file);
        assertEquals("Kept for a later version.", written.getString("future.id"), "unknown ids survive the rewrite");
        assertEquals("That faction does not exist.", written.getString(CommonText.NO_SUCH_FACTION));
        assertEquals("Join a faction first, {name}.", written.getString(CommonText.NOT_IN_FACTION));
    }

    @Test
    void aBrokenFileKeepsTheCurrentWording() throws Exception {
        JsonFiles files = new JsonFiles(TestContexts.LOGGER);
        Path file = dir.resolve("messages.json");
        Messages.install(files, file, TestContexts.LOGGER);
        Files.writeString(file, "{ not json");
        Messages.LoadReport report = Messages.reload();
        assertFalse(report.fileRead());
        assertEquals(1, report.problems().size());
        assertEquals("You are not in a faction.", Messages.get(CommonText.NOT_IN_FACTION));
    }
}
