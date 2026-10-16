package dev.ryvrook.dominion.storage;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.model.Zone;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every persisted model goes to disk through JsonFiles and back, and must serialise to the same
 * document again. Comparing the second document with the first catches any field that is written
 * but not read, or read under another name.
 */
class ModelRoundTripTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);

    private <T> T roundTrip(String name, Document original, Function<Document, T> read, Function<T, Document> write) {
        Path f = dir.resolve(name + ".json");
        assertTrue(files.write(f, original));
        T back = files.read(f, read);
        assertNotNull(back, name + " came back null");
        assertEquals(original.toJson(), write.apply(back).toJson(), name + " changed on the way through disk");
        return back;
    }

    static Faction richFaction() {
        UUID leader = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        Faction f = new Faction(UUID.fromString("10000000-0000-0000-0000-000000000001"), "Wolf", "The Wolves", leader, 1_700_000_000_000L);
        f.putMember(new Member(leader, "Alpha", Rank.LEADER, 1_700_000_000_000L));
        Member m = new Member(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Beta", Rank.OFFICER, 1_700_000_100_000L);
        m.setTitle("Scout");
        m.setLastSeen(1_700_000_200_000L);
        m.setHoldUntil(1_700_000_300_000L);
        m.setKills(3);
        m.setDeaths(1);
        f.putMember(m);
        f.setColor("#ff8800");
        f.setDescription("We howl \"loudly\".\nSecond line");
        f.setMotd("Meet at the den");
        f.invite(UUID.fromString("00000000-0000-0000-0000-00000000000c"), 1_800_000_000_000L);
        f.addRequest(UUID.fromString("00000000-0000-0000-0000-00000000000d"));
        f.ban(UUID.fromString("00000000-0000-0000-0000-00000000000e"));
        f.rosterPut("Gamma", Rank.MEMBER);
        UUID other = UUID.fromString("20000000-0000-0000-0000-000000000002");
        f.setRelation(other, Relation.ALLY);
        f.putRelationRequest(other, new Faction.RelationRequest(Relation.TRUCE, leader, 1_800_000_000_000L));
        f.setHome(new Location("default", 10.5, 64, -20.25, 90f, 0f));
        f.setWarp("mine", new Faction.Warp(new Location("default", 1, 2, 3, 0f, 0f), "secret"));
        f.applyDefaultPerms();
        f.setRankPerm(Rank.RECRUIT, FactionAction.CLAIM, true);
        f.setRelationPerm(Relation.ALLY, FactionAction.BUILD, true);
        f.setBank(1234.5);
        f.setPowerBoost(2.5);
        f.setUpgrade("vault", 2);
        f.setPoints(77);
        f.setShieldStartHour(20);
        f.addStat("kills", 9);
        f.setSetting("open", true);
        f.audit(new AuditEntry(1_700_000_400_000L, leader, "Alpha", "bank", "Deposited 5"), 500);
        f.setRankName(Rank.RECRUIT, "Pup");
        Zone z = new Zone("farm", "North Farm");
        z.setRankPerm(Rank.RECRUIT, FactionAction.BUILD, false);
        z.setRelationPerm(Relation.ALLY, FactionAction.CONTAINER, true);
        f.putZone(z);
        f.setNationId(UUID.fromString("30000000-0000-0000-0000-000000000003"));
        return f;
    }

    @Test
    void factionKeepsEveryField() {
        Faction back = roundTrip("faction", richFaction().toDocument(), Faction::fromDocument, Faction::toDocument);
        assertEquals("Wolf", back.tag());
        assertEquals(2, back.memberCount());
        assertEquals("Scout", back.member(UUID.fromString("00000000-0000-0000-0000-00000000000b")).title());
        assertEquals("North Farm", back.zone("farm").name());
        assertFalse(back.isDirty());
    }

    @Test
    void systemFactionKeepsItsType() {
        Faction f = new Faction(UUID.randomUUID(), "SafeZone", "Safe Zone", null, 1L);
        f.setType(FactionType.SAFEZONE);
        Faction back = roundTrip("safezone", f.toDocument(), Faction::fromDocument, Faction::toDocument);
        assertEquals(FactionType.SAFEZONE, back.type());
        assertNull(back.leader());
    }

    @Test
    void playerDataKeepsEveryField() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        PlayerData p = new PlayerData(id, "Player_One", 7.5, 250);
        p.setFactionId(UUID.randomUUID());
        p.setLastSeen(123);
        p.setLastDeath(456);
        p.setChannel(PlayerData.Channel.OFFICER);
        p.setAutoClaim(true);
        p.setHud(false);
        p.setAlerts(false);
        p.setTutorialSeen(true);
        p.setMapHidden(true);
        p.addKill();
        p.addDeath();
        p.setCounter("upgradeLevel", 3);
        p.setCounter("foreign." + UUID.randomUUID(), 1);
        PlayerData back = roundTrip("player", p.toDocument(), d -> PlayerData.fromDocument(id, d), PlayerData::toDocument);
        assertEquals(PlayerData.Channel.OFFICER, back.channel());
        assertTrue(back.mapHidden());
        assertEquals(3, back.counter("upgradeLevel"));
        assertEquals(1, back.counterCount("foreign."));
        assertEquals(0, back.counter("powerMax"));
    }

    @Test
    void playerWithUnknownChannelFallsBackToPublic() {
        UUID id = UUID.randomUUID();
        Document d = new PlayerData(id, "x", 1, 1).toDocument().append("Channel", "SHOUT");
        assertEquals(PlayerData.Channel.PUBLIC, PlayerData.fromDocument(id, d).channel());
    }

    @Test
    void auditEntryLocationAndZone() {
        AuditEntry a = new AuditEntry(5L, UUID.randomUUID(), "Who", "land", "Claimed 1, 2");
        roundTrip("audit", a.toDocument(), AuditEntry::fromDocument, AuditEntry::toDocument);
        Location l = new Location("w", -1.5, 300, 7.75, 45.5f, -10f);
        assertEquals(l, roundTrip("location", l.toDocument(), Location::fromDocument, Location::toDocument));
        Zone z = new Zone("z1", "Zone One");
        z.setRankPerm(Rank.MEMBER, FactionAction.INTERACT, true);
        roundTrip("zone", z.toDocument(), Zone::fromDocument, Zone::toDocument);
    }

    @Test
    void nationKeepsMembersAndInvites() {
        Nation n = new Nation(UUID.randomUUID(), "NORD", "Northern Pact", UUID.randomUUID(), 99L);
        n.setColor("#123456");
        n.addMember(UUID.randomUUID());
        n.invite(UUID.randomUUID());
        Nation back = roundTrip("nation", n.toDocument(), Nation::fromDocument, Nation::toDocument);
        assertEquals("#123456", back.color());
    }

    @Test
    void siegeKeepsPhaseAndCounters() {
        Siege s = new Siege(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "default", 42L, 1, 2, 3, 100L, UUID.randomUUID());
        s.setPhase(Siege.Phase.BREACHED, 999L);
        s.addBlocksBroken(4);
        s.addContainersOpened(2);
        s.addAttackerDeath();
        s.addDefenderDeath();
        roundTrip("siege", s.toDocument(), Siege::fromDocument, Siege::toDocument);
    }

    @Test
    void listingKeepsBidsAndItem() {
        Listing l = new Listing(UUID.randomUUID(), Listing.Kind.AUCTION, UUID.randomUUID(), "Seller", UUID.randomUUID(),
                "Ingredient_Bar_Gold", 4, new Document("Id", "Ingredient_Bar_Gold").append("Quantity", 4), 10.5, 50, 1L, 2L);
        l.setTopBid(UUID.randomUUID(), "Bidder", 12.25);
        l.setStorefront(true);
        roundTrip("listing", l.toDocument(), Listing::fromDocument, Listing::toDocument);
    }
}
