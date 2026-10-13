package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Players;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rank names and zones on the settings tab. */
final class ZonesSection {
    private final DominionContext ctx;
    private final DominionPanel panel;

    ZonesSection(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    void buildRankNames(Body body, Faction f) {
        body.section("Rank names");
        body.text("Give each rank your own name. Leave a field empty and save to go back to the default name.", Msg.MUTED);
        for (Rank r : Rank.values()) {
            String custom = f.rankNames().get(r.name());
            body.field("Name for " + r.displayName().toLowerCase(java.util.Locale.ROOT), custom == null ? "" : custom, Btn.of("Save", "rankname", r.name()));
        }
    }

    void buildZones(Body body, Faction f) {
        body.section("Zones");
        body.text("A zone is a named part of your land with its own permissions. Stand in a chunk and press Assign here "
                + "to put it into a zone. Chunks outside any zone follow the faction permissions. "
                + "Changing the permissions of a zone needs the permissions right.", Msg.MUTED);
        String here = currentZone(f);
        List<Zone> zones = f.zones();
        if (zones.isEmpty()) {
            body.text("Your faction has no zones yet.", Msg.MUTED);
        }
        Map<String, Integer> counts = chunkCounts(f);
        for (Zone z : zones) {
            int n = counts.getOrDefault(z.id(), 0);
            boolean standing = z.id().equals(here);
            body.row(z.name() + (standing ? "  (here)" : ""), standing ? Msg.GOOD : null,
                    new String[] {n + (n == 1 ? " chunk" : " chunks"), "", "", ""},
                    Btn.of("Assign here", "zone_assign", z.id()), Btn.of("Permissions", "zone_perms", z.id()),
                    Btn.of("Delete", "zone_delete", z.id()));
        }
        body.field("New zone name", "", Btn.of("Create", "zone_create"));
        if (here != null) {
            body.buttons(Btn.of("Clear this chunk", "zone_clear"));
        }
    }

    /** Handles zone and rank name buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        UUID me = panel.uuid();
        switch (e.action) {
            case "rankname" -> {
                Rank r = Rank.parse(e.value());
                if (r == null) {
                    return true;
                }
                // The service clears a custom name when it is given the built in one.
                String name = e.text().isEmpty() ? r.displayName() : e.text();
                panel.reply(factions().setRankName(me, r, name));
            }
            case "zone_create" -> panel.reply(e.text().isEmpty() ? Result.fail("Type a name for the zone first.")
                    : factions().zoneCreate(me, e.text()));
            case "zone_assign" -> panel.reply(factions().zoneAssign(me, e.value()));
            case "zone_clear" -> panel.reply(factions().zoneAssign(me, null));
            case "zone_perms" -> {
                panel.openZonePerms(e.value());
                return false;
            }
            case "zone_delete" -> {
                Faction f = factions().factionOf(me);
                Zone z = f == null ? null : f.zone(e.value());
                String name = z == null ? "this zone" : z.name();
                panel.confirmThen("Delete " + name,
                        "The zone and its permissions are removed. Its chunks stay claimed and follow the faction permissions again.",
                        () -> factions().zoneDelete(me, e.value()), DominionPanel.SETTINGS);
                return false;
            }
            default -> {
                return null;
            }
        }
        return true;
    }

    /** The zone id of the chunk the player stands in when it belongs to their faction. */
    private String currentZone(Faction f) {
        String world = Players.worldName(panel.player());
        long chunk = Players.chunkOf(panel.player());
        if (world == null || chunk == Long.MIN_VALUE) {
            return null;
        }
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        ClaimEntry entry = claims == null ? ctx.repo().claimAt(world, chunk) : claims.claimAt(world, chunk);
        return entry != null && f.id().equals(entry.factionId()) ? entry.zoneId() : null;
    }

    private Map<String, Integer> chunkCounts(Faction f) {
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
}
