package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.text.Messages;
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
            {"classic", UiText.PRESET_CLASSIC},
            {"hardcore", UiText.PRESET_HARDCORE},
            {"peaceful", UiText.PRESET_PEACEFUL},
            {"singleplayer", UiText.PRESET_SINGLEPLAYER}
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
        tabs.tab(PRESETS, Messages.get(UiText.PRESETS_TAB), "", PRESETS.equals(group), "group");

        Body body = new Body(c, events, "#Body");
        if (!allowed()) {
            c.set("#TabTitle.Text", Messages.get(UiText.SETTINGS_TITLE));
            body.text(Messages.get(UiText.SETTINGS_NEED_ADMIN), Msg.BAD);
            return;
        }
        if (PRESETS.equals(group)) {
            c.set("#TabTitle.Text", Messages.get(UiText.PRESETS_TAB));
            c.set("#TabHint.Text", Messages.get(UiText.PRESET_ACTIVE_HINT, "preset", ctx.settings().text("misc.preset")));
            body.text(Messages.get(UiText.PRESETS_EXPLAINED), Msg.MUTED);
            for (String[] p : PRESET_LIST) {
                body.row(p[0], p[0].equalsIgnoreCase(ctx.settings().text("misc.preset")) ? Msg.GOLD : null,
                        new String[] {"", "", "", ""}, Btn.of(Messages.get(UiText.APPLY), "preset", p[0]));
                body.text(Messages.get(p[1]), Msg.MUTED);
            }
            return;
        }
        List<Settings.Def> defs = groups.getOrDefault(group, List.of());
        c.set("#TabTitle.Text", group);
        c.set("#TabHint.Text", Messages.get(UiText.SETTINGS_LIVE_HINT));
        for (Settings.Def d : defs) {
            if (ctx.settings().fileOnly(d.key())) {
                body.text(Messages.get(UiText.SETTING_FILE_ONLY_NOTE, "key", d.key()), Msg.MUTED);
                continue;
            }
            Object value = ctx.settings().raw(d.key());
            if (d.defaultValue() instanceof Boolean) {
                body.toggle(d.label(), d.key(), Boolean.TRUE.equals(value), "bool", d.key());
            } else {
                body.field(d.label(), value == null ? "" : String.valueOf(value), Btn.of(Messages.get(UiText.APPLY), "set", d.key()));
                body.text(d.key() + "    " + Messages.get(UiText.SETTING_DEFAULT, "value", d.defaultValue()) + limits(d.key()), Msg.MUTED);
            }
        }
    }

    /** The allowed range or values of a key as a short suffix, or nothing. */
    private String limits(String key) {
        Settings.Range r = ctx.settings().rangeOf(key);
        if (r != null) {
            return "    " + (r.max() >= Long.MAX_VALUE / 2.0
                    ? Messages.get(UiText.SETTING_RANGE_UP, "min", Msg.number(r.min()))
                    : Messages.get(UiText.SETTING_RANGE, "min", Msg.number(r.min()), "max", Msg.number(r.max())));
        }
        List<String> allowed = ctx.settings().choicesOf(key);
        return allowed.isEmpty() ? "" : "    " + Messages.get(UiText.SETTING_CHOICES, "choices", String.join(", ", allowed));
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
            Msg.send(player(), Msg.bad(Messages.get(UiText.SETTINGS_NEED_ADMIN)));
            return true;
        }
        Settings settings = ctx.settings();
        if (("bool".equals(e.action) || "set".equals(e.action)) && settings.fileOnly(e.value())) {
            Msg.send(player(), Msg.bad(Messages.get(UiText.SETTING_FILE_ONLY, "key", e.value())));
            return true;
        }
        switch (e.action) {
            case "bool" -> {
                boolean next = !settings.bool(e.value());
                String why = settings.trySet(e.value(), next);
                if (why == null) {
                    Msg.send(player(), Msg.good(Messages.get(next ? UiText.SETTING_NOW_ON : UiText.SETTING_NOW_OFF, "key", e.value())));
                } else {
                    Msg.send(player(), Msg.bad(why));
                }
            }
            case "set" -> {
                String why = settings.trySet(e.value(), e.text());
                if (why == null) {
                    Msg.send(player(), Msg.good(Messages.get(UiText.SETTING_NOW, "key", e.value(), "value", settings.text(e.value()))));
                } else {
                    Msg.send(player(), Msg.bad(why));
                }
            }
            case "preset" -> {
                String name = e.value();
                if (!dev.ryvrook.dominion.config.Defaults.isPreset(name)) {
                    Msg.send(player(), Msg.bad(Messages.get(UiText.NO_SUCH_PRESET, "name", name)));
                    return true;
                }
                ui.confirm(player(), Messages.get(UiText.PRESET_CONFIRM_TITLE, "name", name),
                        Messages.get(UiText.PRESET_CONFIRM_BODY),
                        yes -> {
                            if (yes) {
                                ctx.plugin().applyPreset(name);
                                Msg.send(player(), Msg.good(Messages.get(UiText.PRESET_APPLIED, "name", name)));
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
