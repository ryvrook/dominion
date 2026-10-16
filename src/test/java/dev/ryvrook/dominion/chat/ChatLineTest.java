package dev.ryvrook.dominion.chat;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.export.ApiAccess;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.FactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatLineTest {
    Fixtures fx;
    ChatServiceImpl chat;
    final List<DominionEvent.ChatLine> lines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        chat = new ChatServiceImpl(fx.ctx);
        fx.ctx.register(ChatService.class, chat);
        fx.ctx.bus().subscribe(e -> {
            if (e instanceof DominionEvent.ChatLine l) {
                lines.add(l);
            }
        });
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void aLineFromOutsideIsPublishedWithoutASender() {
        Faction f = fx.faction("NRT");
        Result r = ApiAccess.of(fx.ctx).sendFactionChat(f.id(), "Mira on Discord", "  raid at dusk  ");
        assertTrue(r.success(), r.message());
        assertEquals(1, lines.size());
        DominionEvent.ChatLine l = lines.get(0);
        assertEquals(f.id(), l.factionId());
        assertEquals("NRT", l.tag());
        assertEquals("faction", l.channel());
        assertNull(l.sender());
        assertEquals("Mira on Discord", l.senderName());
        assertEquals("raid at dusk", l.text());
        assertTrue(l.millis() > 0);
    }

    @Test
    void longLinesAreCutAndEmptyOnesRefused() {
        Faction f = fx.faction("NRT");
        assertTrue(chat.sendExternal(f, "x", "a".repeat(400)).success());
        assertEquals(ChatServiceImpl.EXTERNAL_MAX, lines.get(0).text().length());
        assertTrue(chat.sendExternal(f, "x", "   ").failed());
        assertEquals(1, lines.size());
    }

    @Test
    void refusedForAnUnknownFactionOrWithChannelsOff() {
        Faction f = fx.faction("NRT");
        assertTrue(ApiAccess.of(fx.ctx).sendFactionChat(UUID.randomUUID(), "x", "hi").failed());
        assertTrue(ApiAccess.of(fx.ctx).sendFactionChat(null, "x", "hi").failed());
        fx.set("chat.enabled", false);
        assertTrue(chat.sendExternal(f, "x", "hi").failed());
        assertTrue(lines.isEmpty());
    }

    @Test
    void channelNamesAreLowerCase() {
        Faction f = fx.faction("NRT");
        UUID who = f.leader();
        for (PlayerData.Channel c : List.of(PlayerData.Channel.FACTION, PlayerData.Channel.ALLY,
                PlayerData.Channel.OFFICER, PlayerData.Channel.NATION)) {
            DominionEvent.ChatLine l = ChatServiceImpl.line(f, c, who, "Lead", "hello", 5L);
            assertEquals(c.name().toLowerCase(java.util.Locale.ROOT), l.channel());
            assertEquals(who, l.sender());
            assertEquals(5L, l.millis());
        }
    }
}
