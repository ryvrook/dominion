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

/**
 * Chat map rows and UI grid cells centred on a player. North is up, toward negative Z.
 * The chat font is proportional, so every chat cell is the same glyph and only the colour changes.
 */
final class LandMap {
    static final String CELL = "#";
    /** Dim grey for wilderness that still reads on the chat background. */
    static final String WILD = "#5b6270";
    /** How far a zone cell moves the owner colour toward white. */
    static final double ZONE_LIGHTEN = 0.45;

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
                Msg.colored("Map at " + Keys.describe(center) + "  ", Msg.MUTED),
                Msg.colored(here == null ? "Wilderness" : here.label(), here == null ? Msg.TEXT : land.colorFor(player, here))));

        String[] cells = new String[w];
        for (int row = 0; row < h; row++) {
            int z = cz - h / 2 + row;
            for (int col = 0; col < w; col++) {
                int x = cx - w / 2 + col;
                long idx = Keys.chunk(x, z);
                Faction owner = idx == center ? null : claims.ownerAt(world, idx);
                if (idx == center) {
                    cells[col] = Land.YOU;
                } else if (owner == null) {
                    cells[col] = WILD;
                } else {
                    String base = colors.computeIfAbsent(owner.id(), k -> land.colorFor(player, owner));
                    cells[col] = zoned(world, idx, owner) ? lighter(base, ZONE_LIGHTEN) : base;
                }
            }
            rows.add(row(cells, row == h / 2 ? w / 2 : -1));
        }
        rows.addAll(legend());
        return rows;
    }

    /** One map row. Runs of the same colour share a segment and the marked cell is bold. */
    static Message row(String[] colors, int bold) {
        List<Message> parts = new ArrayList<>();
        for (String[] run : runs(colors, bold)) {
            Message m = Msg.colored(run[0], run[1]);
            parts.add(run[2] != null ? m.bold(true) : m);
        }
        return Message.join(parts.toArray(new Message[0]));
    }

    /**
     * Splits a row into {text, colour, bold or null} runs. The text is always the cell glyph
     * repeated, so the row is exactly as many glyphs wide as there are colours.
     */
    static List<String[]> runs(String[] colors, int bold) {
        List<String[]> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        String runColor = null;
        boolean runBold = false;
        for (int i = 0; i < colors.length; i++) {
            boolean b = i == bold;
            if (runColor != null && (!runColor.equals(colors[i]) || b != runBold)) {
                out.add(new String[] {run.toString(), runColor, runBold ? "bold" : null});
                run.setLength(0);
            }
            runColor = colors[i];
            runBold = b;
            run.append(CELL);
        }
        if (runColor != null) {
            out.add(new String[] {run.toString(), runColor, runBold ? "bold" : null});
        }
        return out;
    }

    /** The legend as two short lines of {label, colour} pairs. */
    static List<List<String[]>> legendEntries() {
        return List.of(
                List.of(new String[] {"You", Land.YOU}, new String[] {"Own", Relation.OWN.color()},
                        new String[] {"Ally", Relation.ALLY.color()}, new String[] {"Truce", Relation.TRUCE.color()},
                        new String[] {"Neutral", Relation.NEUTRAL.color()}),
                List.of(new String[] {"Enemy", Relation.ENEMY.color()}, new String[] {"Safe", Land.SAFEZONE},
                        new String[] {"War", Land.WARZONE}, new String[] {"Zone", lighter(Relation.OWN.color(), ZONE_LIGHTEN)},
                        new String[] {"Wild", WILD}));
    }

    private static List<Message> legend() {
        List<Message> lines = new ArrayList<>();
        for (List<String[]> line : legendEntries()) {
            List<Message> parts = new ArrayList<>();
            for (String[] e : line) {
                parts.add(Msg.colored(CELL + " ", e[1]));
                parts.add(Msg.colored(e[0] + "   ", Msg.MUTED));
            }
            lines.add(Message.join(parts.toArray(new Message[0])));
        }
        return lines;
    }

    /** Moves a #rrggbb colour toward white by the amount from 0 to 1. Other input comes back as is. */
    static String lighter(String hex, double amount) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return hex;
        }
        double a = Math.max(0, Math.min(1, amount));
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        r = (int) Math.round(r + (255 - r) * a);
        g = (int) Math.round(g + (255 - g) * a);
        b = (int) Math.round(b + (255 - b) * a);
        return String.format("#%02x%02x%02x", r, g, b);
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
