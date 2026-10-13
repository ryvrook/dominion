package dev.ryvrook.dominion.land;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.storage.Repository;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Lazy access to the services other modules provide, with safe fallbacks so land keeps working
 * when a module is missing. Never resolves a service before it is needed.
 */
final class Land {
    static final String WILDERNESS = "#6b7280";
    static final String SAFEZONE = Msg.GOLD;
    static final String WARZONE = "#e0413c";
    static final String YOU = "#ffffff";

    final DominionContext ctx;

    Land(DominionContext ctx) {
        this.ctx = ctx;
    }

    Repository repo() {
        return ctx.repo();
    }

    Settings settings() {
        return ctx.settings();
    }

    Faction factionOf(UUID player) {
        if (player == null) {
            return null;
        }
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs != null) {
            return fs.factionOf(player);
        }
        return ctx.repo().faction(ctx.player(player).factionId());
    }

    boolean can(UUID actor, FactionAction action, Faction faction) {
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs != null) {
            return fs.can(actor, action);
        }
        Rank rank = faction.rankOf(actor);
        return rank != null && faction.rankCan(rank, action);
    }

    double power(Faction f) {
        PowerService ps = ctx.serviceOrNull(PowerService.class);
        if (ps != null) {
            return ps.factionPower(f);
        }
        double total = f.powerBoost();
        for (Member m : f.members()) {
            total += ctx.player(m.uuid()).power();
        }
        return total;
    }

    double ratio(Faction f) {
        PowerService ps = ctx.serviceOrNull(PowerService.class);
        if (ps != null) {
            return ps.factionRatio(f);
        }
        double max = ctx.settings().decimal("power.max") * Math.max(1, f.memberCount());
        return max <= 0 ? 0 : Math.max(0, Math.min(1, power(f) / max));
    }

    Relation relation(UUID viewer, Faction target) {
        if (target == null) {
            return Relation.NEUTRAL;
        }
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        if (rs != null) {
            return rs.playerToFaction(viewer, target);
        }
        Faction own = factionOf(viewer);
        return own == null ? Relation.NEUTRAL : own.relationTo(target.id());
    }

    Relation between(Faction a, Faction b) {
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        if (rs != null) {
            return rs.between(a, b);
        }
        return a.relationTo(b.id());
    }

    /** Map and banner colour of a chunk owner as seen by the viewer. */
    String colorFor(UUID viewer, Faction owner) {
        if (owner == null) {
            return WILDERNESS;
        }
        if (owner.type() == FactionType.SAFEZONE) {
            return SAFEZONE;
        }
        if (owner.type() == FactionType.WARZONE) {
            return WARZONE;
        }
        return relation(viewer, owner).color();
    }

    EconomyService economy() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null && eco.enabled() ? eco : null;
    }

    void audit(Faction f, UUID who, String text) {
        String whoName = who == null ? "Server" : ctx.player(who).name();
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs != null) {
            fs.audit(f, who, whoName, "land", text);
        } else {
            f.audit(new AuditEntry(Time.now(), who, whoName, "land", text), ctx.settings().integer("faction.auditLimit"));
        }
    }

    String nameOf(UUID player) {
        if (player == null) {
            return "the server";
        }
        String n = ctx.player(player).name();
        return n == null || n.isEmpty() ? player.toString().substring(0, 8) : n;
    }

    /** Advances a mission counter when the progress module is loaded. */
    void progress(UUID player, String kind) {
        ProgressService ps = ctx.serviceOrNull(ProgressService.class);
        if (ps == null || player == null) {
            return;
        }
        try {
            // The outpost missions boost multiplies the reward when a mission completes, so progress counts one.
            ps.advance(player, kind, 1);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion progress update failed");
        }
    }

    /** True when the shield module is loaded and covers the chunk of the faction right now. */
    boolean shielded(Faction f, String world, long chunk) {
        ShieldService sh = ctx.serviceOrNull(ShieldService.class);
        if (sh == null || f == null || world == null) {
            return false;
        }
        try {
            return sh.shielded(f, world, chunk);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shield check failed");
            return false;
        }
    }

    void publish(DominionEvent event) {
        ctx.bus().publish(event);
    }

    int onlineMembers(Faction f) {
        int n = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                n++;
            }
        }
        return n;
    }

    boolean graceActive() {
        if (!ctx.settings().bool("grace.active")) {
            return false;
        }
        long ends = ctx.settings().longValue("grace.endsAt");
        return ends <= 0 || Time.now() < ends;
    }

    World world(String name) {
        try {
            Universe u = Universe.get();
            return u == null || name == null ? null : u.getWorld(name);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
