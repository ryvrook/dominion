package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live editor for every Dominion setting, grouped like the config file, plus preset buttons. */
public final class AdminSettingsPage extends DominionPage {
    private static final String PAGE = "Pages/Dominion_Admin.ui";
    private static final String PERMISSION = "dominion.admin";
    private static final String PRESETS = "Presets";
    private static final String[][] PRESET_LIST = {
            {"classic", "Raiding by power, dominance and sieges. The standard factions experience."},
            {"hardcore", "Raids at any time, heavy death penalties and fast sieges."},
            {"peaceful", "No raiding and no neutral PvP. Factions build and trade in peace."},
            {"singleplayer", "No raids, sieges or power loss. For solo worlds and small groups."}
    };

    private String group;

    public AdminSettingsPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui, String group) {
        super(player, ctx, ui);
        this.group = group;
    }

    private Map<String, List<Settings.Def>> grouped() {
        Map<String, List<Settings.Def>> out = new LinkedHashMap<>();
        for (Settings.Def d : ctx.settings().definitions()) {
            out.computeIfAbsent(d.group(), g -> new ArrayList<>()).add(d);
        }
        return out;
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(PAGE);
        Map<String, List<Settings.Def>> groups = grouped();
        if (group == null || (!groups.containsKey(group) && !PRESETS.equals(group))) {
            String wanted = group;
            group = null;
            for (String g : groups.keySet()) {
                if (g.equalsIgnoreCase(wanted)) {
                    group = g;
                }
            }
            if (PRESETS.equalsIgnoreCase(wanted)) {
                group = PRESETS;
            }
            if (group == null) {
                group = groups.isEmpty() ? PRESETS : groups.keySet().iterator().next();
            }
        }

        Body tabs = new Body(c, events, "#Tabs");
        for (Map.Entry<String, List<Settings.Def>> g : groups.entrySet()) {
            tabs.tab(g.getKey(), g.getKey(), String.valueOf(g.getValue().size()), g.getKey().equals(group), "group");
        }
        tabs.tab(PRESETS, PRESETS, "", PRESETS.equals(group), "group");

        Body body = new Body(c, events, "#Body");
        if (!allowed()) {
            c.set("#TabTitle.Text", "Settings");
            body.text("You need the dominion.admin permission to change settings.", Msg.BAD);
            return;
        }
        if (PRESETS.equals(group)) {
            c.set("#TabTitle.Text", PRESETS);
            c.set("#TabHint.Text", "Active preset is " + ctx.settings().text("misc.preset"));
            body.text("A preset changes a bundle of settings at once. Settings it does not mention stay as they are.", Msg.MUTED);
            for (String[] p : PRESET_LIST) {
                body.row(p[0], p[0].equalsIgnoreCase(ctx.settings().text("misc.preset")) ? Msg.GOLD : null,
                        new String[] {"", "", "", ""}, Btn.of("Apply", "preset", p[0]));
                body.text(p[1], Msg.MUTED);
            }
            return;
        }
        List<Settings.Def> defs = groups.getOrDefault(group, List.of());
        c.set("#TabTitle.Text", group);
        c.set("#TabHint.Text", "Changes save at once and apply live");
        for (Settings.Def d : defs) {
            if (ctx.settings().fileOnly(d.key())) {
                body.text(d.key() + " runs console commands, so it can only be changed in config.json. "
                        + "Running season payouts needs the dominion.admin.payouts permission.", Msg.MUTED);
                continue;
            }
            Object value = ctx.settings().raw(d.key());
            if (d.defaultValue() instanceof Boolean) {
                body.toggle(d.label(), d.key(), Boolean.TRUE.equals(value), "bool", d.key());
            } else {
                body.field(d.label(), value == null ? "" : String.valueOf(value), Btn.of("Apply", "set", d.key()));
                body.text(d.key() + "    default " + d.defaultValue(), Msg.MUTED);
            }
        }
    }

    private boolean allowed() {
        return playerRef.hasPermission(PERMISSION);
    }

    @Override
    protected boolean onAction(PageEvent e) {
        if ("group".equals(e.action)) {
            group = e.value();
            return true;
        }
        if (!allowed()) {
            Msg.send(player(), Msg.bad("You need the dominion.admin permission to change settings."));
            return true;
        }
        Settings settings = ctx.settings();
        if (("bool".equals(e.action) || "set".equals(e.action)) && settings.fileOnly(e.value())) {
            Msg.send(player(), Msg.bad(e.value() + " can only be changed in config.json."));
            return true;
        }
        switch (e.action) {
            case "bool" -> {
                boolean next = !settings.bool(e.value());
                if (settings.set(e.value(), next)) {
                    Msg.send(player(), Msg.good(e.value() + " is now " + (next ? "on." : "off.")));
                } else {
                    Msg.send(player(), Msg.bad("Could not change " + e.value() + "."));
                }
            }
            case "set" -> {
                if (settings.set(e.value(), e.text())) {
                    Msg.send(player(), Msg.good(e.value() + " is now " + settings.text(e.value()) + "."));
                } else {
                    Msg.send(player(), Msg.bad("The value " + e.text() + " does not fit " + e.value() + ". Use the same kind of value as the default."));
                }
            }
            case "preset" -> {
                String name = e.value();
                ui.confirm(player(), "Apply the " + name + " preset",
                        "Many settings change at once and apply to every player right away. You can switch presets again later.",
                        yes -> {
                            if (yes) {
                                ctx.plugin().applyPreset(name);
                                Msg.send(player(), Msg.good("The " + name + " preset is active."));
                            }
                            ui.openAdminSettings(player(), PRESETS);
                        });
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
