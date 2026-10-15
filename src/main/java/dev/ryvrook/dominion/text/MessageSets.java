package dev.ryvrook.dominion.text;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.logging.Level;

/**
 * The text classes whose static fields define every message id. Loading them at setup registers every
 * default before messages.json is read, so the file is complete on the first start. Each package adds its
 * own class here, one line, and a class that is missing is skipped with a log line.
 */
public final class MessageSets {
    private MessageSets() {}

    public static final String[] ALL = {
            "dev.ryvrook.dominion.text.CommonText"
    };

    static void load(HytaleLogger logger) {
        for (String name : ALL) {
            try {
                Class.forName(name, true, MessageSets.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                if (logger != null) {
                    logger.at(Level.FINE).log("Dominion text class %s is not present", name);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.at(Level.WARNING).withCause(t).log("Dominion text class %s failed to load", name);
                }
            }
        }
    }
}
