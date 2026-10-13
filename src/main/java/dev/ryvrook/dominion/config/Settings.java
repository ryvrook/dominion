package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flat key value settings with typed defaults. Every rule in the spec has a key here so the
 * admin settings page and the config file edit the same thing. Keys use dotted names.
 */
public final class Settings {
    public record Def(String key, String group, String label, Object defaultValue) {}

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> fileOnly = new HashSet<>();
    private final JsonFiles files;
    private final Path file;

    public Settings(JsonFiles files, Path file) {
        this.files = files;
        this.file = file;
        Defaults.register(this);
        load();
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

    /** True when the key can only be changed by editing config.json. */
    public boolean fileOnly(String key) {
        return fileOnly.contains(key);
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
        return v instanceof Number n ? n.intValue() : v == null ? 0 : (int) Double.parseDouble(v.toString());
    }

    public long longValue(String key) {
        Object v = raw(key);
        return v instanceof Number n ? n.longValue() : v == null ? 0 : (long) Double.parseDouble(v.toString());
    }

    public double decimal(String key) {
        Object v = raw(key);
        return v instanceof Number n ? n.doubleValue() : v == null ? 0 : Double.parseDouble(v.toString());
    }

    public String text(String key) {
        Object v = raw(key);
        return v == null ? "" : v.toString();
    }

    /** Sets and saves. Returns false if the key is unknown or the value has the wrong type. */
    public synchronized boolean set(String key, Object value) {
        Def d = defs.get(key);
        if (d == null) {
            return false;
        }
        Object coerced = coerce(d.defaultValue(), value);
        if (coerced == null) {
            return false;
        }
        values.put(key, coerced);
        save();
        return true;
    }

    public synchronized void applyPreset(Map<String, Object> preset) {
        for (Map.Entry<String, Object> e : preset.entrySet()) {
            Def d = defs.get(e.getKey());
            if (d != null) {
                Object c = coerce(d.defaultValue(), e.getValue());
                if (c != null) {
                    values.put(e.getKey(), c);
                }
            }
        }
        save();
    }

    private static Object coerce(Object def, Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (def instanceof Boolean) {
                return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
            }
            if (def instanceof Integer) {
                return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString().trim());
            }
            if (def instanceof Long) {
                return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString().trim());
            }
            if (def instanceof Double) {
                return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString().trim());
            }
            return value.toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public synchronized void load() {
        Document doc = files.read(file);
        values.clear();
        if (doc != null) {
            for (Map.Entry<String, Object> e : doc.entrySet()) {
                Def d = defs.get(e.getKey());
                if (d != null) {
                    Object c = coerce(d.defaultValue(), e.getValue());
                    if (c != null) {
                        values.put(e.getKey(), c);
                    }
                }
            }
        }
        save();
    }

    public synchronized void save() {
        Document doc = new Document();
        for (Def d : defs.values()) {
            doc.append(d.key(), raw(d.key()));
        }
        files.write(file, doc);
    }
}
