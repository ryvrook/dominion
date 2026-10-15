package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** The single decision function for protection and PvP. Safe to call from any thread. */
public final class RulesServiceImpl implements RulesService {
    private static final EnumSet<FactionAction> HOLD_LIMITED =
            EnumSet.of(FactionAction.CLAIM, FactionAction.UNCLAIM, FactionAction.WITHDRAW, FactionAction.VAULT);

    private final DominionContext ctx;
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();

    RulesServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    // ---- protection -------------------------------------------------------

    @Override
    public Result canAct(UUID player, FactionAction action, String world, int blockX, int blockY, int blockZ) {
        return canAct(player, action, world, Keys.chunkAtBlock(blockX, blockZ));
    }

    @Override
    public Result canAct(UUID player, FactionAction action, String world, long chunkIndex) {
        if (player == null || action == null || bypassing(player) || world == null) {
            return Result.pass();
        }
        Faction owner = ctx.repo().ownerAt(world, chunkIndex);
        if (owner == null) {
            return Result.pass();
        }
        if (owner.type() == FactionType.SAFEZONE) {
            if (action == FactionAction.BUILD || action == FactionAction.CONTAINER || action == FactionAction.DAMAGE_ENTITY) {
                return Result.fail(Messages.get(CombatText.DENY_SAFEZONE, "action", verb(action), "zone", owner.name()));
            }
            return Result.pass();
        }
        if (owner.type() == FactionType.WARZONE) {
            if (action == FactionAction.BUILD || action == FactionAction.CONTAINER) {
                return Result.fail(Messages.get(CombatText.DENY_WARZONE, "action", verb(action), "zone", owner.name()));
            }
            return Result.pass();
        }
        Faction mine = factionOf(player);
        if (mine != null && mine.id().equals(owner.id())) {
            Rank rank = owner.rankOf(player);
            if (rank == null) {
                return Result.fail(Messages.get(CombatText.NOT_ON_ROSTER, "tag", owner.tag()));
            }
            Member m = owner.member(player);
            if (rank != Rank.LEADER && m != null && m.onHold(Time.now()) && HOLD_LIMITED.contains(action)) {
                rank = Rank.RECRUIT;
            }
            Zone zone = zoneAt(owner, world, chunkIndex);
            Boolean zoned = zone == null || rank == Rank.LEADER ? null : zone.rankCan(rank, action);
            boolean allowed = zoned != null ? zoned : owner.rankCan(rank, action);
            if (allowed) {
                return Result.pass();
            }
            if (zoned != null) {
                return Result.fail(Messages.get(CombatText.DENY_RANK_ZONE, "action", verb(action), "zone", zone.name()));
            }
            return Result.fail(Messages.get(CombatText.DENY_RANK, "tag", owner.tag(), "action", verb(action)));
        }
        Relation relation = relation(player, owner);
        if (relation == Relation.ENEMY) {
            ClaimService claims = ctx.serviceOrNull(ClaimService.class);
            if (claims != null && mine != null) {
                if (claims.breached(world, chunkIndex, mine.id())) {
                    return Result.pass();
                }
                if (action == FactionAction.BUILD && claims.raidableBy(mine, owner, world, chunkIndex).success()) {
                    return Result.pass();
                }
            }
            return Result.fail(Messages.get(CombatText.DENY_ENEMY, "tag", owner.tag(), "action", verb(action)));
        }
        Zone zone = zoneAt(owner, world, chunkIndex);
        Boolean zoned = zone == null ? null : zone.relationCan(relation, action);
        if (zoned != null) {
            return zoned ? Result.pass()
                    : Result.fail(Messages.get(CombatText.DENY_ZONE, "tag", owner.tag(), "action", verb(action), "zone", zone.name()));
        }
        if (relation == Relation.ALLY && action == FactionAction.BUILD
                && !owner.setting("allyBuild", ctx.settings().bool("relation.allyBuildDefault"))) {
            return Result.fail(Messages.get(CombatText.DENY_ALLY_BUILD, "tag", owner.tag()));
        }
        if (relation == Relation.TRUCE && action == FactionAction.INTERACT
                && !owner.setting("truceInteract", ctx.settings().bool("relation.truceInteractDefault"))) {
            return Result.fail(Messages.get(CombatText.DENY_TRUCE_USE, "tag", owner.tag()));
        }
        if (owner.relationCan(relation, action)) {
            return Result.pass();
        }
        return Result.fail(Messages.get(CombatText.DENY_LAND, "tag", owner.tag(), "action", verb(action)));
    }

    /**
     * Using an entity such as an NPC or a mount. It follows the creature rules in faction land, but a
     * SafeZone only stops hits, so spawn NPCs and merchants stay usable there.
     */
    Result canUseEntity(UUID player, String world, long chunkIndex) {
        Faction owner = world == null ? null : ctx.repo().ownerAt(world, chunkIndex);
        if (owner != null && owner.type() == FactionType.SAFEZONE) {
            return Result.pass();
        }
        return canAct(player, FactionAction.DAMAGE_ENTITY, world, chunkIndex);
    }

    /** The zone a claimed chunk belongs to, or null when it has none or the zone was deleted. */
    private Zone zoneAt(Faction owner, String world, long chunkIndex) {
        ClaimEntry entry = ctx.repo().claimAt(world, chunkIndex);
        if (entry == null || entry.zoneId() == null || !owner.id().equals(entry.factionId())) {
            return null;
        }
        return owner.zone(entry.zoneId());
    }

    private static String verb(FactionAction action) {
        return switch (action) {
            case BUILD -> Messages.get(CombatText.VERB_BUILD);
            case CONTAINER -> Messages.get(CombatText.VERB_CONTAINER);
            case INTERACT -> Messages.get(CombatText.VERB_INTERACT);
            case DAMAGE_ENTITY -> Messages.get(CombatText.VERB_DAMAGE_ENTITY);
            default -> action.description().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Called after an allowed build or container action. When the chunk is breached for the
     * actor's faction the siege module records it. Does nothing when no siege module is loaded.
     */
    void reportBreach(UUID player, FactionAction action, String world, long chunkIndex) {
        if (player == null || world == null || (action != FactionAction.BUILD && action != FactionAction.CONTAINER)) {
            return;
        }
        // Most allowed actions happen in the wild or at home. Only claimed land can be breached.
        Faction owner = ctx.repo().ownerAt(world, chunkIndex);
        if (owner == null || owner.isSystem()) {
            return;
        }
        SiegeService siege = ctx.serviceOrNull(SiegeService.class);
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        if (siege == null || claims == null) {
            return;
        }
        Faction mine = factionOf(player);
        if (mine == null || mine.id().equals(owner.id()) || !claims.breached(world, chunkIndex, mine.id())) {
            return;
        }
        try {
            siege.onBreachAction(player, world, chunkIndex, action == FactionAction.CONTAINER);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion siege breach hook failed");
        }
    }

    // ---- pvp --------------------------------------------------------------

    @Override
    public boolean canPvp(UUID attacker, UUID victim, String world, long victimChunk) {
        return pvp(attacker, victim, world, victimChunk).success();
    }

    /** PvP decision that also stops attackers hitting out of a SafeZone. */
    Result pvp(UUID attacker, UUID victim, String world, long victimChunk, long attackerChunk) {
        return decide(attacker, victim, world, victimChunk, attackerChunk);
    }

    /** PvP decision with a reason for the attacker. Grace does not block PvP. */
    Result pvp(UUID attacker, UUID victim, String world, long victimChunk) {
        return decide(attacker, victim, world, victimChunk, Long.MIN_VALUE);
    }

    private Result decide(UUID attacker, UUID victim, String world, long victimChunk, long attackerChunk) {
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return Result.pass();
        }
        Settings s = ctx.settings();
        FactionType attackerZone = null;
        if (world != null && attackerChunk != Long.MIN_VALUE) {
            Faction from = ctx.repo().ownerAt(world, attackerChunk);
            attackerZone = from == null ? null : from.type();
        }
        Faction zone = world == null ? null : ctx.repo().ownerAt(world, victimChunk);
        Faction a = factionOf(attacker);
        Faction v = factionOf(victim);
        Relation relation = a != null && v != null && !a.id().equals(v.id()) ? between(a, v) : null;
        boolean friendlyFire = a != null && a.setting("friendlyFire", s.bool("combat.friendlyFireDefault"));
        PvpRules.Toggles toggles = new PvpRules.Toggles(s.bool("combat.pvpFactionless"), s.bool("combat.pvpNeutral"),
                s.bool("combat.allyFire"), s.bool("combat.truceFire"));
        return PvpRules.decide(false, attackerZone, zone == null ? null : zone.type(),
                a == null ? null : a.id(), v == null ? null : v.id(), relation, friendlyFire, toggles);
    }

    // ---- toggles ----------------------------------------------------------

    @Override
    public boolean bypassing(UUID player) {
        return player != null && bypass.contains(player);
    }

    @Override
    public void setBypass(UUID player, boolean on) {
        if (player == null) {
            return;
        }
        if (on) {
            bypass.add(player);
        } else {
            bypass.remove(player);
        }
    }

    @Override
    public boolean graceActive() {
        if (!ctx.settings().bool("grace.active")) {
            return false;
        }
        long ends = ctx.settings().longValue("grace.endsAt");
        return ends <= 0 || Time.now() < ends;
    }

    @Override
    public boolean eotwActive() {
        return ctx.settings().bool("eotw.active");
    }

    // ---- lookups ----------------------------------------------------------

    Faction factionOf(UUID player) {
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs != null) {
            return fs.factionOf(player);
        }
        return ctx.repo().faction(ctx.player(player).factionId());
    }

    private Relation relation(UUID player, Faction owner) {
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        if (rs != null) {
            return rs.playerToFaction(player, owner);
        }
        Faction mine = factionOf(player);
        return mine == null ? Relation.NEUTRAL : mine.relationTo(owner.id());
    }

    private Relation between(Faction a, Faction b) {
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        return rs != null ? rs.between(a, b) : a.relationTo(b.id());
    }
}
