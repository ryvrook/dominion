package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.text.Palette;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Flat key value settings with typed defaults. Every rule in the spec has a key here so the
 * admin settings page and the config file edit the same thing. Keys use dotted names.
 *
 * <p>Reads are lock free. The current values live in an immutable map that every change replaces
 * whole, so a reader on any thread sees either the old or the new set and never half of a reload.
 * Numbers can carry a range and text a list of allowed values. A value outside them is refused by
 * the setters and replaced by the default when config.json is read, with the reason reported.
 */
public final class Settings {
    public record Def(String key, String group, String label, Object defaultValue) {}

    /** Inclusive bounds of a numeric setting. */
    public record Range(double min, double max) {
        boolean contains(double v) {
            return v >= min && v <= max;
        }
    }

    /** Outcome of reading config.json. Problems are plain sentences fit for chat and the log. */
    public record LoadReport(boolean fileRead, List<String> problems) {
        public boolean clean() {
            return fileRead && problems.isEmpty();
        }
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Range> ranges = new LinkedHashMap<>();
    private final Map<String, List<String>> choices = new LinkedHashMap<>();
    private final Set<String> fileOnly = new HashSet<>();
    /** Text settings that must match a pattern, with a few words that say what is expected. */
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();
    private final Map<String, String> patternHints = new LinkedHashMap<>();
    /** Run after every load, reload, set and preset, on the thread that made the change. */
    private final List<Consumer<Settings>> listeners = new CopyOnWriteArrayList<>();
    /** Pairs of keys where the first must not be above the second, such as a minimum and a maximum. */
    private final List<String[]> ordered = new ArrayList<>();
    private volatile Map<String, Object> values = Map.of();
    private volatile LoadReport lastLoad = new LoadReport(true, List.of());
    private final JsonFiles files;
    private final Path file;

    public Settings(JsonFiles files, Path file) {
        this.files = files;
        this.file = file;
        Defaults.register(this);
        load();
    }

    /**
     * Calls the listener after every change from config.json, /fa set, the settings page or a preset.
     * The colour palette is refreshed before any listener runs.
     */
    public void onChange(Consumer<Settings> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Refreshes the palette and tells the listeners. A failing listener never stops the others. */
    private void changed() {
        Palette.refresh(this::text);
        for (Consumer<Settings> l : listeners) {
            try {
                l.accept(this);
            } catch (RuntimeException e) {
                // A listener that fails must not undo or block the change.
            }
        }
    }

    /** Limits a text setting to values that match the regular expression. hint says what is expected. */
    public void pattern(String key, String regex, String hint) {
        patterns.put(key, Pattern.compile(regex));
        patternHints.put(key, hint);
    }

    /** The pattern hint of a key, or null when it has none. */
    public String patternOf(String key) {
        return patternHints.get(key);
    }

    public void define(String key, String group, String label, Object defaultValue) {
        defs.put(key, new Def(key, group, label, defaultValue));
    }

    /**
     * Defines a setting that only config.json may change. The in game setter and the settings
     * page refuse it, and /fa reload picks up a new value from the file.
     */
    public void defineFileOnly(String key, String group, String label, Object defaultValue) {
        define(key, group, label, defaultValue);
        fileOnly.add(key);
    }

    /** What the read of config.json at start found, so the plugin can log it. */
    public LoadReport startReport() {
        return lastLoad;
    }

    /** Limits a numeric setting to min and max, both inclusive. */
    public void range(String key, double min, double max) {
        ranges.put(key, new Range(min, max));
    }

    /** Limits a text setting to one of the given values, compared without case. */
    public void choices(String key, String... allowed) {
        List<String> list = new ArrayList<>();
        for (String a : allowed) {
            list.add(a.toLowerCase(Locale.ROOT));
        }
        choices.put(key, List.copyOf(list));
    }

    /** Requires low to be at most high, for a minimum and maximum pair such as faction.tagMin and faction.tagMax. */
    public void ordered(String low, String high) {
        ordered.add(new String[] {low, high});
    }

    /** The low and high key pairs registered with ordered. */
    public List<String[]> orderedPairs() {
        List<String[]> out = new ArrayList<>();
        for (String[] p : ordered) {
            out.add(p.clone());
        }
        return out;
    }

    /** The value a key would have in the given map, falling back to its default. */
    private double numberIn(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            Def d = defs.get(key);
            v = d == null ? null : d.defaultValue();
        }
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /** The first pair that is out of order in the given values, or null. */
    private String[] brokenPair(Map<String, Object> map) {
        for (String[] p : ordered) {
            if (numberIn(map, p[0]) > numberIn(map, p[1])) {
                return p;
            }
        }
        return null;
    }

    private String orderReason(String[] pair, Map<String, Object> map) {
        return pair[0] + " (" + plain(numberIn(map, pair[0])) + ") cannot be above " + pair[1]
                + " (" + plain(numberIn(map, pair[1])) + ").";
    }

    /**
     * Puts every out of order pair back to the values in fallback, or to the defaults when fallback
     * is null, and reports each one.
     */
    private void repairOrder(Map<String, Object> next, Map<String, Object> fallback, List<String> problems) {
        for (String[] p : ordered) {
            if (numberIn(next, p[0]) <= numberIn(next, p[1])) {
                continue;
            }
            problems.add(orderReason(p, next) + (fallback == null ? " Both use their defaults." : " Both keep their current values."));
            for (String key : p) {
                Object old = fallback == null ? null : fallback.get(key);
                if (old == null) {
                    next.remove(key);
                } else {
                    next.put(key, old);
                }
            }
        }
    }

    /** True when the key can only be changed by editing config.json. */
    public boolean fileOnly(String key) {
        return fileOnly.contains(key);
    }

    public boolean known(String key) {
        return defs.containsKey(key);
    }

    public Def definition(String key) {
        return defs.get(key);
    }

    public Range rangeOf(String key) {
        return ranges.get(key);
    }

    public List<String> choicesOf(String key) {
        return choices.getOrDefault(key, List.of());
    }

    public List<Def> definitions() {
        return new ArrayList<>(defs.values());
    }

    public List<Def> definitions(String group) {
        List<Def> out = new ArrayList<>();
        for (Def d : defs.values()) {
            if (d.group().equals(group)) {
                out.add(d);
            }
        }
        return out;
    }

    public Object raw(String key) {
        Object v = values.get(key);
        if (v != null) {
            return v;
        }
        Def d = defs.get(key);
        return d == null ? null : d.defaultValue();
    }

    public boolean bool(String key) {
        Object v = raw(key);
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
    }

    public int integer(String key) {
        Object v = raw(key);
        return v instanceof Number n ? n.intValue() : v == null ? 0 : (int) parseOr(v.toString(), 0);
    }

    public long longValue(String key) {
        Object v = raw(key);
        return v instanceof Number n ? n.longValue() : v == null ? 0 : (long) parseOr(v.toString(), 0);
    }

    public double decimal(String key) {
        Object v = raw(key);
        return v instanceof Number n ? n.doubleValue() : v == null ? 0 : parseOr(v.toString(), 0);
    }

    public String text(String key) {
        Object v = raw(key);
        return v == null ? "" : v.toString();
    }

    private static double parseOr(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Sets and saves. Returns false if the key is unknown or the value does not fit. */
    public boolean set(String key, Object value) {
        return trySet(key, value) == null;
    }

    /**
     * Sets and saves. Returns null on success, otherwise a sentence that says why the value was
     * refused, such as the allowed range.
     */
    public synchronized String trySet(String key, Object value) {
        Def d = defs.get(key);
        if (d == null) {
            return "There is no setting called " + key + ".";
        }
        Object coerced = coerce(d.defaultValue(), value);
        if (coerced == null) {
            return key + " needs " + kind(d.defaultValue()) + ".";
        }
        String why = check(key, coerced);
        if (why != null) {
            return why;
        }
        Map<String, Object> next = new LinkedHashMap<>(values);
        next.put(key, coerced);
        String[] broken = brokenPair(next);
        if (broken != null) {
            return orderReason(broken, next) + " Change the other one first.";
        }
        values = Collections.unmodifiableMap(next);
        save();
        changed();
        return null;
    }

    /** Applies every value of a preset that fits and saves once. Returns the keys that were refused, with a reason for pairs. */
    public synchronized List<String> applyPreset(Map<String, Object> preset) {
        Map<String, Object> next = new LinkedHashMap<>(values);
        List<String> refused = new ArrayList<>();
        for (Map.Entry<String, Object> e : preset.entrySet()) {
            Def d = defs.get(e.getKey());
            Object c = d == null ? null : coerce(d.defaultValue(), e.getValue());
            if (c == null || check(e.getKey(), c) != null) {
                refused.add(e.getKey());
                continue;
            }
            next.put(e.getKey(), c);
        }
        repairOrder(next, values, refused);
        values = Collections.unmodifiableMap(next);
        save();
        changed();
        return refused;
    }

    /** Null when the already typed value fits the key's range or choices, otherwise the reason. */
    String check(String key, Object value) {
        Range r = ranges.get(key);
        if (r != null && value instanceof Number n) {
            double v = n.doubleValue();
            if (Double.isNaN(v) || Double.isInfinite(v) || !r.contains(v)) {
                return key + " must be between " + plain(r.min()) + " and " + plain(r.max()) + ".";
            }
        }
        if (value instanceof Double dv && (dv.isNaN() || dv.isInfinite())) {
            return key + " must be a finite number.";
        }
        List<String> allowed = choices.get(key);
        if (allowed != null && !allowed.contains(value.toString().toLowerCase(Locale.ROOT))) {
            return key + " must be one of " + String.join(", ", allowed) + ".";
        }
        Pattern p = patterns.get(key);
        if (p != null && !p.matcher(value.toString()).matches()) {
            return key + " must be " + patternHints.get(key) + ".";
        }
        return null;
    }

    private static String plain(double v) {
        if (v >= Long.MAX_VALUE / 2.0) {
            return "any size";
        }
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static String kind(Object def) {
        if (def instanceof Boolean) {
            return "true or false";
        }
        if (def instanceof Integer || def instanceof Long) {
            return "a whole number";
        }
        if (def instanceof Double) {
            return "a number";
        }
        return "some text";
    }

    /** Converts a raw value to the default's type, or null when it cannot be read as that type. */
    static Object coerce(Object def, Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (def instanceof Boolean) {
                if (value instanceof Boolean b) {
                    return b;
                }
                return switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
                    case "true", "on", "yes", "1" -> Boolean.TRUE;
                    case "false", "off", "no", "0" -> Boolean.FALSE;
                    default -> null;
                };
            }
            if (def instanceof Integer) {
                if (value instanceof Number n) {
                    return wholeOrNull(n.doubleValue(), Integer.MIN_VALUE, Integer.MAX_VALUE) == null ? null : n.intValue();
                }
                return Integer.parseInt(value.toString().trim());
            }
            if (def instanceof Long) {
                if (value instanceof Number n) {
                    return wholeOrNull(n.doubleValue(), Long.MIN_VALUE, Long.MAX_VALUE) == null ? null : n.longValue();
                }
                return Long.parseLong(value.toString().trim());
            }
            if (def instanceof Double) {
                double d = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString().trim());
                return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
            }
            return value instanceof String s ? s : value.toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The value when it is a whole number inside the bounds, otherwise null. */
    private static Double wholeOrNull(double v, double min, double max) {
        return v == Math.rint(v) && v >= min && v <= max ? v : null;
    }

    /**
     * Loads config.json at start. A damaged file is moved aside by the storage layer and the
     * defaults are written in its place. Values that do not fit are replaced by their defaults.
     */
    public synchronized LoadReport load() {
        Document doc = files.read(file);
        List<String> problems = new ArrayList<>();
        Map<String, Object> read = readValues(doc, problems);
        repairOrder(read, null, problems);
        values = Collections.unmodifiableMap(read);
        save();
        lastLoad = new LoadReport(true, List.copyOf(problems));
        changed();
        return lastLoad;
    }

    /**
     * Rereads config.json for /fa reload. When the file cannot be parsed nothing changes and the file
     * stays where it is so the admin can fix it. Values that do not fit keep their current value.
     */
    public synchronized LoadReport reload() {
        Document doc;
        try {
            doc = files.parse(file);
        } catch (Exception e) {
            return new LoadReport(false, List.of("config.json could not be read, so nothing changed. "
                    + "Check it for a missing comma or quote. " + e.getMessage()));
        }
        if (doc == null) {
            save();
            return new LoadReport(true, List.of("config.json was missing, so it was written again with the current values."));
        }
        List<String> problems = new ArrayList<>();
        Map<String, Object> next = new LinkedHashMap<>(values);
        Map<String, Object> read = readValues(doc, problems);
        for (Def d : defs.values()) {
            if (read.containsKey(d.key())) {
                next.put(d.key(), read.get(d.key()));
            } else if (!doc.containsKey(d.key())) {
                // A key removed from the file goes back to its default.
                next.remove(d.key());
            }
        }
        repairOrder(next, values, problems);
        values = Collections.unmodifiableMap(next);
        changed();
        if (!problems.isEmpty()) {
            // Leave the admin's file alone so the bad line can be found and fixed.
            return new LoadReport(true, problems);
        }
        save();
        return new LoadReport(true, problems);
    }

    private Map<String, Object> readValues(Document doc, List<String> problems) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (doc == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            Def d = defs.get(e.getKey());
            if (d == null) {
                problems.add(e.getKey() + " is not a Dominion setting and was ignored.");
                continue;
            }
            Object c = coerce(d.defaultValue(), e.getValue());
            if (c == null) {
                problems.add(e.getKey() + " needs " + kind(d.defaultValue()) + ", so " + raw(e.getKey()) + " is used.");
                continue;
            }
            String why = check(e.getKey(), c);
            if (why != null) {
                problems.add(why + " The value " + c + " was not used.");
                continue;
            }
            out.put(e.getKey(), c);
        }
        return out;
    }

    public synchronized void save() {
        Document doc = new Document();
        for (Def d : defs.values()) {
            doc.append(d.key(), raw(d.key()));
        }
        files.write(file, doc);
    }
}
