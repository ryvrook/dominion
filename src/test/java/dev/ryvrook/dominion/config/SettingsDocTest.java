package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * docs/SETTINGS.md and the key count in README.md are generated from Defaults. Run the tests with
 * DOMINION_WRITE_DOCS=1 to write them again after changing a key.
 */
class SettingsDocTest {
    private static final Path DOC = Path.of("docs/SETTINGS.md");
    private static final Path README = Path.of("README.md");
    private static final Pattern README_SENTENCE = Pattern.compile(
            "There are (\\S+) keys grouped into\\n(.*?)\\. Every key is listed", Pattern.DOTALL);

    @TempDir
    Path dir;

    private Settings defaults() {
        return new Settings(new JsonFiles(TestContexts.LOGGER), dir.resolve("config.json"));
    }

    private static boolean write() {
        return "1".equals(System.getenv("DOMINION_WRITE_DOCS"));
    }

    static String sectionList(Settings s) {
        List<String> names = new ArrayList<>(ConfigReport.sections(s).keySet());
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    @Test
    void settingsDocMatchesTheDefaults() throws Exception {
        String expected = ConfigReport.markdown(defaults());
        if (write()) {
            Files.writeString(DOC, expected);
        }
        assertTrue(Files.exists(DOC), "docs/SETTINGS.md is missing, run the tests with DOMINION_WRITE_DOCS=1");
        assertEquals(expected, Files.readString(DOC),
                "docs/SETTINGS.md is out of date with config/Defaults.java, run the tests with DOMINION_WRITE_DOCS=1");
    }

    @Test
    void readmeCountsEveryKeyAndSectionAndLinksTheDoc() throws Exception {
        Settings s = defaults();
        String readme = Files.readString(README);
        Matcher m = README_SENTENCE.matcher(readme);
        assertTrue(m.find(), "README.md lost the sentence that counts the settings");
        String count = String.valueOf(s.definitions().size());
        String sections = sectionList(s);
        if (write() && (!m.group(1).equals(count) || !m.group(2).replace("\n", " ").equals(sections))) {
            readme = readme.substring(0, m.start()) + "There are " + count + " keys grouped into\n" + sections
                    + ". Every key is listed" + readme.substring(m.end());
            Files.writeString(README, readme);
            m = README_SENTENCE.matcher(readme);
            assertTrue(m.find());
        }
        assertEquals(count, m.group(1), "README.md key count, run the tests with DOMINION_WRITE_DOCS=1");
        assertEquals(sections, m.group(2).replace("\n", " "), "README.md section list, run the tests with DOMINION_WRITE_DOCS=1");
        assertTrue(readme.contains("](docs/SETTINGS.md)"), "README.md must link docs/SETTINGS.md");
    }

    @Test
    void everyDefinedKeyIsInTheDoc() {
        Settings s = defaults();
        String doc = ConfigReport.markdown(s);
        for (Settings.Def d : s.definitions()) {
            assertTrue(doc.contains("| `" + d.key() + "` |"), d.key());
        }
    }
}
