package dev.ryvrook.dominion.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.Argument;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.text.Msg;

import java.util.UUID;

/**
 * One command invocation. Player commands fill every field. Server commands run from the
 * console have no player, store, ref or world, so those are null.
 */
public final class Call {
    private final CommandContext context;
    private final Store<EntityStore> store;
    private final Ref<EntityStore> ref;
    private final PlayerRef player;
    private final World world;

    public Call(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        this.context = context;
        this.store = store;
        this.ref = ref;
        this.player = player;
        this.world = world;
    }

    public CommandContext context() { return context; }
    public Store<EntityStore> store() { return store; }
    public Ref<EntityStore> ref() { return ref; }
    public PlayerRef player() { return player; }
    public World world() { return world; }

    public boolean isPlayer() {
        return player != null;
    }

    /** The acting player's UUID, or the sender's UUID for server commands, or null for the console. */
    public UUID uuid() {
        if (player != null) {
            return player.getUuid();
        }
        return context.isPlayer() ? context.sender().getUuid() : null;
    }

    public String name() {
        if (player != null) {
            return player.getUsername();
        }
        return context.sender().getUsername();
    }

    public <T> T arg(Argument<?, T> argument) {
        return argument.get(context);
    }

    public boolean has(Argument<?, ?> argument) {
        return argument.provided(context);
    }

    public void send(Message message) {
        context.sendMessage(message);
    }

    public void reply(Result result) {
        if (result == null) {
            return;
        }
        if (result.success()) {
            if (!result.message().isBlank()) {
                send(Msg.good(result.message()));
            }
        } else {
            send(Msg.bad(result.message().isBlank() ? "That did not work." : result.message()));
        }
    }

    public void good(String text) {
        send(Msg.good(text));
    }

    public void bad(String text) {
        send(Msg.bad(text));
    }

    public void info(String text) {
        send(Msg.info(text));
    }
}
