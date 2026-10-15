package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.faction.FactionText;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.text.Messages;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PanelFixesTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);

    @AfterEach
    void forgetTheFile() throws Exception {
        Files.deleteIfExists(dir.resolve("messages.json"));
        Messages.reload();
    }

    @Test
    void theLatestMotdChangeIsFoundWithCustomWording() throws Exception {
        Path file = dir.resolve("messages.json");
        Messages.install(files, file, TestContexts.LOGGER);
        Document doc = files.parse(file);
        doc.put(FactionText.AUDIT_MOTD_CHANGED, "Neue Nachricht des Tages");
        files.write(file, doc);
        Messages.reload();

        Faction f = new Faction(UUID.randomUUID(), "abc", "Abc", null, 0L);
        f.audit(new AuditEntry(1_000L, null, "Ann", "settings", "Turned open joining on"), 100);
        AuditEntry motd = new AuditEntry(2_000L, null, "Bob", "settings", Messages.get(FactionText.AUDIT_MOTD_CHANGED));
        f.audit(motd, 100);
        assertEquals(motd, DominionPanel.latestAnnouncement(f));
    }

    @Test
    void cellsBeyondASmallerGridAreHidden() {
        UICommandBuilder c = new UICommandBuilder();
        Body body = new Body(c, new UIEventBuilder(), "#Body");
        String[] five = new String[5];
        body.gridRow(five, new String[5], new String[5]);
        List<String> hidden = new ArrayList<>();
        for (CustomUICommand cmd : c.getCommands()) {
            if (cmd.selector != null && cmd.selector.endsWith(".Visible")) {
                hidden.add(cmd.selector.substring(cmd.selector.lastIndexOf(' ') + 1));
            }
        }
        List<String> expected = new ArrayList<>();
        for (int i = 5; i < Body.GRID_CELLS; i++) {
            expected.add("#C" + i + ".Visible");
        }
        assertEquals(expected, hidden);
    }
}
