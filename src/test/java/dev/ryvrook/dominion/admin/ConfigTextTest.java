package dev.ryvrook.dominion.admin;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

class ConfigTextTest {
    @Test
    void everySentenceIsADefinedAdminIdWithTheExpectedPlaceholders() throws Exception {
        TextClassCheck.check(ConfigText.class, "admin", TextClassCheck.expect(
                ConfigText.SECTIONS, "keys sections",
                ConfigText.SECTION_LINE, "keys section",
                ConfigText.SECTION_PAGE, "page pages section",
                ConfigText.NEXT_PAGE, "next section",
                ConfigText.KEY_DESCRIPTION, "label section",
                ConfigText.UNKNOWN, "name",
                ConfigText.NOT_A_PAGE, "value",
                ConfigText.SET, "key value",
                ConfigText.FILE_ONLY, "key",
                ConfigText.DUMPED, "file",
                ConfigText.MESSAGES_COUNT, "customised ids",
                ConfigText.MESSAGES_UNKNOWN, "id",
                ConfigText.MESSAGES_NOT_CUSTOM, "id",
                ConfigText.MESSAGES_FOUND, "count text",
                ConfigText.MESSAGES_FOUND_MORE, "count shown text",
                ConfigText.MESSAGES_NONE, "text",
                ConfigText.MESSAGES_RESET, "id"));
    }
}
