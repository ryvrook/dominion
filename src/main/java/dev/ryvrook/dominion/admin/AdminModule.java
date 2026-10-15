package dev.ryvrook.dominion.admin;

import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** The /fa admin tree, singleplayer preset detection and grace period expiry. */
public final class AdminModule implements DominionModule {
    public static final String PERMISSION = "dominion.admin";
    private static final long FIRST_BACKUP_DELAY = TimeUnit.MINUTES.toMillis(10);
    private static final long BACKUP_INTERVAL = TimeUnit.DAYS.toMillis(1);

    /** When the next automatic backup is due, in epoch millis. */
    private final AtomicLong nextBackupAt = new AtomicLong();
    private final AtomicBoolean backupRunning = new AtomicBoolean();

    public AdminModule() {}

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public void init(DominionContext ctx) {
        // Nothing to register. Admin tools only consume other services.
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.plugin().getCommandRegistry().registerCommand(new AdminCommands(ctx).build());
        detectSingleplayer(ctx);
        ctx.everyMinute(() -> expireGrace(ctx));
        nextBackupAt.set(System.currentTimeMillis() + FIRST_BACKUP_DELAY);
        ctx.everyMinute(() -> dailyBackup(ctx));
    }

    /**
     * Saves everything and zips the data folder, keeping the newest keep archives. The /fa backup
     * command and the daily backup both use this. Call it off the world threads.
     */
    static Path backup(DominionContext ctx, int keep) throws IOException {
        ctx.repo().saveAll();
        return Backups.create(ctx.repo().root(), keep);
    }

    /**
     * Checked every minute. The first backup runs ten minutes after start and then once a day.
     * A storage.backupKeep of zero or less turns automatic backups off.
     */
    private void dailyBackup(DominionContext ctx) {
        long now = System.currentTimeMillis();
        if (now < nextBackupAt.get()) {
            return;
        }
        nextBackupAt.set(now + BACKUP_INTERVAL);
        int keep = ctx.settings().integer("storage.backupKeep");
        if (keep <= 0 || !backupRunning.compareAndSet(false, true)) {
            return;
        }
        ctx.runIo("daily backup", () -> {
            try {
                Path archive = backup(ctx, keep);
                ctx.logger().at(Level.INFO).log("Dominion daily backup written to %s, keeping the newest %s",
                        ctx.repo().root().relativize(archive), keep);
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion daily backup failed");
            } finally {
                backupRunning.set(false);
            }
        });
    }

    /**
     * Constants.SINGLEPLAYER is set by the server from its launch options when the game client
     * hosts the world. The singleplayer preset is applied once, while the preset is still the
     * default classic one, and it records itself as the active preset so it never repeats.
     */
    static void detectSingleplayer(DominionContext ctx) {
        if (!Constants.SINGLEPLAYER) {
            return;
        }
        if (!ctx.settings().bool("misc.singleplayerAutoPreset")) {
            return;
        }
        if (!"classic".equalsIgnoreCase(ctx.settings().text("misc.preset"))) {
            return;
        }
        ctx.plugin().applyPreset("singleplayer");
        ctx.logger().at(Level.INFO).log("Dominion detected a singleplayer world and applied the singleplayer preset");
    }

    /** Only runs without the shield service, which expires grace in its own tick. */
    static void expireGrace(DominionContext ctx) {
        if (ctx.has(ShieldService.class)) {
            return;
        }
        if (!ctx.settings().bool("grace.active")) {
            return;
        }
        long endsAt = ctx.settings().longValue("grace.endsAt");
        if (endsAt <= 0 || System.currentTimeMillis() < endsAt) {
            return;
        }
        ctx.settings().set("grace.active", false);
        ctx.settings().set("grace.endsAt", 0L);
        ctx.logger().at(Level.INFO).log("Dominion grace period ended");
        Universe universe = Universe.get();
        if (universe != null) {
            universe.sendMessage(Msg.warn(Messages.get(AdminText.GRACE_OVER)));
        }
    }
}
