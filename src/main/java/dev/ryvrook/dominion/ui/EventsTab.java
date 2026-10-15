package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The events tab of the panel. Lists the season, hills, outposts, envoys and bounties from the
 * events service. Status lines register as live labels so the panel can refresh their countdowns
 * every second without rebuilding the page.
 */
final class EventsTab {
    static final String ID = "events";

    private final DominionContext ctx;
    private final DominionPanel panel;

    EventsTab(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    EventsService events() {
        EventsService s = ctx.serviceOrNull(EventsService.class);
        try {
            return s != null && s.enabled() ? s : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    boolean available() {
        return events() != null;
    }

    String count() {
        EventsService s = events();
        if (s == null) {
            return "";
        }
        int bounties = safe(s::bounties).size();
        return bounties > 0 ? bounties + " bounties" : "";
    }

    void build(UICommandBuilder c, Body body, Faction f) {
        EventsService s = events();
        if (s == null) {
            panel.title(c, "Events", "Events are not running");
            body.text("Events are turned off on this server.", Msg.MUTED);
            return;
        }
        panel.title(c, "Events", "Hills, outposts, envoys and bounties");

        String season = text(s::seasonStatus);
        if (!season.isBlank()) {
            body.section("Season");
            live(body, body.text(season), s::seasonStatus);
        }

        body.section("King of the hill");
        List<String> hills = names(s::hillNames, s::koths, "Hill ");
        if (hills.isEmpty()) {
            body.text("No hills are set up.", Msg.MUTED);
        }
        int shown = 0;
        for (String name : hills) {
            if (shown++ >= ctx.settings().integer("ui.eventRows")) {
                break;
            }
            body.row(name, Msg.GOLD, new String[] {"", "", "", ""});
            live(body, body.text(statusOr(s.kothStatus(name)), Msg.MUTED), () -> statusOr(s.kothStatus(name)));
        }

        body.section("Outposts");
        List<String> outposts = names(s::outpostNames, s::outposts, "Outpost ");
        if (outposts.isEmpty()) {
            body.text("No outposts are set up.", Msg.MUTED);
        }
        shown = 0;
        for (String name : outposts) {
            if (shown++ >= ctx.settings().integer("ui.eventRows")) {
                break;
            }
            Faction holder = holder(s, name);
            boolean ours = holder != null && f != null && holder.id().equals(f.id());
            body.row(name + (holder == null ? "  unheld" : "  held by " + holder.tag()), ours ? Msg.GOOD : Msg.GOLD,
                    new String[] {"", "", "", ""}, holder == null ? null : Btn.of("Holder", "info", holder.tag()));
            live(body, body.text(statusOr(s.outpostStatus(name)), Msg.MUTED), () -> statusOr(s.outpostStatus(name)));
        }

        List<String> envoys = safe(s::envoys);
        body.section("Envoys");
        if (envoys.isEmpty()) {
            body.text("No envoy drops are scheduled.", Msg.MUTED);
        }
        shown = 0;
        for (String line : envoys) {
            if (shown++ >= ctx.settings().integer("ui.eventRows")) {
                break;
            }
            body.text(line);
        }

        body.section("Bounties");
        List<String> bounties = safe(s::bounties);
        if (bounties.isEmpty()) {
            body.text("Nobody has a bounty on their head.", Msg.MUTED);
        }
        shown = 0;
        for (String line : bounties) {
            if (shown++ >= ctx.settings().integer("ui.eventRows")) {
                break;
            }
            body.text(line, Msg.WARN);
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        double min = ctx.settings().decimal("events.bountyMin");
        body.text("Place a bounty on a player. Whoever kills them collects it. The smallest bounty is "
                + (eco != null ? eco.format(min) : Msg.number(min)) + ".", Msg.MUTED);
        body.form(new String[] {"Player", "Amount"}, Btn.of("Place bounty", "ev_bounty"));
        body.buttons(Btn.of("Refresh", "refresh"));
    }

    /** Handles events tab buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        if (!"ev_bounty".equals(e.action)) {
            return null;
        }
        EventsService s = events();
        if (s == null) {
            panel.reply(Result.fail("Events are not available on this server."));
            return true;
        }
        String name = e.text();
        if (name.isEmpty()) {
            panel.reply(Result.fail("Type the player's name first."));
            return true;
        }
        UUID target = panel.resolvePlayer(name);
        if (target == null) {
            panel.reply(Result.fail("No player named " + name + " is known."));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(e.text2().replace(",", "").replace("_", "").trim());
        } catch (NumberFormatException ex) {
            amount = -1;
        }
        if (!(amount > 0) || !Double.isFinite(amount)) {
            panel.reply(Result.fail("Type a positive amount for the bounty."));
            return true;
        }
        panel.reply(s.bountyPlace(panel.uuid(), target, amount));
        return true;
    }

    private void live(Body body, String selector, Supplier<String> supplier) {
        if (selector != null && supplier != null) {
            panel.addLive(selector + " #Text", () -> text(supplier));
        }
    }

    /**
     * Hill or outpost names from the service. When the name list is empty or fails, the names are
     * read from the status lines instead, which start with the name.
     */
    private static List<String> names(Supplier<List<String>> direct, Supplier<List<String>> statusLines, String prefix) {
        List<String> out = new java.util.ArrayList<>();
        for (String n : safe(direct)) {
            if (n != null && !n.isBlank()) {
                out.add(n.strip());
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        for (String line : safe(statusLines)) {
            String n = nameOf(line, prefix);
            if (!n.isBlank()) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * The hill or outpost name from a list entry. Names have no spaces, so an entry with a space is
     * a status line whose second word is the name.
     */
    static String nameOf(String entry, String prefix) {
        String e = entry == null ? "" : entry.strip();
        if (e.startsWith(prefix)) {
            e = e.substring(prefix.length());
        }
        int space = e.indexOf(' ');
        return space < 0 ? e : e.substring(0, space);
    }

    private Faction holder(EventsService s, String name) {
        try {
            return s.outpostHolder(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String statusOr(String status) {
        return status == null || status.isBlank() ? "Waiting." : status;
    }

    private static String text(Supplier<String> supplier) {
        try {
            String s = supplier.get();
            return s == null ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static List<String> safe(Supplier<List<String>> supplier) {
        try {
            List<String> out = supplier.get();
            return out == null ? List.of() : out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
