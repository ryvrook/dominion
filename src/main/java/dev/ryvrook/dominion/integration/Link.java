package dev.ryvrook.dominion.integration;

/**
 * A loaded bridge to one sibling. Implemented only by the bridge classes, which are the only classes that use the
 * sibling's types. Every method is safe from any thread and never throws.
 */
public interface Link {
    /** True while the sibling's API is linked and in use. */
    boolean bound();

    /** The sibling version linked to, or null. */
    String version();

    /** Every 5 seconds from the integration watch, for bridges that have to look for their API again. */
    default void poll() {}

    /** Removes every hook and listener. Safe to call twice. Nothing binds again afterwards. */
    void stop();
}
