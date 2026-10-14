package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.EconomyService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Just enough economy for claim costs and refunds. Faction banks live on the faction itself. */
final class FakeEconomy implements EconomyService {
    final Map<UUID, Double> wallets = new HashMap<>();

    @Override public boolean enabled() { return true; }
    @Override public String currencyName() { return "Coins"; }
    @Override public String format(double amount) { return String.format("%.2fc", amount); }
    @Override public double balance(UUID player) { return wallets.getOrDefault(player, 0.0); }
    @Override public Result deposit(UUID player, double amount, String reason) { wallets.merge(player, amount, Double::sum); return Result.pass(); }
    @Override public Result withdraw(UUID player, double amount, String reason) {
        if (balance(player) < amount) return Result.fail("Not enough coins");
        wallets.merge(player, -amount, Double::sum);
        return Result.pass();
    }
    @Override public Result transfer(UUID from, UUID to, double amount, String reason) { return Result.fail("unused"); }
    @Override public Result bankDeposit(UUID actor, double amount) { return Result.fail("unused"); }
    @Override public Result bankWithdraw(UUID actor, double amount) { return Result.fail("unused"); }
    @Override public Result bankPay(UUID actor, String targetTag, double amount) { return Result.fail("unused"); }
    @Override public void set(UUID player, double amount) { wallets.put(player, amount); }
    @Override public double bankDrain(Faction faction, String reason) {
        synchronized (faction) { double b = faction.bank(); faction.setBank(0); return b; }
    }
    @Override public Result bankCharge(Faction faction, double amount, String reason) {
        synchronized (faction) {
            if (faction.bank() + 1e-6 < amount) return Result.fail("The faction bank cannot cover it");
            faction.setBank(faction.bank() - amount);
        }
        return Result.pass();
    }
    @Override public Result bankCredit(Faction faction, double amount, String reason) {
        synchronized (faction) { faction.setBank(faction.bank() + amount); }
        return Result.pass();
    }
    @Override public Result bankTransfer(Faction from, Faction to, double amount, String reason) { return Result.fail("unused"); }
    @Override public String providerName() { return "internal"; }
    @Override public List<Faction> richestFactions(int limit) { return List.of(); }
    @Override public List<UUID> richestPlayers(int limit) { return List.of(); }
    @Override public void dailyTick() {}
}
