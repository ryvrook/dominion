package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.util.Players;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Asks before a destructive action. The confirm button stays disabled for three seconds so a
 * stray click cannot disband a faction. Dismissing the page counts as a no.
 */
public final class ConfirmPage extends DominionPage {
    private static final String PAGE = "Pages/Dominion_Confirm.ui";

    private final String title;
    private final String consequence;
    private final Consumer<Boolean> onDecision;
    private final long unlockAt;
    private final AtomicBoolean decided = new AtomicBoolean();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ConfirmPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui, String title, String consequence,
                       Consumer<Boolean> onDecision) {
        super(player, ctx, ui);
        this.title = title == null || title.isBlank() ? "Are you sure" : title;
        this.consequence = consequence == null ? "" : consequence;
        this.onDecision = onDecision;
        this.unlockAt = System.currentTimeMillis() + ctx.settings().integer("ui.confirmLockMillis");
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(PAGE);
        commands.set("#PageTitle.Text", title);
        commands.set("#Consequence.Text", consequence);
        boolean locked = System.currentTimeMillis() < unlockAt;
        commands.set("#Confirm.Disabled", locked);
        commands.set("#Wait.Text", locked ? "Read this first. Confirm unlocks in a few seconds." : "");
        Body.on(events, "#Confirm", "confirm", "");
        Body.on(events, "#Cancel", "cancel", "");
        if (locked && scheduled.compareAndSet(false, true)) {
            scheduleUnlock();
        }
    }

    private void scheduleUnlock() {
        long delay = Math.max(50, unlockAt - System.currentTimeMillis() + 50);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (decided.get()) {
                return;
            }
            World world = Players.world(playerRef);
            if (world == null) {
                return;
            }
            world.execute(() -> {
                if (!decided.get() && ui.isShowing(playerRef, this)) {
                    safeRebuild();
                }
            });
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    protected boolean onAction(PageEvent event) {
        switch (event.action) {
            case "confirm" -> {
                if (System.currentTimeMillis() < unlockAt) {
                    return false;
                }
                // Decide before closing. Closing tears the page down through onDismiss, which
                // would otherwise record a no first and swallow the yes.
                decide(true);
                closePage();
            }
            case "cancel" -> {
                decide(false);
                closePage();
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        super.onDismiss(ref, store);
        decide(false);
    }

    private void decide(boolean yes) {
        if (!decided.compareAndSet(false, true) || onDecision == null) {
            return;
        }
        try {
            onDecision.accept(yes);
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion confirmation callback failed");
        }
    }
}
