package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.util.Map;

/** Faction home, warps and the stuck escape. */
final class HomeCommands extends Section {

    HomeCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        root.sub(new PlayerCmd("home", Messages.get(CommandText.DESC_TELEPORT_TO_YOUR_FACTION_HOME))).handler(c -> c.reply(homes().home(c.uuid())));
        root.sub(new PlayerCmd("sethome", Messages.get(CommandText.DESC_SET_THE_FACTION_HOME_WHERE_YOU))).handler(c -> c.reply(homes().setHome(c.uuid())));
        root.sub(new PlayerCmd("delhome", Messages.get(CommandText.DESC_REMOVE_THE_FACTION_HOME))).handler(c -> c.reply(homes().delHome(c.uuid())));

        PlayerCmd allyHome = root.sub(new PlayerCmd("allyhome", Messages.get(CommandText.DESC_TELEPORT_TO_THE_HOME_OF_AN)));
        RequiredArg<String> allyTag = allyHome.withRequiredArg("tag", Messages.get(CommandText.ARG_ALLY_FACTION_TAG), ArgTypes.STRING);
        allyHome.handler(c -> c.reply(homes().allyHome(c.uuid(), c.arg(allyTag))));

        PlayerCmd warp = root.sub(new PlayerCmd("warp", Messages.get(CommandText.DESC_TELEPORT_TO_A_FACTION_WARP)));
        RequiredArg<String> warpName = warp.withRequiredArg("name", Messages.get(CommandText.ARG_WARP_NAME), ArgTypes.STRING);
        warp.handler(c -> c.reply(homes().warp(c.uuid(), c.arg(warpName), null)));
        PlayerCmd warpLocked = new PlayerCmd(Messages.get(CommandText.DESC_TELEPORT_TO_A_WARP_THAT_HAS));
        RequiredArg<String> lockedName = warpLocked.withRequiredArg("name", Messages.get(CommandText.ARG_WARP_NAME), ArgTypes.STRING);
        RequiredArg<String> lockedPass = warpLocked.withRequiredArg("password", Messages.get(CommandText.ARG_WARP_PASSWORD), ArgTypes.STRING);
        warpLocked.handler(c -> c.reply(homes().warp(c.uuid(), c.arg(lockedName), c.arg(lockedPass))));
        warp.variant(warpLocked);

        PlayerCmd setwarp = root.sub(new PlayerCmd("setwarp", Messages.get(CommandText.DESC_CREATE_A_WARP_WHERE_YOU_STAND)));
        RequiredArg<String> setName = setwarp.withRequiredArg("name", Messages.get(CommandText.ARG_WARP_NAME), ArgTypes.STRING);
        setwarp.handler(c -> c.reply(homes().setWarp(c.uuid(), c.arg(setName), null)));
        PlayerCmd setwarpLocked = new PlayerCmd(Messages.get(CommandText.DESC_CREATE_A_WARP_PROTECTED_BY_A));
        RequiredArg<String> setLockedName = setwarpLocked.withRequiredArg("name", Messages.get(CommandText.ARG_WARP_NAME), ArgTypes.STRING);
        RequiredArg<String> setLockedPass = setwarpLocked.withRequiredArg("password", Messages.get(CommandText.ARG_WARP_PASSWORD), ArgTypes.STRING);
        setwarpLocked.handler(c -> c.reply(homes().setWarp(c.uuid(), c.arg(setLockedName), c.arg(setLockedPass))));
        setwarp.variant(setwarpLocked);

        PlayerCmd delwarp = root.sub(new PlayerCmd("delwarp", Messages.get(CommandText.DESC_DELETE_A_WARP)));
        RequiredArg<String> delName = delwarp.withRequiredArg("name", Messages.get(CommandText.ARG_WARP_NAME), ArgTypes.STRING);
        delwarp.handler(c -> c.reply(homes().delWarp(c.uuid(), c.arg(delName))));

        root.sub(new PlayerCmd("warps", Messages.get(CommandText.DESC_LIST_YOUR_FACTION_WARPS))).handler(c -> {
            Faction f = requireFaction(c);
            if (f == null) {
                return;
            }
            Map<String, Faction.Warp> warps = f.warps();
            if (warps.isEmpty()) {
                c.info(Messages.get(CommandText.YOUR_FACTION_HAS_NO_WARPS_USE));
                return;
            }
            c.info(Messages.get(CommandText.WARPS_HEADER, "tag", f.tag(), "count", warps.size()));
            for (Map.Entry<String, Faction.Warp> e : warps.entrySet()) {
                Faction.Warp w = e.getValue();
                boolean locked = w.password() != null && !w.password().isBlank();
                String where = w.location() == null ? "" : "  " + (int) w.location().x() + ", "
                        + (int) w.location().y() + ", " + (int) w.location().z();
                c.send(Message.join(Msg.muted(" - "), Msg.colored(e.getKey(), Msg.TEXT),
                        Msg.muted(where + (locked ? "  " + Messages.get(CommandText.WARP_LOCKED) : ""))));
            }
        });

        root.sub(new PlayerCmd("stuck", Messages.get(CommandText.DESC_ESCAPE_TO_THE_NEAREST_WILDERNESS))).handler(c -> c.reply(homes().stuck(c.uuid())));
    }
}
