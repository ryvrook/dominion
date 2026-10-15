package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.PageEvent;

import java.util.UUID;
import java.util.logging.Level;

/** Shared plumbing for the shop pages, following the Dominion page conventions. */
abstract class ShopPage extends InteractiveCustomUIPage<PageEvent> {
    /** Quantities offered by the selector on buying and selling pages. */
    static final int[] QUANTITIES = {1, 8, 32, 64};

    protected final DominionContext ctx;
    protected final ShopServiceImpl shops;
    protected int quantity = 1;

    ShopPage(PlayerRef player, DominionContext ctx, ShopServiceImpl shops) {
        super(player, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
        this.ctx = ctx;
        this.shops = shops;
    }

    protected UUID uuid() {
        return playerRef.getUuid();
    }

    @Override
    public final void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        try {
            render(commands, events);
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop page %s failed to render", getClass().getSimpleName());
            Msg.send(playerRef, Msg.bad(Messages.get(ShopText.PAGE_FAILED)));
        }
    }

    protected abstract void render(UICommandBuilder c, UIEventBuilder events);

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEvent data) {
        if (data == null || data.action == null) {
            return;
        }
        boolean refresh;
        try {
            if ("qty".equals(data.action)) {
                quantity = parseQuantity(data.value());
                refresh = true;
            } else if ("close".equals(data.action)) {
                close();
                return;
            } else {
                refresh = onAction(data);
            }
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop action %s failed", data.action);
            Msg.send(playerRef, Msg.bad(Messages.get(ShopText.BUTTON_FAILED)));
            refresh = true;
        }
        if (refresh) {
            try {
                rebuild();
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop page %s failed to rebuild", getClass().getSimpleName());
            }
        }
    }

    /** Handles one button press. Returns true when the page should rebuild afterwards. */
    protected abstract boolean onAction(PageEvent event);

    protected void reply(Result result) {
        if (result == null) {
            return;
        }
        String text = result.message() == null || result.message().isBlank()
                ? Messages.get(result.success() ? ShopText.DONE : ShopText.DID_NOT_WORK)
                : result.message();
        Msg.send(playerRef, result.success() ? Msg.good(text) : Msg.bad(text));
    }

    /** The quantity selector row. The current choice is bracketed. */
    protected void quantityRow(ShopBody body) {
        ShopBody.Btn[] buttons = new ShopBody.Btn[QUANTITIES.length];
        for (int i = 0; i < QUANTITIES.length; i++) {
            int q = QUANTITIES[i];
            String label = q == quantity ? "[ " + q + " ]" : String.valueOf(q);
            buttons[i] = ShopBody.Btn.of(label, "qty", String.valueOf(q));
        }
        body.buttons(Messages.get(ShopText.LABEL_QUANTITY), buttons);
    }

    protected String balanceLine() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco == null || !eco.enabled()) {
            return "";
        }
        return Messages.get(ShopText.BALANCE, "balance", eco.format(eco.balance(uuid())));
    }

    protected String money(double amount) {
        return shops.fmt(amount);
    }

    static int parseQuantity(String value) {
        try {
            int q = Integer.parseInt(value.trim());
            for (int allowed : QUANTITIES) {
                if (allowed == q) {
                    return q;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the default
        }
        return 1;
    }

    static Double parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
