package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionPlugin;
import dev.ryvrook.dominion.text.Msg;

import java.util.function.Consumer;
import java.util.logging.Level;

/** Runs command handlers so one failing command never leaks a stack trace to the player. */
final class Handlers {
    private Handlers() {}

    static void run(Consumer<Call> handler, Call call) {
        if (handler == null) {
            call.send(Msg.info("Use /f help to see what you can do."));
            return;
        }
        try {
            handler.accept(call);
        } catch (IllegalStateException e) {
            call.send(Msg.bad("That feature is not available on this server right now."));
            log(e);
        } catch (Exception e) {
            call.send(Msg.bad("Something went wrong running that command."));
            log(e);
        }
    }

    private static void log(Exception e) {
        DominionPlugin plugin = DominionPlugin.get();
        if (plugin != null) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("Dominion command failed");
        }
    }
}
