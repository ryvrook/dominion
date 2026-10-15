package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.text.Messages;
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

    /** The nation service while nation.enabled is on, otherwise null. */
    NationService nations() {
        return ctx.settings().bool("nation.enabled") ? ctx.serviceOrNull(NationService.class) : null;
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    void build(Body body, Faction f) {
        NationService ns = nations();
        if (ns == null) {
            return;
        }
        body.section(Messages.get(UiText.LABEL_NATION));
        Nation n = ns.nationOf(f);
        // Every nation action belongs to faction leaders, so other members only see the overview.
        boolean factionLeader = panel.uuid().equals(f.leader());
        if (n == null) {
            body.text(Messages.get(UiText.NATION_NONE), Msg.MUTED);
            boolean invited = false;
            for (Nation other : ns.all()) {
                if (!other.invites().contains(f.id())) {
                    continue;
                }
                invited = true;
                body.row(other.label(), color(other),
                        new String[] {Messages.get(UiText.FACTIONS_COUNT, "count", other.members().size()),
                                Messages.get(UiText.INVITED_YOU), "", ""},
                        factionLeader ? Btn.of(Messages.get(UiText.JOIN), "nat_join", other.tag()) : null);
            }
            if (!invited) {
                body.text(Messages.get(UiText.NATION_NO_INVITES), Msg.MUTED);
            }
            if (factionLeader) {
                body.form(new String[] {Messages.get(UiText.FIELD_TAG), Messages.get(UiText.FIELD_NAME)},
                        Btn.of(Messages.get(UiText.CREATE), "nat_create"));
            } else {
                body.text(Messages.get(UiText.NATION_LEADER_ONLY), Msg.MUTED);
            }
            return;
        }
        boolean leads = factionLeader && f.id().equals(n.leaderFaction());
        body.stat(Messages.get(UiText.LABEL_NATION), n.label(), color(n));
        for (UUID id : n.members()) {
            Faction m = factions().byId(id);
            if (m == null) {
                continue;
            }
            boolean self = m.id().equals(f.id());
            boolean leader = m.id().equals(n.leaderFaction());
            String[] cols = {leader ? Messages.get(UiText.NATION_ROLE_LEADER) : Messages.get(UiText.NATION_ROLE_MEMBER),
                    Messages.get(UiText.MEMBERS_COUNT, "count", m.memberCount()), "", ""};
            if (leads && !self) {
                body.row(m.label(), safe(m.color()), cols,
                        Btn.of(Messages.get(UiText.KICK), "nat_kick", m.tag()),
                        Btn.of(Messages.get(UiText.MAKE_LEADER), "nat_leader", m.tag()),
                        Btn.of(Messages.get(UiText.INFO), "info", m.tag()));
            } else {
                body.row(self ? Messages.get(UiText.NAME_YOU, "name", m.label()) : m.label(), safe(m.color()), cols,
                        Btn.of(Messages.get(UiText.INFO), "info", m.tag()));
            }
        }
        for (UUID id : n.invites()) {
            Faction m = factions().byId(id);
            if (m != null) {
                body.row(m.label(), Msg.MUTED, new String[] {Messages.get(UiText.INVITED), "", "", ""},
                        leads ? Btn.of(Messages.get(UiText.WITHDRAW), "nat_kick", m.tag()) : null);
            }
        }
        if (leads) {
            body.field(Messages.get(UiText.INVITE_FACTION_TAG), "", Btn.of(Messages.get(UiText.INVITE), "nat_invite"));
            body.buttons(Btn.of(Messages.get(UiText.LEAVE), "nat_leave"), Btn.of(Messages.get(UiText.DISBAND), "nat_disband"));
        } else if (factionLeader) {
            body.buttons(Btn.of(Messages.get(UiText.LEAVE), "nat_leave"));
        }
        String prefix = ctx.settings().text("chat.nationPrefix");
        body.text(prefix == null || prefix.isEmpty() ? Messages.get(UiText.NATION_CHAT_HINT)
                : Messages.get(UiText.NATION_CHAT_HINT_PREFIX, "prefix", prefix), Msg.MUTED);
    }

    /** Handles nation buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        if (!e.action.startsWith("nat_")) {
            return null;
        }
        NationService ns = nations();
        if (ns == null) {
            panel.reply(Result.fail(Messages.get(UiText.NATIONS_UNAVAILABLE)));
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
                    panel.reply(Result.fail(Messages.get(UiText.NATION_TAG_FIRST)));
                } else {
                    panel.reply(ns.create(me, e.text(), e.text2().isEmpty() ? null : e.text2()));
                }
            }
            case "nat_invite" -> panel.reply(e.text().isEmpty() ? Result.fail(Messages.get(UiText.FACTION_TAG_FIRST)) : ns.invite(me, e.text()));
            case "nat_disband" -> {
                panel.confirmThen(Messages.get(UiText.NATION_DISBAND_TITLE),
                        Messages.get(UiText.NATION_DISBAND_BODY),
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
