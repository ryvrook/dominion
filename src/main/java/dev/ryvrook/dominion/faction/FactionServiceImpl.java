package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Facade over the faction operations. Each group of calls lives in its own package class. */
public final class FactionServiceImpl implements FactionService {
    private final DominionContext ctx;
    private final FactionSupport support;
    private final Identity identity;
    private final Membership membership;
    private final Ranks ranks;
    private final Activity activity;
    private final PermEdits perms;
    private final AdminActions admin;
    private final Zones zones;

    public FactionServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.support = new FactionSupport(ctx);
        this.identity = new Identity(support);
        this.membership = new Membership(support);
        this.ranks = new Ranks(support);
        this.activity = new Activity(support, membership, ranks);
        this.perms = new PermEdits(support);
        this.admin = new AdminActions(support, identity);
        this.zones = new Zones(support);
    }

    /** Shared helpers and the activity tracker, for package tests. */
    FactionSupport support() { return support; }
    Activity activity() { return activity; }
    Zones zones() { return zones; }

    @Override public Faction factionOf(UUID player) { return support.factionOf(player); }
    @Override public Faction byTag(String tag) { return ctx.repo().factionByTag(tag); }
    @Override public Faction byId(UUID id) { return ctx.repo().faction(id); }
    @Override public List<Faction> all() { return new ArrayList<>(ctx.repo().factions()); }

    @Override public Result create(UUID actor, String actorName, String tag, String name) { return identity.create(actor, actorName, tag, name); }
    @Override public Result disband(UUID actor, boolean confirmed) { return identity.disband(actor, confirmed); }
    @Override public Result rename(UUID actor, String newTag, String newName) { return identity.rename(actor, newTag, newName); }
    @Override public Result setDescription(UUID actor, String description) { return identity.setDescription(actor, description); }
    @Override public Result setMotd(UUID actor, String motd) { return identity.setMotd(actor, motd); }
    @Override public Result setColor(UUID actor, String color) { return identity.setColor(actor, color); }
    @Override public Result setOpen(UUID actor, boolean open) { return identity.setSetting(actor, "open", open); }
    @Override public Result setSetting(UUID actor, String key, boolean value) { return identity.setSetting(actor, key, value); }

    @Override public Result invite(UUID actor, UUID target, String targetName) { return membership.invite(actor, target, targetName); }
    @Override public Result uninvite(UUID actor, UUID target) { return membership.uninvite(actor, target); }
    @Override public Result join(UUID player, String playerName, String tag) { return membership.join(player, playerName, tag); }
    @Override public Result requestJoin(UUID player, String playerName, String tag) { return membership.requestJoin(player, playerName, tag); }
    @Override public Result acceptRequest(UUID actor, UUID target) { return membership.acceptRequest(actor, target); }
    @Override public Result declineRequest(UUID actor, UUID target) { return membership.declineRequest(actor, target); }
    @Override public Result leave(UUID player) { return membership.leave(player); }
    @Override public Result kick(UUID actor, UUID target) { return membership.kick(actor, target); }
    @Override public Result ban(UUID actor, UUID target) { return membership.ban(actor, target); }
    @Override public Result unban(UUID actor, UUID target) { return membership.unban(actor, target); }

    @Override public Result setRank(UUID actor, UUID target, Rank rank) { return ranks.setRank(actor, target, rank); }
    @Override public Result promote(UUID actor, UUID target) { return ranks.promote(actor, target); }
    @Override public Result demote(UUID actor, UUID target) { return ranks.demote(actor, target); }
    @Override public Result transferLeader(UUID actor, UUID target, boolean confirmed) { return ranks.transferLeader(actor, target, confirmed); }
    @Override public Result setTitle(UUID actor, UUID target, String title) { return ranks.setTitle(actor, target, title); }
    @Override public Result rosterAdd(UUID actor, String name, Rank rank) { return ranks.rosterAdd(actor, name, rank); }
    @Override public Result rosterRemove(UUID actor, String name) { return ranks.rosterRemove(actor, name); }

    @Override
    public void audit(Faction faction, UUID who, String whoName, String category, String text) {
        if (faction != null) {
            support.audit(faction, who, whoName == null ? support.nameOf(who) : whoName, category, text);
        }
    }

    @Override
    public boolean can(UUID actor, FactionAction action) {
        return Perms.can(support.factionOf(actor), actor, action, Time.now());
    }

    @Override public void inactivityTick() { activity.inactivityTick(); }
    @Override public void onPlayerSeen(UUID player, String name, boolean online) { activity.onPlayerSeen(player, name, online); }

    // ---- permissions and admin actions ----------------------------------------

    @Override public Result setRankPerm(UUID actor, Rank rank, FactionAction action, boolean allowed) { return perms.setRankPerm(actor, rank, action, allowed); }
    @Override public Result setRelationPerm(UUID actor, Relation relation, FactionAction action, boolean allowed) { return perms.setRelationPerm(actor, relation, action, allowed); }
    @Override public Result resetPerms(UUID actor) { return perms.resetPerms(actor); }

    @Override public Result forceJoin(UUID admin, String adminName, UUID target, String targetName, Faction faction) { return this.admin.forceJoin(admin, adminName, target, targetName, faction); }
    @Override public Result forceKick(UUID admin, String adminName, UUID target) { return this.admin.forceKick(admin, adminName, target); }
    @Override public Result forceLeader(UUID admin, String adminName, Faction faction, UUID target) { return this.admin.forceLeader(admin, adminName, faction, target); }
    @Override public Result forceDisband(UUID admin, String adminName, Faction faction) { return this.admin.forceDisband(admin, adminName, faction); }
    @Override public Result forceRename(UUID admin, String adminName, Faction faction, String tag, String name) { return this.admin.forceRename(admin, adminName, faction, tag, name); }

    // ---- rank names and zones ------------------------------------------------

    @Override public Result setRankName(UUID actor, Rank rank, String name) { return zones.setRankName(actor, rank, name); }
    @Override public Result zoneCreate(UUID actor, String name) { return zones.create(actor, name); }
    @Override public Result zoneDelete(UUID actor, String zoneId) { return zones.delete(actor, zoneId); }
    @Override public Result zoneAssign(UUID actor, String zoneId) { return zones.assign(actor, zoneId); }
    @Override public Result zoneSetPerm(UUID actor, String zoneId, Rank rank, Relation relation, FactionAction action, Boolean allowed) {
        return zones.setPerm(actor, zoneId, rank, relation, action, allowed);
    }
    @Override public Result zoneReset(UUID actor, String zoneId) { return zones.reset(actor, zoneId); }
}
