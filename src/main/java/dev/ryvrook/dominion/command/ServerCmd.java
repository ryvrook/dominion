package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A command that also works from the server console. The call has no player when run there. */
public final class ServerCmd extends CommandBase implements Sealable {
    private Consumer<Call> handler;
    private final List<AbstractCommand> children = new ArrayList<>();
    private final List<ServerCmd> variants = new ArrayList<>();
    private boolean sealed;

    public ServerCmd(String name, String description) {
        super(name, description);
        requireNoPermission();
    }

    public ServerCmd(String description) {
        super(description);
        requireNoPermission();
    }

    public ServerCmd handler(Consumer<Call> handler) {
        this.handler = handler;
        return this;
    }

    public ServerCmd permission(String node) {
        requirePermission(node);
        return this;
    }

    /** Records a sub command. It is attached in seal once it is complete. */
    public <C extends AbstractCommand> C sub(C command) {
        children.add(command);
        return command;
    }

    public ServerCmd variant(ServerCmd variant) {
        variants.add(variant);
        return variant;
    }

    @Override
    public void seal() {
        if (sealed) {
            return;
        }
        sealed = true;
        for (ServerCmd v : variants) {
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
    protected void executeSync(CommandContext context) {
        Handlers.run(handler, new Call(context, null, null, null, null));
    }
}
