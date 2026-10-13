package dev.ryvrook.dominion.command;

/**
 * The engine finalises a command as soon as it is attached to a parent or registered, after
 * which no argument, alias, variant or sub command can be added. Dominion commands therefore
 * collect their children and attach them bottom up in seal, which must run before the root
 * command is registered.
 */
public interface Sealable {
    void seal();
}
