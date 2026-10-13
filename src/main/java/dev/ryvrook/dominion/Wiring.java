package dev.ryvrook.dominion;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads modules by class name so each module can be developed and compiled on its own.
 * A missing module logs a warning and is skipped. Order matters for init.
 */
final class Wiring {
    private Wiring() {}

    public static final String[] MODULES = {
            "dev.ryvrook.dominion.economy.EconomyModule",
            "dev.ryvrook.dominion.faction.FactionModule",
            "dev.ryvrook.dominion.power.PowerModule",
            "dev.ryvrook.dominion.relation.RelationModule",
            "dev.ryvrook.dominion.land.LandModule",
            "dev.ryvrook.dominion.combat.CombatModule",
            "dev.ryvrook.dominion.chat.ChatModule",
            "dev.ryvrook.dominion.home.HomeModule",
            "dev.ryvrook.dominion.shield.ShieldModule",
            "dev.ryvrook.dominion.siege.SiegeModule",
            "dev.ryvrook.dominion.market.MarketModule",
            "dev.ryvrook.dominion.vault.VaultModule",
            "dev.ryvrook.dominion.progress.ProgressModule",
            "dev.ryvrook.dominion.nation.NationModule",
            "dev.ryvrook.dominion.shop.ShopModule",
            "dev.ryvrook.dominion.events.EventsModule",
            "dev.ryvrook.dominion.importer.ImportModule",
            "dev.ryvrook.dominion.ui.UiModule",
            "dev.ryvrook.dominion.map.MapModule",
            "dev.ryvrook.dominion.command.CommandModule",
            "dev.ryvrook.dominion.admin.AdminModule",
            "dev.ryvrook.dominion.export.ExportModule"
    };

    static List<DominionModule> load(HytaleLogger logger) {
        List<DominionModule> out = new ArrayList<>();
        for (String name : MODULES) {
            try {
                Class<?> c = Class.forName(name, true, Wiring.class.getClassLoader());
                out.add((DominionModule) c.getDeclaredConstructor().newInstance());
            } catch (ClassNotFoundException e) {
                logger.at(Level.WARNING).log("Dominion module %s is not present, skipping", name);
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Dominion module %s failed to load", name);
            }
        }
        return out;
    }
}
