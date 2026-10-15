package dev.ryvrook.dominion.faction;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Predicate;

/** Shared plumbing for the faction operations. Membership changes run under {@link #lock}. */
final class FactionSupport {
    static final long HOUR = 3_600_000L;
    static final long DAY = 24 * HOUR;

    final DominionContext ctx;
    /** Serialises creation, joining, leaving and disbanding so tags and memberships stay consistent. */
    final Object lock = new Object();
    /** Online check. Tests swap it, the server answers through the universe. */
    Predicate<UUID> online = FactionSupport::liveOnline;

    FactionSupport(DominionContext ctx) {
        this.ctx = ctx;
    }

    /** True when the player is connected. False when no universe is running, as in unit tests. */
    static boolean liveOnline(UUID player) {
        return player != null && Universe.get() != null && Players.isOnline(player);
    }

    /** The connected player, or null when offline or when no universe is running. */
    static PlayerRef liveRef(UUID player) {
        return player == null || Universe.get() == null ? null : Players.online(player);
    }

    boolean isOnline(UUID player) {
        return online.test(player);
    }

    /** A faction plus the failure to return when the caller may not proceed. */
    record Gate(Faction faction, Result failure) {
        boolean blocked() {
            return failure != null;
        }
    }

    Settings settings() {
        return ctx.settings();
    }

    Faction factionOf(UUID player) {
        if (player == null) {
            return null;
        }
        PlayerData p = ctx.player(player);
        Faction f = ctx.repo().faction(p.factionId());
        return f != null && f.isMember(player) ? f : null;
    }

    /** Resolves the actor's faction and checks the rank permission. */
    Gate gate(UUID actor, FactionAction action) {
        Faction f = factionOf(actor);
        if (f == null) {
            return new Gate(null, Result.fail(Messages.get(CommonText.NOT_IN_FACTION)));
        }
        if (action != null && !Perms.can(f, actor, action, Time.now())) {
            return new Gate(f, Result.fail(Perms.denial(f, actor, action, Time.now())));
        }
        return new Gate(f, null);
    }

    String nameOf(UUID player) {
        if (player == null) {
            return Messages.get(FactionText.SERVER_NAME);
        }
        String n = ctx.player(player).name();
        return n == null || n.isEmpty() ? player.toString().substring(0, 8) : n;
    }

    void audit(Faction f, UUID who, String category, String text) {
        audit(f, who, nameOf(who), category, text);
    }

    void audit(Faction f, UUID who, String whoName, String category, String text) {
        f.audit(new AuditEntry(Time.now(), who, whoName, category, text), settings().integer("faction.auditLimit"));
        publish(new DominionEvent.AuditLogged(f.id(), who, category, text));
    }

    void publish(DominionEvent event) {
        ctx.bus().publish(event);
    }

    void changed(Faction f) {
        publish(new DominionEvent.FactionChanged(f.id()));
    }

    void tell(UUID player, Message message) {
        Msg.send(liveRef(player), message);
    }

    /** Sends to online members through the chat module, or directly when it is not loaded. */
    void toFaction(Faction f, Message message, boolean officersOnly) {
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat != null) {
            chat.sendToFaction(f, message, officersOnly);
            return;
        }
        for (Member m : f.members()) {
            Rank r = f.rankOf(m.uuid());
            if (!officersOnly || (r != null && r.atLeast(Rank.OFFICER))) {
                PlayerRef ref = liveRef(m.uuid());
                Msg.send(ref, message);
            }
        }
    }

    EconomyService economy() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null && eco.enabled() ? eco : null;
    }

    int maxMembers(Faction f) {
        return settings().integer("faction.maxMembers") + 5 * f.upgrade("members");
    }

    boolean full(Faction f) {
        return f.memberCount() >= maxMembers(f);
    }

    /** Adds the player as a member, keeps PlayerData in sync and announces it. Caller holds the lock. */
    Member addMember(Faction f, UUID player, String name, Rank rank, UUID actor, String how) {
        UUID who = actor == null ? player : actor;
        return addMember(f, player, name, rank, who, nameOf(who), how);
    }

    /** As above with the audit written under an explicit name, used by admin actions. */
    Member addMember(Faction f, UUID player, String name, Rank rank, UUID who, String whoName, String how) {
        long now = Time.now();
        Member m = new Member(player, name, rank, now);
        long hold = settings().integer("faction.newMemberHoldHours") * HOUR;
        if (rank != Rank.LEADER && hold > 0) {
            m.setHoldUntil(now + hold);
        }
        f.putMember(m);
        f.uninvite(player);
        f.removeRequest(player);
        PlayerData p = ctx.player(player);
        p.setName(name);
        p.setFactionId(f.id());
        audit(f, who, whoName, "member", Messages.get(FactionText.AUDIT_JOINED, "name", name, "how", how));
        publish(new DominionEvent.MemberJoined(f.id(), player));
        toFaction(f, Msg.good(Messages.get(FactionText.JOINED_NOTICE, "name", name)), false);
        return m;
    }

    /** Removes a member, clears their faction id and announces it. Caller holds the lock. */
    void removeMember(Faction f, UUID player, UUID actor, String reason, String auditText) {
        UUID who = actor == null ? player : actor;
        removeMember(f, player, who, nameOf(who), reason, auditText);
    }

    /** As above with the audit written under an explicit name, used by admin actions. */
    void removeMember(Faction f, UUID player, UUID who, String whoName, String reason, String auditText) {
        Member m = f.removeMember(player);
        if (m == null) {
            return;
        }
        PlayerData p = ctx.player(player);
        if (f.id().equals(p.factionId())) {
            p.setFactionId(null);
        }
        audit(f, who, whoName, "member", auditText);
        publish(new DominionEvent.MemberLeft(f.id(), player, reason));
    }

    /** Empties the bank of a disbanded faction atomically and pays it to the leader, or mails it when the payment is refused. */
    private void payOutBank(Faction f, UUID leader) {
        EconomyService eco = economy();
        double bank;
        if (eco != null) {
            bank = eco.bankDrain(f, Messages.get(FactionText.LEDGER_DISBANDED));
        } else {
            synchronized (f) {
                bank = f.bank();
                f.setBank(0);
            }
        }
        if (bank <= 0 || leader == null) {
            if (bank > 0) {
                ctx.logger().at(java.util.logging.Level.WARNING).log("Bank of disbanded faction %s had no leader to receive %s", f.tag(), bank);
            }
            return;
        }
        double amount = Math.round(bank * 100.0) / 100.0;
        if (eco == null) {
            PlayerData lp = ctx.player(leader);
            lp.setBalance(lp.balance() + amount);
            return;
        }
        if (eco.deposit(leader, amount, Messages.get(FactionText.LEDGER_DISBANDED)).failed()) {
            // The bank is already empty, so a refused payment would destroy the money. Mail it instead.
            dev.ryvrook.dominion.service.MarketService market = ctx.serviceOrNull(dev.ryvrook.dominion.service.MarketService.class);
            if (market != null) {
                market.mail(leader, java.util.List.of(), amount);
            } else {
                ctx.logger().at(java.util.logging.Level.WARNING).log("Bank of disbanded faction %s could not be paid to its leader %s, %s",
                        f.tag(), leader, amount);
            }
        }
    }

    /** Disbands the faction, returns the bank to the leader and clears every link to it. Caller holds the lock. */
    void disband(Faction f, UUID actor, String reason) {
        disband(f, actor, nameOf(actor), reason);
    }

    /** As above with the audit written under an explicit name, used by admin actions. */
    void disband(Faction f, UUID actor, String actorName, String reason) {
        UUID leader = f.leader();
        audit(f, actor, actorName, "faction", Messages.get(FactionText.AUDIT_DISBANDED, "reason", reason));
        toFaction(f, Msg.warn(Messages.get(FactionText.DISBANDED_NOTICE, "faction", f.tag())), false);
        for (Member m : f.members()) {
            f.removeMember(m.uuid());
            PlayerData p = ctx.player(m.uuid());
            if (f.id().equals(p.factionId())) {
                p.setFactionId(null);
            }
            publish(new DominionEvent.MemberLeft(f.id(), m.uuid(), "disband"));
        }
        for (Faction other : new ArrayList<>(ctx.repo().factions())) {
            if (other == f) {
                continue;
            }
            if (other.relations().containsKey(f.id())) {
                other.setRelation(f.id(), null);
            }
            other.removeRelationRequest(f.id());
        }
        dev.ryvrook.dominion.service.VaultService vault = ctx.serviceOrNull(dev.ryvrook.dominion.service.VaultService.class);
        if (vault != null) {
            try {
                vault.onDisband(f, f.leader());
            } catch (Exception e) {
                ctx.logger().at(java.util.logging.Level.WARNING).withCause(e).log("Vault cleanup failed for %s", f.tag());
            }
        }
        ctx.repo().removeFaction(f);
        // The bank is drained after the faction left the repository, so no lookup can credit it
        // any more and anything that landed before is part of the drained amount.
        payOutBank(f, leader);
        publish(new DominionEvent.FactionDisbanded(f.id(), f.tag()));
    }
}
