package dev.ryvrook.dominion.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text views of the settings for /fa config, the config dump and docs/SETTINGS.md. Sections are the
 * groups of the admin settings page, in the order their first key is defined.
 */
public final class ConfigReport {
    private ConfigReport() {}

    /** Keys per page of /fa config section. */
    public static final int PAGE_SIZE = 8;

    /** Every section with its key count, in page order. */
    public static Map<String, Integer> sections(Settings s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Settings.Def d : s.definitions()) {
            out.merge(d.group(), 1, Integer::sum);
        }
        return out;
    }

    /** The section name as the settings page writes it, matched without case, or null. */
    public static String section(Settings s, String name) {
        if (name == null) {
            return null;
        }
        for (String g : sections(s).keySet()) {
            if (g.equalsIgnoreCase(name.trim())) {
                return g;
            }
        }
        return null;
    }

    /** Number of pages of a section. */
    public static int pages(Settings s, String section) {
        int n = s.definitions(section).size();
        return Math.max(1, (n + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The keys of one page of a section, the first page is 1. Pages past the end are clamped. */
    public static List<Settings.Def> page(Settings s, String section, int page) {
        List<Settings.Def> all = s.definitions(section);
        int p = Math.max(1, Math.min(pages(s, section), page));
        int from = (p - 1) * PAGE_SIZE;
        return new ArrayList<>(all.subList(Math.min(from, all.size()), Math.min(all.size(), from + PAGE_SIZE)));
    }

    /** A value as config.json shows it, numbers without a trailing .0 when whole. */
    public static String show(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Double d && d == Math.rint(d) && !d.isInfinite() && Math.abs(d) < 1e15) {
            return Long.toString(d.longValue()) + ".0";
        }
        String text = v.toString();
        return v instanceof String && text.isEmpty() ? "\"\"" : text;
    }

    /** What a key accepts, such as "1 to 32", "one of auto, off" or "a colour written as #rrggbb", or "" when anything of its type fits. */
    public static String accepts(Settings s, String key) {
        Settings.Range r = s.rangeOf(key);
        if (r != null) {
            return plain(r.min()) + " to " + plain(r.max());
        }
        List<String> choices = s.choicesOf(key);
        if (!choices.isEmpty()) {
            return "one of " + String.join(", ", choices);
        }
        String hint = s.patternOf(key);
        if (hint != null) {
            return hint;
        }
        Settings.Def d = s.definition(key);
        if (d != null && d.defaultValue() instanceof Boolean) {
            return "true or false";
        }
        return "";
    }

    private static String plain(double v) {
        if (v >= Long.MAX_VALUE / 2.0) {
            return "any size";
        }
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    /** One line for chat, key, current value, default and what it accepts. */
    public static String line(Settings s, Settings.Def d) {
        StringBuilder b = new StringBuilder(d.key()).append(" = ").append(show(s.raw(d.key())));
        b.append("   default ").append(show(d.defaultValue()));
        String accepts = accepts(s, d.key());
        if (!accepts.isEmpty()) {
            b.append(", ").append(accepts);
        }
        if (s.fileOnly(d.key())) {
            b.append(", config.json only");
        }
        return b.toString();
    }

    /** The whole config as plain text, every section with every key, its description, value, default and limits. */
    public static String dump(Settings s, Instant now) {
        Map<String, Integer> sections = sections(s);
        StringBuilder b = new StringBuilder();
        b.append("Dominion settings dump written ").append(now).append('\n');
        b.append(s.definitions().size()).append(" keys in ").append(sections.size()).append(" sections\n");
        for (Map.Entry<String, Integer> e : sections.entrySet()) {
            b.append('\n').append("[").append(e.getKey()).append("] ").append(e.getValue()).append(" keys\n");
            for (Settings.Def d : s.definitions(e.getKey())) {
                b.append('\n').append(d.key()).append('\n');
                b.append("  description  ").append(d.label()).append('\n');
                b.append("  value        ").append(show(s.raw(d.key()))).append('\n');
                b.append("  default      ").append(show(d.defaultValue())).append('\n');
                String accepts = accepts(s, d.key());
                if (!accepts.isEmpty()) {
                    b.append("  accepts      ").append(accepts).append('\n');
                }
                if (s.fileOnly(d.key())) {
                    b.append("  config.json only\n");
                }
            }
        }
        return b.toString();
    }

    /** docs/SETTINGS.md, a table per section generated from the defaults. */
    public static String markdown(Settings s) {
        Map<String, Integer> sections = sections(s);
        StringBuilder b = new StringBuilder();
        b.append("# Dominion settings\n\n");
        b.append("Every key in `config.json`, generated from `config/Defaults.java`. There are ")
                .append(s.definitions().size()).append(" keys in ").append(sections.size()).append(" sections. ")
                .append("Change them with `/fa set`, `/fa config` or the settings page, or edit the file and run `/fa reload`. ")
                .append("A test fails when this file and the defaults disagree, ")
                .append("so after adding a key run `DOMINION_WRITE_DOCS=1 ./gradlew test` to write it again.\n");
        for (Map.Entry<String, Integer> e : sections.entrySet()) {
            b.append("\n## ").append(e.getKey()).append("\n\n");
            b.append("| Key | Default | Accepts | Description |\n");
            b.append("| --- | --- | --- | --- |\n");
            for (Settings.Def d : s.definitions(e.getKey())) {
                String accepts = accepts(s, d.key());
                if (s.fileOnly(d.key())) {
                    accepts = accepts.isEmpty() ? "config.json only" : accepts + ", config.json only";
                }
                b.append("| `").append(d.key()).append("` | `").append(cell(show(d.defaultValue()))).append("` | ")
                        .append(cell(accepts)).append(" | ").append(cell(d.label())).append(" |\n");
            }
        }
        return b.toString();
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
