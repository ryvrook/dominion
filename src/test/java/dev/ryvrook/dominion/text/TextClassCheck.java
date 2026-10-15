package dev.ryvrook.dominion.text;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared checks for a package text class. Every public constant is a defined id that starts with the package
 * name, no two constants share an id, the class is listed in MessageSets, the default follows the prose rules
 * and its placeholders are exactly the names the test lists for that id.
 */
public final class TextClassCheck {
    private TextClassCheck() {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9]+)}");

    /** Builds the expected placeholder map from pairs of an id and its space separated placeholder names. */
    public static Map<String, Set<String>> expect(String... pairs) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Set<String> names = new TreeSet<>();
            for (String n : pairs[i + 1].split(" ")) {
                if (!n.isBlank()) {
                    names.add(n);
                }
            }
            assertTrue(map.put(pairs[i], names) == null, "the test lists " + pairs[i] + " twice");
        }
        return map;
    }

    public static void check(Class<?> textClass, String prefix, Map<String, Set<String>> placeholders) throws Exception {
        assertTrue(Arrays.asList(MessageSets.ALL).contains(textClass.getName()), textClass.getName() + " is listed in MessageSets");
        Set<String> seen = new HashSet<>();
        for (Field f : textClass.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || f.getType() != String.class) {
                continue;
            }
            assertTrue(Modifier.isPublic(m) && Modifier.isFinal(m), f.getName() + " is public static final");
            String id = (String) f.get(null);
            assertTrue(Messages.has(id), id + " is defined");
            assertTrue(id.startsWith(prefix + "."), id + " starts with " + prefix);
            assertTrue(seen.add(id), id + " is used by two constants");
            String text = Messages.defaultOf(id);
            assertNotNull(text);
            assertFalse(text.contains(";") || text.indexOf((char) 0x2014) >= 0, id + " follows the prose rules");
            Set<String> found = new TreeSet<>();
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            Set<String> expected = placeholders.get(id);
            assertNotNull(expected, "the test lists the placeholders of " + id);
            assertEquals(expected, found, "placeholders of " + id);
        }
        assertEquals(new TreeSet<>(placeholders.keySet()), new TreeSet<>(seen), "the test lists exactly the ids of " + textClass.getSimpleName());
    }
}
