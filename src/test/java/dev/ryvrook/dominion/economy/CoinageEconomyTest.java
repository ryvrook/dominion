package dev.ryvrook.dominion.economy;

import dev.ryvrook.coinage.api.AccountPermission;
import dev.ryvrook.coinage.api.AccountStatus;
import dev.ryvrook.coinage.api.Accounts;
import dev.ryvrook.coinage.api.Failure;
import dev.ryvrook.coinage.api.Initiator;
import dev.ryvrook.coinage.api.Request;
import dev.ryvrook.coinage.api.Transaction;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.integration.Sibling;
import dev.ryvrook.dominion.integration.TestHosts;
import dev.ryvrook.dominion.integration.coinage.CoinageLink;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Wallets and faction banks in Coinage, through the real Coinage link over a stand in Coinage API. Checks the
 * provider choice, the conversion on the way, the faction account from its creation and one time seed to its
 * rights, the listener that mirrors and guards it, the net worth part, and the drain and delete at disband.
 */
class CoinageEconomyTest {
    Fixtures fx;
    EconomyServiceImpl eco;
    FakeCoinage coinage;
    Link link;

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        new EconomyModule().init(fx.ctx);
        eco = (EconomyServiceImpl) fx.ctx.service(EconomyService.class);
        fx.set("eco.provider", "coinage");
        coinage = new FakeCoinage();
        coinage.up();
        link = CoinageLink.install(TestHosts.host(fx.ctx, Sibling.COINAGE, "1.0.0"));
    }

    @AfterEach
    void tearDown() {
        link.stop();
        coinage.down();
        fx.close();
    }

    private UUID account(Faction f) {
        return coinage.accountsApi.byHandle("dominion:" + f.id()).map(a -> a.id()).orElse(null);
    }

    private boolean logged(Faction f, String text) {
        for (AuditEntry e : f.audit()) {
            if (e.text().equals(text)) {
                return true;
            }
        }
        return false;
    }

    // ---- provider choice ------------------------------------------------------------------------------

    @Test
    void autoPicksCoinageThenVaultUnlockedThenInternal() {
        assertEquals(EconomyServiceImpl.Kind.COINAGE, EconomyServiceImpl.select("auto", true, () -> fail("not asked")));
        assertEquals(EconomyServiceImpl.Kind.COINAGE, EconomyServiceImpl.select("", true, () -> fail("not asked")));
        assertEquals(EconomyServiceImpl.Kind.VAULT, EconomyServiceImpl.select("auto", false, () -> true));
        assertEquals(EconomyServiceImpl.Kind.INTERNAL, EconomyServiceImpl.select(" Auto ", false, () -> false));
        assertEquals(EconomyServiceImpl.Kind.INTERNAL, EconomyServiceImpl.select(null, false, () -> false));
        assertEquals(EconomyServiceImpl.Kind.COINAGE, EconomyServiceImpl.select("coinage", false, () -> fail("not asked")));
        assertEquals(EconomyServiceImpl.Kind.INTERNAL, EconomyServiceImpl.select("internal", true, () -> fail("not asked")));
        assertEquals(EconomyServiceImpl.Kind.VAULT, EconomyServiceImpl.select("vault", true, () -> fail("not asked")));
        assertEquals(EconomyServiceImpl.Kind.ITEM, EconomyServiceImpl.select("items", true, () -> fail("not asked")));
        assertEquals(EconomyServiceImpl.Kind.UNKNOWN, EconomyServiceImpl.select("bank", true, () -> fail("not asked")));
    }

    @Test
    void theDefaultIsAutoAndCoinageFallsBackWhileItIsNotLinked() {
        try (Fixtures fresh = Fixtures.create()) {
            assertEquals("auto", fresh.settings().text("eco.provider"));
        }
        assertEquals("coinage", eco.providerName());
        link.stop();
        assertEquals("internal", eco.providerName());
    }

    @Test
    void aNewPlayerGetsNoDominionStartingBalanceWhenCoinageHoldsTheWallets() {
        fx.set("eco.startingBalance", 100.0);
        assertEquals(0.0, fx.ctx.player(UUID.randomUUID()).balance(), "Coinage hands out its own");
        fx.set("eco.provider", "internal");
        assertEquals(100.0, fx.ctx.player(UUID.randomUUID()).balance(), "the internal ledger still grants it");
    }

    // ---- wallets --------------------------------------------------------------------------------------

    @Test
    void walletsMoveThroughCoinageWithDominionAsThePlugin() {
        UUID ann = fx.player("Ann");
        assertTrue(eco.deposit(ann, 12.34, "test").success());
        assertEquals(1234, coinage.bal(ann));
        assertEquals(12.34, eco.balance(ann));
        Transaction tx = coinage.by("Dominion").get(0);
        assertEquals(Transaction.Type.DEPOSIT, tx.type());
        assertEquals(Initiator.Type.PLUGIN, tx.initiator().type());
        assertEquals("test", tx.reason());

        Result r = eco.withdraw(ann, 20, "test");
        assertTrue(r.failed());
        assertEquals(Messages.get(EconomyText.NEED_HAVE, "amount", eco.format(20), "have", eco.format(12.34)), r.message());

        UUID bob = fx.player("Bob");
        assertTrue(eco.transfer(ann, bob, 2.34, "pay").success());
        assertEquals(1000, coinage.bal(ann));
        assertEquals(234, coinage.bal(bob));

        eco.set(ann, 3);
        assertEquals(300, coinage.bal(ann));
        assertFalse(coinage.by("Dominion").isEmpty());
    }

    @Test
    void failuresReadAsDominionSentences() {
        UUID ann = fx.player("Ann");
        coinage.give(ann, 500);
        coinage.accountsApi.setStatus(ann, AccountStatus.FROZEN, Initiator.server());
        assertEquals(Messages.get(EconomyText.COINAGE_FROZEN), eco.withdraw(ann, 1, "x").message());

        fx.set("eco.coinageCurrency", "gems");
        assertEquals(Messages.get(EconomyText.COINAGE_NO_CURRENCY, "currency", "gems"), eco.deposit(ann, 1, "x").message());

        DoubleFunction<String> fmt = a -> "$" + a;
        CoinagePort.Move cancelled = CoinagePort.Move.failed(CoinagePort.Failure.CANCELLED, "A region plugin said no");
        assertEquals("A region plugin said no", CoinageBridge.reason(cancelled, 1, () -> 0, null, null, fmt));
        assertEquals(Messages.get(EconomyText.PAYMENT_REFUSED),
                CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.CANCELLED, ""), 1, () -> 0, null, null, fmt));
        assertEquals("short bank", CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.INSUFFICIENT_FUNDS, ""),
                1, () -> 0, "short bank", null, fmt));
        assertEquals(Messages.get(EconomyText.COINAGE_LOCKED),
                CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.ACCOUNT_LOCKED, ""), 1, () -> 0, null, null, fmt));
        assertEquals(Messages.get(EconomyText.COINAGE_MAX_BALANCE),
                CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.MAX_BALANCE, ""), 1, () -> 0, null, null, fmt));
        assertEquals(Messages.get(EconomyText.NOT_RESPONDING),
                CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.STOPPING, ""), 1, () -> 0, null, null, fmt));
        assertEquals(Messages.get(EconomyText.COINAGE_NO_ACCOUNT),
                CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.ACCOUNT_NOT_FOUND, ""), 1, () -> 0, null, null, fmt));
        for (Failure f : Failure.values()) {
            String text = CoinageBridge.reason(CoinagePort.Move.failed(CoinagePort.Failure.of(f.name()), ""), 1, () -> 0, null, "x", fmt);
            assertNotNull(text, f.name());
            assertFalse(text.isBlank(), f.name());
        }
        assertEquals(CoinagePort.Failure.CANCELLED, CoinagePort.Failure.of("SOMETHING_NEW"));
        assertEquals(Messages.get(EconomyText.COINAGE_WHOLE), CoinageBridge.tooFine(1.5, 0));
        assertEquals(Messages.get(EconomyText.COINAGE_DECIMALS, "decimals", 1), CoinageBridge.tooFine(1.25, 1));
    }

    // ---- faction banks --------------------------------------------------------------------------------

    @Test
    void theBankMovesIntoASharedAccountOnceAndRanksStayInFront() {
        Faction f = fx.faction("ABC");
        UUID lead = f.leader();
        f.setBank(50);
        coinage.give(lead, 10_000);

        assertTrue(eco.bankDeposit(lead, 10).success());
        UUID acc = account(f);
        assertNotNull(acc);
        assertEquals("dominion:" + f.id(), f.coinageAccount());
        assertEquals(lead, coinage.acc(acc).owner());
        assertEquals(6000, coinage.bal(acc));
        assertEquals(60.0, f.bank());
        assertTrue(eco.bankDeposit(lead, 5).success());
        assertEquals(6500, coinage.bal(acc));
        long seeds = coinage.log.stream().filter(t -> t.reason().equals(Messages.get(EconomyText.REASON_BANK_TO_COINAGE))).count();
        assertEquals(1, seeds, "the old bank moved in once");

        UUID off = fx.member(f, "Off", Rank.OFFICER);
        UUID rec = fx.member(f, "Rec", Rank.RECRUIT);
        fx.set("eco.withdrawDailyLimitOfficer", 20.0);
        assertTrue(eco.bankWithdraw(off, 15).success());
        assertEquals(1500, coinage.bal(off));
        assertEquals(Messages.get(EconomyText.WITHDRAW_LEFT_TODAY, "amount", eco.format(5)), eco.bankWithdraw(off, 10).message());
        assertEquals(Messages.get(EconomyText.RANK_NO_WITHDRAW), eco.bankWithdraw(rec, 1).message());
        assertEquals(Messages.get(EconomyText.BANK_ONLY_HOLDS, "amount", eco.format(50)), eco.bankWithdraw(lead, 500).message());
        assertEquals(50.0, f.bank());

        eco.coinageTick();
        assertEquals(EnumSet.of(AccountPermission.BALANCE, AccountPermission.DEPOSIT, AccountPermission.WITHDRAW),
                coinage.acc(acc).permissions(off));
        assertEquals(EnumSet.of(AccountPermission.BALANCE, AccountPermission.DEPOSIT), coinage.acc(acc).permissions(rec));
        assertEquals("[ABC] ABC", coinage.acc(acc).name());

        f.removeMember(rec);
        f.setLeader(off);
        eco.coinageTick();
        assertEquals(off, coinage.acc(acc).owner());
        assertTrue(coinage.acc(acc).permissions(rec).isEmpty(), "a member who left loses the account");
        assertFalse(coinage.acc(acc).permissions(lead).isEmpty(), "the old leader keeps the rights of the rank");
    }

    @Test
    void dailyMovesAndOtherModulesUseTheSharedAccount() {
        Faction f = fx.faction("ABC");
        Faction g = fx.faction("XYZ");
        UUID lead = f.leader();
        coinage.give(lead, 1000);
        assertTrue(eco.bankCredit(f, 40, "a mission").success());
        assertTrue(eco.bankCharge(f, 15, "a claim").success());
        assertEquals(2500, coinage.bal(account(f)));
        assertEquals(Messages.get(EconomyText.CHARGE_BANK_SHORT, "cost", eco.format(100), "faction", "ABC", "amount", eco.format(25)),
                eco.bankCharge(f, 100, "a claim").message());
        assertTrue(eco.bankTransfer(f, g, 5, "a kill").success());
        assertEquals(500, coinage.bal(account(g)));
        assertEquals(20.0, f.bank());
        assertTrue(eco.bankPay(lead, "XYZ", 5).success());
        assertEquals(15.0, f.bank());
        assertEquals(10.0, g.bank());

        int[] missed = {0};
        assertEquals(4.0, eco.coinageBanks().dues(f, List.of(lead, UUID.randomUUID()), 4, missed));
        assertEquals(1, missed[0]);
        assertEquals(600, coinage.bal(lead));
        Books.Upkeep u = eco.coinageBanks().upkeep(f, 30);
        assertFalse(u.paid());
        assertEquals(19.0, u.taken());
        assertEquals(0.0, f.bank());
        coinage.ledgerApi.deposit(Request.deposit(account(f), null, 10_000));
        assertEquals(1.0, eco.coinageBanks().interest(f, 1));
        assertEquals(101.0, f.bank(), "the listener mirrors a Coinage deposit and the interest");
    }

    @Test
    void aBankSeededBeforeACrashIsNotSeededAgain() {
        Faction f = fx.faction("ABC");
        f.setBank(30);
        UUID acc = coinage.accountsApi.createShared("dominion:" + f.id(), "x", f.leader(), "Dominion").id();
        coinage.ledgerApi.deposit(Request.deposit(acc, null, 3000));
        assertTrue(eco.bankCredit(f, 1, "x").success());
        assertEquals(3100, coinage.bal(acc));
        assertEquals(31.0, f.bank());

        Faction g = fx.faction("XYZ");
        g.setBank(7);
        UUID empty = coinage.accountsApi.createShared("dominion:" + g.id(), "y", g.leader(), "Dominion").id();
        assertTrue(eco.bankCredit(g, 1, "x").success());
        assertEquals(800, coinage.bal(empty), "an account made before a crash but never paid is seeded");
    }

    @Test
    void aBankSeededAndSpentToZeroBeforeACrashIsNotSeededAgain() {
        Faction f = fx.faction("ABC");
        f.setBank(30);
        UUID acc = coinage.accountsApi.createShared("dominion:" + f.id(), "x", f.leader(), "Dominion").id();
        coinage.ledgerApi.deposit(Request.deposit(acc, null, 3000));
        coinage.ledgerApi.withdraw(Request.withdraw(acc, null, 3000));
        assertTrue(eco.bankCredit(f, 1, "x").success());
        assertEquals(100, coinage.bal(acc), "the faction file lost the handle but the account already took its seed");
        assertEquals(1.0, f.bank());
    }

    @Test
    void disbandPaysTheFactionLeaderEvenBeforeTheOwnerFollowed() {
        Faction f = fx.faction("ABC");
        UUID old = f.leader();
        UUID heir = fx.member(f, "Heir", Rank.OFFICER);
        assertTrue(eco.bankCredit(f, 42, "start").success());
        UUID acc = account(f);
        f.setLeader(heir);
        assertEquals(old, coinage.acc(acc).owner(), "the minute timer has not moved the owner yet");
        fx.repo().removeFaction(f);
        assertEquals(0.0, eco.bankDrain(f, "disband"));
        assertEquals(4200, coinage.bal(heir));
        assertEquals(0, coinage.bal(old));
    }

    @Test
    void movingBackTakesADepositThatLandsBeforeTheDelete() {
        Faction f = fx.faction("ABC");
        assertTrue(eco.bankCredit(f, 12, "start").success());
        UUID acc = account(f);
        boolean[] once = {false};
        coinage.subscribe("Other", new dev.ryvrook.coinage.api.EconomyListener() {
            @Override
            public void afterTransaction(Transaction tx) {
                if (!once[0] && acc.equals(tx.from()) && tx.type() == Transaction.Type.WITHDRAW) {
                    once[0] = true;
                    coinage.ledgerApi.deposit(Request.deposit(acc, null, 500).plugin("Other"));
                }
            }
        });
        fx.set("eco.coinageBanks", false);
        eco.coinageTick();
        assertNull(f.coinageAccount());
        assertNull(coinage.acc(acc));
        assertEquals(17.0, f.bank());
    }

    @Test
    void coinageMovesOnTheAccountAreGuardedMirroredAndLogged() {
        Faction f = fx.faction("ABC");
        UUID lead = f.leader();
        UUID off = fx.member(f, "Off", Rank.OFFICER);
        UUID rec = fx.member(f, "Rec", Rank.RECRUIT);
        UUID stranger = fx.player("Zed");
        assertTrue(eco.bankCredit(f, 100, "start").success());
        UUID acc = account(f);
        fx.set("eco.withdrawDailyLimitOfficer", 30.0);

        var r = coinage.ledgerApi.transfer(Request.transfer(acc, stranger, null, 100).by(Initiator.player(stranger, "Zed")).plugin("Coinage"));
        assertEquals(Messages.get(EconomyText.COINAGE_NOT_MEMBER), r.message());
        r = coinage.ledgerApi.transfer(Request.transfer(acc, rec, null, 100).by(Initiator.player(rec, "Rec")).plugin("Coinage"));
        assertEquals(Messages.get(EconomyText.RANK_NO_WITHDRAW), r.message());
        r = coinage.ledgerApi.transfer(Request.transfer(acc, off, null, 4000).by(Initiator.player(off, "Off")).plugin("Coinage"));
        assertEquals(Messages.get(EconomyText.WITHDRAW_LEFT_TODAY, "amount", eco.format(30)), r.message());

        eco.coinageTick();
        coinage.accountsApi.player(off);
        r = coinage.ledgerApi.transfer(Request.transfer(acc, off, null, 2000).by(Initiator.player(off, "Off")).plugin("Coinage").reason("pay"));
        assertTrue(r.ok(), r.message());
        assertEquals(80.0, f.bank());
        assertTrue(logged(f, Messages.get(EconomyText.COINAGE_OUT_LOG, "amount", eco.format(20), "to", "p", "reason", "pay")));
        assertEquals(Messages.get(EconomyText.WITHDRAW_LEFT_TODAY, "amount", eco.format(10)), eco.bankWithdraw(off, 15).message(),
                "a Coinage withdrawal counts against the allowance");

        coinage.ledgerApi.deposit(Request.deposit(acc, null, 500).plugin("Coinage").reason("gift"));
        assertEquals(85.0, f.bank());
        assertTrue(logged(f, Messages.get(EconomyText.COINAGE_IN_LOG, "amount", eco.format(5),
                "from", Messages.get(EconomyText.COINAGE_SOMEONE), "reason", "gift")));
        assertFalse(logged(f, Messages.get(EconomyText.COINAGE_IN_LOG, "amount", eco.format(100),
                "from", Messages.get(EconomyText.COINAGE_SOMEONE), "reason", "start")), "Dominion's own moves are not logged twice");
        assertNull(eco.coinageVeto(acc, lead, 1_000_000), "the leader has no allowance");
    }

    @Test
    void theNetWorthPartIsAMembersShareAndLeavesWithTheLink() {
        Faction f = fx.faction("ABC");
        UUID lead = f.leader();
        UUID mem = fx.member(f, "Mem", Rank.MEMBER);
        assertTrue(eco.bankCredit(f, 10.01, "start").success());
        eco.coinageTick();
        assertEquals(1, coinage.parts.size());
        assertEquals("faction", coinage.parts.get(0).part());
        assertEquals(500, coinage.parts.get(0).held(mem, "coins"));
        assertEquals(500, coinage.parts.get(0).held(lead, "coins"));
        assertEquals(0, coinage.parts.get(0).held(mem, "gems"));
        assertEquals(0, coinage.parts.get(0).held(UUID.randomUUID(), "coins"));

        link.stop();
        assertTrue(coinage.parts.isEmpty());
        assertTrue(coinage.listeners.isEmpty());
        coinage.down();
        coinage.up();
        assertFalse(link.bound(), "a stopped link never binds again");
        assertTrue(coinage.listeners.isEmpty());
    }

    @Test
    void disbandPaysTheOwnerAndDeletesTheAccount() {
        Faction f = fx.faction("ABC");
        UUID lead = f.leader();
        assertTrue(eco.bankCredit(f, 42, "start").success());
        UUID acc = account(f);
        fx.repo().removeFaction(f);
        assertEquals(0.0, eco.bankDrain(f, "disband"), "the leader was paid directly");
        assertEquals(4200, coinage.bal(lead));
        assertNull(coinage.acc(acc));
        assertEquals(0.0, f.bank());

        fx.set("eco.coinageDisbandTo", "server");
        Faction g = fx.faction("XYZ");
        assertTrue(eco.bankCredit(g, 8, "start").success());
        fx.repo().removeFaction(g);
        assertEquals(0.0, eco.bankDrain(g, "disband"));
        assertEquals(800, coinage.bal(Accounts.SERVER));
    }

    @Test
    void turningCoinageBanksOffMovesThemBackAndAMissingLinkRefusesThem() {
        Faction f = fx.faction("ABC");
        assertTrue(eco.bankCredit(f, 12, "start").success());
        UUID acc = account(f);
        fx.set("eco.coinageBanks", false);
        eco.coinageTick();
        assertNull(f.coinageAccount());
        assertNull(coinage.acc(acc));
        assertEquals(12.0, f.bank());
        assertTrue(eco.bankCredit(f, 1, "x").success());
        assertEquals(13.0, f.bank());

        fx.set("eco.coinageBanks", true);
        assertTrue(eco.bankCredit(f, 1, "x").success());
        assertEquals(1400, coinage.bal(account(f)));
        link.stop();
        fx.set("eco.provider", "internal");
        assertEquals(Messages.get(EconomyText.NOT_RESPONDING), eco.bankCredit(f, 1, "x").message(),
                "a bank in Coinage is not spent from its mirror while Coinage is away");
        fx.repo().removeFaction(f);
        assertEquals(0.0, eco.bankDrain(f, "disband"));
        assertEquals(0.0, f.bank());
    }
}
