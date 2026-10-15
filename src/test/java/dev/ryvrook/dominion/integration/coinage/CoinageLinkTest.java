package dev.ryvrook.dominion.integration.coinage;

import dev.ryvrook.coinage.api.AccountPermission;
import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Failure;
import dev.ryvrook.coinage.api.Result;
import dev.ryvrook.dominion.economy.CoinagePort;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parts of the Coinage link, the rights and results it translates and the API version it reads. */
class CoinageLinkTest {
    @Test
    void rightsBecomePermissionsAndBack() {
        Set<CoinagePort.BankRight> all = EnumSet.allOf(CoinagePort.BankRight.class);
        assertEquals(EnumSet.of(AccountPermission.BALANCE, AccountPermission.DEPOSIT, AccountPermission.WITHDRAW),
                CoinageLink.permissions(all));
        assertEquals(all, CoinageLink.rights(CoinageLink.permissions(all)));
        assertTrue(CoinageLink.permissions(Set.of()).isEmpty());
        assertTrue(CoinageLink.permissions(null).isEmpty());
        assertEquals(EnumSet.of(CoinagePort.BankRight.DEPOSIT),
                CoinageLink.rights(EnumSet.of(AccountPermission.DEPOSIT, AccountPermission.INVITE_MEMBER, AccountPermission.DELETE)),
                "permissions Dominion does not manage are left out");
    }

    @Test
    void resultsBecomeMoves() {
        CoinagePort.Move ok = CoinageLink.toMove(Result.ok("fine", 5, 7, null));
        assertTrue(ok.ok());
        assertEquals(5, ok.fromAfter());
        assertEquals(7, ok.toAfter());
        for (Failure f : Failure.values()) {
            if (f == Failure.NONE) {
                continue;
            }
            CoinagePort.Move m = CoinageLink.toMove(Result.fail(f, "why"));
            assertFalse(m.ok());
            assertEquals(f.name(), m.failure().name(), "every Coinage failure has a Dominion twin");
            assertEquals("why", m.message());
        }
        assertEquals(CoinagePort.Failure.CANCELLED, CoinageLink.toMove(null).failure());
    }

    @Test
    void theRunningApiVersionIsReadAtRunTime() {
        assertEquals(CoinageApi.API_VERSION, CoinageLink.apiVersion());
        assertTrue(CoinageLink.apiVersion() >= CoinageLink.NET_WORTH_API);
    }
}
