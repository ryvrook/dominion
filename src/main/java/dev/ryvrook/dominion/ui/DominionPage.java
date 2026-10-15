package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.UUID;
import java.util.logging.Level;

/** Shared plumbing for every Dominion page. Subclasses render in build and react in onAction. */
public abstract class DominionPage extends InteractiveCustomUIPage<PageEvent> {
    protected final DominionContext ctx;
    protected final UiServiceImpl ui;

    protected DominionPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui) {
        super(player, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
        this.ctx = ctx;
        this.ui = ui;
    }

    protected PlayerRef player() {
        return playerRef;
    }

    protected UUID uuid() {
        return playerRef.getUuid();
    }

    protected String username() {
        return playerRef.getUsername();
    }

    protected FactionService factions() {
        return ctx.service(FactionService.class);
    }

    /**
     * Renders the page. A failure is logged and reported in chat so the page stays open with
     * whatever was drawn before the error, instead of the exception reaching the page manager.
     */
    @Override
    public final void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        try {
            render(ref, commands, events, store);
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion page %s failed to render", getClass().getSimpleName());
            Msg.send(playerRef, Msg.bad(Messages.get(UiText.PAGE_RENDER_FAILED)));
        }
    }

    /** Draws the page. Runs on the player's world thread. */
    protected abstract void render(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store);

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEvent data) {
        if (data == null || data.action == null) {
            return;
        }
        boolean refresh;
        try {
            refresh = onAction(data);
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion page action %s failed", data.action);
            Msg.send(playerRef, Msg.bad(Messages.get(UiText.BUTTON_FAILED)));
            refresh = true;
        }
        if (refresh) {
            safeRebuild();
        }
    }

    /** Rebuilds without letting a missing player component or render error escape. */
    protected void safeRebuild() {
        try {
            rebuild();
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion page %s failed to rebuild", getClass().getSimpleName());
        }
    }

    /** Handles one button press. Returns true when the page should rebuild afterwards. */
    protected abstract boolean onAction(PageEvent event);

    /** Sends a service result to the player in chat. */
    protected void reply(Result result) {
        if (result == null) {
            return;
        }
        String text = result.message() == null || result.message().isBlank()
                ? (result.success() ? Messages.get(UiText.DONE) : Messages.get(UiText.DID_NOT_WORK))
                : result.message();
        Msg.send(playerRef, result.success() ? Msg.good(text) : Msg.bad(text));
    }

    protected void closePage() {
        close();
    }

    /** Finds a player by name, online first, then faction rosters and loaded player data. */
    protected UUID resolvePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim();
        PlayerRef online = Players.byName(n);
        if (online != null) {
            return online.getUuid();
        }
        for (Faction f : factions().all()) {
            for (Member m : f.members()) {
                if (m.name().equalsIgnoreCase(n)) {
                    return m.uuid();
                }
            }
        }
        for (PlayerData p : ctx.repo().loadedPlayers()) {
            if (p.name().equalsIgnoreCase(n)) {
                return p.uuid();
            }
        }
        return null;
    }

    protected static String safeColor(String color, String fallback) {
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
            return color;
        }
        return fallback;
    }
}
