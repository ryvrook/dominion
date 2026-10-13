package dev.ryvrook.dominion.chat;

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.ChatService;

import java.util.logging.Level;

/** Faction, ally and officer channels, relation coloured tags and the login message of the day. */
public final class ChatModule implements DominionModule {
    private ChatServiceImpl service;

    public ChatModule() {}

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new ChatServiceImpl(ctx);
        ctx.register(ChatService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ChatServiceImpl chat = service;
        ctx.plugin().getEventRegistry().<String, PlayerChatEvent>registerAsyncGlobal(PlayerChatEvent.class,
                future -> future.thenApply(event -> {
                    try {
                        chat.handle(event);
                    } catch (Exception e) {
                        // Never break chat. The event goes through untouched if Dominion fails.
                        ctx.logger().at(Level.WARNING).withCause(e).log("Dominion chat handling failed");
                    }
                    return event;
                }));
        ctx.onPlayerReady(chat::onPlayerReady);
    }
}
