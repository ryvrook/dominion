package dev.ryvrook.dominion.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
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

import java.util.List;
import java.util.logging.Level;

/**
 * Events overview. Hills, outposts, envoys, bounties and the season with their timers. Uses the
 * shared PageEvent payload and the same layout as the Dominion pages, with its own markup.
 */
final class EventsPage extends InteractiveCustomUIPage<PageEvent> {
    static final String PAGE = "Pages/Dominion_Events_Page.ui";
    static final String ROW = "Pages/Dominion_Events_Row.ui";
    static final String SECTION = "Pages/Dominion_Events_Section.ui";
    static final String TEXT = "Pages/Dominion_Events_Text.ui";
    private static final int MAX_BOUNTIES = 15;

    private final DominionContext ctx;
    private final EventsServiceImpl events;
    private int count;

    EventsPage(PlayerRef player, DominionContext ctx, EventsServiceImpl events) {
        super(player, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
        this.ctx = ctx;
        this.events = events;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        try {
            render(c, e);
        } catch (Exception ex) {
            ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion events page failed to render");
            Msg.send(playerRef, Msg.bad(Messages.get(EventsText.PAGE_FAILED)));
        }
    }

    private void render(UICommandBuilder c, UIEventBuilder e) {
        count = 0;
        c.append(PAGE);
        c.set("#PageTitle.Text", Messages.get(EventsText.PANEL_TITLE));
        c.set("#Heading.Text", Messages.get(EventsText.PANEL_HEADING));
        c.set("#Subheading.Text", Messages.get(events.enabled() ? EventsText.PANEL_SUBHEADING : EventsText.PANEL_OFF));
        c.clear("#Body");
        c.set("#F1.Visible", true);
        c.set("#F1.Text", Messages.get(EventsText.BUTTON_REFRESH));
        bind(e, "#F1", "refresh", "");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", Messages.get(EventsText.BUTTON_CLAIM));
        bind(e, "#F2", "claim", "");

        section(c, Messages.get(EventsText.SECTION_HILLS));
        rows(c, events.hillRows(), Messages.get(EventsText.NO_HILLS));
        section(c, Messages.get(EventsText.SECTION_OUTPOSTS));
        rows(c, events.outpostRows(), Messages.get(EventsText.NO_OUTPOSTS));
        section(c, Messages.get(EventsText.SECTION_ENVOYS));
        rows(c, events.envoyRows(), Messages.get(EventsText.NO_ENVOYS_NOW));

        section(c, Messages.get(EventsText.SECTION_BOUNTIES));
        List<Object[]> bounties = events.bountyRows();
        if (bounties.isEmpty()) {
            text(c, Messages.get(EventsText.NO_BOUNTIES), Msg.MUTED);
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        for (int i = 0; i < Math.min(MAX_BOUNTIES, bounties.size()); i++) {
            Object[] b = bounties.get(i);
            double amount = (double) b[1];
            int placers = (int) b[2];
            row(c, new EventsServiceImpl.Row((String) b[0], eco != null ? eco.format(amount) : Msg.number(amount),
                    Messages.get(placers == 1 ? EventsText.ROW_PLACER : EventsText.ROW_PLACERS, "count", placers), Msg.BAD));
        }

        section(c, Messages.get(EventsText.SECTION_SEASON));
        text(c, events.seasonStatus(), Msg.TEXT);
        List<String> board = events.snapshotLines();
        if (!board.isEmpty()) {
            text(c, Messages.get(EventsText.FINAL_STANDINGS), Msg.GOLD);
            for (String line : board) {
                text(c, line, Msg.TEXT);
            }
        }
    }

    private String add(UICommandBuilder c, String template) {
        String selector = "#Body[" + count++ + "]";
        c.append("#Body", template);
        return selector;
    }

    private void section(UICommandBuilder c, String title) {
        String s = add(c, SECTION);
        c.set(s + " #Text.Text", title);
    }

    private void text(UICommandBuilder c, String text, String color) {
        String s = add(c, TEXT);
        c.set(s + " #Text.Text", text);
        c.set(s + " #Text.Style.TextColor", color);
    }

    private void rows(UICommandBuilder c, List<EventsServiceImpl.Row> rows, String empty) {
        if (rows.isEmpty()) {
            text(c, empty, Msg.MUTED);
            return;
        }
        for (EventsServiceImpl.Row r : rows) {
            row(c, r);
        }
    }

    private void row(UICommandBuilder c, EventsServiceImpl.Row r) {
        String s = add(c, ROW);
        c.set(s + " #Name.Text", r.name());
        c.set(s + " #Name.Style.TextColor", r.color());
        c.set(s + " #State.Text", r.state());
        c.set(s + " #Timer.Text", r.timer());
    }

    private static void bind(UIEventBuilder e, String selector, String action, String value) {
        e.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Value", value), false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEvent data) {
        if (data == null || data.action == null) {
            return;
        }
        try {
            if ("claim".equals(data.action)) {
                Result r = events.envoyClaim(playerRef.getUuid());
                Msg.send(playerRef, r.success() ? Msg.good(r.message()) : Msg.bad(r.message()));
            }
            rebuild();
        } catch (Exception ex) {
            ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion events page action %s failed", data.action);
        }
    }
}
