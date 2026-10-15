package dev.ryvrook.dominion.economy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the economy needs from Coinage, free of Coinage types so the economy loads on any server. The one
 * implementation is {@code integration.coinage.CoinageLink}, which attaches itself to {@link CoinageSeam} once
 * Coinage is up. Amounts are whole minor units of a currency. A null currency is the primary one. Every method is
 * safe from any thread, reads or changes memory only and never throws.
 */
public interface CoinagePort {
    /** Why a move did not happen, the same cases as Coinage's own. */
    enum Failure {
        NONE, INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, ACCOUNT_LOCKED, ACCOUNT_FROZEN, UNKNOWN_CURRENCY, INVALID_AMOUNT,
        MAX_BALANCE, NOT_PERMITTED, CANCELLED, SAME_ACCOUNT, ALREADY_REVERSED, STOPPING;

        /** The case of the same name, {@link #CANCELLED} for anything unknown. */
        public static Failure of(String name) {
            try {
                return name == null ? CANCELLED : valueOf(name);
            } catch (IllegalArgumentException e) {
                return CANCELLED;
            }
        }
    }

    /** What a faction member may do with the faction account in Coinage's own pages. */
    enum BankRight { BALANCE, DEPOSIT, WITHDRAW }

    /**
     * What a move did.
     *
     * @param message   Coinage's own sentence, used only when another plugin cancelled the move with a reason
     * @param fromAfter the paying account's balance after
     * @param toAfter   the receiving account's balance after
     */
    record Move(boolean ok, Failure failure, String message, long fromAfter, long toAfter) {
        public static Move failed(Failure failure, String message) {
            return new Move(false, failure == null ? Failure.CANCELLED : failure, message == null ? "" : message, 0, 0);
        }
    }

    /** True while Coinage is up and linked. */
    boolean bound();

    /** The id of the currency, the primary one for a null or blank id, or null when Coinage has no such currency. */
    String currencyId(String currency);

    /** Digits after the point of the currency, or -1 when Coinage has no such currency. */
    int decimals(String currency);

    long balance(UUID account, String currency);

    /** Makes sure a player has a Coinage account, which Coinage creates with its starting balance. */
    void ensurePlayer(UUID player);

    Move deposit(UUID to, String currency, long amount, String reason);

    Move withdraw(UUID from, String currency, long amount, String reason);

    Move transfer(UUID from, UUID to, String currency, long amount, String reason);

    Move set(UUID to, String currency, long amount, String reason);

    /** The server treasury account. */
    UUID serverAccount();

    /** The id of the shared account with this handle, or null. */
    UUID account(String handle);

    /** Creates the shared account, or returns the one that has the handle already. Null when it could not. */
    UUID createShared(String handle, String name, UUID owner);

    /** The id of the last transaction applied to the account, 0 before the first, -1 when it cannot be read. */
    long lastTransaction(UUID account);

    /** The owner of a shared account, or null. */
    UUID owner(UUID account);

    boolean setOwner(UUID account, UUID owner);

    /** Members of a shared account other than the owner, with their rights. Empty for an unknown account. */
    Map<UUID, Set<BankRight>> members(UUID account);

    /** Sets a member's rights, an empty set removes the member. */
    boolean setRights(UUID account, UUID member, Set<BankRight> rights);

    /** Deletes an empty shared account. */
    boolean delete(UUID account);

    boolean rename(UUID account, String name);
}
