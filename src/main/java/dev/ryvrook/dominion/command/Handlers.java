package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionPlugin;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.util.function.Consumer;
import java.util.logging.Level;

/** Runs command handlers so one failing command never leaks a stack trace to the player. */
final class Handlers {
    private Handlers() {}

    static void run(Consumer<Call> handler, Call call) {
        if (handler == null) {
            call.send(Msg.info(Messages.get(CommandText.USE_F_HELP_TO_SEE_WHAT)));
            return;
        }
        try {
            handler.accept(call);
        } catch (IllegalStateException e) {
            call.send(Msg.bad(Messages.get(CommandText.THAT_FEATURE_IS_NOT_AVAILABLE_ON)));
            log(e);
        } catch (Exception e) {
            call.send(Msg.bad(Messages.get(CommandText.SOMETHING_WENT_WRONG_RUNNING_THAT_COMMAND)));
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
