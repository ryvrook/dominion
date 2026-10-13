package dev.ryvrook.dominion.relation;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Ally and truce need a request accepted by the other side. Enemy and neutral are unilateral.
 * Requests are stored on the receiving faction keyed by the sender. All changes run under one
 * lock so both sides of a pair always agree.
 */
public final class RelationServiceImpl implements RelationService {
    private static final long HOUR = 3_600_000L;

    private final DominionContext ctx;
    private final RelationEffects effects;
    private final Object lock = new Object();

    public RelationServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.effects = new RelationEffects(ctx);
    }

    /**
     * Factions in the same nation are always allies. Otherwise enemy wins when either side
     * declared it, then the first faction's view.
     */
    @Override
    public Relation between(Faction a, Faction b) {
        if (a == null || b == null) {
            return Relation.NEUTRAL;
        }
        if (a.id().equals(b.id())) {
            return Relation.OWN;
        }
        if (sameNation(a, b)) {
            return Relation.ALLY;
        }
        Relation ab = a.relationTo(b.id());
        if (ab == Relation.ENEMY || b.relationTo(a.id()) == Relation.ENEMY) {
            return Relation.ENEMY;
        }
        return ab;
    }

    @Override
    public Relation betweenPlayers(UUID viewer, UUID other) {
        FactionService fs = factions();
        return between(fs.factionOf(viewer), fs.factionOf(other));
    }

    @Override
    public Relation playerToFaction(UUID viewer, Faction target) {
        return between(factions().factionOf(viewer), target);
    }

    @Override
    public Result request(UUID actor, String targetTag, Relation relation) {
        if (relation == null || relation == Relation.OWN) {
            return Result.fail("That is not a relation you can set");
        }
        Pair p = pair(actor, targetTag);
        if (p.failure != null) {
            return p.failure;
        }
        synchronized (lock) {
            return switch (relation) {
                case ENEMY -> enemy(p.own, p.target, actor);
                case NEUTRAL -> neutral(p.own, p.target, actor);
                default -> propose(p.own, p.target, relation, actor);
            };
        }
    }

    @Override
    public Result accept(UUID actor, String targetTag) {
        Pair p = pair(actor, targetTag);
        if (p.failure != null) {
            return p.failure;
        }
        synchronized (lock) {
            Faction.RelationRequest req = p.own.relationRequests().get(p.target.id());
            if (req == null) {
                return Result.fail(p.target.tag() + " has not asked your faction for anything");
            }
            if (req.expiresAt() <= Time.now()) {
                p.own.removeRelationRequest(p.target.id());
                return Result.fail("That request has expired");
            }
            return establish(p.own, p.target, req.type(), actor);
        }
    }

    @Override
    public Result decline(UUID actor, String targetTag) {
        Pair p = pair(actor, targetTag);
        if (p.failure != null) {
            return p.failure;
        }
        Faction.RelationRequest req;
        synchronized (lock) {
            req = p.own.removeRelationRequest(p.target.id());
        }
        if (req == null) {
            return Result.fail(p.target.tag() + " has not asked your faction for anything");
        }
        effects.declined(p.own, p.target, req.type(), actor);
        return Result.pass("Declined the request from " + p.target.tag());
    }

    @Override
    public void tick() {
        long now = Time.now();
        synchronized (lock) {
            for (Faction f : new ArrayList<>(ctx.repo().factions())) {
                for (Map.Entry<UUID, Faction.RelationRequest> e : f.relationRequests().entrySet()) {
                    Faction from = ctx.repo().faction(e.getKey());
                    if (from == null) {
                        f.removeRelationRequest(e.getKey());
                    } else if (e.getValue().expiresAt() <= now) {
                        f.removeRelationRequest(e.getKey());
                        effects.expired(f, from, e.getValue().type());
                    }
                }
            }
        }
    }

    // ---- operations, called under the lock --------------------------------

    private Result enemy(Faction own, Faction target, UUID actor) {
        if (own.relationTo(target.id()) == Relation.ENEMY) {
            return Result.fail("You are already enemies with " + target.tag());
        }
        own.setRelation(target.id(), Relation.ENEMY);
        Relation theirs = target.relationTo(own.id());
        if (theirs == Relation.ALLY || theirs == Relation.TRUCE) {
            target.setRelation(own.id(), Relation.NEUTRAL);
        }
        clearRequests(own, target);
        effects.changed(own, target, Relation.ENEMY, actor);
        return Result.pass(target.tag() + " is now your enemy");
    }

    private Result neutral(Faction own, Faction target, UUID actor) {
        Relation current = own.relationTo(target.id());
        boolean pending = target.relationRequests().containsKey(own.id());
        if (current == Relation.NEUTRAL && !pending) {
            return Result.fail("You are already neutral with " + target.tag());
        }
        target.removeRelationRequest(own.id());
        if (current == Relation.NEUTRAL) {
            return Result.pass("Withdrew your request to " + target.tag());
        }
        own.setRelation(target.id(), Relation.NEUTRAL);
        if (current == Relation.ALLY || current == Relation.TRUCE) {
            target.setRelation(own.id(), Relation.NEUTRAL);
        }
        effects.changed(own, target, Relation.NEUTRAL, actor);
        return Result.pass("You are now neutral with " + target.tag());
    }

    private Result propose(Faction own, Faction target, Relation relation, UUID actor) {
        if (own.relationTo(target.id()) == relation && target.relationTo(own.id()) == relation) {
            return Result.fail("You are already " + (relation == Relation.ALLY ? "allied with " : "in a truce with ") + target.tag());
        }
        Result cap = capacity(own, target, relation, "Your faction");
        if (cap != null) {
            return cap;
        }
        long now = Time.now();
        Faction.RelationRequest incoming = own.relationRequests().get(target.id());
        if (incoming != null && incoming.type() == relation && incoming.expiresAt() > now) {
            return establish(own, target, relation, actor);
        }
        Faction.RelationRequest outgoing = target.relationRequests().get(own.id());
        if (outgoing != null && outgoing.type() == relation && outgoing.expiresAt() > now) {
            return Result.fail("You already asked " + target.tag() + " and are waiting for an answer");
        }
        long hours = Math.max(1, ctx.settings().integer("relation.requestHours"));
        // Stored on the receiving faction, keyed by and naming the sending faction.
        target.putRelationRequest(own.id(), new Faction.RelationRequest(relation, own.id(), now + hours * HOUR));
        effects.requested(own, target, relation, actor);
        return Result.pass("Asked " + target.tag() + ". The request lasts " + hours + (hours == 1 ? " hour" : " hours"));
    }

    private Result establish(Faction own, Faction target, Relation relation, UUID actor) {
        Result cap = capacity(own, target, relation, "Your faction");
        if (cap == null) {
            cap = capacity(target, own, relation, target.tag());
        }
        if (cap != null) {
            return cap;
        }
        own.setRelation(target.id(), relation);
        target.setRelation(own.id(), relation);
        clearRequests(own, target);
        effects.changed(own, target, relation, actor);
        return Result.pass((relation == Relation.ALLY ? "You are now allied with " : "You are now in a truce with ") + target.tag());
    }

    /** Null when the faction has room for one more of this relation toward the other. */
    private Result capacity(Faction f, Faction other, Relation relation, String who) {
        String key = relation == Relation.ALLY ? "relation.maxAllies" : "relation.maxTruces";
        int max = ctx.settings().integer(key);
        if (max < 0) {
            return null;
        }
        int count = f.countRelation(relation) - (f.relationTo(other.id()) == relation ? 1 : 0);
        if (count >= max) {
            String noun = relation == Relation.ALLY ? (max == 1 ? "ally" : "allies") : (max == 1 ? "truce" : "truces");
            return Result.fail(who + " already has the maximum of " + max + " " + noun);
        }
        return null;
    }

    private static void clearRequests(Faction a, Faction b) {
        a.removeRelationRequest(b.id());
        b.removeRelationRequest(a.id());
    }

    // ---- lookups ----------------------------------------------------------

    private record Pair(Faction own, Faction target, Result failure) {}

    private Pair pair(UUID actor, String targetTag) {
        FactionService fs = factions();
        Faction own = fs.factionOf(actor);
        if (own == null) {
            return new Pair(null, null, Result.fail("You are not in a faction"));
        }
        if (!fs.can(actor, FactionAction.RELATIONS)) {
            return new Pair(own, null, Result.fail("Your rank does not allow you to change relations"));
        }
        Faction target = fs.byTag(targetTag);
        if (target == null) {
            return new Pair(own, null, Result.fail("No faction has that tag"));
        }
        if (target.id().equals(own.id())) {
            return new Pair(own, target, Result.fail("You cannot set a relation with your own faction"));
        }
        if (target.isSystem()) {
            return new Pair(own, target, Result.fail("Server zones do not take part in diplomacy"));
        }
        if (sameNation(own, target)) {
            return new Pair(own, target, Result.fail("You share a nation with " + target.tag() + " so you are always allies"));
        }
        return new Pair(own, target, null);
    }

    /** True when the nation module is loaded and both factions belong to the same nation. */
    private boolean sameNation(Faction a, Faction b) {
        if (a.nationId() == null || b.nationId() == null) {
            return false;
        }
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations == null) {
            return false;
        }
        try {
            return nations.sameNation(a, b);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }
}
