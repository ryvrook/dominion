package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;

import java.util.List;
import java.util.UUID;

/** Internal ledger plus faction banks. Other providers wrap this interface. */
public interface EconomyService {
    boolean enabled();

    String currencyName();

    String format(double amount);

    double balance(UUID player);

    Result deposit(UUID player, double amount, String reason);

    Result withdraw(UUID player, double amount, String reason);

    Result transfer(UUID from, UUID to, double amount, String reason);

    Result bankDeposit(UUID actor, double amount);

    Result bankWithdraw(UUID actor, double amount);

    Result bankPay(UUID actor, String targetTag, double amount);

    /** Admin set without validation. */
    void set(UUID player, double amount);

    /** Atomically zeroes a faction bank and returns what it held, so nothing can land in between. */
    double bankDrain(Faction faction, String reason);

    /** Takes from a faction bank. Fails when the bank cannot cover it. Audits with the reason. */
    Result bankCharge(Faction faction, double amount, String reason);

    /** Adds to a faction bank and audits with the reason. */
    Result bankCredit(Faction faction, double amount, String reason);

    /** Moves between two faction banks under a consistent lock order. */
    Result bankTransfer(Faction from, Faction to, double amount, String reason);

    /** Name of the active provider, internal or vaultunlocked or item. */
    String providerName();

    /** True when amounts must be whole numbers, as with an item backed currency. */
    default boolean wholeUnits() {
        return "item".equals(providerName());
    }

    List<Faction> richestFactions(int limit);

    List<UUID> richestPlayers(int limit);

    /** Called once a day. Upkeep, dues, interest and stipends. */
    void dailyTick();
}
