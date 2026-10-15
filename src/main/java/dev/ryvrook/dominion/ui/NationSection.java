package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;

import java.util.UUID;

/** The nation part of the relations tab. Shown only while the nation service is installed. */
final class NationSection {
    private final DominionContext ctx;
    private final DominionPanel panel;

    NationSection(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    NationService nations() {
        return ctx.serviceOrNull(NationService.class);
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    void build(Body body, Faction f) {
        NationService ns = nations();
        if (ns == null) {
            return;
        }
        body.section("Nation");
        Nation n = ns.nationOf(f);
        // Every nation action belongs to faction leaders, so other members only see the overview.
        boolean factionLeader = panel.uuid().equals(f.leader());
        if (n == null) {
            body.text("Your faction is not in a nation. A nation joins allied factions under one tag with a shared chat.",
                    Msg.MUTED);
            boolean invited = false;
            for (Nation other : ns.all()) {
                if (!other.invites().contains(f.id())) {
                    continue;
                }
                invited = true;
                body.row(other.label(), color(other),
                        new String[] {other.members().size() + " factions", "Invited you", "", ""},
                        factionLeader ? Btn.of("Join", "nat_join", other.tag()) : null);
            }
            if (!invited) {
                body.text("No nation has invited your faction.", Msg.MUTED);
            }
            if (factionLeader) {
                body.form(new String[] {"Tag", "Name"}, Btn.of("Create", "nat_create"));
            } else {
                body.text("Your faction leader can found or join a nation.", Msg.MUTED);
            }
            return;
        }
        boolean leads = factionLeader && f.id().equals(n.leaderFaction());
        body.stat("Nation", n.label(), color(n));
        for (UUID id : n.members()) {
            Faction m = factions().byId(id);
            if (m == null) {
                continue;
            }
            boolean self = m.id().equals(f.id());
            boolean leader = m.id().equals(n.leaderFaction());
            String[] cols = {leader ? "Leader" : "Member", m.memberCount() + " members", "", ""};
            if (leads && !self) {
                body.row(m.label(), safe(m.color()), cols,
                        Btn.of("Kick", "nat_kick", m.tag()), Btn.of("Make leader", "nat_leader", m.tag()),
                        Btn.of("Info", "info", m.tag()));
            } else {
                body.row(m.label() + (self ? "  (you)" : ""), safe(m.color()), cols,
                        Btn.of("Info", "info", m.tag()));
            }
        }
        for (UUID id : n.invites()) {
            Faction m = factions().byId(id);
            if (m != null) {
                body.row(m.label(), Msg.MUTED, new String[] {"Invited", "", "", ""},
                        leads ? Btn.of("Withdraw", "nat_kick", m.tag()) : null);
            }
        }
        if (leads) {
            body.field("Invite faction tag", "", Btn.of("Invite", "nat_invite"));
            body.buttons(Btn.of("Leave", "nat_leave"), Btn.of("Disband", "nat_disband"));
        } else if (factionLeader) {
            body.buttons(Btn.of("Leave", "nat_leave"));
        }
        String prefix = ctx.settings().text("chat.nationPrefix");
        body.text("Talk to the whole nation with /f chat nation"
                + (prefix == null || prefix.isEmpty() ? "." : ", or start a line with " + prefix + " to send just that line."), Msg.MUTED);
    }

    /** Handles nation buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        if (!e.action.startsWith("nat_")) {
            return null;
        }
        NationService ns = nations();
        if (ns == null) {
            panel.reply(Result.fail("Nations are not available on this server."));
            return true;
        }
        UUID me = panel.uuid();
        switch (e.action) {
            case "nat_join" -> panel.reply(ns.join(me, e.value()));
            case "nat_kick" -> panel.reply(ns.kick(me, e.value()));
            case "nat_leader" -> panel.reply(ns.setLeaderFaction(me, e.value()));
            case "nat_leave" -> panel.reply(ns.leave(me));
            case "nat_create" -> {
                if (e.text().isEmpty()) {
                    panel.reply(Result.fail("Type a tag for the nation first."));
                } else {
                    panel.reply(ns.create(me, e.text(), e.text2().isEmpty() ? null : e.text2()));
                }
            }
            case "nat_invite" -> panel.reply(e.text().isEmpty() ? Result.fail("Type a faction tag first.") : ns.invite(me, e.text()));
            case "nat_disband" -> {
                panel.confirmThen("Disband the nation",
                        "The nation ends and every member faction becomes independent again.",
                        () -> ns.disband(me, true), DominionPanel.RELATIONS);
                return false;
            }
            default -> {
                return null;
            }
        }
        return true;
    }

    private static String color(Nation n) {
        return safe(n.color()) == null ? Msg.GOLD : n.color();
    }

    private static String safe(String c) {
        return c != null && c.matches("#[0-9a-fA-F]{6}") ? c : null;
    }
}
