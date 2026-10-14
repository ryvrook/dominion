package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.power.PowerServiceImpl;
import dev.ryvrook.dominion.relation.RelationServiceImpl;
import dev.ryvrook.dominion.service.CombatTagService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Claiming and unclaiming while combat tagged. */
class ClaimCombatTest {
    static final String W = "world";

    Fixtures fx;
    ClaimServiceImpl claims;
    final Set<UUID> tagged = new HashSet<>();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        fx.ctx.register(PowerService.class, new PowerServiceImpl(fx.ctx));
        fx.ctx.register(RelationService.class, new RelationServiceImpl(fx.ctx));
        claims = new ClaimServiceImpl(fx.ctx);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    void useSharedTag() {
        fx.ctx.register(CombatTagService.class, TestContexts.fake(CombatTagService.class,
                Map.of("tagged", args -> tagged.contains((UUID) args[0]))));
    }

    static long c(int x, int z) {
        return Keys.chunk(x, z);
    }

    static void fails(Result r, String text) {
        assertTrue(r.failed(), "expected a failure but got " + r.message());
        assertEquals(text, r.message());
    }

    @Test
    void aTaggedPlayerCannotClaimOrUnclaim() {
        useSharedTag();
        Faction a = fx.faction("Alpha");
        assertTrue(claims.claim(a.leader(), W, c(0, 0)).success());
        tagged.add(a.leader());
        fails(claims.claim(a.leader(), W, c(1, 0)), "You cannot claim land while in combat.");
        fails(claims.claimRadius(a.leader(), W, c(1, 0), 1), "You cannot claim land while in combat.");
        fails(claims.unclaim(a.leader(), W, c(0, 0)), "You cannot unclaim land while in combat.");
        fails(claims.unclaimRadius(a.leader(), W, c(0, 0), 1), "You cannot unclaim land while in combat.");
        fails(claims.unclaimAll(a.leader(), true), "You cannot unclaim land while in combat.");
        assertEquals(1, claims.claimCount(a));
        tagged.clear();
        assertTrue(claims.claim(a.leader(), W, c(1, 0)).success());
        assertTrue(claims.unclaim(a.leader(), W, c(1, 0)).success());
    }

    @Test
    void theSettingOffAllowsClaimsWhileTagged() {
        useSharedTag();
        fx.set("combat.blockClaimsWhileTagged", false);
        Faction a = fx.faction("Alpha");
        tagged.add(a.leader());
        assertTrue(claims.claim(a.leader(), W, c(0, 0)).success());
        assertTrue(claims.unclaim(a.leader(), W, c(0, 0)).success());
    }

    @Test
    void withoutTheCombatModuleTheOwnTagStillRefuses() {
        Faction a = fx.faction("Alpha");
        fx.ctx.player(a.leader()).setCombatTagUntil(Time.now() + 10_000L);
        fails(claims.claim(a.leader(), W, c(0, 0)), "You cannot claim land while in combat.");
        fx.ctx.player(a.leader()).setCombatTagUntil(0L);
        assertTrue(claims.claim(a.leader(), W, c(0, 0)).success());
    }

    @Test
    void adminClaimsIgnoreTheTag() {
        useSharedTag();
        Faction a = fx.faction("Alpha");
        tagged.add(a.leader());
        assertTrue(claims.adminClaim(a.leader(), a, W, c(5, 5)).success());
    }

    @Test
    void aRefusalForTheRankComesBeforeTheCombatCheck() {
        useSharedTag();
        UUID loner = fx.player("Loner");
        tagged.add(loner);
        fails(claims.claim(loner, W, c(0, 0)), "You need to be in a faction to claim land.");
    }
}
