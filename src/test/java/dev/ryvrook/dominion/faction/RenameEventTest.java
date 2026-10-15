package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.service.FactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameEventTest {
    Fixtures fx;
    FactionServiceImpl fs;
    final List<DominionEvent.FactionRenamed> renames = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fs = new FactionServiceImpl(fx.ctx);
        fx.ctx.register(FactionService.class, fs);
        fx.ctx.bus().subscribe(e -> {
            if (e instanceof DominionEvent.FactionRenamed r) {
                renames.add(r);
            }
        });
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void playerRenamePublishesOldAndNewIdentity() {
        UUID lead = fx.player("Lea");
        assertTrue(fs.create(lead, "Lea", "Alpha", "The Alphas").success());
        Faction f = fs.byTag("Alpha");
        assertTrue(fs.rename(lead, "Omega", "The Omegas").success());
        assertEquals(1, renames.size());
        DominionEvent.FactionRenamed r = renames.get(0);
        assertEquals(f.id(), r.factionId());
        assertEquals("Alpha", r.oldTag());
        assertEquals("Omega", r.newTag());
        assertEquals("The Alphas", r.oldName());
        assertEquals("The Omegas", r.newName());
    }

    @Test
    void nameOnlyRenameKeepsTheTag() {
        UUID lead = fx.player("Lea");
        assertTrue(fs.create(lead, "Lea", "Alpha", null).success());
        assertTrue(fs.rename(lead, null, "Alpha Squad").success());
        assertEquals(1, renames.size());
        assertEquals("Alpha", renames.get(0).oldTag());
        assertEquals("Alpha", renames.get(0).newTag());
        assertEquals("Alpha", renames.get(0).oldName());
        assertEquals("Alpha Squad", renames.get(0).newName());
    }

    @Test
    void adminRenamePublishesToo() {
        UUID lead = fx.player("Lea");
        assertTrue(fs.create(lead, "Lea", "Alpha", null).success());
        Faction f = fs.byTag("Alpha");
        assertTrue(fs.forceRename(null, "Admin", f, "Brava", null).success());
        assertEquals(1, renames.size());
        assertEquals("Brava", renames.get(0).newTag());
    }

    @Test
    void failedRenamePublishesNothing() {
        UUID lead = fx.player("Lea");
        assertTrue(fs.create(lead, "Lea", "Alpha", null).success());
        UUID other = fx.player("Bo");
        assertTrue(fs.create(other, "Bo", "Bravo", null).success());
        assertTrue(fs.rename(lead, "Bravo", null).failed());
        assertTrue(fs.rename(lead, "Alpha", null).failed());
        assertTrue(fs.rename(fx.player("Nobody"), "Gamma", null).failed());
        assertTrue(renames.isEmpty());
    }
}
