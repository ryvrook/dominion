package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
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
        body.section(Messages.get(UiText.RANK_NAMES));
        body.text(Messages.get(UiText.RANK_NAMES_HINT), Msg.MUTED);
        for (Rank r : Rank.values()) {
            String custom = f.rankNames().get(r.name());
            body.field(Messages.get(UiText.RANK_NAME_FOR, "rank", r.displayName().toLowerCase(java.util.Locale.ROOT)),
                    custom == null ? "" : custom, Btn.of(Messages.get(UiText.SAVE), "rankname", r.name()));
        }
    }

    void buildZones(Body body, Faction f) {
        body.section(Messages.get(UiText.ZONES));
        body.text(Messages.get(UiText.ZONES_HINT), Msg.MUTED);
        String here = currentZone(f);
        List<Zone> zones = f.zones();
        if (zones.isEmpty()) {
            body.text(Messages.get(UiText.NO_ZONES), Msg.MUTED);
        }
        Map<String, Integer> counts = chunkCounts(f);
        for (Zone z : zones) {
            int n = counts.getOrDefault(z.id(), 0);
            boolean standing = z.id().equals(here);
            body.row(standing ? Messages.get(UiText.ZONE_HERE, "zone", z.name()) : z.name(), standing ? Msg.GOOD : null,
                    new String[] {Messages.get(n == 1 ? UiText.CHUNK_ONE : UiText.CHUNKS, "count", n), "", "", ""},
                    Btn.of(Messages.get(UiText.ASSIGN_HERE), "zone_assign", z.id()),
                    Btn.of(Messages.get(UiText.PERMISSIONS), "zone_perms", z.id()),
                    Btn.of(Messages.get(UiText.DELETE), "zone_delete", z.id()));
        }
        body.field(Messages.get(UiText.NEW_ZONE_NAME), "", Btn.of(Messages.get(UiText.CREATE), "zone_create"));
        if (here != null) {
            body.buttons(Btn.of(Messages.get(UiText.CLEAR_THIS_CHUNK), "zone_clear"));
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
            case "zone_create" -> panel.reply(e.text().isEmpty() ? Result.fail(Messages.get(UiText.ZONE_NAME_FIRST))
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
                String name = z == null ? Messages.get(UiText.THIS_ZONE) : z.name();
                panel.confirmThen(Messages.get(UiText.DELETE_NAMED, "name", name),
                        Messages.get(UiText.ZONE_DELETE_BODY),
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
