package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;

import java.util.UUID;

/** Balances kept in PlayerData. Every change runs through {@link Books} on the live record under the ledger lock. */
final class InternalLedger implements BalanceProvider {
    private final DominionContext ctx;
    private final Books books;

    InternalLedger(DominionContext ctx, Books books) {
        this.ctx = ctx;
        this.books = books;
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
        String failed = books.walletWithdraw(player, amount);
        return failed == null ? Result.pass() : Result.fail(failed);
    }

    @Override
    public Result deposit(UUID player, double amount, String reason) {
        books.walletDeposit(player, amount);
        return Result.pass();
    }

    @Override
    public void set(UUID player, double amount) {
        books.walletSet(player, amount);
    }
}
