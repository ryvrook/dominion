package dev.ryvrook.dominion.relation;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
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
            return Result.fail(Messages.get(RelationText.NOT_SETTABLE));
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
                return Result.fail(Messages.get(RelationText.NOT_ASKED, "faction", p.target.tag()));
            }
            if (req.expiresAt() <= Time.now()) {
                p.own.removeRelationRequest(p.target.id());
                return Result.fail(Messages.get(RelationText.REQUEST_EXPIRED));
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
            return Result.fail(Messages.get(RelationText.NOT_ASKED, "faction", p.target.tag()));
        }
        effects.declined(p.own, p.target, req.type(), actor);
        return Result.pass(Messages.get(RelationText.DECLINED, "faction", p.target.tag()));
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
            return Result.fail(Messages.get(RelationText.ALREADY_ENEMIES, "faction", target.tag()));
        }
        own.setRelation(target.id(), Relation.ENEMY);
        Relation theirs = target.relationTo(own.id());
        if (theirs == Relation.ALLY || theirs == Relation.TRUCE) {
            target.setRelation(own.id(), Relation.NEUTRAL);
        }
        clearRequests(own, target);
        effects.changed(own, target, Relation.ENEMY, actor);
        return Result.pass(Messages.get(RelationText.NOW_ENEMY, "faction", target.tag()));
    }

    private Result neutral(Faction own, Faction target, UUID actor) {
        Relation current = own.relationTo(target.id());
        boolean pending = target.relationRequests().containsKey(own.id());
        if (current == Relation.NEUTRAL && !pending) {
            return Result.fail(Messages.get(RelationText.ALREADY_NEUTRAL, "faction", target.tag()));
        }
        target.removeRelationRequest(own.id());
        if (current == Relation.NEUTRAL) {
            return Result.pass(Messages.get(RelationText.WITHDREW, "faction", target.tag()));
        }
        own.setRelation(target.id(), Relation.NEUTRAL);
        if (current == Relation.ALLY || current == Relation.TRUCE) {
            target.setRelation(own.id(), Relation.NEUTRAL);
        }
        effects.changed(own, target, Relation.NEUTRAL, actor);
        return Result.pass(Messages.get(RelationText.NOW_NEUTRAL, "faction", target.tag()));
    }

    private Result propose(Faction own, Faction target, Relation relation, UUID actor) {
        if (own.relationTo(target.id()) == relation && target.relationTo(own.id()) == relation) {
            return Result.fail(Messages.get(relation == Relation.ALLY ? RelationText.ALREADY_ALLIED : RelationText.ALREADY_TRUCE, "faction", target.tag()));
        }
        Result cap = capacity(own, target, relation, Messages.get(RelationText.YOUR_FACTION));
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
            return Result.fail(Messages.get(RelationText.ALREADY_ASKED, "faction", target.tag()));
        }
        long hours = Math.max(1, ctx.settings().integer("relation.requestHours"));
        // Stored on the receiving faction, keyed by and naming the sending faction.
        target.putRelationRequest(own.id(), new Faction.RelationRequest(relation, own.id(), now + hours * HOUR));
        effects.requested(own, target, relation, actor);
        return Result.pass(Messages.get(hours == 1 ? RelationText.ASKED_HOUR : RelationText.ASKED_HOURS, "faction", target.tag(), "hours", hours));
    }

    private Result establish(Faction own, Faction target, Relation relation, UUID actor) {
        Result cap = capacity(own, target, relation, Messages.get(RelationText.YOUR_FACTION));
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
        return Result.pass(Messages.get(relation == Relation.ALLY ? RelationText.NOW_ALLIED : RelationText.NOW_TRUCE, "faction", target.tag()));
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
            String id = relation == Relation.ALLY
                    ? (max == 1 ? RelationText.MAX_ALLY : RelationText.MAX_ALLIES)
                    : (max == 1 ? RelationText.MAX_TRUCE : RelationText.MAX_TRUCES);
            return Result.fail(Messages.get(id, "who", who, "max", max));
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
            return new Pair(null, null, Result.fail(Messages.get(CommonText.NOT_IN_FACTION)));
        }
        if (!fs.can(actor, FactionAction.RELATIONS)) {
            return new Pair(own, null, Result.fail(Messages.get(RelationText.RANK_DENIED)));
        }
        Faction target = fs.byTag(targetTag);
        if (target == null) {
            return new Pair(own, null, Result.fail(Messages.get(RelationText.NO_FACTION_WITH_TAG)));
        }
        if (target.id().equals(own.id())) {
            return new Pair(own, target, Result.fail(Messages.get(RelationText.OWN_FACTION)));
        }
        if (target.isSystem()) {
            return new Pair(own, target, Result.fail(Messages.get(RelationText.ZONES_NO_DIPLOMACY)));
        }
        if (sameNation(own, target)) {
            return new Pair(own, target, Result.fail(Messages.get(RelationText.SAME_NATION, "faction", target.tag())));
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
