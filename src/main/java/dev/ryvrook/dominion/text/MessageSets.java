package dev.ryvrook.dominion.text;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.logging.Level;

/**
 * The text classes whose static fields define every message id. Loading them at setup registers every
 * default before messages.json is read, so the file is complete on the first start. Each package adds its
 * own class here, one line, and a class that is missing is skipped with a log line.
 */
public final class MessageSets {
    private MessageSets() {}

    public static final String[] ALL = {
            "dev.ryvrook.dominion.admin.AdminText",
            "dev.ryvrook.dominion.admin.ConfigText",
            "dev.ryvrook.dominion.chat.ChatText",
            "dev.ryvrook.dominion.combat.CombatText",
            "dev.ryvrook.dominion.command.CommandText",
            "dev.ryvrook.dominion.economy.EconomyText",
            "dev.ryvrook.dominion.events.EventsText",
            "dev.ryvrook.dominion.export.ExportText",
            "dev.ryvrook.dominion.faction.FactionText",
            "dev.ryvrook.dominion.home.HomeText",
            "dev.ryvrook.dominion.importer.ImporterText",
            "dev.ryvrook.dominion.integration.IntegrationText",
            "dev.ryvrook.dominion.land.LandText",
            "dev.ryvrook.dominion.map.MapText",
            "dev.ryvrook.dominion.market.MarketText",
            "dev.ryvrook.dominion.nation.NationText",
            "dev.ryvrook.dominion.progress.ProgressText",
            "dev.ryvrook.dominion.relation.RelationText",
            "dev.ryvrook.dominion.shield.ShieldText",
            "dev.ryvrook.dominion.shop.ShopText",
            "dev.ryvrook.dominion.siege.SiegeText",
            "dev.ryvrook.dominion.text.CommonText",
            "dev.ryvrook.dominion.ui.UiText",
            "dev.ryvrook.dominion.util.UtilText",
            "dev.ryvrook.dominion.vault.VaultText"
    };

    static void load(HytaleLogger logger) {
        for (String name : ALL) {
            try {
                Class.forName(name, true, MessageSets.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                if (logger != null) {
                    logger.at(Level.FINE).log("Dominion text class %s is not present", name);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.at(Level.WARNING).withCause(t).log("Dominion text class %s failed to load", name);
                }
            }
        }
    }
}
