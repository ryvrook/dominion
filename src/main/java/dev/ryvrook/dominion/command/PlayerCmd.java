package dev.ryvrook.dominion.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A player command whose behaviour is a handler set after its arguments are declared.
 * Commands are open to every player unless a permission is required explicitly, because the
 * engine checks each sub command on its own and never the parent.
 */
public final class PlayerCmd extends AbstractPlayerCommand implements Sealable {
    private Consumer<Call> handler;
    private final List<AbstractCommand> children = new ArrayList<>();
    private final List<PlayerCmd> variants = new ArrayList<>();
    private boolean sealed;

    public PlayerCmd(String name, String description) {
        super(name, description);
        requireNoPermission();
    }

    /** Usage variant constructor. Variants have no name and are chosen by argument count. */
    public PlayerCmd(String description) {
        super(description);
        requireNoPermission();
    }

    public PlayerCmd handler(Consumer<Call> handler) {
        this.handler = handler;
        return this;
    }

    public PlayerCmd permission(String node) {
        requirePermission(node);
        return this;
    }

    public PlayerCmd aliases(String... aliases) {
        addAliases(aliases);
        return this;
    }

    /** Records a sub command. It is attached in seal once it is complete. */
    public <C extends AbstractCommand> C sub(C command) {
        children.add(command);
        return command;
    }

    /** Records a variant. Declare its arguments before seal runs. */
    public PlayerCmd variant(PlayerCmd variant) {
        variants.add(variant);
        return variant;
    }

    @Override
    public void seal() {
        if (sealed) {
            return;
        }
        sealed = true;
        for (PlayerCmd v : variants) {
            v.seal();
            addUsageVariant(v);
        }
        for (AbstractCommand child : children) {
            if (child instanceof Sealable s) {
                s.seal();
            }
            addSubCommand(child);
        }
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        Call call = new Call(context, store, ref, playerRef, world);
        Handlers.run(handler, call);
    }
}
