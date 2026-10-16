package dev.ryvrook.dominion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DependentsTest {
    private static final String SOCIAL = "ryvrook:socialbridge";
    private static final String REST = "ryvrook:restapi";
    private static final String DOMINION = "ryvrook:dominion";
    private static final String FEATS = "ryvrook:feats";

    @Test
    void pluginsThatDoNotReadEachOtherKeepTheLoadOrder() {
        Map<String, Set<String>> deps = Map.of(
                SOCIAL, Set.of(DOMINION, FEATS),
                REST, Set.of(DOMINION, FEATS));
        assertEquals(List.of(REST, SOCIAL), Dependents.order(List.of(REST, SOCIAL), deps));
    }

    @Test
    void aPluginComesBackAfterTheOneItReads() {
        Map<String, Set<String>> deps = Map.of("a:relay", Set.of(DOMINION, "a:web"), "a:web", Set.of(DOMINION));
        assertEquals(List.of("a:web", "a:relay"), Dependents.order(List.of("a:relay", "a:web"), deps));
    }

    @Test
    void dominionTakesTheSameLockAsTheOtherReloaders() {
        assertSame(Dependents.sharedLock(), System.getProperties().get("ryvrook.dependents.lock"));
    }
}
