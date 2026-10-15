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
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Claiming, the chat map, power, nearby players and sharing coordinates. */
final class LandCommands extends Section {

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

        root.sub(new PlayerCmd("coords", "Share your position with your faction.")).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Vector3d pos = Players.position(c.player());
            if (pos == null) {
                c.bad("Your position is not known yet.");
                return;
            }
            String world = c.world() == null ? "" : " in " + c.world().getName();
            Message msg = Message.join(Msg.prefix(), Msg.colored(c.name(), Relation.OWN.color()),
                    Msg.colored(" is at " + (int) Math.floor(pos.x) + ", " + (int) Math.floor(pos.y) + ", "
                            + (int) Math.floor(pos.z) + world, Msg.TEXT));
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
            c.bad("Your position is not known yet. Try again in a moment.");
            return false;
        }
        return true;
    }

    private void registerClaim(PlayerCmd root) {
        PlayerCmd claim = root.sub(new PlayerCmd("claim", "Claim the chunk you stand in."));
        claim.handler(c -> {
            if (located(c)) {
                c.reply(claims().claim(c.uuid(), world(c), chunk(c)));
            }
        });

        claim.sub(new PlayerCmd("auto", "Claim chunks automatically as you walk.")).handler(c -> {
            PlayerData pd = ctx.player(c.uuid());
            boolean on = !pd.autoClaim();
            pd.setAutoClaim(on);
            c.good(on ? "Auto claim is on. Walk into wilderness to claim it." : "Auto claim is off.");
        });

        PlayerCmd radius = claim.sub(new PlayerCmd("radius", "Claim a square around you."));
        RequiredArg<Integer> radiusArg = radius.withRequiredArg("radius", "Radius in chunks", ArgTypes.INTEGER);
        radius.handler(c -> {
            if (located(c)) {
                c.reply(claims().claimRadius(c.uuid(), world(c), chunk(c), Math.max(0, c.arg(radiusArg))));
            }
        });

        PlayerCmd line = claim.sub(new PlayerCmd("line", "Claim a line of chunks in the direction you face."));
        RequiredArg<Integer> lengthArg = line.withRequiredArg("length", "Number of chunks", ArgTypes.INTEGER);
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
        PlayerCmd unclaim = root.sub(new PlayerCmd("unclaim", "Release the chunk you stand in."));
        unclaim.handler(c -> {
            if (located(c)) {
                c.reply(claims().unclaim(c.uuid(), world(c), chunk(c)));
            }
        });

        PlayerCmd radius = unclaim.sub(new PlayerCmd("radius", "Release a square around you."));
        RequiredArg<Integer> radiusArg = radius.withRequiredArg("radius", "Radius in chunks", ArgTypes.INTEGER);
        radius.handler(c -> {
            if (located(c)) {
                c.reply(claims().unclaimRadius(c.uuid(), world(c), chunk(c), Math.max(0, c.arg(radiusArg))));
            }
        });

        unclaim.sub(new PlayerCmd("all", "Release every chunk your faction owns.")).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            UUID actor = c.uuid();
            confirmations.put(actor, "unclaim all", () -> claims().unclaimAll(actor, true));
            c.send(Msg.warn("This releases all " + claims().claimCount(f) + " chunks of " + f.tag()
                    + ". Type /f confirm within 30 seconds to go ahead."));
        });
    }

    private void registerMap(PlayerCmd root) {
        PlayerCmd map = root.sub(new PlayerCmd("map", "Show the land around you."));
        map.handler(c -> printMap(c, 1));
        PlayerCmd sized = new PlayerCmd("Show a larger map. Size 1 to 3.");
        RequiredArg<Integer> page = sized.withRequiredArg("size", "Map size from 1 to 3", ArgTypes.INTEGER);
        sized.handler(c -> printMap(c, Math.min(3, clampPage(c.arg(page)))));
        map.variant(sized);

        // Web map privacy. The snapshot never writes a hidden player's position.
        map.sub(new PlayerCmd("hide", "Hide yourself from the web map.")).handler(c ->
                c.good(PlayerToggles.webMap(ctx.player(c.uuid()), true)));
        map.sub(new PlayerCmd("show", "Show yourself on the web map again.")).handler(c ->
                c.good(PlayerToggles.webMap(ctx.player(c.uuid()), false)));
    }

    private void printMap(Call c, int size) {
        int width = 21 + (size - 1) * 10;
        int height = 9 + (size - 1) * 4;
        List<Message> rows = claims().chatMap(c.uuid(), width, height);
        if (rows == null || rows.isEmpty()) {
            c.bad("The map is not available here.");
            return;
        }
        for (Message row : rows) {
            c.send(row);
        }
    }

    private void registerPower(PlayerCmd root) {
        PlayerCmd power = root.sub(new PlayerCmd("power", "Show your power."));
        power.handler(c -> showPower(c, c.uuid(), c.name()));
        PlayerCmd other = new PlayerCmd("Show another player's power.");
        RequiredArg<String> who = other.withRequiredArg("player", "Player name", ArgTypes.STRING);
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
        c.info(name + " has " + Msg.number(p) + " of " + Msg.number(max) + " power.");
        Faction f = factions().factionOf(who);
        if (f != null) {
            c.send(Msg.muted(f.tag() + " has " + Msg.number(power().factionPower(f)) + " of "
                    + Msg.number(power().factionMaxPower(f)) + " power and holds " + claims().claimCount(f)
                    + " of " + claims().maxClaims(f) + " claims."));
        }
    }

    private void registerNear(PlayerCmd root) {
        PlayerCmd near = root.sub(new PlayerCmd("near", "List players near you."));
        near.handler(c -> showNear(c, ctx.settings().integer("land.nearDefault")));
        PlayerCmd ranged = new PlayerCmd("List players within a radius.");
        RequiredArg<Integer> radius = ranged.withRequiredArg("radius", "Radius in blocks", ArgTypes.INTEGER);
        ranged.handler(c -> showNear(c, Math.min(ctx.settings().integer("land.nearMax"), Math.max(1, c.arg(radius)))));
        near.variant(ranged);
    }

    private void showNear(Call c, int radius) {
        Vector3d me = Players.position(c.player());
        if (me == null || c.world() == null) {
            c.bad("Your position is not known yet.");
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
            c.info("Nobody is within " + radius + " blocks.");
            return;
        }
        seen.sort(Comparator.comparingDouble(Seen::distance));
        c.info(seen.size() + " players within " + radius + " blocks");
        for (Seen s : seen) {
            Relation r = relations().betweenPlayers(c.uuid(), s.ref().getUuid());
            Faction f = factions().factionOf(s.ref().getUuid());
            String tag = f == null ? "" : f.tag() + " ";
            c.send(Message.join(Msg.muted(" - "), Msg.colored(tag + s.ref().getUsername(), r.color()),
                    Msg.muted("  " + (int) s.distance() + " blocks")));
        }
    }
}
