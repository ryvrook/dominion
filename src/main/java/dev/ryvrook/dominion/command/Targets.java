package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves a typed player name. Online players win, then members, invitees, requesters and
 * banned players of the actor's faction, then any player whose data is loaded.
 */
public final class Targets {
    public record Target(UUID uuid, String name) {}

    private final DominionContext ctx;

    public Targets(DominionContext ctx) {
        this.ctx = ctx;
    }

    public Target resolve(UUID actor, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        PlayerRef online = Players.byName(name);
        if (online != null) {
            return new Target(online.getUuid(), online.getUsername());
        }
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        Faction faction = actor == null || fs == null ? null : fs.factionOf(actor);
        if (faction != null) {
            Target t = fromFaction(faction, name);
            if (t != null) {
                return t;
            }
        }
        for (PlayerData pd : ctx.repo().loadedPlayers()) {
            if (pd.name().equalsIgnoreCase(name)) {
                return new Target(pd.uuid(), pd.name());
            }
        }
        return null;
    }

    /** Admin lookup that also searches every faction's member list. */
    public Target resolveAnywhere(String name) {
        Target t = resolve(null, name);
        if (t != null) {
            return t;
        }
        for (Faction f : ctx.repo().factions()) {
            for (Member m : f.members()) {
                if (m.name().equalsIgnoreCase(name)) {
                    return new Target(m.uuid(), m.name());
                }
            }
        }
        return null;
    }

    private Target fromFaction(Faction faction, String name) {
        for (Member m : faction.members()) {
            if (m.name().equalsIgnoreCase(name)) {
                return new Target(m.uuid(), m.name());
            }
        }
        List<UUID> others = new ArrayList<>(faction.invites().keySet());
        others.addAll(faction.requests());
        others.addAll(faction.bans());
        for (UUID uuid : others) {
            PlayerData pd = ctx.repo().playerIfLoaded(uuid);
            if (pd == null) {
                pd = ctx.player(uuid);
            }
            if (pd.name().equalsIgnoreCase(name)) {
                return new Target(uuid, pd.name());
            }
        }
        return null;
    }

    /** Display name for a UUID using loaded data or the faction roster. */
    public String nameOf(UUID uuid) {
        if (uuid == null) {
            return "nobody";
        }
        PlayerRef online = Players.online(uuid);
        if (online != null) {
            return online.getUsername();
        }
        PlayerData pd = ctx.repo().playerIfLoaded(uuid);
        if (pd != null && !pd.name().isBlank()) {
            return pd.name();
        }
        for (Faction f : ctx.repo().factions()) {
            Member m = f.member(uuid);
            if (m != null && !m.name().isBlank()) {
                return m.name();
            }
        }
        return uuid.toString().substring(0, 8);
    }
}
