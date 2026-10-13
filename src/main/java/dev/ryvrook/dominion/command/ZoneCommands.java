package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.text.Msg;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Zones inside your claims and custom rank names. */
final class ZoneCommands extends Section {

    ZoneCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd zone = root.sub(new PlayerCmd("zone", "List the zones inside your land."));
        zone.handler(this::list);
        zone.sub(new PlayerCmd("list", "List the zones inside your land.")).handler(this::list);

        PlayerCmd create = zone.sub(new PlayerCmd("create", "Create a zone with its own permissions."));
        RequiredArg<String> createName = create.withRequiredArg("name", "Zone name", ArgTypes.GREEDY_STRING);
        create.handler(c -> c.reply(factions().zoneCreate(c.uuid(), c.arg(createName).trim())));

        PlayerCmd delete = zone.sub(new PlayerCmd("delete", "Delete a zone. Its chunks go back to the faction rules."));
        RequiredArg<String> deleteName = delete.withRequiredArg("name", "Zone name", ArgTypes.GREEDY_STRING);
        delete.handler(c -> {
            Faction f = requireFaction(c);
            if (f != null) {
                c.reply(factions().zoneDelete(c.uuid(), zoneId(f, c.arg(deleteName))));
            }
        });

        PlayerCmd assign = zone.sub(new PlayerCmd("assign", "Put the chunk you stand in into a zone."));
        RequiredArg<String> assignName = assign.withRequiredArg("name", "Zone name", ArgTypes.GREEDY_STRING);
        assign.handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Zone z = find(f, c.arg(assignName));
            if (z == null) {
                c.bad("Your faction has no zone called " + c.arg(assignName) + ". See /f zone list.");
                return;
            }
            c.reply(factions().zoneAssign(c.uuid(), z.id()));
        });

        zone.sub(new PlayerCmd("clear", "Take the chunk you stand in out of its zone."))
                .handler(c -> c.reply(factions().zoneAssign(c.uuid(), null)));

        PlayerCmd rankName = root.sub(new PlayerCmd("rankname", "Rename one of the four ranks for your faction."));
        RequiredArg<String> rankArg = rankName.withRequiredArg("rank", "Recruit, member, officer or leader", ArgTypes.STRING);
        RequiredArg<String> nameArg = rankName.withRequiredArg("name", "New name, or reset", ArgTypes.GREEDY_STRING);
        rankName.handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Rank r = parseRank(f, c.arg(rankArg));
            if (r == null) {
                c.bad("Ranks are recruit, member, officer and leader.");
                return;
            }
            String name = c.arg(nameArg).trim();
            if (name.isEmpty() || name.equalsIgnoreCase("reset") || name.equalsIgnoreCase("default")) {
                // The service clears a custom name when it is given the built in one.
                name = r.displayName();
            }
            c.reply(factions().setRankName(c.uuid(), r, name));
        });
    }

    private void list(Call c) {
        Faction f = requireFaction(c);
        if (f == null) {
            return;
        }
        List<Zone> zones = f.zones();
        if (zones.isEmpty()) {
            c.info("Your faction has no zones. Create one with /f zone create and a name.");
            return;
        }
        Map<String, Integer> counts = chunkCounts(ctx, f);
        c.info("Zones of " + f.tag());
        for (Zone z : zones) {
            int n = counts.getOrDefault(z.id(), 0);
            c.send(Message.join(Msg.colored(" " + z.name(), Msg.GOLD),
                    Msg.muted("  " + n + (n == 1 ? " chunk" : " chunks"))));
        }
        c.send(Msg.muted(" Stand in a chunk and use /f zone assign with a name. Edit zone permissions under Zones on the settings tab of /f. That needs the permissions right."));
    }

    /** A zone by name or id, ignoring case. */
    static Zone find(Faction f, String text) {
        if (f == null || text == null) {
            return null;
        }
        String t = text.trim();
        Zone byId = f.zone(t);
        if (byId != null) {
            return byId;
        }
        for (Zone z : f.zones()) {
            if (z.name().equalsIgnoreCase(t)) {
                return z;
            }
        }
        return null;
    }

    /** The zone id for a typed name, or the text itself so the service can explain the miss. */
    static String zoneId(Faction f, String text) {
        Zone z = find(f, text);
        return z == null ? text.trim() : z.id();
    }

    /** Chunks assigned to each zone of the faction across every world. */
    static Map<String, Integer> chunkCounts(DominionContext ctx, Faction f) {
        Map<String, Integer> out = new HashMap<>();
        for (String world : ctx.repo().claimWorlds()) {
            for (ClaimEntry e : ctx.repo().claimsIn(world).values()) {
                if (e.zoneId() != null && f.id().equals(e.factionId())) {
                    out.merge(e.zoneId(), 1, Integer::sum);
                }
            }
        }
        return out;
    }

    /** A rank by its built in name or the faction's custom name. */
    static Rank parseRank(Faction f, String text) {
        if (text == null) {
            return null;
        }
        // Custom names win, so a recruit rank renamed to Member is found by that name.
        if (f != null) {
            for (Rank candidate : Rank.values()) {
                if (f.rankNames().containsKey(candidate.name())
                        && f.rankName(candidate).toLowerCase(Locale.ROOT).equals(text.trim().toLowerCase(Locale.ROOT))) {
                    return candidate;
                }
            }
        }
        return Rank.parse(text.trim());
    }
}
