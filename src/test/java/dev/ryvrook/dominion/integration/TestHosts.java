package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;

/** Builds the Host a bridge gets, for bridge tests in other packages. */
public final class TestHosts {
    private TestHosts() {}

    /** A host for a sibling found running at the version. */
    public static Host host(DominionContext ctx, Sibling sibling, String version) {
        Choice choice = Choice.decide(sibling, Mode.AUTO, new Presence.Found(new Object(), version, true));
        return new Host(ctx, new StatFanout(new ContextFacts(ctx)), choice);
    }
}
