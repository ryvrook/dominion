package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.PlayerData;

import java.util.UUID;
import java.util.function.DoubleFunction;

/** Balances kept in PlayerData. Every change runs under the shared ledger lock. */
final class InternalLedger implements BalanceProvider {
    private final DominionContext ctx;
    private final Object lock;
    private final DoubleFunction<String> format;

    InternalLedger(DominionContext ctx, Object lock, DoubleFunction<String> format) {
        this.ctx = ctx;
        this.lock = lock;
        this.format = format;
    }

    @Override
    public String name() {
        return "internal";
    }

    @Override
    public double balance(UUID player) {
        return ctx.player(player).balance();
    }

    @Override
    public Result withdraw(UUID player, double amount, String reason) {
        synchronized (lock) {
            PlayerData p = ctx.player(player);
            if (!Money.covers(p.balance(), amount)) {
                return Result.fail("You need " + format.apply(amount) + " but only have " + format.apply(p.balance()));
            }
            p.setBalance(Money.round(p.balance() - amount));
        }
        return Result.pass();
    }

    @Override
    public Result deposit(UUID player, double amount, String reason) {
        synchronized (lock) {
            PlayerData p = ctx.player(player);
            p.setBalance(Money.round(p.balance() + amount));
        }
        return Result.pass();
    }

    @Override
    public void set(UUID player, double amount) {
        synchronized (lock) {
            ctx.player(player).setBalance(Money.round(Math.max(0, amount)));
        }
    }

    /** Adds without rounding, for the per minute stipend that is smaller than a cent. Caller holds no lock. */
    void addRaw(PlayerData p, double amount) {
        synchronized (lock) {
            p.setBalance(p.balance() + amount);
        }
    }
}
