package dev.ryvrook.dominion.command;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Claiming, the chat map, power, nearby players and sharing coordinates. */
final class LandCommands extends Section {
    private static final int NEAR_DEFAULT = 64;
    private static final int NEAR_MAX = 256;

    LandCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        registerClaim(root);
        registerUnclaim(root);
        registerMap(root);
        registerPower(root);
        registerNear(root);

        root.sub(new PlayerCmd("coords", Messages.get(CommandText.DESC_SHARE_YOUR_POSITION_WITH_YOUR_FACTION))).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Vector3d pos = Players.position(c.player());
            if (pos == null) {
                c.bad(Messages.get(dev.ryvrook.dominion.text.CommonText.POSITION_UNKNOWN));
                return;
            }
            int x = (int) Math.floor(pos.x);
            int y = (int) Math.floor(pos.y);
            int z = (int) Math.floor(pos.z);
            String at = c.world() == null
                    ? Messages.get(CommandText.COORDS_AT, "x", x, "y", y, "z", z)
                    : Messages.get(CommandText.COORDS_AT_IN, "x", x, "y", y, "z", z, "world", c.world().getName());
            Message msg = Message.join(Msg.prefix(), Msg.colored(c.name(), Relation.OWN.color()),
                    Msg.colored(" " + at, Msg.TEXT));
            chat().sendToFaction(f, msg, false);
        });
    }

    private String world(Call c) {
        return c.world() == null ? Players.worldName(c.player()) : c.world().getName();
    }

    private long chunk(Call c) {
        return Players.chunkOf(c.player());
    }

    private boolean located(Call c) {
        if (world(c) == null || chunk(c) == Long.MIN_VALUE) {
            c.bad(Messages.get(CommandText.YOUR_POSITION_IS_NOT_KNOWN_YET));
            return false;
        }
        return true;
    }

    private void registerClaim(PlayerCmd root) {
        PlayerCmd claim = root.sub(new PlayerCmd("claim", Messages.get(CommandText.DESC_CLAIM_THE_CHUNK_YOU_STAND_IN)));
        claim.handler(c -> {
            if (located(c)) {
                c.reply(claims().claim(c.uuid(), world(c), chunk(c)));
            }
        });

        claim.sub(new PlayerCmd("auto", Messages.get(CommandText.DESC_CLAIM_CHUNKS_AUTOMATICALLY_AS_YOU_WALK))).handler(c -> {
            PlayerData pd = ctx.player(c.uuid());
            boolean on = !pd.autoClaim();
            pd.setAutoClaim(on);
            c.good(on ? Messages.get(CommandText.AUTO_CLAIM_IS_ON_WALK_INTO) : Messages.get(CommandText.AUTO_CLAIM_IS_OFF));
        });

        PlayerCmd radius = claim.sub(new PlayerCmd("radius", Messages.get(CommandText.DESC_CLAIM_A_SQUARE_AROUND_YOU)));
        RequiredArg<Integer> radiusArg = radius.withRequiredArg("radius", Messages.get(CommandText.ARG_RADIUS_IN_CHUNKS), ArgTypes.INTEGER);
        radius.handler(c -> {
            if (located(c)) {
                c.reply(claims().claimRadius(c.uuid(), world(c), chunk(c), Math.max(0, c.arg(radiusArg))));
            }
        });

        PlayerCmd line = claim.sub(new PlayerCmd("line", Messages.get(CommandText.DESC_CLAIM_A_LINE_OF_CHUNKS_IN)));
        RequiredArg<Integer> lengthArg = line.withRequiredArg("length", Messages.get(CommandText.ARG_NUMBER_OF_CHUNKS), ArgTypes.INTEGER);
        line.handler(c -> {
            if (!located(c)) {
                return;
            }
            int[] dir = facing(c.player());
            c.reply(claims().claimLine(c.uuid(), world(c), chunk(c), dir[0], dir[1], Math.max(1, c.arg(lengthArg))));
        });
    }

    /** Cardinal direction the player looks along, as chunk steps on x and z. */
    private static int[] facing(PlayerRef player) {
        Transform t = player.getTransform();
        Vector3d d = t == null ? null : t.getDirection();
        if (d == null) {
            return new int[] {0, 1};
        }
        if (Math.abs(d.x) >= Math.abs(d.z)) {
            return new int[] {d.x >= 0 ? 1 : -1, 0};
        }
        return new int[] {0, d.z >= 0 ? 1 : -1};
    }

    private void registerUnclaim(PlayerCmd root) {
        PlayerCmd unclaim = root.sub(new PlayerCmd("unclaim", Messages.get(CommandText.DESC_RELEASE_THE_CHUNK_YOU_STAND_IN)));
        unclaim.handler(c -> {
            if (located(c)) {
                c.reply(claims().unclaim(c.uuid(), world(c), chunk(c)));
            }
        });

        PlayerCmd radius = unclaim.sub(new PlayerCmd("radius", Messages.get(CommandText.DESC_RELEASE_A_SQUARE_AROUND_YOU)));
        RequiredArg<Integer> radiusArg = radius.withRequiredArg("radius", Messages.get(CommandText.ARG_RADIUS_IN_CHUNKS), ArgTypes.INTEGER);
        radius.handler(c -> {
            if (located(c)) {
                c.reply(claims().unclaimRadius(c.uuid(), world(c), chunk(c), Math.max(0, c.arg(radiusArg))));
            }
        });

        unclaim.sub(new PlayerCmd("all", Messages.get(CommandText.DESC_RELEASE_EVERY_CHUNK_YOUR_FACTION_OWNS))).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            UUID actor = c.uuid();
            confirmations.put(actor, "unclaim all", () -> claims().unclaimAll(actor, true));
            c.send(Msg.warn(Messages.get(CommandText.UNCLAIM_ALL_CONFIRM, "count", claims().claimCount(f), "tag", f.tag())));
        });
    }

    private void registerMap(PlayerCmd root) {
        PlayerCmd map = root.sub(new PlayerCmd("map", Messages.get(CommandText.DESC_SHOW_THE_LAND_AROUND_YOU)));
        map.handler(c -> printMap(c, 1));
        PlayerCmd sized = new PlayerCmd(Messages.get(CommandText.DESC_MAP_SIZED));
        RequiredArg<Integer> page = sized.withRequiredArg("size", Messages.get(CommandText.ARG_MAP_SIZE_FROM_1_TO_3), ArgTypes.INTEGER);
        sized.handler(c -> printMap(c, Math.min(3, clampPage(c.arg(page)))));
        map.variant(sized);

        // Web map privacy. The snapshot never writes a hidden player's position.
        map.sub(new PlayerCmd("hide", Messages.get(CommandText.DESC_HIDE_YOURSELF_FROM_THE_WEB_MAP))).handler(c ->
                c.good(PlayerToggles.webMap(ctx.player(c.uuid()), true)));
        map.sub(new PlayerCmd("show", Messages.get(CommandText.DESC_SHOW_YOURSELF_ON_THE_WEB_MAP))).handler(c ->
                c.good(PlayerToggles.webMap(ctx.player(c.uuid()), false)));
    }

    private void printMap(Call c, int size) {
        int width = 21 + (size - 1) * 10;
        int height = 9 + (size - 1) * 4;
        List<Message> rows = claims().chatMap(c.uuid(), width, height);
        if (rows == null || rows.isEmpty()) {
            c.bad(Messages.get(CommandText.THE_MAP_IS_NOT_AVAILABLE_HERE));
            return;
        }
        for (Message row : rows) {
            c.send(row);
        }
    }

    private void registerPower(PlayerCmd root) {
        PlayerCmd power = root.sub(new PlayerCmd("power", Messages.get(CommandText.DESC_SHOW_YOUR_POWER)));
        power.handler(c -> showPower(c, c.uuid(), c.name()));
        PlayerCmd other = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_ANOTHER_PLAYERS_POWER));
        RequiredArg<String> who = other.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        other.handler(c -> {
            Targets.Target t = target(c, c.arg(who));
            if (t != null) {
                showPower(c, t.uuid(), t.name());
            }
        });
        power.variant(other);
    }

    private void showPower(Call c, UUID who, String name) {
        double p = power().powerOf(who);
        double max = power().maxPowerOf(who);
        c.info(Messages.get(CommandText.POWER_PLAYER, "name", name, "power", Msg.number(p), "max", Msg.number(max)));
        Faction f = factions().factionOf(who);
        if (f != null) {
            c.send(Msg.muted(Messages.get(CommandText.POWER_FACTION, "tag", f.tag(), "power", Msg.number(power().factionPower(f)), "max", Msg.number(power().factionMaxPower(f)), "claims", claims().claimCount(f), "maxClaims", claims().maxClaims(f))));
        }
    }

    private void registerNear(PlayerCmd root) {
        PlayerCmd near = root.sub(new PlayerCmd("near", Messages.get(CommandText.DESC_LIST_PLAYERS_NEAR_YOU)));
        near.handler(c -> showNear(c, NEAR_DEFAULT));
        PlayerCmd ranged = new PlayerCmd(Messages.get(CommandText.DESC_LIST_PLAYERS_WITHIN_A_RADIUS));
        RequiredArg<Integer> radius = ranged.withRequiredArg("radius", Messages.get(CommandText.ARG_RADIUS_IN_BLOCKS), ArgTypes.INTEGER);
        ranged.handler(c -> showNear(c, Math.min(NEAR_MAX, Math.max(1, c.arg(radius)))));
        near.variant(ranged);
    }

    private void showNear(Call c, int radius) {
        Vector3d me = Players.position(c.player());
        if (me == null || c.world() == null) {
            c.bad(Messages.get(dev.ryvrook.dominion.text.CommonText.POSITION_UNKNOWN));
            return;
        }
        record Seen(PlayerRef ref, double distance) {}
        List<Seen> seen = new ArrayList<>();
        for (PlayerRef other : c.world().getPlayerRefs()) {
            if (other.getUuid().equals(c.uuid())) {
                continue;
            }
            Vector3d pos = Players.position(other);
            if (pos == null) {
                continue;
            }
            double d = pos.distance(me);
            if (d <= radius) {
                seen.add(new Seen(other, d));
            }
        }
        if (seen.isEmpty()) {
            c.info(Messages.get(CommandText.NEAR_NOBODY, "radius", radius));
            return;
        }
        seen.sort(Comparator.comparingDouble(Seen::distance));
        c.info(Messages.get(CommandText.NEAR_HEADER, "count", seen.size(), "radius", radius));
        for (Seen s : seen) {
            Relation r = relations().betweenPlayers(c.uuid(), s.ref().getUuid());
            Faction f = factions().factionOf(s.ref().getUuid());
            String tag = f == null ? "" : f.tag() + " ";
            c.send(Message.join(Msg.muted(" - "), Msg.colored(tag + s.ref().getUsername(), r.color()),
                    Msg.muted("  " + Messages.get(CommandText.NEAR_DISTANCE, "distance", (int) s.distance()))));
        }
    }
}
