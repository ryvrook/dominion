package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.FactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FactionRulesTest {
    Fixtures fx;
    FactionServiceImpl fs;

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fs = new FactionServiceImpl(fx.ctx);
        fx.ctx.register(FactionService.class, fs);
        // Most tests here use word length tags in their own case, so they run with a raised limit and
        // without the upper case rule. tagRules and tagsAreStoredInUpperCase check the defaults.
        fx.set("faction.tagMax", 16).set("faction.tagUppercase", false);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    static void ok(Result r) {
        assertTrue(r.success(), r.message());
    }

    static void fails(Result r, String fragment) {
        assertTrue(r.failed(), "expected a failure but got " + r.message());
        assertTrue(r.message().contains(fragment), r.message());
    }

    UUID founder(String name, String tag) {
        UUID id = fx.player(name);
        ok(fs.create(id, name, tag, null));
        return id;
    }

    /** Joins by invite, then clears the new member hold. */
    UUID joined(Faction f, String name) {
        UUID id = fx.player(name);
        ok(fs.invite(f.leader(), id, name));
        ok(fs.join(id, name, f.tag()));
        f.member(id).setHoldUntil(0);
        return id;
    }

    // ---- creating ----------------------------------------------------------------

    @Test
    void tagRules() {
        fx.set("faction.tagMax", 4).set("faction.tagUppercase", true);
        UUID p = fx.player("Pat");
        fails(fs.create(p, "Pat", "a", null), "at least 2");
        fails(fs.create(p, "Pat", "abcde", null), "at most 4");
        fails(fs.create(p, "Pat", "ab-c", null), "letters and digits");
        fails(fs.create(p, "Pat", "Ünïc", null), "letters and digits");
        fails(fs.create(p, "Pat", "MOD", null), "not allowed");
        fails(fs.create(p, "Pat", " ", null), "choose a tag");
        fails(fs.create(p, "Pat", "Good", "x".repeat(49)), "at most 48");
        ok(fs.create(p, "Pat", "abc", null));
        ok(fs.create(fx.player("Max"), "Max", "abcd", "Four Chars"));
        fails(fs.create(fx.player("Sam"), "Sam", "ABC", null), "already taken");
        fails(fs.create(p, "Pat", "Oth", null), "already in a faction");
        Faction f = fs.factionOf(p);
        assertEquals(Rank.LEADER, f.rankOf(p));
        assertEquals("ABC", f.tag(), "new tags are stored in upper case");
        assertEquals("ABC", f.name(), "a missing name uses the tag");
        assertFalse(f.setting("open", true));
    }

    @Test
    void tagsAreStoredInUpperCase() {
        fx.set("faction.tagMax", 4).set("faction.tagUppercase", true);
        UUID lead = fx.player("Tia");
        Result made = fs.create(lead, "Tia", " tst ", "Testers");
        ok(made);
        assertEquals("Created [TST] Testers", made.message());
        Faction f = fs.factionOf(lead);
        assertEquals("TST", f.tag());
        assertEquals("[TST] Testers", f.label());
        ok(fs.rename(lead, "abc", null));
        assertEquals("ABC", f.tag(), "a renamed tag is stored in upper case");
        fails(fs.rename(lead, "abc", null), "Nothing to change");

        fx.repo().retag(f, "old");
        assertEquals("old", f.tag(), "existing tags are left as they are");
        ok(fs.rename(lead, "old", null));
        assertEquals("OLD", f.tag(), "until the faction renames itself");

        fx.set("faction.tagUppercase", false);
        UUID other = fx.player("Uma");
        ok(fs.create(other, "Uma", "Low", null));
        assertEquals("Low", fs.factionOf(other).tag());
    }

    // ---- joining -------------------------------------------------------------------

    @Test
    void joinNeedsAnInviteAnOpenFactionOrARosterEntry() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("alpha");
        UUID p = fx.player("Pat");
        fails(fs.join(p, "Pat", "Nope"), "No faction has that tag");
        fails(fs.join(p, "Pat", "Alpha"), "invite only");
        f.invite(p, System.currentTimeMillis() - 1);
        fails(fs.join(p, "Pat", "Alpha"), "invite only");
        ok(fs.invite(lead, p, "Pat"));
        ok(fs.join(p, "Pat", "Alpha"));
        assertEquals(Rank.RECRUIT, f.rankOf(p));
        assertTrue(f.member(p).onHold(System.currentTimeMillis()), "new members start on hold");
        assertTrue(f.invites().isEmpty(), "the invite is used up");
        fails(fs.join(p, "Pat", "Alpha"), "already in a faction");
        ok(fs.setOpen(lead, true));
        ok(fs.join(fx.player("Max"), "Max", "Alpha"));
    }

    @Test
    void invitesRespectSelfMembersBansAndSize() {
        fx.set("faction.maxMembers", 2);
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        fails(fs.invite(lead, lead, "Lea"), "invite yourself");
        UUID a = joined(f, "Ann");
        fails(fs.invite(lead, a, "Ann"), "already a member");
        UUID b = fx.player("Ben");
        fails(fs.invite(lead, b, "Ben"), "faction is full");
        f.setUpgrade("members", 1);
        ok(fs.invite(lead, b, "Ben"));
        UUID c = fx.player("Cat");
        ok(fs.ban(lead, c));
        fails(fs.invite(lead, c, "Cat"), "banned from your faction");
        fails(fs.invite(a, b, "Ben"), "Your rank does not allow you to invite players");
        fails(fs.invite(fx.player("Out"), b, "Ben"), "not in a faction");
    }

    @Test
    void fullFactionsRefuseJoins() {
        fx.set("faction.maxMembers", 1);
        UUID lead = founder("Lea", "Alpha");
        ok(fs.setOpen(lead, true));
        fails(fs.join(fx.player("Pat"), "Pat", "Alpha"), "Alpha is full");
    }

    @Test
    void joinRequests() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID p = fx.player("Pat");
        ok(fs.requestJoin(p, "Pat", "Alpha"));
        fails(fs.requestJoin(p, "Pat", "Alpha"), "already asked");
        UUID member = joined(f, "Mem");
        fails(fs.acceptRequest(member, p), "Your rank does not allow you");
        ok(fs.acceptRequest(lead, p));
        assertTrue(f.isMember(p));
        fails(fs.acceptRequest(lead, p), "has not asked");
        UUID q = fx.player("Quin");
        ok(fs.requestJoin(q, "Quin", "Alpha"));
        ok(fs.declineRequest(lead, q));
        fails(fs.declineRequest(lead, q), "has not asked");
        ok(fs.setOpen(lead, true));
        Result direct = fs.requestJoin(q, "Quin", "Alpha");
        ok(direct);
        assertEquals("You joined Alpha", direct.message(), "an open faction skips the request");
    }

    @Test
    void acceptRequestChecksTheTargetAgain() {
        UUID lead = founder("Lea", "Alpha");
        UUID p = fx.player("Pat");
        ok(fs.requestJoin(p, "Pat", "Alpha"));
        founder("Bo", "Bravo");
        ok(fs.setOpen(fs.byTag("Bravo").leader(), true));
        ok(fs.join(p, "Pat", "Bravo"));
        fails(fs.acceptRequest(lead, p), "already joined another faction");
        assertTrue(fs.byTag("Alpha").requests().isEmpty());
    }

    // ---- leaving, kicks and bans ------------------------------------------------

    @Test
    void leadersCannotLeaveButMembersCan() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        fails(fs.leave(lead), "transfer leadership or disband");
        UUID a = joined(f, "Ann");
        ok(fs.leave(a));
        assertNull(fs.factionOf(a));
        assertNull(fx.ctx.player(a).factionId());
        fails(fs.leave(a), "not in a faction");
    }

    @Test
    void kicksNeedAHigherRank() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID o1 = joined(f, "One");
        UUID o2 = joined(f, "Two");
        UUID m = joined(f, "Mem");
        ok(fs.setRank(lead, o1, Rank.OFFICER));
        ok(fs.setRank(lead, o2, Rank.OFFICER));
        fails(fs.kick(o1, o2), "below your rank");
        fails(fs.kick(o1, lead), "below your rank");
        fails(fs.kick(o1, o1), "to yourself");
        fails(fs.kick(m, o1), "Your rank does not allow you to kick members");
        fails(fs.kick(o1, fx.player("Out")), "not in your faction");
        ok(fs.kick(o1, m));
        assertFalse(f.isMember(m));
        ok(fs.kick(lead, o2));
    }

    @Test
    void aKickOrLeaveTakesThePlayerOffTheRoster() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.rosterAdd(lead, "Ann", Rank.MEMBER));
        UUID ann = fx.player("Ann");
        ok(fs.join(ann, "Ann", "Alpha"));
        assertEquals(Rank.MEMBER, f.rankOf(ann), "the roster rank applies");
        ok(fs.kick(lead, ann));
        assertFalse(f.roster().containsKey("ann"));
        fails(fs.join(ann, "Ann", "Alpha"), "invite only");
        fs.onPlayerSeen(ann, "Ann", true);
        assertNull(fs.factionOf(ann), "the login roster join does not pull a kicked player back");

        ok(fs.rosterAdd(lead, "Ben", Rank.RECRUIT));
        UUID ben = fx.player("Ben");
        fs.onPlayerSeen(ben, "Ben", true);
        assertEquals(f.id(), fs.factionOf(ben).id(), "a roster player joins at login");
        ok(fs.leave(ben));
        fs.onPlayerSeen(ben, "Ben", true);
        assertNull(fs.factionOf(ben), "leaving lasts across logins");
    }

    @Test
    void aRenamedRosterPlayerStaysOffTheRosterAfterAKick() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.rosterAdd(lead, "Ann", Rank.MEMBER));
        UUID ann = fx.player("Ann");
        fs.onPlayerSeen(ann, "Ann", true);
        assertEquals(f.id(), fs.factionOf(ann).id());
        fs.onPlayerSeen(ann, "Annie", true);
        assertEquals("Annie", f.member(ann).name());
        ok(fs.kick(lead, ann));
        assertTrue(f.roster().isEmpty(), "the entry joined through is removed by uuid");
        fs.onPlayerSeen(ann, "Ann", true);
        assertNull(fs.factionOf(ann), "renaming back does not revive the entry");
    }

    @Test
    void bansRemoveAndBlock() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID a = joined(f, "Ann");
        fails(fs.ban(lead, lead), "ban yourself");
        ok(fs.ban(lead, a));
        assertFalse(f.isMember(a));
        fails(fs.ban(lead, a), "already banned");
        ok(fs.setOpen(lead, true));
        fails(fs.join(a, "Ann", "Alpha"), "banned from Alpha");
        fails(fs.requestJoin(a, "Ann", "Alpha"), "banned from Alpha");
        ok(fs.unban(lead, a));
        fails(fs.unban(lead, a), "not banned");
        ok(fs.join(a, "Ann", "Alpha"));
    }

    // ---- ranks ----------------------------------------------------------------------

    @Test
    void promoteAndDemoteStopAtTheEnds() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID a = joined(f, "Ann");
        fails(fs.demote(lead, a), "already the lowest rank");
        ok(fs.promote(lead, a));
        assertEquals(Rank.MEMBER, f.rankOf(a));
        ok(fs.promote(lead, a));
        assertEquals(Rank.OFFICER, f.rankOf(a));
        fails(fs.promote(lead, a), "cannot be promoted further");
        fails(fs.demote(a, lead), "leader cannot be demoted");
        fails(fs.setRank(lead, a, Rank.LEADER), "Use transfer");
        fails(fs.setRank(lead, a, null), "not a rank");
        fails(fs.setRank(lead, a, Rank.OFFICER), "already an Officer");
        ok(fs.demote(lead, a));
        assertEquals(Rank.MEMBER, f.rankOf(a));
        fails(fs.promote(lead, fx.player("Out")), "not in your faction");
    }

    @Test
    void officersOnlyGiveRanksBelowTheirOwn() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID o = joined(f, "Off");
        UUID m = joined(f, "Mem");
        ok(fs.setRank(lead, o, Rank.OFFICER));
        ok(fs.promote(o, m));
        assertEquals(Rank.MEMBER, f.rankOf(m));
        fails(fs.promote(o, m), "below your own");
        ok(fs.demote(o, m));
        assertEquals(Rank.RECRUIT, f.rankOf(m));
    }

    @Test
    void transferNeedsConfirmAndDemotesTheOldLeader() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID a = fx.player("Ann");
        ok(fs.invite(lead, a, "Ann"));
        ok(fs.join(a, "Ann", "Alpha"));
        fails(fs.transferLeader(a, lead, true), "Only the leader");
        fails(fs.transferLeader(lead, lead, true), "already lead");
        fails(fs.transferLeader(lead, fx.player("Out"), true), "not in your faction");
        fails(fs.transferLeader(lead, a, false), "confirm");
        ok(fs.transferLeader(lead, a, true));
        assertEquals(a, f.leader());
        assertEquals(Rank.OFFICER, f.rankOf(lead));
        assertFalse(f.member(a).onHold(System.currentTimeMillis()), "the new leader has no hold");
        ok(fs.leave(lead));
    }

    @Test
    void newMemberHoldLimitsHighRiskActions() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.rosterAdd(lead, "Off", Rank.OFFICER));
        UUID o = fx.player("Off");
        ok(fs.join(o, "Off", "Alpha"));
        assertEquals(Rank.OFFICER, f.rankOf(o));
        long now = System.currentTimeMillis();
        assertFalse(fs.can(o, FactionAction.CLAIM), "held members claim as recruits");
        assertTrue(fs.can(o, FactionAction.INVITE), "invite is not a held action");
        assertEquals("New members cannot do that until their hold ends", Perms.denial(f, o, FactionAction.CLAIM, now));
        assertTrue(Perms.denial(f, o, FactionAction.DISBAND, now).startsWith("Your rank does not allow"),
                "an action the rank lacks anyway names the rank");
        f.member(o).setHoldUntil(now - 1);
        assertTrue(fs.can(o, FactionAction.CLAIM));
        assertTrue(fs.can(lead, FactionAction.DISBAND));
        assertFalse(fs.can(fx.player("Out"), FactionAction.INTERACT));
        fx.set("faction.newMemberHoldHours", 0);
        UUID q = fx.player("Quin");
        ok(fs.invite(lead, q, "Quin"));
        ok(fs.join(q, "Quin", "Alpha"));
        assertFalse(f.member(q).onHold(System.currentTimeMillis()), "a zero hold setting means no hold");
    }

    @Test
    void rosterRules() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        fails(fs.rosterAdd(lead, "x", Rank.MEMBER), "not a valid player name");
        fails(fs.rosterAdd(lead, "Bad Name", Rank.MEMBER), "not a valid player name");
        fails(fs.rosterAdd(lead, "Boss", Rank.LEADER), "cannot hand out leadership");
        UUID o = joined(f, "Off");
        ok(fs.setRank(lead, o, Rank.OFFICER));
        fails(fs.rosterAdd(o, "Peer", Rank.OFFICER), "below your own");
        ok(fs.rosterAdd(o, "Newbie", null));
        assertEquals(Rank.RECRUIT, f.roster().get("newbie"));
        fails(fs.rosterRemove(lead, "Nobody"), "not on the roster");
        ok(fs.rosterRemove(lead, "NEWBIE"));
    }

    @Test
    void rosterRotationMakesRoomByRemovingTheLongestOffline() {
        fx.set("faction.maxMembers", 3).set("faction.rosterRotate", true);
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID old = joined(f, "Old");
        UUID newer = joined(f, "New");
        f.member(old).setLastSeen(1000L);
        f.member(newer).setLastSeen(2000L);
        ok(fs.rosterAdd(lead, "Vip", Rank.MEMBER));
        UUID vip = fx.player("Vip");
        ok(fs.join(vip, "Vip", "Alpha"));
        assertFalse(f.isMember(old));
        assertTrue(f.isMember(newer));
        fx.set("faction.rosterRotate", false);
        ok(fs.rosterAdd(lead, "Vip2", Rank.MEMBER));
        fails(fs.join(fx.player("Vip2"), "Vip2", "Alpha"), "is full");
        fx.set("faction.rosterRotate", true);
        fs.support().online = id -> true;
        fails(fs.join(fx.player("Vip3"), "Vip3", "Alpha"), "is full");
    }

    // ---- permissions -------------------------------------------------------------------

    @Test
    void rankPermissionEditsCannotEscalate() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID o = joined(f, "Off");
        ok(fs.setRank(lead, o, Rank.OFFICER));
        fails(fs.setRankPerm(o, Rank.MEMBER, FactionAction.CLAIM, true), "Your rank does not allow you to edit permissions");
        ok(fs.setRankPerm(lead, Rank.OFFICER, FactionAction.PERMS, true));
        fails(fs.setRankPerm(o, Rank.OFFICER, FactionAction.BUILD, false), "ranks below your own");
        fails(fs.setRankPerm(o, Rank.MEMBER, FactionAction.DISBAND, true), "do not have yourself");
        ok(fs.setRankPerm(o, Rank.MEMBER, FactionAction.CLAIM, true));
        fails(fs.setRankPerm(o, Rank.MEMBER, FactionAction.CLAIM, true), "can already claim land");
        ok(fs.setRankPerm(o, Rank.MEMBER, FactionAction.BUILD, false));
        fails(fs.setRankPerm(lead, Rank.LEADER, FactionAction.BUILD, false), "always has every permission");
        fails(fs.setRankPerm(lead, null, FactionAction.BUILD, false), "Pick a rank");
        ok(fs.resetPerms(lead));
        assertFalse(f.rankCan(Rank.MEMBER, FactionAction.CLAIM));
        assertTrue(f.rankCan(Rank.MEMBER, FactionAction.BUILD));
    }

    @Test
    void relationPermissionEditsKeepTheTogglesInStep() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        fails(fs.setRelationPerm(lead, Relation.OWN, FactionAction.BUILD, true), "rank permissions");
        fails(fs.setRelationPerm(lead, Relation.ALLY, FactionAction.CLAIM, true), "cannot be given to other factions");
        ok(fs.setRelationPerm(lead, Relation.ALLY, FactionAction.BUILD, true));
        assertTrue(f.setting("allyBuild", false));
        ok(fs.setSetting(lead, "trucEINTERACT", false));
        assertFalse(f.relationCan(Relation.TRUCE, FactionAction.INTERACT));
        fails(fs.setSetting(lead, "flying", true), "Unknown setting");
        ok(fs.resetPerms(lead));
        assertTrue(f.relationCan(Relation.ALLY, FactionAction.BUILD), "a reset keeps the ally build toggle");
        assertFalse(f.relationCan(Relation.TRUCE, FactionAction.INTERACT), "and the truce toggle");
    }

    // ---- zones ----------------------------------------------------------------------------

    @Test
    void zoneNamesAndIds() {
        assertEquals("my-farm", Zones.slug("My Farm!!"));
        assertEquals("a-b", Zones.slug("--a  b--"));
        assertEquals("", Zones.slug("!!!"));
        assertEquals(16, Zones.slug("x".repeat(40)).length());
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.zoneCreate(lead, "Farm"));
        fails(fs.zoneCreate(lead, "farm"), "already has a zone called Farm");
        Result second = fs.zoneCreate(lead, "Farm!");
        ok(second);
        assertTrue(second.message().endsWith("farm-2"), second.message());
        fails(fs.zoneCreate(lead, "!!!"), "at least one letter");
        fails(fs.zoneCreate(lead, ""), "1 to 24");
        for (int i = f.zones().size(); i < Zones.ZONE_LIMIT; i++) {
            ok(fs.zoneCreate(lead, "Z" + i));
        }
        fails(fs.zoneCreate(lead, "One more"), "already has 20 zones");
        assertNotNull(Zones.find(f, "FARM-2"));
        assertNotNull(Zones.find(f, "farm!"));
        ok(fs.zoneDelete(lead, "farm"));
        fails(fs.zoneDelete(lead, "farm"), "no zone called");
    }

    @Test
    void zoneOverridesStartFromTheFactionGridAndFallBack() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.zoneCreate(lead, "Farm"));
        assertOneZone(f);
        fails(fs.zoneSetPerm(lead, "farm", Rank.MEMBER, Relation.ALLY, FactionAction.BUILD, true), "either a rank or a relation");
        ok(fs.zoneSetPerm(lead, "farm", Rank.RECRUIT, null, FactionAction.CONTAINER, true));
        var zone = f.zone("farm");
        assertEquals(Boolean.TRUE, zone.rankCan(Rank.RECRUIT, FactionAction.CONTAINER));
        assertEquals(Boolean.TRUE, zone.rankCan(Rank.RECRUIT, FactionAction.INTERACT), "the rest of the set is copied");
        assertNull(zone.rankCan(Rank.MEMBER, FactionAction.CONTAINER));
        fails(fs.zoneSetPerm(lead, "farm", Rank.RECRUIT, null, FactionAction.INTERACT, null), "no override for that");
        ok(fs.zoneSetPerm(lead, "farm", Rank.RECRUIT, null, FactionAction.CONTAINER, null));
        assertNull(f.zone("farm").rankCan(Rank.RECRUIT, FactionAction.CONTAINER), "matching the grid again drops the entry");
        ok(fs.zoneSetPerm(lead, "farm", null, Relation.ALLY, FactionAction.BUILD, true));
        fails(fs.zoneSetPerm(lead, "farm", null, Relation.ALLY, FactionAction.CLAIM, true), "other factions");
        ok(fs.zoneReset(lead, "farm"));
        fails(fs.zoneReset(lead, "farm"), "already follows the faction rules");
        fails(fs.zoneSetPerm(lead, "farm", Rank.LEADER, null, FactionAction.BUILD, true), "always has every permission");
    }

    private static void assertOneZone(Faction f) {
        assertEquals(1, f.zones().size());
    }

    @Test
    void zoneAssignWritesOnlyAnUnchangedChunk() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.zoneCreate(lead, "Farm"));
        Zones zones = fs.zones();
        long chunk = dev.ryvrook.dominion.util.Keys.chunk(0, 0);
        fails(zones.assignAt(lead, f, "w", chunk, "farm"), "Stand in your own faction's land");
        fx.repo().putClaim("w", chunk, new ClaimEntry(f.id(), 1L, lead, null));
        fails(zones.assignAt(lead, f, "w", chunk, "nope"), "no zone called nope");
        fails(zones.assignAt(lead, f, "w", chunk, null), "not in a zone");
        ok(zones.assignAt(lead, f, "w", chunk, "Farm"));
        assertEquals("farm", fx.repo().claimAt("w", chunk).zoneId());
        assertEquals(1, fx.repo().claimCount(f.id()), "a rewrite keeps the claim count");
        fails(zones.assignAt(lead, f, "w", chunk, "farm"), "already in Farm");

        // Another faction takes the chunk between the read and the write.
        founder("Bo", "Bravo");
        Faction b = fs.byTag("Bravo");
        ClaimEntry taken = new ClaimEntry(b.id(), 2L, b.leader(), null);
        zones.beforeWrite = () -> fx.repo().putClaim("w", chunk, taken);
        fails(zones.assignAt(lead, f, "w", chunk, null), "changed hands");
        assertEquals(taken, fx.repo().claimAt("w", chunk), "the new owner keeps the chunk");
        assertEquals(0, fx.repo().claimCount(f.id()));
        zones.beforeWrite = () -> { };

        fx.repo().putClaim("w", chunk, new ClaimEntry(f.id(), 3L, lead, "farm"));
        ok(fs.zoneDelete(lead, "farm"));
        assertNull(fx.repo().claimAt("w", chunk).zoneId(), "deleting a zone returns its chunks to the faction rules");
    }

    @Test
    void zoneOverridesCannotRaiseYourOwnRank() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        ok(fs.zoneCreate(lead, "Vault"));
        UUID o = joined(f, "Off");
        ok(fs.setRank(lead, o, Rank.OFFICER));
        ok(fs.setRankPerm(lead, Rank.OFFICER, FactionAction.PERMS, true));
        fails(fs.zoneSetPerm(o, "vault", Rank.OFFICER, null, FactionAction.CONTAINER, false), "ranks below your own");
        fails(fs.zoneSetPerm(o, "vault", Rank.MEMBER, null, FactionAction.SETTINGS, true), "do not have yourself");
        ok(fs.zoneSetPerm(o, "vault", Rank.MEMBER, null, FactionAction.CONTAINER, false));
    }

    // ---- identity --------------------------------------------------------------------------

    @Test
    void renameAndTexts() {
        UUID lead = founder("Lea", "Alpha");
        founder("Bo", "Bravo");
        Faction f = fs.byTag("Alpha");
        fails(fs.rename(lead, "Alpha", null), "Nothing to change");
        fails(fs.rename(lead, "bravo", null), "already taken");
        ok(fs.rename(lead, "ALPHA", null));
        assertEquals("ALPHA", f.tag());
        assertSame(f, fs.byTag("alpha"));
        ok(fs.rename(lead, "Apex", "The Apex"));
        assertNull(fs.byTag("Alpha"));
        assertEquals("The Apex", f.name());
        fails(fs.setDescription(lead, "x".repeat(121)), "at most 120");
        fails(fs.setMotd(lead, "bad\u0007bell"), "special characters");
        ok(fs.setDescription(lead, "  We build  "));
        assertEquals("We build", f.description());
        fails(fs.setColor(lead, "red"), "hex value");
        ok(fs.setColor(lead, "ABCDEF"));
        assertEquals("#abcdef", f.color());
        UUID m = joined(f, "Mem");
        fails(fs.setTitle(m, m, "Me"), "Your rank does not allow you");
        ok(fs.setTitle(lead, m, " Scout "));
        assertEquals("Scout", f.member(m).title());
        fails(fs.setTitle(lead, m, "x".repeat(33)), "at most 32");
        fails(fs.setRankName(lead, Rank.MEMBER, "Officer"), "already called Officer");
        ok(fs.setRankName(lead, Rank.MEMBER, "Knight"));
        assertEquals("Knight", f.rankName(Rank.MEMBER));
        ok(fs.setRankName(lead, Rank.MEMBER, "Member"));
        assertTrue(f.rankNames().isEmpty(), "the built in name clears the custom one");
    }

    // ---- disbanding ------------------------------------------------------------------------

    @Test
    void disbandClearsMembersRelationsAndPaysTheLeader() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID a = joined(f, "Ann");
        UUID bo = founder("Bo", "Bravo");
        Faction b = fs.byTag("Bravo");
        b.setRelation(f.id(), Relation.ENEMY);
        b.putRelationRequest(f.id(), new Faction.RelationRequest(Relation.TRUCE, f.id(), Long.MAX_VALUE));
        f.setBank(50);
        double before = fx.ctx.player(lead).balance();
        fails(fs.disband(a, true), "Your rank does not allow you");
        fails(fs.disband(lead, false), "confirm");
        ok(fs.disband(lead, true));
        assertNull(fs.byTag("Alpha"));
        assertNull(fs.factionOf(a));
        assertNull(fx.ctx.player(lead).factionId());
        assertTrue(b.relations().isEmpty());
        assertTrue(b.relationRequests().isEmpty());
        assertEquals(before + 50, fx.ctx.player(lead).balance(), 1e-9);
        assertNotNull(fs.factionOf(bo));
    }

    // ---- admin ----------------------------------------------------------------------------

    @Test
    void adminActionsKeepTheFactionConsistent() {
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID a = joined(f, "Ann");
        UUID o = joined(f, "Off");
        ok(fs.setRank(lead, o, Rank.OFFICER));
        founder("Bo", "Bravo");
        Faction b = fs.byTag("Bravo");
        fails(fs.forceJoin(null, "Admin", lead, "Lea", b), "leads Alpha");
        ok(fs.forceJoin(null, "Admin", a, "Ann", b));
        assertEquals(b.id(), fs.factionOf(a).id());
        assertFalse(f.isMember(a));
        ok(fs.forceKick(null, "Admin", lead));
        assertEquals(o, f.leader(), "the officer takes over from a kicked leader");
        fails(fs.forceKick(null, "Admin", o), "only member of Alpha");
        fails(fs.forceLeader(null, "Admin", b, o), "not a member of Bravo");
        ok(fs.forceLeader(null, "Admin", b, a));
        assertEquals(Rank.OFFICER, b.rankOf(fx.repo().factionByTag("Bravo").members().stream()
                .filter(m -> !m.uuid().equals(a)).findFirst().map(Member::uuid).orElseThrow()));
        ok(fs.forceRename(null, "Admin", b, "Brava", null));
        ok(fs.forceDisband(null, "Admin", b));
        fails(fs.forceDisband(null, "Admin", b), "no longer exists");
    }

    // ---- inactivity ------------------------------------------------------------------------

    @Test
    void inactiveMembersAreRemovedAndTheBestActiveMemberLeads() {
        Activity act = fs.activity();
        long boot = act.bootAt;
        long day = FactionSupport.DAY;
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        UUID o = joined(f, "Off");
        UUID m = joined(f, "Mem");
        UUID r = joined(f, "Rec");
        ok(fs.setRank(lead, o, Rank.OFFICER));
        ok(fs.setRank(lead, m, Rank.MEMBER));
        f.member(lead).setLastSeen(boot + 1);
        f.member(o).setLastSeen(boot + 49 * day);
        f.member(m).setLastSeen(boot + 2);
        f.member(r).setLastSeen(boot + 48 * day);
        act.inactivityTick(boot + Activity.BOOT_GRACE - 1);
        assertEquals(4, f.memberCount(), "nothing happens right after a boot");
        act.inactivityTick(boot + 50 * day);
        assertEquals(o, f.leader(), "the officer outranks the more recent recruit");
        assertFalse(f.isMember(lead));
        assertFalse(f.isMember(m));
        assertTrue(f.isMember(r));
    }

    @Test
    void factionsWithNoRecentLoginAreDisbanded() {
        Activity act = fs.activity();
        long boot = act.bootAt;
        long day = FactionSupport.DAY;
        UUID lead = founder("Lea", "Alpha");
        Faction f = fs.byTag("Alpha");
        f.member(lead).setLastSeen(boot + 1);
        act.inactivityTick(boot + 44 * day);
        assertNotNull(fs.byTag("Alpha"), "the leader stays without a successor and 44 days is not 45");
        act.inactivityTick(boot + 46 * day);
        assertNull(fs.byTag("Alpha"));
    }

    @Test
    void textRules() {
        assertNull(TagRules.color("#00ff00"));
        assertNull(TagRules.color("00FF00"));
        assertNotNull(TagRules.color("#00ff0"));
        assertNotNull(TagRules.color(null));
        assertEquals("#00ff00", TagRules.normaliseColor("00FF00"));
        assertNull(TagRules.playerName("abc"));
        assertNull(TagRules.playerName("a_b_c_d_e_f_g_h1"));
        assertNotNull(TagRules.playerName("a_b_c_d_e_f_g_h12"));
        assertNotNull(TagRules.playerName("ab"));
        assertNull(TagRules.text("", 5, "X"));
        assertNull(TagRules.text("12345", 5, "X"));
        assertNotNull(TagRules.text("123456", 5, "X"));
    }
}
