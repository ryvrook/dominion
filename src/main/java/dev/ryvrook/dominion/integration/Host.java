package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;

import java.util.logging.Level;

/** What a bridge gets from Dominion. Free of sibling types, so the module can build it on any server. */
public final class Host {
    private final DominionContext ctx;
    private final StatFanout stats;
    private final Choice choice;

    Host(DominionContext ctx, StatFanout stats, Choice choice) {
        this.ctx = ctx;
        this.stats = stats;
        this.choice = choice;
    }

    public DominionContext ctx() {
        return ctx;
    }

    /** Where a bridge that takes counters registers itself. */
    public StatFanout stats() {
        return stats;
    }

    /** The sibling version the plugin manager reported at start. */
    public String foundVersion() {
        return choice.found().version();
    }

    public void info(String text) {
        ctx.logger().at(Level.INFO).log("%s", text);
    }

    public void warn(String text) {
        ctx.logger().at(Level.WARNING).log("%s", text);
    }

    public void warn(Throwable cause, String text) {
        ctx.logger().at(Level.WARNING).withCause(cause).log("%s", text);
    }
}
