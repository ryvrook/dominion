package dev.ryvrook.dominion.chat;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

class ChatTextTest {
    @Test
    void everySentenceIsADefinedIdWithTheNamedPlaceholders() throws Exception {
        TextClassCheck.check(ChatText.class, "chat", Map.ofEntries(
                entry("chat.labelNation", Set.of("tag")),
                entry("chat.channelNeedsFaction", Set.of("channel")),
                entry("chat.channelSet", Set.of("channel")),
                entry("chat.announcementFrom", Set.of("tag", "author")),
                entry("chat.motdTitle", Set.of("tag")),
                entry("chat.renamed", Set.of("before", "after"))
        ));
    }
}
