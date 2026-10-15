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
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
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
    /** Sub command names left out when sealing, from commands.disabled. */
    private Set<String> skipped = Set.of();
    /** The feature switch of this command, passed down to its variants and sub commands when sealing. */
    private BooleanSupplier gate;

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

    /** Refuses the command, its variants and sub commands with the feature off sentence while the gate is false. */
    public PlayerCmd gate(BooleanSupplier enabled) {
        gate = enabled;
        return this;
    }

    /** Gates the sub commands named in the map, by lower case name. Call before seal. */
    public void gateChildren(Map<String, BooleanSupplier> gates) {
        for (AbstractCommand child : children) {
            String name = child.getName();
            BooleanSupplier g = name == null ? null : gates.get(name.toLowerCase(java.util.Locale.ROOT));
            if (g != null && child instanceof PlayerCmd p && p.gate == null) {
                p.gate = g;
            }
        }
    }

    /** Leaves out the sub commands with these lower case names when sealing. */
    public PlayerCmd skip(Set<String> names) {
        skipped = names == null ? Set.of() : Set.copyOf(names);
        return this;
    }

    /** True when seal leaves this child out, named by its name or one of its aliases. */
    static boolean skipped(AbstractCommand child, Set<String> names) {
        String name = child.getName();
        if (name != null && names.contains(name.toLowerCase(java.util.Locale.ROOT))) {
            return true;
        }
        Set<String> aliases = child.getAliases();
        for (String alias : aliases == null ? Set.<String>of() : aliases) {
            if (alias != null && names.contains(alias.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The names given to skip plus the name of every sub command they leave out, so an alias in
     * commands.disabled also hides the panel tab of its command.
     */
    public Set<String> skippedNames() {
        Set<String> out = new java.util.LinkedHashSet<>(skipped);
        for (AbstractCommand child : children) {
            if (child.getName() != null && skipped(child, skipped)) {
                out.add(child.getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out;
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
            if (v.gate == null) {
                v.gate = gate;
            }
            v.seal();
            addUsageVariant(v);
        }
        for (AbstractCommand child : children) {
            if (skipped(child, skipped)) {
                continue;
            }
            if (child instanceof PlayerCmd p && p.gate == null) {
                p.gate = gate;
            }
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
        Handlers.run(handler, call, gate);
    }
}
