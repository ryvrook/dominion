package dev.ryvrook.dominion.market;

import dev.ryvrook.dominion.DominionContext;

import java.util.UUID;

/**
 * The hand off of the panel Market tab to another market, Coinage's while it is linked and market.useCoinage is on.
 * Registered as a service under its own class by the market module. The Coinage link attaches a {@link Target}
 * and the panel asks {@link #active} before it shows Dominion's own listings. Dominion's market keeps running
 * underneath, so listings already placed, auctions and the mailbox still settle.
 */
public final class MarketHandoff {
    /** Another market that can open its page for a player. */
    public interface Target {
        /** True while its market is on. */
        boolean available();

        /** Opens its market page. False when the player is not online or the market is off. */
        boolean open(UUID player);
    }

    private final DominionContext ctx;
    private volatile Target target;

    MarketHandoff(DominionContext ctx) {
        this.ctx = ctx;
    }

    public void attach(Target t) {
        target = t;
    }

    /** Detaches the target when it is the attached one. */
    public void detach(Target t) {
        if (target == t) {
            target = null;
        }
    }

    /** True while the panel Market tab opens the other market. */
    public boolean active() {
        Target t = target;
        if (t == null || !ctx.settings().bool("market.useCoinage")) {
            return false;
        }
        try {
            return t.available();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Opens the other market for a player. False when it could not. */
    public boolean open(UUID player) {
        Target t = target;
        if (t == null) {
            return false;
        }
        try {
            return t.open(player);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
