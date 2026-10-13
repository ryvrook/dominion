package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.text.Msg;

import java.util.Map;

/** Faction home, warps and the stuck escape. */
final class HomeCommands extends Section {

    HomeCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        root.sub(new PlayerCmd("home", "Teleport to your faction home.")).handler(c -> c.reply(homes().home(c.uuid())));
        root.sub(new PlayerCmd("sethome", "Set the faction home where you stand.")).handler(c -> c.reply(homes().setHome(c.uuid())));
        root.sub(new PlayerCmd("delhome", "Remove the faction home.")).handler(c -> c.reply(homes().delHome(c.uuid())));

        PlayerCmd allyHome = root.sub(new PlayerCmd("allyhome", "Teleport to the home of an ally that allows it."));
        RequiredArg<String> allyTag = allyHome.withRequiredArg("tag", "Ally faction tag", ArgTypes.STRING);
        allyHome.handler(c -> c.reply(homes().allyHome(c.uuid(), c.arg(allyTag))));

        PlayerCmd warp = root.sub(new PlayerCmd("warp", "Teleport to a faction warp."));
        RequiredArg<String> warpName = warp.withRequiredArg("name", "Warp name", ArgTypes.STRING);
        warp.handler(c -> c.reply(homes().warp(c.uuid(), c.arg(warpName), null)));
        PlayerCmd warpLocked = new PlayerCmd("Teleport to a warp that has a password.");
        RequiredArg<String> lockedName = warpLocked.withRequiredArg("name", "Warp name", ArgTypes.STRING);
        RequiredArg<String> lockedPass = warpLocked.withRequiredArg("password", "Warp password", ArgTypes.STRING);
        warpLocked.handler(c -> c.reply(homes().warp(c.uuid(), c.arg(lockedName), c.arg(lockedPass))));
        warp.variant(warpLocked);

        PlayerCmd setwarp = root.sub(new PlayerCmd("setwarp", "Create a warp where you stand."));
        RequiredArg<String> setName = setwarp.withRequiredArg("name", "Warp name", ArgTypes.STRING);
        setwarp.handler(c -> c.reply(homes().setWarp(c.uuid(), c.arg(setName), null)));
        PlayerCmd setwarpLocked = new PlayerCmd("Create a warp protected by a password.");
        RequiredArg<String> setLockedName = setwarpLocked.withRequiredArg("name", "Warp name", ArgTypes.STRING);
        RequiredArg<String> setLockedPass = setwarpLocked.withRequiredArg("password", "Warp password", ArgTypes.STRING);
        setwarpLocked.handler(c -> c.reply(homes().setWarp(c.uuid(), c.arg(setLockedName), c.arg(setLockedPass))));
        setwarp.variant(setwarpLocked);

        PlayerCmd delwarp = root.sub(new PlayerCmd("delwarp", "Delete a warp."));
        RequiredArg<String> delName = delwarp.withRequiredArg("name", "Warp name", ArgTypes.STRING);
        delwarp.handler(c -> c.reply(homes().delWarp(c.uuid(), c.arg(delName))));

        root.sub(new PlayerCmd("warps", "List your faction warps.")).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Map<String, Faction.Warp> warps = f.warps();
            if (warps.isEmpty()) {
                c.info("Your faction has no warps. Use /f setwarp to make one.");
                return;
            }
            c.info(f.tag() + " has " + warps.size() + " warps");
            for (Map.Entry<String, Faction.Warp> e : warps.entrySet()) {
                Faction.Warp w = e.getValue();
                boolean locked = w.password() != null && !w.password().isBlank();
                String where = w.location() == null ? "" : "  " + (int) w.location().x() + ", "
                        + (int) w.location().y() + ", " + (int) w.location().z();
                c.send(Message.join(Msg.muted(" - "), Msg.colored(e.getKey(), Msg.TEXT),
                        Msg.muted(where + (locked ? "  locked" : ""))));
            }
        });

        root.sub(new PlayerCmd("stuck", "Escape to the nearest wilderness.")).handler(c -> c.reply(homes().stuck(c.uuid())));
    }
}
