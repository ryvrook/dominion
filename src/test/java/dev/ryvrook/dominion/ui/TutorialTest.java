package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TutorialTest {
    @TempDir
    Path dir;

    private static final List<String> ALL = List.of("found", "claim", "map", "home", "invite", "relations", "raids", "bank", "chat", "commands");

    @Test
    void stepsKeepThePageOrderWhateverOrderTheSettingNames() {
        assertEquals(ALL, TutorialPage.STEPS);
        assertEquals(List.of("found", "home", "commands"), TutorialPage.configured("commands, HOME ,found"));
        assertEquals(List.of("claim"), TutorialPage.configured("claim,claim"));
    }

    @Test
    void anEmptyOrUnknownListMeansEveryStep() {
        assertEquals(ALL, TutorialPage.configured(""));
        assertEquals(ALL, TutorialPage.configured(null));
        assertEquals(ALL, TutorialPage.configured("siege, , nothing"));
        assertEquals(List.of("map"), TutorialPage.configured("siege,map"));
    }

    @Test
    void stepsWhoseFeatureIsOffAreLeftOut() {
        Set<String> off = Set.of("raids", "bank");
        List<String> steps = TutorialPage.visible("", id -> !off.contains(id));
        assertEquals(8, steps.size());
        assertFalse(steps.contains("raids"));
        assertFalse(steps.contains("bank"));
        assertEquals(List.of("found", "claim"), TutorialPage.visible("found,claim,raids", id -> !off.contains(id)));
        assertEquals(List.of("commands"), TutorialPage.visible("raids,bank", id -> !off.contains(id)));
    }

    @Test
    void aTrimmedStepLandsOnTheNextOneLeft() {
        List<String> steps = List.of("found", "claim", "invite", "commands");
        assertEquals(2, TutorialPage.indexFor(steps, "invite"));
        assertEquals(2, TutorialPage.indexFor(steps, "map"));
        assertEquals(3, TutorialPage.indexFor(steps, "raids"));
        assertEquals(0, TutorialPage.indexFor(steps, "unknown"));
        assertEquals(0, TutorialPage.indexFor(steps, null));
        assertEquals(1, TutorialPage.indexFor(List.of("found", "claim"), "chat"));
    }

    @Test
    void thePageOpensAtTheStepReachedOnlyWhenResumingAnUnfinishedTutorial() {
        assertEquals(3, TutorialPage.startIndex(ALL, "home", false, true));
        assertEquals(0, TutorialPage.startIndex(ALL, "home", false, false));
        assertEquals(0, TutorialPage.startIndex(ALL, "home", true, true));
        assertEquals(0, TutorialPage.startIndex(ALL, null, false, true));
        assertEquals(2, TutorialPage.startIndex(List.of("found", "claim", "invite"), "map", false, true));
    }

    @Test
    void goingBackNeverLowersTheStepReached() {
        assertEquals("home", TutorialPage.furthest("home", "claim"));
        assertEquals("chat", TutorialPage.furthest("home", "chat"));
        assertEquals("found", TutorialPage.furthest(null, "found"));
        assertEquals("claim", TutorialPage.furthest("claim", null));
        assertEquals("claim", TutorialPage.furthest("old", "claim"));
    }

    @Test
    void progressIsSavedAndAResetStartsOver() {
        PlayerData p = new PlayerData(UUID.randomUUID(), "Ann", 10, 0);
        assertFalse(p.tutorialFinished());
        assertNull(p.tutorialStep());
        p.setTutorialStep("home");
        PlayerData loaded = PlayerData.fromDocument(p.uuid(), p.toDocument());
        assertEquals("home", loaded.tutorialStep());
        assertFalse(loaded.tutorialFinished());

        loaded.setTutorialFinished(true);
        PlayerData again = PlayerData.fromDocument(p.uuid(), loaded.toDocument());
        assertTrue(again.tutorialFinished());
        assertEquals("home", again.tutorialStep());

        again.resetTutorial();
        PlayerData reset = PlayerData.fromDocument(p.uuid(), again.toDocument());
        assertFalse(reset.tutorialFinished());
        assertNull(reset.tutorialStep());
    }

    @Test
    void olderFilesLoadAndTheOldTutorialCountsAsFinished() {
        UUID id = UUID.randomUUID();
        Document seen = new Document("Uuid", id.toString()).append("Name", "Old").append("TutorialSeen", true);
        PlayerData oldSeen = PlayerData.fromDocument(id, seen);
        assertTrue(oldSeen.tutorialFinished());
        assertNull(oldSeen.tutorialStep());

        PlayerData never = PlayerData.fromDocument(id, new Document("Uuid", id.toString()).append("Name", "New"));
        assertFalse(never.tutorialFinished());
        assertNull(never.tutorialStep());

        Document blank = new Document("Uuid", id.toString()).append("TutorialStep", " ").append("TutorialFinished", "yes");
        PlayerData odd = PlayerData.fromDocument(id, blank);
        assertNull(odd.tutorialStep());
        assertFalse(odd.tutorialFinished());
    }

    @Test
    void theJoinOpenerFiresUntilTheTutorialIsFinished() {
        PlayerData fresh = new PlayerData(UUID.randomUUID(), "Ann", 10, 0);
        assertTrue(TutorialOnJoin.shouldOpen(fresh));
        fresh.setTutorialStep("claim");
        fresh.setFactionId(UUID.randomUUID());
        assertTrue(TutorialOnJoin.shouldOpen(fresh), "a player who started it keeps seeing it");
        fresh.setTutorialFinished(true);
        assertFalse(TutorialOnJoin.shouldOpen(fresh));

        PlayerData returning = new PlayerData(UUID.randomUUID(), "Bob", 10, 0);
        returning.setFactionId(UUID.randomUUID());
        assertFalse(TutorialOnJoin.shouldOpen(returning));
        assertTrue(returning.tutorialFinished());
    }

    @Test
    void theStepsSettingDefaultsToEveryStepAndRefusesUnknownIds() {
        Settings s = new Settings(new JsonFiles(TestContexts.LOGGER), dir.resolve("config.json"));
        assertEquals(ALL, TutorialPage.configured(s.text("misc.tutorialSteps")));
        assertEquals(String.join(",", ALL), s.text("misc.tutorialSteps"));
        assertNull(s.trySet("misc.tutorialSteps", "found, claim,commands"));
        assertEquals(List.of("found", "claim", "commands"), TutorialPage.configured(s.text("misc.tutorialSteps")));
        assertNotNull(s.trySet("misc.tutorialSteps", "found,siege"));
        assertNotNull(s.trySet("misc.tutorialSteps", ""));
        assertTrue(s.bool("misc.tutorial"));
    }

    @Test
    void theMarkupIsBalancedAndHasEveryElementThePageSets() throws Exception {
        String page = resource("Common/UI/Custom/Pages/Dominion_Tutorial.ui");
        balanced(page);
        for (String id : new String[] {"#PageTitle ", "#Heading ", "#Dots ", "#Counter ", "#Body ", "#Reply ", "#Action ",
                "#Skip ", "#Back ", "#Next "}) {
            assertTrue(page.contains(id), id);
        }
        for (int i = 0; i < TutorialPage.DOTS; i++) {
            assertTrue(page.contains("#D" + i + " "), "dot " + i);
        }
        assertTrue(TutorialPage.STEPS.size() <= TutorialPage.DOTS);
        String code = resource("Common/UI/Custom/Pages/Dominion_Tutorial_Code.ui");
        balanced(code);
        assertTrue(code.contains("#Command ") && code.contains("#Desc "));
    }

    private static void balanced(String m) {
        assertEquals(m.chars().filter(c -> c == '{').count(), m.chars().filter(c -> c == '}').count());
        assertEquals(m.chars().filter(c -> c == '(').count(), m.chars().filter(c -> c == ')').count());
        assertEquals(0, m.chars().filter(c -> c == '"').count() % 2);
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = TutorialTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
