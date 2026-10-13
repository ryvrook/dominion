package dev.ryvrook.dominion;

/**
 * A feature module. Each module registers its services into the context during init, then
 * registers commands, systems and listeners during start once every service exists.
 */
public interface DominionModule {
    String name();

    void init(DominionContext ctx);

    default void start(DominionContext ctx) {}

    default void shutdown(DominionContext ctx) {}
}
