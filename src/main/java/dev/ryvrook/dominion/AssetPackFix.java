package dev.ryvrook.dominion;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * The stale asset pack fix, the same one Coinage and Chronicle Stats carry. The engine registers a plugin's UI asset pack right after
 * setup() and refuses it when a pack with the same name is already registered. That happens after the jar was
 * replaced by a file with another name, or when the old file is gone, and it would block every load of the
 * pack until a restart. Both methods never throw. Called on the plugin lifecycle thread.
 */
final class AssetPackFix {
    private AssetPackFix() {
    }

    /** First call in setup(). Never throws. */
    static void dropStale(JavaPlugin plugin) {
        try {
            dropStale0(plugin);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("Dominion could not check the asset pack registration");
        }
    }

    private static void dropStale0(JavaPlugin plugin) {
        AssetModule assets = AssetModule.get();
        if (assets == null) {
            return;
        }
        AssetPack stale = findPack(assets, plugin.getIdentifier().toString());
        if (stale == null) {
            return;
        }
        boolean sameFile = samePath(plugin.getFile(), stale.getPackLocation());
        // A pack the boot scan registered from this very jar is left alone, the engine reuses it. One
        // registered by a runtime load is always replaced, because the engine refuses a second RUNTIME pack
        // with the same name and the plugin load would fail.
        if (sameFile && stale.getSource() != AssetPack.PackSource.RUNTIME) {
            return;
        }
        String name = stale.getName();
        Path location = stale.getPackLocation();
        if (archiveWithMonitor(assets, stale)) {
            // The engine's unregisterPack hands the jar's /Common folder to the AssetMonitor, which mixes the
            // jar file system with the default one and logs a SEVERE ProviderMismatchException. Dropping the
            // entry directly avoids that, and the pack registered next replaces its assets by name.
            if (forgetPack(assets, name)) {
                plugin.getLogger().at(Level.INFO).log("Dominion dropped the previous asset pack registration from %s so this jar can register its own", location);
            } else {
                plugin.getLogger().at(Level.WARNING).log("Dominion found a stale asset pack registration from %s and could not remove it. Restart the server if the faction panel does not open.", location);
            }
            return;
        }
        try {
            assets.unregisterPack(name);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            // The engine throws checked exceptions here without declaring them, for example when the old
            // jar no longer exists. The registration is usually gone by then, so check instead of giving up.
            plugin.getLogger().at(Level.FINE).withCause(e).log("Dominion hit an error while removing the stale asset pack");
        }
        if (findPack(assets, name) == null) {
            plugin.getLogger().at(Level.INFO).log("Dominion refreshed the asset pack registration from %s so this jar can register its own", location);
            return;
        }
        if (forgetPack(assets, name)) {
            plugin.getLogger().at(Level.INFO).log("Dominion dropped a stale asset pack registration from %s so this jar can register its own", location);
        } else {
            plugin.getLogger().at(Level.WARNING).log("Dominion found a stale asset pack registration from %s and could not remove it. Restart the server if the faction panel does not open.", location);
        }
    }

    /**
     * Last call in shutdown(). When the pack is this jar and the AssetMonitor runs, drops the registration
     * directly, so the engine's own unload does not call unregisterPack and log the monitor's SEVERE. The
     * next load registers the pack again. Never throws.
     */
    static void release(JavaPlugin plugin) {
        try {
            AssetModule assets = AssetModule.get();
            if (assets == null) {
                return;
            }
            AssetPack pack = findPack(assets, plugin.getIdentifier().toString());
            if (pack != null && samePath(plugin.getFile(), pack.getPackLocation()) && archiveWithMonitor(assets, pack)) {
                forgetPack(assets, pack.getName());
            }
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("Dominion could not release the asset pack");
        }
    }

    /**
     * True when both paths name the same file. Paths from different file system providers, such as a jar
     * file system and the default one, are compared by their absolute normalized text, never handed to
     * {@link Files#isSameFile}. Never throws.
     */
    static boolean samePath(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            String ta = a.toAbsolutePath().normalize().toString();
            String tb = b.toAbsolutePath().normalize().toString();
            if (ta.equals(tb)) {
                return true;
            }
            if (a.getFileSystem().provider() != b.getFileSystem().provider()) {
                return false;
            }
            return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean archiveWithMonitor(AssetModule assets, AssetPack pack) {
        try {
            java.nio.file.FileSystem fs = pack.getFileSystem();
            boolean archive = fs != null && fs != java.nio.file.FileSystems.getDefault();
            return archive && assets.getAssetMonitor() != null;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** First call in start(). One line saying whether the pack is registered. Never throws. */
    static void report(JavaPlugin plugin) {
        try {
            AssetModule assets = AssetModule.get();
            AssetPack pack = assets == null ? null : findPack(assets, plugin.getIdentifier().toString());
            if (pack == null) {
                plugin.getLogger().at(Level.WARNING).log("Dominion UI pack is not registered, the faction panel will not open until the server restarts");
            } else {
                plugin.getLogger().at(Level.INFO).log("Dominion UI pack registered from %s", pack.getPackLocation());
            }
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("Dominion could not report the asset pack");
        }
    }

    private static AssetPack findPack(AssetModule assets, String name) {
        for (AssetPack pack : assets.getAssetPacks()) {
            if (pack != null && name.equalsIgnoreCase(pack.getName())) {
                return pack;
            }
        }
        return null;
    }

    /** Last resort when the engine's own removal failed partway, drops the entry from its pack list directly. */
    private static boolean forgetPack(AssetModule assets, String name) {
        try {
            java.lang.reflect.Field field = AssetModule.class.getDeclaredField("assetPacks");
            field.setAccessible(true);
            Object value = field.get(assets);
            if (value instanceof java.util.List<?> list) {
                return list.removeIf(o -> o instanceof AssetPack pack && name.equalsIgnoreCase(pack.getName()));
            }
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable ignored) {
            // the engine changed, leave it to a restart
        }
        return false;
    }
}
