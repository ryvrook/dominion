package dev.ryvrook.dominion.text;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks a package text class against the rules every text class follows. */
public final class TextClassCheck {
    private TextClassCheck() {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9]+)}");

    /**
     * Every public static String constant is a defined id that starts with the prefix, no two constants share
     * an id, and the placeholders of each default are exactly the names listed for it (none when not listed).
     */
    public static void check(Class<?> textClass, String prefix, Map<String, Set<String>> placeholders) throws Exception {
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (Field f : textClass.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m) || f.getType() != String.class) {
                continue;
            }
            String id = (String) f.get(null);
            count++;
            assertNotNull(id, f.getName());
            assertTrue(id.startsWith(prefix + "."), id + " starts with " + prefix);
            assertTrue(Messages.has(id), id + " is defined");
            assertTrue(seen.add(id), id + " is used by one constant only");
            String text = Messages.defaultOf(id);
            Set<String> found = new TreeSet<>();
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            assertEquals(new TreeSet<>(placeholders.getOrDefault(id, Set.of())), found, "placeholders of " + id);
        }
        assertTrue(count > 0, textClass.getSimpleName() + " defines sentences");
        for (String id : placeholders.keySet()) {
            assertTrue(seen.contains(id), "listed id " + id + " is a constant of " + textClass.getSimpleName());
        }
    }
}
