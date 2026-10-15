package dev.ryvrook.dominion.economy;

import java.util.UUID;

/**
 * Where the Coinage link meets the economy. Registered as a service under its own class by the economy module, so
 * {@code integration.coinage.CoinageLink} finds it without the economy naming a Coinage type. The link attaches
 * its {@link CoinagePort} once Coinage is up, detaches it when Coinage or Dominion stops, and forwards the Coinage
 * listener and net worth calls here.
 */
public final class CoinageSeam {
    private final EconomyServiceImpl service;
    private volatile CoinagePort port;

    CoinageSeam(EconomyServiceImpl service) {
        this.service = service;
    }

    /** The attached port, or null. */
    public CoinagePort port() {
        return port;
    }

    /** Attaches the link. Refreshes the mirrors and rights at once, so nothing waits for the minute timer. */
    public void attach(CoinagePort p) {
        port = p;
        if (p != null) {
            try {
                service.coinageTick();
            } catch (RuntimeException ignored) {
                // the minute timer tries again
            }
        }
    }

    /** Detaches the link when it is the attached one. */
    public void detach(CoinagePort p) {
        if (port == p) {
            port = null;
        }
    }

    /** The provider name in use, internal, vaultunlocked, item or coinage. */
    public String providerName() {
        return service.provider().name();
    }

    /**
     * Before a move a player started in Coinage. The reason it is refused, or null.
     *
     * @param from   the paying account
     * @param player the player who asked
     * @param amount minor units
     */
    public String veto(UUID from, UUID player, long amount) {
        return service.coinageVeto(from, player, amount);
    }

    /** True when either account is a faction bank, so the listener reads names only for those moves. */
    public boolean touches(UUID a, UUID b) {
        CoinageBanks banks = service.coinageBanks();
        return banks.factionOf(a) != null || banks.factionOf(b) != null;
    }

    /**
     * After any Coinage move. Names come from Coinage's accounts and may be null.
     *
     * @param set    true for an admin set, where {@code toAfter} is the new balance
     * @param player the player who asked, or null for the server or a plugin
     */
    public void moved(UUID from, String fromName, long fromAfter, UUID to, String toName, long toAfter, long amount,
                      boolean set, String plugin, String reason, UUID player) {
        service.coinageMoved(from, fromName, fromAfter, to, toName, toAfter, amount, set, plugin, reason, player);
    }

    /** The faction net worth part of a player, in minor units of the currency. */
    public long share(UUID player, String currency) {
        return service.coinageShare(player, currency);
    }
}
