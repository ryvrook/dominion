package dev.ryvrook.dominion.model;

import com.hypixel.hytale.math.util.ChunkUtil;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    private static Faction faction() {
        UUID leader = UUID.randomUUID();
        Faction f = new Faction(UUID.randomUUID(), "Tag", "Name", leader, 1000L);
        f.putMember(new Member(leader, "Boss", Rank.LEADER, 1000L));
        return f;
    }

    @Test
    void rankStepsStopBelowLeaderAndAtRecruit() {
        assertEquals(Rank.MEMBER, Rank.RECRUIT.next());
        assertEquals(Rank.OFFICER, Rank.MEMBER.next());
        assertEquals(Rank.OFFICER, Rank.OFFICER.next(), "promotion never reaches leader");
        assertEquals(Rank.LEADER, Rank.LEADER.next());
        assertEquals(Rank.RECRUIT, Rank.RECRUIT.previous());
        assertEquals(Rank.OFFICER, Rank.LEADER.previous());
        assertTrue(Rank.OFFICER.atLeast(Rank.OFFICER));
        assertFalse(Rank.MEMBER.atLeast(Rank.OFFICER));
    }

    @Test
    void rankAndRelationParsing() {
        assertEquals(Rank.OFFICER, Rank.parse("officer"));
        assertEquals(Rank.LEADER, Rank.parse("Leader"));
        assertNull(Rank.parse("captain"));
        assertEquals(Relation.ALLY, Relation.parse("ally"));
        assertEquals(Relation.ENEMY, Relation.parse("ENEMY"));
        assertNull(Relation.parse("own"), "own is not a relation players can set");
        assertNull(Relation.parse(null));
    }

    @Test
    void defaultPermissionGrid() {
        Faction f = faction();
        assertFalse(f.rankCan(Rank.RECRUIT, FactionAction.BUILD));
        assertTrue(f.rankCan(Rank.RECRUIT, FactionAction.INTERACT));
        assertTrue(f.rankCan(Rank.MEMBER, FactionAction.BUILD));
        assertFalse(f.rankCan(Rank.MEMBER, FactionAction.CLAIM));
        assertTrue(f.rankCan(Rank.OFFICER, FactionAction.CLAIM));
        assertFalse(f.rankCan(Rank.OFFICER, FactionAction.DISBAND));
        assertFalse(f.rankCan(Rank.OFFICER, FactionAction.PERMS));
        for (FactionAction a : FactionAction.values()) {
            assertTrue(f.rankCan(Rank.LEADER, a), "leader can " + a);
        }
        assertTrue(f.relationCan(Relation.ALLY, FactionAction.INTERACT));
        assertFalse(f.relationCan(Relation.ALLY, FactionAction.BUILD));
        assertTrue(f.relationCan(Relation.TRUCE, FactionAction.INTERACT));
        assertFalse(f.relationCan(Relation.NEUTRAL, FactionAction.INTERACT));
        assertFalse(f.relationCan(Relation.ENEMY, FactionAction.INTERACT));
    }

    @Test
    void permissionSettersRespectScopes() {
        Faction f = faction();
        f.setRankPerm(Rank.LEADER, FactionAction.BUILD, false);
        assertTrue(f.rankCan(Rank.LEADER, FactionAction.BUILD), "leader cannot lose a permission");
        f.setRelationPerm(Relation.ALLY, FactionAction.CLAIM, true);
        assertFalse(f.relationCan(Relation.ALLY, FactionAction.CLAIM), "claim is not relation scoped");
        f.setRelationPerm(Relation.ALLY, FactionAction.BUILD, true);
        assertTrue(f.relationCan(Relation.ALLY, FactionAction.BUILD));
        f.setRelationPerm(Relation.OWN, FactionAction.BUILD, true);
        assertFalse(f.relationCan(Relation.OWN, FactionAction.BUILD), "own is not a diplomatic relation");
    }

    @Test
    void leaderRankComesFromTheLeaderField() {
        Faction f = faction();
        UUID other = UUID.randomUUID();
        f.putMember(new Member(other, "Other", Rank.MEMBER, 1L));
        assertEquals(Rank.MEMBER, f.rankOf(other));
        f.setLeader(other);
        assertEquals(Rank.LEADER, f.rankOf(other));
        assertNull(f.rankOf(UUID.randomUUID()));
    }

    @Test
    void relationsStoreOnlyNonNeutralStates() {
        Faction f = faction();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        f.setRelation(a, Relation.ALLY);
        f.setRelation(b, Relation.ALLY);
        assertEquals(2, f.countRelation(Relation.ALLY));
        f.setRelation(a, Relation.NEUTRAL);
        assertEquals(1, f.countRelation(Relation.ALLY));
        assertFalse(f.relations().containsKey(a));
        assertEquals(Relation.NEUTRAL, f.relationTo(a));
        assertEquals(Relation.OWN, f.relationTo(f.id()));
        assertEquals(Relation.NEUTRAL, f.relationTo(null));
    }

    @Test
    void inviteExpiresAtItsTime() {
        Faction f = faction();
        UUID p = UUID.randomUUID();
        f.invite(p, 5000L);
        assertTrue(f.hasValidInvite(p, 4999L));
        assertFalse(f.hasValidInvite(p, 5000L));
        assertFalse(f.hasValidInvite(UUID.randomUUID(), 0L));
    }

    @Test
    void holdEndsAtItsTime() {
        Member m = new Member(UUID.randomUUID(), "x", Rank.RECRUIT, 0L);
        m.setHoldUntil(100L);
        assertTrue(m.onHold(99L));
        assertFalse(m.onHold(100L));
    }

    @Test
    void auditKeepsNewestFirstWithAFloorOfTen() {
        Faction f = faction();
        for (int i = 0; i < 15; i++) {
            f.audit(new AuditEntry(i, null, "Server", "test", "line " + i), 3);
        }
        List<AuditEntry> lines = f.audit();
        assertEquals(10, lines.size(), "a limit below ten still keeps ten lines");
        assertEquals("line 14", lines.get(0).text());
        Faction back = Faction.fromDocument(f.toDocument());
        assertEquals(lines, back.audit());
    }

    @Test
    void factionRoundTrip() {
        Faction f = faction();
        UUID m = UUID.randomUUID();
        Member mem = new Member(m, "Mia", Rank.OFFICER, 2000L);
        mem.setTitle("Scout");
        mem.setHoldUntil(9000L);
        mem.setKills(3);
        f.putMember(mem);
        UUID other = UUID.randomUUID();
        f.setRelation(other, Relation.ENEMY);
        f.putRelationRequest(other, new Faction.RelationRequest(Relation.TRUCE, other, 77L));
        f.invite(UUID.randomUUID(), 55L);
        f.ban(UUID.randomUUID());
        f.rosterPut("Alex", Rank.MEMBER);
        f.setHome(new Location("default", 1.5, 64, -20.5, 90f, 0f));
        f.setRankPerm(Rank.RECRUIT, FactionAction.BUILD, true);
        f.setUpgrade("members", 2);
        f.addStat("kills", 4);
        f.setSetting("open", true);
        f.setRankName(Rank.OFFICER, "Captain");
        Zone z = new Zone("farm", "Farm");
        z.setRankPerm(Rank.RECRUIT, FactionAction.CONTAINER, true);
        f.putZone(z);
        f.setNationId(UUID.randomUUID());
        f.setBank(12.5);

        Faction back = Faction.fromDocument(f.toDocument());
        assertEquals(f.toDocument().toJson(), back.toDocument().toJson());
        assertFalse(back.isDirty(), "a freshly loaded faction is clean");
        assertEquals("Scout", back.member(m).title());
        assertEquals(Relation.ENEMY, back.relationTo(other));
        assertEquals(Rank.MEMBER, back.roster().get("alex"));
        assertTrue(back.rankCan(Rank.RECRUIT, FactionAction.BUILD));
        assertEquals("Captain", back.rankName(Rank.OFFICER));
        assertTrue(back.zone("farm").rankCan(Rank.RECRUIT, FactionAction.CONTAINER));
        assertNull(back.zone("farm").rankCan(Rank.MEMBER, FactionAction.CONTAINER));
    }

    @Test
    void damagedEntriesDoNotDropTheFaction() {
        Faction f = faction();
        UUID good = UUID.randomUUID();
        f.putMember(new Member(good, "Good", Rank.MEMBER, 10L));
        Document d = f.toDocument();
        @SuppressWarnings("unchecked")
        List<Document> members = (List<Document>) d.get("Members");
        // A member with an unknown rank and no times, and one with an unreadable id.
        members.get(1).put("Rank", "CAPTAIN");
        members.get(1).remove("LastSeen");
        members.get(1).remove("JoinedAt");
        members.add(new Document("Uuid", "not-a-uuid").append("Rank", "MEMBER"));
        d.put("Relations", new Document(UUID.randomUUID().toString(), "FRENEMY").append("bad", "ALLY"));
        d.put("RankPerms", new Document("RECRUIT", List.of("BUILD", "TELEPORT")).append("CAPTAIN", List.of("BUILD")));
        d.put("Roster", new Document("alex", "CAPTAIN").append("sam", "MEMBER").append("lee", "LEADER"));
        d.put("Audit", List.of(new Document("Text", "no time")));
        d.put("Zones", List.of(new Document("Id", "z").append("Name", "Z")
                .append("RankPerms", new Document("GOD", List.of("BUILD")))));
        d.remove("CreatedAt");

        Faction back = Faction.fromDocument(d);
        assertEquals(2, back.memberCount());
        assertEquals(Rank.RECRUIT, back.member(good).rank(), "an unknown rank falls back to recruit");
        assertTrue(back.relations().isEmpty());
        assertTrue(back.rankCan(Rank.RECRUIT, FactionAction.BUILD));
        assertEquals(1, back.roster().size(), "unknown and leader roster ranks are dropped");
        assertTrue(back.audit().isEmpty());
        assertNotNull(back.zone("z"));
    }

    @Test
    void aDamagedLeaderEntryIsRebuilt() {
        Faction f = faction();
        Document d = f.toDocument();
        @SuppressWarnings("unchecked")
        List<Document> members = (List<Document>) d.get("Members");
        members.get(0).put("Uuid", "broken");
        Faction back = Faction.fromDocument(d);
        assertEquals(Rank.LEADER, back.rankOf(f.leader()));
        assertNotNull(back.member(f.leader()));
        assertEquals(1, back.memberCount());
    }

    @Test
    void everyOptionalFieldToleratesBadValues() {
        Faction f = faction();
        Document d = f.toDocument();
        d.put("Type", "CASTLE");
        d.put("Nation", "not-a-uuid");
        d.put("Leader", 12);
        d.put("ShieldStartHour", "noon");
        d.put("Upgrades", new Document("members", "lots").append("regen", 2));
        d.put("Stats", new Document("kills", "many").append("deaths", 3));
        d.put("RankNames", new Document("OFFICER", null).append("MEMBER", "Knight").append("GOD", "Zeus"));
        d.put("Zones", List.of(new Document("Id", 5), new Document("Id", "ok").append("Name", "Ok")));
        d.put("Warps", new Document("a", "not a warp").append("b", new Document("Location", new Document("World", "w"))));
        d.put("Home", new Document("World", "w"));
        d.put("Requests", List.of("bad", 7));
        d.put("Members", "not a list");
        Faction back = Faction.fromDocument(d);
        assertEquals(FactionType.NORMAL, back.type());
        assertNull(back.nationId());
        assertNull(back.leader(), "an unreadable leader is cleared");
        assertEquals(-1, back.shieldStartHour());
        assertEquals(0, back.upgrade("members"));
        assertEquals(2, back.upgrade("regen"));
        assertEquals(3, back.stat("deaths"));
        assertEquals("Officer", back.rankName(Rank.OFFICER));
        assertEquals("Knight", back.rankName(Rank.MEMBER));
        assertEquals(1, back.zones().size());
        assertTrue(back.warps().isEmpty());
        assertNull(back.home());
        assertTrue(back.requests().isEmpty());
    }

    @Test
    void unusablePermissionGridsFallBackToTheDefaults() {
        Faction f = faction();
        Document d = f.toDocument();
        d.put("RankPerms", new Document("CAPTAIN", List.of("BUILD")).append("MEMBER", "BUILD"));
        d.put("RelationPerms", new Document("FRIEND", List.of("INTERACT")));
        Faction back = Faction.fromDocument(d);
        assertTrue(back.rankCan(Rank.MEMBER, FactionAction.BUILD));
        assertTrue(back.rankCan(Rank.OFFICER, FactionAction.CLAIM));
        assertTrue(back.relationCan(Relation.ALLY, FactionAction.INTERACT));
        // A grid that parses but grants nothing is a real choice and stays.
        f.setRankPerm(Rank.RECRUIT, FactionAction.INTERACT, false);
        assertFalse(Faction.fromDocument(f.toDocument()).rankCan(Rank.RECRUIT, FactionAction.INTERACT));
    }

    @Test
    void rosterKeysIgnoreTheDefaultLocale() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
            Faction f = faction();
            f.rosterPut("IAN", Rank.MEMBER);
            assertTrue(f.roster().containsKey("ian"), "a Turkish default locale would store a dotless i");
            assertEquals(Rank.MEMBER, f.rosterRank("Ian"));
            UUID p = UUID.randomUUID();
            f.rosterBind(p, "ian");
            Faction back = Faction.fromDocument(f.toDocument());
            assertEquals("ian", back.rosterIds().get(p));
            assertEquals(List.of("ian"), back.rosterRemovePlayer(p, "SomeNewName"));
            assertTrue(back.roster().isEmpty());
            assertTrue(back.rosterIds().isEmpty());
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void releasedLandMarksPersistAndExpire() {
        Faction f = faction();
        f.noteReleased("w", 42L, 1000L, 500L);
        assertTrue(f.releasedWithin("w", 42L, 1499L, 500L));
        assertFalse(f.releasedWithin("w", 42L, 1500L, 500L));
        assertFalse(f.releasedWithin("other", 42L, 1200L, 500L));
        Faction back = Faction.fromDocument(f.toDocument());
        assertTrue(back.releasedWithin("w", 42L, 1200L, 500L));
        back.noteReleased("w", 7L, 2000L, 500L);
        assertEquals(1, back.released().size(), "expired marks are dropped on the next note");
    }

    @Test
    void nationLoadSkipsDamagedIds() {
        UUID lead = UUID.randomUUID();
        Nation n = new Nation(UUID.randomUUID(), "NAT", "N", lead, 5L);
        Document d = n.toDocument();
        d.put("Members", List.of(lead.toString(), "bad", 3));
        d.put("Invites", List.of("bad"));
        d.put("Leader", "bad");
        d.remove("CreatedAt");
        Nation back = Nation.fromDocument(d);
        assertEquals(List.of(lead), back.members());
        assertTrue(back.invites().isEmpty());
        assertNull(back.leaderFaction());
    }

    @Test
    void zoneOverridesFallBackWhenMissing() {
        Zone z = new Zone("a", "A");
        assertNull(z.rankCan(Rank.MEMBER, FactionAction.BUILD));
        z.setRankPerm(Rank.MEMBER, FactionAction.BUILD, false);
        assertEquals(Boolean.FALSE, z.rankCan(Rank.MEMBER, FactionAction.BUILD));
        assertTrue(z.hasRankOverride(Rank.MEMBER));
        z.setRelationPerm(Relation.ALLY, FactionAction.INTERACT, true);
        Zone back = Zone.fromDocument(z.toDocument());
        assertEquals(Boolean.TRUE, back.relationCan(Relation.ALLY, FactionAction.INTERACT));
        back.clearOverrides();
        assertNull(back.relationCan(Relation.ALLY, FactionAction.INTERACT));
    }

    @Test
    void nationRoundTrip() {
        UUID lead = UUID.randomUUID();
        Nation n = new Nation(UUID.randomUUID(), "NAT", "", lead, 5L);
        assertEquals("NAT", n.name(), "a blank name falls back to the tag");
        assertTrue(n.isMember(lead));
        UUID other = UUID.randomUUID();
        n.addMember(other);
        n.addMember(other);
        n.invite(UUID.randomUUID());
        Nation back = Nation.fromDocument(n.toDocument());
        assertEquals(n.members(), back.members());
        assertEquals(n.invites(), back.invites());
        assertEquals(lead, back.leaderFaction());
        assertFalse(back.isDirty());
    }

    @Test
    void playerDataRoundTripAndClamps() {
        PlayerData p = new PlayerData(UUID.randomUUID(), "Pat", 10, 100);
        p.setBalance(-5);
        assertEquals(0, p.balance());
        p.setPower(-3.5);
        p.setChannel(PlayerData.Channel.ALLY);
        p.setAutoClaim(true);
        p.setHud(false);
        p.addKill();
        PlayerData back = PlayerData.fromDocument(p.uuid(), p.toDocument());
        assertEquals(-3.5, back.power());
        assertEquals(PlayerData.Channel.ALLY, back.channel());
        assertTrue(back.autoClaim());
        assertFalse(back.hud());
        assertEquals(1, back.kills());
        Document d = p.toDocument().append("Channel", "SHOUT");
        assertEquals(PlayerData.Channel.PUBLIC, PlayerData.fromDocument(p.uuid(), d).channel());
    }

    @Test
    void playerNameIsNeverNull() {
        UUID id = UUID.randomUUID();
        assertEquals("", PlayerData.fromDocument(id, new Document("Power", 3.0)).name());
        assertEquals("", PlayerData.fromDocument(id, new Document("Name", null)).name());
        assertEquals("", PlayerData.fromDocument(id, new Document("Name", 42)).name());
        assertEquals("", new PlayerData(id, null, 0, 0).name());
        PlayerData p = new PlayerData(id, "Pat", 0, 0);
        p.setName(null);
        assertEquals("Pat", p.name(), "a null rename is ignored");
        assertTrue(PlayerData.fromDocument(id, new Document()).name().equalsIgnoreCase(""));
    }

    @Test
    void locationChunkFloorsNegativeCoordinates() {
        int size = ChunkUtil.SIZE;
        Location justWest = new Location("w", -0.5, 0, 0.5, 0, 0);
        assertEquals(-1, ChunkUtil.xOfChunkIndex(justWest.chunkIndex()));
        assertEquals(0, ChunkUtil.zOfChunkIndex(justWest.chunkIndex()));
        Location edge = new Location("w", size, 0, -size, 0, 0);
        assertEquals(1, ChunkUtil.xOfChunkIndex(edge.chunkIndex()));
        assertEquals(-1, ChunkUtil.zOfChunkIndex(edge.chunkIndex()));
        Location back = Location.fromDocument(edge.toDocument());
        assertEquals(edge, back);
        assertNull(Location.fromDocument(null));
    }
}
