package dev.ryvrook.dominion.chat;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.ChatService;

import java.util.logging.Level;

/** Faction, ally and officer channels, relation coloured tags and the login message of the day. */
public final class ChatModule implements DominionModule {
    private ChatServiceImpl service;
    private Nameplates nameplates;

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
        // Private channels are routed at normal priority, so a relay or logger that runs later never
        // sees faction, ally, officer or nation lines, while a mute plugin that runs earlier still can
        // cancel them. Public chat is formatted last, after title and rank plugins set their formatter,
        // so their prefix is kept inside Dominion's line.
        ctx.plugin().getEventRegistry().<String, PlayerChatEvent>registerAsyncGlobal(EventPriority.NORMAL, PlayerChatEvent.class,
                future -> future.thenApply(event -> guarded(ctx, () -> chat.handle(event), event)));
        ctx.plugin().getEventRegistry().<String, PlayerChatEvent>registerAsyncGlobal(EventPriority.LAST, PlayerChatEvent.class,
                future -> future.thenApply(event -> guarded(ctx, () -> chat.handlePublic(event), event)));
        ctx.onPlayerReady(chat::onPlayerReady);

        Announcements announcements = new Announcements(ctx);
        ctx.bus().subscribe(announcements::onEvent);

        Nameplates plates = new Nameplates(ctx);
        nameplates = plates;
        ctx.plugin().getEntityStoreRegistry().registerSystem(plates);
        ctx.bus().subscribe(plates::onEvent);
        ctx.onPlayerReady(plates::onPlayerReady);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        Nameplates plates = nameplates;
        if (plates != null) {
            plates.restoreAll();
        }
    }

    /** Never breaks chat. The event goes on untouched if Dominion fails. */
    private static PlayerChatEvent guarded(DominionContext ctx, Runnable work, PlayerChatEvent event) {
        try {
            work.run();
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion chat handling failed");
        }
        return event;
    }
}
