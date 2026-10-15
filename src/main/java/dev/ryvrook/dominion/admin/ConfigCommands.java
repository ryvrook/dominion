package dev.ryvrook.dominion.admin;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.command.Call;
import dev.ryvrook.dominion.command.ServerCmd;
import dev.ryvrook.dominion.config.ConfigReport;
import dev.ryvrook.dominion.config.Perms;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import org.bson.Document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * /fa config to browse, read, change and dump the settings, and /fa messages to look up and reset the
 * wording in messages.json. Both work from the console.
 */
final class ConfigCommands {
    /** Most ids /fa messages search lists. */
    static final int SEARCH_LIMIT = 20;
    static final String DUMP_FILE = "config-dump.txt";
    static final String MESSAGES_FILE = "messages.json";

    private final DominionContext ctx;

    ConfigCommands(DominionContext ctx) {
        this.ctx = ctx;
    }

    private static ServerCmd server(String name, String description) {
        return new ServerCmd(name, description).permission(Perms.admin());
    }

    private static ServerCmd variant(String description) {
        return new ServerCmd(description).permission(Perms.admin());
    }

    void register(ServerCmd root) {
        ServerCmd config = root.sub(server("config", "Browse, read, change and dump the settings."));
        config.handler(this::sections);
        ServerCmd one = variant("Show a section, a setting, or dump every setting to a file.");
        RequiredArg<String> target = one.withRequiredArg("target", "Section, key or dump", ArgTypes.STRING);
        one.handler(c -> target(c, c.arg(target)));
        config.variant(one);
        ServerCmd two = variant("Show a page of a section, or change a setting.");
        RequiredArg<String> target2 = two.withRequiredArg("target", "Section or key", ArgTypes.STRING);
        RequiredArg<String> value = two.withRequiredArg("value", "Page number or new value", ArgTypes.GREEDY_STRING);
        two.handler(c -> targetWith(c, c.arg(target2), c.arg(value).trim()));
        config.variant(two);

        ServerCmd messages = root.sub(server("messages", "Look up, search and reset the wording in messages.json."));
        messages.handler(this::messageCount);
        ServerCmd id = variant("Show the default and custom wording of one message.");
        RequiredArg<String> idArg = id.withRequiredArg("id", "Message id", ArgTypes.STRING);
        id.handler(c -> message(c, c.arg(idArg)));
        messages.variant(id);
        // Sub commands rather than variants, because the engine picks a variant by word count and the search text may have several words.
        ServerCmd search = messages.sub(server("search", "List the message ids whose wording contains some text."));
        RequiredArg<String> text = search.withRequiredArg("text", "Text to find", ArgTypes.GREEDY_STRING);
        search.handler(c -> search(c, c.arg(text).trim()));
        ServerCmd reset = messages.sub(server("reset", "Put the default wording of one message back."));
        RequiredArg<String> resetId = reset.withRequiredArg("id", "Message id", ArgTypes.STRING);
        reset.handler(c -> reset(c, c.arg(resetId).trim()));
    }

    // ---- /fa config ----------------------------------------------------------

    private void sections(Call c) {
        Settings s = ctx.settings();
        Map<String, Integer> sections = ConfigReport.sections(s);
        c.info(Messages.get(ConfigText.SECTIONS, "keys", s.definitions().size(), "sections", sections.size()));
        for (Map.Entry<String, Integer> e : sections.entrySet()) {
            c.send(Msg.muted(" " + Messages.get(ConfigText.SECTION_LINE, "section", e.getKey(), "keys", e.getValue())));
        }
    }

    private void target(Call c, String name) {
        if ("dump".equalsIgnoreCase(name)) {
            dump(c);
            return;
        }
        Settings s = ctx.settings();
        String section = ConfigReport.section(s, name);
        if (section != null) {
            page(c, section, 1);
            return;
        }
        if (s.known(name)) {
            key(c, s.definition(name));
            return;
        }
        c.bad(Messages.get(ConfigText.UNKNOWN, "name", name));
    }

    private void targetWith(Call c, String name, String value) {
        Settings s = ctx.settings();
        if (s.known(name)) {
            set(c, name, value);
            return;
        }
        String section = ConfigReport.section(s, name);
        if (section == null) {
            c.bad(Messages.get(ConfigText.UNKNOWN, "name", name));
            return;
        }
        int page;
        try {
            page = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            c.bad(Messages.get(ConfigText.NOT_A_PAGE, "value", value));
            return;
        }
        page(c, section, page);
    }

    private void page(Call c, String section, int page) {
        Settings s = ctx.settings();
        int pages = ConfigReport.pages(s, section);
        int p = Math.max(1, Math.min(pages, page));
        c.info(Messages.get(ConfigText.SECTION_PAGE, "section", section, "page", p, "pages", pages));
        for (Settings.Def d : ConfigReport.page(s, section, p)) {
            c.send(Msg.colored(" " + ConfigReport.line(s, d), Msg.TEXT));
        }
        if (p < pages) {
            c.send(Msg.muted(Messages.get(ConfigText.NEXT_PAGE, "section", section.toLowerCase(Locale.ROOT), "next", p + 1)));
        }
    }

    private void key(Call c, Settings.Def d) {
        Settings s = ctx.settings();
        c.info(ConfigReport.line(s, d));
        c.send(Msg.muted(" " + Messages.get(ConfigText.KEY_DESCRIPTION, "label", d.label(), "section", d.group())));
    }

    private void set(Call c, String key, String value) {
        Settings s = ctx.settings();
        if (s.fileOnly(key)) {
            c.bad(Messages.get(ConfigText.FILE_ONLY, "key", key));
            return;
        }
        String why = s.trySet(key, value);
        if (why != null) {
            c.bad(why);
            return;
        }
        c.good(Messages.get(ConfigText.SET, "key", key, "value", ConfigReport.show(s.raw(key))));
        if (appliesLater(s.definition(key))) {
            c.send(Msg.muted(Messages.get(ConfigText.RESTART)));
        }
    }

    /** True for keys that are read once at start, which say so in their description. */
    static boolean appliesLater(Settings.Def d) {
        String label = d == null ? "" : d.label().toLowerCase(Locale.ROOT);
        return label.contains("next start") || label.contains("read at start");
    }

    private void dump(Call c) {
        Path file = ctx.repo().root().resolve(DUMP_FILE);
        String text = ConfigReport.dump(ctx.settings(), Instant.now());
        ctx.runIo("settings dump", () -> {
            boolean ok = ctx.files().writeText(file, text, false);
            if (ok) {
                c.good(Messages.get(ConfigText.DUMPED, "file", file.toAbsolutePath()));
            } else {
                c.bad(Messages.get(ConfigText.DUMP_FAILED));
            }
        });
    }

    // ---- /fa messages --------------------------------------------------------

    private void messageCount(Call c) {
        List<String> ids = Messages.ids();
        int customised = 0;
        for (String id : ids) {
            if (Messages.customised(id)) {
                customised++;
            }
        }
        c.info(Messages.get(ConfigText.MESSAGES_COUNT, "ids", ids.size(), "customised", customised));
        c.send(Msg.muted(Messages.get(ConfigText.MESSAGES_HINT)));
    }

    private void message(Call c, String id) {
        if (!Messages.has(id)) {
            c.bad(Messages.get(ConfigText.MESSAGES_UNKNOWN, "id", id));
            return;
        }
        c.info(id);
        c.send(Msg.muted(" " + Messages.get(ConfigText.MESSAGES_DEFAULT)));
        c.send(Msg.colored("  " + Messages.defaultOf(id), Msg.TEXT));
        if (Messages.customised(id)) {
            c.send(Msg.muted(" " + Messages.get(ConfigText.MESSAGES_CUSTOM)));
            c.send(Msg.colored("  " + Messages.raw(id), Msg.TEXT));
        } else {
            c.send(Msg.muted(" " + Messages.get(ConfigText.MESSAGES_NOT_CUSTOM, "id", id)));
        }
    }

    /** Ids whose id, default or custom wording contains the text, ignoring case. */
    static List<String> search(String text) {
        String needle = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (needle.isEmpty()) {
            return out;
        }
        for (String id : Messages.ids()) {
            String def = Messages.defaultOf(id);
            String now = Messages.raw(id);
            if (id.toLowerCase(Locale.ROOT).contains(needle)
                    || (def != null && def.toLowerCase(Locale.ROOT).contains(needle))
                    || (now != null && now.toLowerCase(Locale.ROOT).contains(needle))) {
                out.add(id);
            }
        }
        return out;
    }

    private void search(Call c, String text) {
        List<String> found = search(text);
        if (found.isEmpty()) {
            c.bad(Messages.get(ConfigText.MESSAGES_NONE, "text", text));
            return;
        }
        if (found.size() > SEARCH_LIMIT) {
            c.info(Messages.get(ConfigText.MESSAGES_FOUND_MORE, "count", found.size(), "text", text, "shown", SEARCH_LIMIT));
        } else {
            c.info(Messages.get(ConfigText.MESSAGES_FOUND, "count", found.size(), "text", text));
        }
        for (String id : found.subList(0, Math.min(SEARCH_LIMIT, found.size()))) {
            c.send(Msg.colored(" " + id, Msg.GOLD));
            c.send(Msg.muted("   " + Messages.raw(id)));
        }
    }

    private void reset(Call c, String id) {
        if (!Messages.has(id)) {
            c.bad(Messages.get(ConfigText.MESSAGES_UNKNOWN, "id", id));
            return;
        }
        if (!Messages.customised(id)) {
            c.info(Messages.get(ConfigText.MESSAGES_NOT_CUSTOM, "id", id));
            return;
        }
        Path file = ctx.repo().root().resolve(MESSAGES_FILE);
        ctx.runIo("message reset", () -> {
            if (resetInFile(ctx.files(), file, id)) {
                c.good(Messages.get(ConfigText.MESSAGES_RESET, "id", id));
            } else {
                c.bad(Messages.get(ConfigText.MESSAGES_RESET_FAILED));
            }
        });
    }

    /**
     * Puts the default wording of the id back into messages.json and reads the file again. Every other
     * entry, unknown ids included, is kept. False when the file could not be read or written.
     */
    static boolean resetInFile(JsonFiles files, Path file, String id) {
        String def = Messages.defaultOf(id);
        if (def == null) {
            return false;
        }
        Document doc;
        try {
            doc = files.parse(file);
        } catch (Exception e) {
            dev.ryvrook.dominion.DominionPlugin plugin = dev.ryvrook.dominion.DominionPlugin.get();
            if (plugin != null) {
                plugin.getLogger().at(Level.WARNING).withCause(e).log("Dominion could not read messages.json to reset %s", id);
            }
            return false;
        }
        if (doc == null) {
            doc = new Document();
        }
        doc.put(id, def);
        if (!files.write(file, doc)) {
            return false;
        }
        Messages.reload();
        return true;
    }
}
