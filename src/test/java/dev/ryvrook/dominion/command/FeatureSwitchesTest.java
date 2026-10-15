package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.home.HomeServiceImpl;
import dev.ryvrook.dominion.progress.ProgressServiceImpl;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.vault.VaultServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class FeatureSwitchesTest {
    @TempDir
    Path dir;

    private DominionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = TestContexts.create(dir);
    }

    @AfterEach
    void tearDown() {
        TestContexts.close(ctx);
    }

    @Test
    void everyNewSwitchExistsAndDefaultsToOn() {
        for (String key : List.of("vault.enabled", "missions.enabled", "upgrades.enabled", "home.enabled", "chat.enabled",
                "hud.enabled", "map.enabled", "top.enabled", "integrations.enabled")) {
            assertTrue(ctx.settings().known(key), key);
            assertTrue(ctx.settings().bool(key), key);
        }
    }

    @Test
    void commandGatesFollowTheirSwitchLive() {
        Map<String, BooleanSupplier> gates = CommandModule.gates(ctx);
        String[][] pairs = {
                {"vault", "vault.enabled"}, {"missions", "missions.enabled"}, {"upgrade", "upgrades.enabled"},
                {"upgrades", "upgrades.enabled"}, {"home", "home.enabled"}, {"warp", "home.enabled"},
                {"stuck", "home.enabled"}, {"chat", "chat.enabled"}, {"hud", "hud.enabled"}, {"map", "map.enabled"},
                {"top", "top.enabled"}, {"nation", "nation.enabled"}, {"shield", "shield.enabled"},
                {"market", "market.enabled"}, {"bank", "eco.enabled"}, {"shop", "shop.factionEnabled"},
                {"siege", "raid.modeSiege"},
        };
        for (String[] p : pairs) {
            BooleanSupplier g = gates.get(p[0]);
            assertNotNull(g, p[0]);
            assertTrue(g.getAsBoolean(), p[0]);
            ctx.settings().set(p[1], false);
            assertFalse(g.getAsBoolean(), p[0]);
            ctx.settings().set(p[1], true);
        }
        assertNull(gates.get("claim"));
    }

    @Test
    void servicesRefuseWithTheFeatureOffSentence() {
        String off = Messages.get(CommonText.FEATURE_OFF);
        UUID player = UUID.randomUUID();
        ctx.settings().set("upgrades.enabled", false);
        ctx.settings().set("missions.enabled", false);
        ctx.settings().set("home.enabled", false);
        ctx.settings().set("vault.enabled", false);
        ProgressServiceImpl progress = new ProgressServiceImpl(ctx);
        Result r = progress.buyUpgrade(player, "vault");
        assertFalse(r.success());
        assertEquals(off, r.message());
        assertTrue(progress.activeMissions(false).isEmpty());
        HomeServiceImpl homes = new HomeServiceImpl(ctx);
        assertEquals(off, homes.home(player).message());
        assertEquals(off, homes.stuck(player).message());
        assertNull(homes.respawnLocation(player));
        assertEquals(off, new VaultServiceImpl(ctx).open(player, 1).message());
    }
}
