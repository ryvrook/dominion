package dev.ryvrook.dominion.land;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chat map rows and UI grid cells centred on a player. North is up, toward negative Z. */
final class LandMap {
    private static final String CLAIMED = "#";
    private static final String FREE = "-";
    private static final String HERE = "@";
    private static final String ZONE = "+";

    private final Land land;
    private final ClaimServiceImpl claims;

    LandMap(Land land, ClaimServiceImpl claims) {
        this.land = land;
        this.claims = claims;
    }

    List<Message> chat(UUID player, int width, int height) {
        PlayerRef p = Players.online(player);
        String world = Players.worldName(p);
        long center = Players.chunkOf(p);
        if (p == null || world == null || center == Long.MIN_VALUE) {
            return List.of(Msg.bad("Your position is not known yet."));
        }
        int w = odd(width <= 0 ? 39 : width, 5, 61);
        int h = odd(height <= 0 ? 11 : height, 3, 21);
        int cx = Keys.chunkX(center);
        int cz = Keys.chunkZ(center);
        Map<UUID, String> colors = new HashMap<>();
        List<Message> rows = new ArrayList<>();

        Faction here = claims.ownerAt(world, center);
        rows.add(Message.join(Msg.prefix(),
                Msg.colored("Map around chunk " + Keys.describe(center) + ". You stand in ", Msg.TEXT),
                Msg.colored(here == null ? "Wilderness" : here.name(), land.colorFor(player, here)),
                Msg.colored(".", Msg.TEXT)));

        for (int row = 0; row < h; row++) {
            int z = cz - h / 2 + row;
            List<Message> parts = new ArrayList<>();
            StringBuilder run = new StringBuilder();
            String runColor = null;
            for (int col = 0; col < w; col++) {
                int x = cx - w / 2 + col;
                long idx = Keys.chunk(x, z);
                Faction owner = claims.ownerAt(world, idx);
                String glyph;
                String color;
                if (idx == center) {
                    glyph = HERE;
                    color = Land.YOU;
                } else if (owner == null) {
                    glyph = FREE;
                    color = Land.WILDERNESS;
                } else {
                    glyph = zoned(world, idx, owner) ? ZONE : CLAIMED;
                    color = colors.computeIfAbsent(owner.id(), k -> land.colorFor(player, owner));
                }
                if (runColor != null && !runColor.equals(color)) {
                    parts.add(Msg.colored(run.toString(), runColor));
                    run.setLength(0);
                }
                runColor = color;
                run.append(glyph);
            }
            if (runColor != null) {
                parts.add(Msg.colored(run.toString(), runColor));
            }
            rows.add(Message.join(parts.toArray(new Message[0])));
        }
        rows.add(legend());
        return rows;
    }

    private static Message legend() {
        return Message.join(
                Msg.colored(HERE + " You  ", Land.YOU),
                Msg.colored(CLAIMED + " Own  ", Relation.OWN.color()),
                Msg.colored(CLAIMED + " Ally  ", Relation.ALLY.color()),
                Msg.colored(CLAIMED + " Truce  ", Relation.TRUCE.color()),
                Msg.colored(CLAIMED + " Neutral  ", Relation.NEUTRAL.color()),
                Msg.colored(CLAIMED + " Enemy  ", Relation.ENEMY.color()),
                Msg.colored(CLAIMED + " SafeZone  ", Land.SAFEZONE),
                Msg.colored(CLAIMED + " WarZone  ", Land.WARZONE),
                Msg.colored(ZONE + " Zone  ", Msg.TEXT),
                Msg.colored(FREE + " Wilderness", Land.WILDERNESS));
    }

    /**
     * Cells row major from north west, each {colorHex, tag or empty, flags}. Flags is a comma
     * separated list drawn from player, own, raidable, breached, zone, safezone and warzone.
     */
    List<String[]> grid(UUID player, int radius) {
        PlayerRef p = Players.online(player);
        String world = Players.worldName(p);
        long center = Players.chunkOf(p);
        if (p == null || world == null || center == Long.MIN_VALUE) {
            return List.of();
        }
        int r = Math.max(0, Math.min(32, radius));
        int cx = Keys.chunkX(center);
        int cz = Keys.chunkZ(center);
        Faction mine = land.factionOf(player);
        Map<UUID, String> colors = new HashMap<>();
        Map<UUID, Boolean> raidable = new HashMap<>();
        List<String[]> cells = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        StringBuilder flags = new StringBuilder();
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                long idx = Keys.chunk(x, z);
                Faction owner = claims.ownerAt(world, idx);
                flags.setLength(0);
                if (idx == center) {
                    flag(flags, "player");
                }
                String color = Land.WILDERNESS;
                String tag = "";
                if (owner != null) {
                    tag = owner.tag();
                    color = colors.computeIfAbsent(owner.id(), k -> land.colorFor(player, owner));
                    if (owner.type() == FactionType.SAFEZONE) {
                        flag(flags, "safezone");
                    } else if (owner.type() == FactionType.WARZONE) {
                        flag(flags, "warzone");
                    } else {
                        if (mine != null && mine.id().equals(owner.id())) {
                            flag(flags, "own");
                        }
                        if (raidable.computeIfAbsent(owner.id(), k -> claims.raidable(owner))) {
                            flag(flags, "raidable");
                        }
                        if (mine != null && claims.breached(world, idx, mine.id())) {
                            flag(flags, "breached");
                        }
                        if (zoned(world, idx, owner)) {
                            flag(flags, "zone");
                        }
                    }
                }
                cells.add(new String[] { color, tag, flags.isEmpty() ? "" : flags.toString() });
            }
        }
        return cells;
    }

    /** True when the chunk sits in a zone that still exists in the owner's zone list. */
    private boolean zoned(String world, long idx, Faction owner) {
        if (owner.isSystem()) {
            return false;
        }
        ClaimEntry entry = land.ctx.repo().claimAt(world, idx);
        return entry != null && entry.zoneId() != null && owner.zone(entry.zoneId()) != null;
    }

    private static void flag(StringBuilder flags, String flag) {
        if (!flags.isEmpty()) {
            flags.append(',');
        }
        flags.append(flag);
    }

    private static int odd(int value, int min, int max) {
        int v = Math.max(min, Math.min(max, value));
        return v % 2 == 0 ? v - 1 : v;
    }
}
