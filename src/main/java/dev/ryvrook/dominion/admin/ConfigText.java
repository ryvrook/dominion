package dev.ryvrook.dominion.admin;

import dev.ryvrook.dominion.text.Messages;

/** Sentences of /fa config and /fa messages. */
public final class ConfigText {
    private ConfigText() {}

    public static final String SECTIONS = Messages.define("admin.config.sections",
            "{keys} settings in {sections} sections. Type /fa config with a section, a key or dump.");
    public static final String SECTION_LINE = Messages.define("admin.config.sectionLine", "{section} has {keys} keys");
    public static final String SECTION_PAGE = Messages.define("admin.config.sectionPage",
            "{section} settings, page {page} of {pages}");
    public static final String NEXT_PAGE = Messages.define("admin.config.nextPage", "Type /fa config {section} {next} for more.");
    public static final String KEY_DESCRIPTION = Messages.define("admin.config.keyDescription", "{label}, in {section}.");
    public static final String UNKNOWN = Messages.define("admin.config.unknown",
            "There is no section or setting called {name}. Type /fa config for the sections.");
    public static final String NOT_A_PAGE = Messages.define("admin.config.notAPage", "{value} is not a page number.");
    public static final String SET = Messages.define("admin.config.set", "{key} is now {value}.");
    public static final String FILE_ONLY = Messages.define("admin.config.fileOnly",
            "{key} can only be changed in config.json. Edit the file and run /fa reload.");
    public static final String RESTART = Messages.define("admin.config.restart",
            "This one applies at the next start or /plugin reload.");
    public static final String DUMPED = Messages.define("admin.config.dumped", "Wrote every setting to {file}.");
    public static final String DUMP_FAILED = Messages.define("admin.config.dumpFailed",
            "The settings dump could not be written. The server log has the reason.");

    public static final String MESSAGES_COUNT = Messages.define("admin.messages.count",
            "{ids} message ids, {customised} customised in messages.json.");
    public static final String MESSAGES_HINT = Messages.define("admin.messages.hint",
            "Type /fa messages with an id, search with some text, or reset with an id.");
    public static final String MESSAGES_UNKNOWN = Messages.define("admin.messages.unknown", "There is no message with the id {id}.");
    public static final String MESSAGES_DEFAULT = Messages.define("admin.messages.default", "Default wording");
    public static final String MESSAGES_CUSTOM = Messages.define("admin.messages.custom", "Custom wording");
    public static final String MESSAGES_NOT_CUSTOM = Messages.define("admin.messages.notCustom",
            "{id} uses the default wording.");
    public static final String MESSAGES_FOUND = Messages.define("admin.messages.found", "Messages that contain {text}, {count} in all.");
    public static final String MESSAGES_FOUND_MORE = Messages.define("admin.messages.foundMore",
            "Messages that contain {text}, {count} in all. The first {shown} are listed.");
    public static final String MESSAGES_NONE = Messages.define("admin.messages.none", "No message contains {text}.");
    public static final String MESSAGES_RESET = Messages.define("admin.messages.reset",
            "{id} is back to its default wording.");
    public static final String MESSAGES_RESET_FAILED = Messages.define("admin.messages.resetFailed",
            "messages.json could not be changed. The server log has the reason.");
}
