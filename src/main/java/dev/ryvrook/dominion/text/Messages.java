package dev.ryvrook.dominion.text;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Every player facing sentence, keyed by a stable id and editable in messages.json next to config.json.
 *
 * <p>Each package declares its sentences in one text class as
 * {@code static final String CLAIMED = Messages.define("land.claimed", "Claimed chunk {chunk} in {world}.")}
 * and reads them with {@code Messages.get(LandText.CLAIMED, "chunk", where, "world", world)}. The text
 * classes are listed in {@link MessageSets} so every default is known at start, when the file is read,
 * missing ids are appended with their defaults and custom wording is kept. Unknown ids in the file are kept
 * too, so a server can prepare wording for a later version. {@code /fa reload} reads the file again.
 *
 * <p>Placeholders are {@code {name}} and are replaced in order of the pairs given to {@link #get}. A value
 * that is null prints as an empty string. An id that was never defined prints the id itself and logs once,
 * so a typo is visible in chat and in the log instead of hidden.
 *
 * <p>Static on purpose. Sentences are needed from services, commands, pages and static helpers that have no
 * context in hand, and the file is one per server. All reads are lock free, the custom map is swapped whole.
 */
public final class Messages {
    private Messages() {}

    private static final Map<String, String> DEFAULTS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, String> custom = Map.of();
    private static volatile JsonFiles files;
    private static volatile Path file;
    private static volatile HytaleLogger logger;

    /** What a load found. customised counts reworded ids that have a default, unknown ids are kept but not counted. */
    public record LoadReport(boolean fileRead, int customised, int added, List<String> problems) {}

    /** Registers a default sentence and returns its id, so a text class can keep the id in a constant. */
    public static String define(String id, String text) {
        if (id == null || id.isBlank() || text == null) {
            throw new IllegalArgumentException("A message needs an id and a default text");
        }
        String old = DEFAULTS.putIfAbsent(id, text);
        if (old != null && !old.equals(text)) {
            throw new IllegalStateException("Message " + id + " is defined twice with different text");
        }
        return id;
    }

    /** The sentence for an id with its placeholders filled from name and value pairs. */
    public static String get(String id, Object... pairs) {
        String text = raw(id);
        if (pairs == null || pairs.length == 0 || text.indexOf('{') < 0) {
            return text;
        }
        Map<String, String> values = new java.util.HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.putIfAbsent(String.valueOf(pairs[i]), pairs[i + 1] == null ? "" : String.valueOf(pairs[i + 1]));
        }
        // One pass, so a value that holds a placeholder itself, such as a faction named {name}, is never filled again.
        StringBuilder out = new StringBuilder(text.length() + 16);
        int at = 0;
        while (at < text.length()) {
            int open = text.indexOf('{', at);
            int close = open < 0 ? -1 : text.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            String value = values.get(text.substring(open + 1, close));
            if (value == null) {
                out.append(text, at, open + 1);
                at = open + 1;
            } else {
                out.append(text, at, open).append(value);
                at = close + 1;
            }
        }
        return out.append(text, at, text.length()).toString();
    }

    /** The sentence for an id with its placeholders left in place. */
    public static String raw(String id) {
        String c = custom.get(id);
        if (c != null) {
            return c;
        }
        String d = DEFAULTS.get(id);
        if (d != null) {
            return d;
        }
        if (WARNED.add(id)) {
            HytaleLogger log = logger;
            if (log != null) {
                log.at(Level.WARNING).log("Dominion has no message with the id %s, the id is shown instead", id);
            }
        }
        return id == null ? "" : id;
    }

    /** True when the id has a default. */
    public static boolean has(String id) {
        return id != null && DEFAULTS.containsKey(id);
    }

    /** Every defined id in definition order. */
    public static List<String> ids() {
        synchronized (DEFAULTS) {
            return new ArrayList<>(DEFAULTS.keySet());
        }
    }

    /** The default text of an id, or null. */
    public static String defaultOf(String id) {
        return id == null ? null : DEFAULTS.get(id);
    }

    /** True when the server changed the wording of this id. */
    public static boolean customised(String id) {
        return id != null && custom.containsKey(id);
    }

    /**
     * Loads every text class, reads messages.json and writes it back when ids are missing. Called once at
     * plugin setup. A file that cannot be read keeps the defaults and reports the problem.
     */
    public static LoadReport install(JsonFiles jsonFiles, Path messagesFile, HytaleLogger log) {
        files = jsonFiles;
        file = messagesFile;
        logger = log;
        MessageSets.load(log);
        LoadReport report = load();
        if (report.added() > 0 || !Files.exists(messagesFile)) {
            write();
        }
        return report;
    }

    /** Reads the file again. Keeps the current wording when the file cannot be read. */
    public static LoadReport reload() {
        if (file == null || files == null) {
            return new LoadReport(false, custom.size(), 0, List.of("Messages were never installed"));
        }
        return load();
    }

    private static LoadReport load() {
        List<String> problems = new ArrayList<>();
        Path path = file;
        if (path == null || !Files.exists(path)) {
            custom = Map.of();
            return new LoadReport(false, 0, DEFAULTS.size(), problems);
        }
        Document doc;
        try {
            doc = files.parse(path);
        } catch (Exception e) {
            problems.add("messages.json could not be read, " + e.getMessage() + ". The previous wording stays in use");
            return new LoadReport(false, custom.size(), 0, problems);
        }
        Map<String, String> next = new LinkedHashMap<>();
        int added = 0;
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if (e.getValue() instanceof String s) {
                String def = DEFAULTS.get(e.getKey());
                if (def == null || !def.equals(s)) {
                    next.put(e.getKey(), s);
                }
            } else {
                problems.add("messages.json entry " + e.getKey() + " is not text and was ignored");
            }
        }
        for (String id : ids()) {
            if (!doc.containsKey(id)) {
                added++;
            }
        }
        int customised = 0;
        for (String id : next.keySet()) {
            if (DEFAULTS.containsKey(id)) {
                customised++;
            }
        }
        custom = Map.copyOf(next);
        return new LoadReport(true, customised, added, problems);
    }

    /** Writes every default merged with the custom wording, sorted by id, keeping unknown ids. */
    public static boolean write() {
        if (file == null || files == null) {
            return false;
        }
        Map<String, String> merged = new TreeMap<>();
        synchronized (DEFAULTS) {
            merged.putAll(DEFAULTS);
        }
        merged.putAll(custom);
        Document doc = new Document();
        doc.putAll(merged);
        return files.write(file, doc);
    }

    /** Forgets the file and the custom wording. Defaults stay, because text classes define them once. */
    static void resetForTests() {
        custom = Map.of();
        files = null;
        file = null;
        logger = null;
        WARNED.clear();
    }
}
