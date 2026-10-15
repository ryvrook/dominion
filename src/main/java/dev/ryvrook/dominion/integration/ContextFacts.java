package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.RelationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The mapper facts read from the repository and the relation service. Memory reads only. */
final class ContextFacts implements StatMapper.Facts {
    private final DominionContext ctx;

    ContextFacts(DominionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isMember(UUID factionId, UUID player) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        return f != null && !f.isSystem() && f.isMember(player);
    }

    @Override
    public List<UUID> members(UUID factionId) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || f.isSystem()) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (Member m : f.members()) {
            out.add(m.uuid());
        }
        return out;
    }

    @Override
    public boolean atWar(UUID factionA, UUID factionB) {
        Faction a = factionA == null ? null : ctx.repo().faction(factionA);
        Faction b = factionB == null ? null : ctx.repo().faction(factionB);
        if (a == null || b == null || a.isSystem() || b.isSystem()) {
            return false;
        }
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        if (rs != null) {
            try {
                return rs.between(a, b) == Relation.ENEMY;
            } catch (RuntimeException e) {
                // fall back to the stored relations below
            }
        }
        return a.relationTo(b.id()) == Relation.ENEMY || b.relationTo(a.id()) == Relation.ENEMY;
    }

    @Override
    public UUID founder(UUID factionId) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || f.isSystem()) {
            return null;
        }
        long age = System.currentTimeMillis() - f.createdAt();
        long window = ctx.settings().integer("integrations.foundedWindowSeconds") * 1000L;
        return age >= -window && age <= window ? f.leader() : null;
    }
}
