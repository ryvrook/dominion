package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.ryvrook.dominion.config.CommandNames;

import java.util.Set;

/**
 * Appends reusable row templates into one scrolling container and binds their buttons.
 * Every helper returns the selector of the element it added so callers can tweak it.
 */
final class Body {
    static final String SECTION = "Pages/Dominion_Section.ui";
    static final String TEXT = "Pages/Dominion_Text.ui";
    static final String STAT = "Pages/Dominion_Stat.ui";
    static final String ROW = "Pages/Dominion_Row.ui";
    static final String FIELD = "Pages/Dominion_Field.ui";
    static final String TOGGLE = "Pages/Dominion_Toggle.ui";
    static final String BUTTONS = "Pages/Dominion_Buttons.ui";
    static final String PALETTE = "Pages/Dominion_Palette.ui";
    static final String GRID_ROW = "Pages/Dominion_GridRow.ui";
    static final String TAB = "Pages/Dominion_Tab.ui";
    static final String ITEM_ROW = "Pages/Dominion_ItemRow.ui";
    static final String FORM = "Pages/Dominion_Form.ui";
    static final String HOURS = "Pages/Dominion_Hours.ui";
    static final String UPGRADE = "Pages/Dominion_Upgrade.ui";
    static final String CARD = "Pages/Dominion_Card.ui";
    static final int MAX_DOTS = 10;
    /** Level dots drawn per upgrade, ui.maxDots, never more than the markup holds. */
    private int maxDots = MAX_DOTS;

    /** A button label with the action and value it sends. */
    record Btn(String label, String action, String value) {
        static Btn of(String label, String action) {
            return new Btn(label, action, "");
        }

        static Btn of(String label, String action, String value) {
            return new Btn(label, action, value);
        }
    }

    private final UICommandBuilder commands;
    private final UIEventBuilder events;
    private final String root;
    private int count;

    Body(UICommandBuilder commands, UIEventBuilder events, String root) {
        this.commands = commands;
        this.events = events;
        this.root = root;
        commands.clear(root);
    }

    UICommandBuilder commands() {
        return commands;
    }

    String add(String template) {
        String selector = root + "[" + count++ + "]";
        commands.append(root, template);
        return selector;
    }

    /** Binds a button outside any body container. */
    static void on(UIEventBuilder events, String selector, String action, String value) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Value", value == null ? "" : value), false);
    }

    void bind(String selector, String action, String value) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Value", value == null ? "" : value), false);
    }

    void bindWithText(String selector, String action, String value, String input) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Value", value == null ? "" : value)
                        .append(PageEvent.TEXT_KEY, input + ".Value"), false);
    }

    String section(String text) {
        String s = add(SECTION);
        commands.set(s + " #Text.Text", CommandNames.localize(text));
        return s;
    }

    String text(String text) {
        String s = add(TEXT);
        // root command name comes from commands.root
        commands.set(s + " #Text.Text", CommandNames.localize(text));
        return s;
    }

    String text(String text, String color) {
        String s = text(text);
        commands.set(s + " #Text.Style.TextColor", color);
        return s;
    }

    String stat(String name, String value) {
        String s = add(STAT);
        commands.set(s + " #Name.Text", name);
        commands.set(s + " #Value.Text", value == null ? "" : value);
        return s;
    }

    String stat(String name, String value, String color) {
        String s = stat(name, value);
        commands.set(s + " #Value.Style.TextColor", color);
        return s;
    }

    /** A table row. Columns beyond the given values are left blank. Buttons fill B1 to B3. */
    String row(String name, String nameColor, String[] cols, Btn... buttons) {
        String s = add(ROW);
        commands.set(s + " #Name.Text", name);
        if (nameColor != null) {
            commands.set(s + " #Name.Style.TextColor", nameColor);
        }
        for (int i = 0; i < 4; i++) {
            String v = cols != null && i < cols.length && cols[i] != null ? cols[i] : "";
            commands.set(s + " #Col" + (i + 1) + ".Text", v);
        }
        for (int i = 0; i < Math.min(3, buttons.length); i++) {
            Btn b = buttons[i];
            if (b == null) {
                continue;
            }
            String bs = s + " #B" + (i + 1);
            commands.set(bs + ".Visible", true);
            commands.set(bs + ".Text", b.label());
            bind(bs, b.action(), b.value());
        }
        return s;
    }

    void dot(String row, boolean online) {
        commands.set(row + (online ? " #Online" : " #Offline") + ".Visible", true);
    }

    /** A label, a text field and up to three buttons that all submit the field. Null buttons are left out. */
    String field(String label, String initial, Btn... buttons) {
        String s = add(FIELD);
        commands.set(s + " #Label.Text", label);
        if (initial != null && !initial.isEmpty()) {
            commands.set(s + " #Input.Value", initial);
        }
        int slot = 0;
        for (int i = 0; i < buttons.length && slot < 3; i++) {
            Btn b = buttons[i];
            if (b == null) {
                continue;
            }
            String bs = s + " #G" + (++slot);
            commands.set(bs + ".Visible", true);
            commands.set(bs + ".Text", b.label());
            bindWithText(bs, b.action(), b.value(), s + " #Input");
        }
        return s;
    }

    String toggle(String label, String key, boolean on, String action, String value) {
        return toggle(label, key, on, action, value, true);
    }

    /** A toggle row. Without editable the state is shown and the button is hidden. */
    String toggle(String label, String key, boolean on, String action, String value, boolean editable) {
        String s = add(TOGGLE);
        commands.set(s + " #Label.Text", label);
        commands.set(s + " #Key.Text", key == null ? "" : key);
        commands.set(s + " #State.Text", on ? "On" : "Off");
        commands.set(s + " #State.Style.TextColor", on ? "#5fd37a" : "#878e9c");
        if (editable) {
            commands.set(s + " #Button.Text", on ? "Turn off" : "Turn on");
            bind(s + " #Button", action, value);
        } else {
            commands.set(s + " #Button.Visible", false);
        }
        return s;
    }

    /** A row of up to six buttons. */
    String buttons(Btn... buttons) {
        String s = add(BUTTONS);
        for (int i = 0; i < Math.min(6, buttons.length); i++) {
            Btn b = buttons[i];
            if (b == null) {
                continue;
            }
            String bs = s + " #B" + i;
            commands.set(bs + ".Visible", true);
            commands.set(bs + ".Text", b.label());
            bind(bs, b.action(), b.value());
        }
        return s;
    }

    /** Twelve fixed swatches. The colours here must match Dominion_Palette.ui. */
    static final String[] PALETTE_COLORS = {
            "#e74c3c", "#ff8c42", "#f1c40f", "#a3d65c", "#2ecc71", "#1abc9c",
            "#7fd6ff", "#3498db", "#6c5ce7", "#d99cff", "#ff6fae", "#d3d6db"
    };

    String palette(String current, String action) {
        String s = add(PALETTE);
        commands.set(s + " #Current.Text", current == null ? "" : "Now " + current);
        if (current != null) {
            commands.set(s + " #Current.Style.TextColor", current);
        }
        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            bind(s + " #S" + i, action, PALETTE_COLORS[i]);
        }
        return s;
    }

    /**
     * One row of the nearby chunks grid. Each cell is a square filled with its background colour
     * and a letter on top. Colours must be plain #rrggbb, null keeps the markup default.
     */
    String gridRow(String[] glyphs, String[] textColors, String[] backgrounds) {
        String s = add(GRID_ROW);
        for (int i = 0; i < Math.min(15, glyphs.length); i++) {
            commands.set(s + " #G" + i + ".Text", glyphs[i] == null ? "" : glyphs[i]);
            if (textColors[i] != null) {
                commands.set(s + " #G" + i + ".Style.TextColor", textColors[i]);
            }
            if (backgrounds[i] != null) {
                // The same colour fill the vanilla world event rows set on their status bar.
                commands.setObject(s + " #C" + i + ".Background", new PatchStyle().setColor(Value.of(backgrounds[i])));
            }
        }
        return s;
    }

    /** Tab ids that tab leaves out, for disabled commands and features that are off. */
    private Set<String> hiddenTabs = Set.of();

    Body hide(Set<String> tabIds) {
        hiddenTabs = tabIds == null ? Set.of() : Set.copyOf(tabIds);
        return this;
    }

    /** A sidebar tab in the Feats style. Returns null without adding anything for a hidden tab. */
    String tab(String id, String label, String count, boolean selected, String action) {
        if (hiddenTabs.contains(id)) {
            return null;
        }
        String s = add(TAB);
        commands.set(s + " #Button.Text", label);
        commands.set(s + " #Selected.Text", label);
        commands.set(s + " #Button.Visible", !selected);
        commands.set(s + " #Selected.Visible", selected);
        commands.set(s + " #Count.Text", count == null ? "" : count);
        bind(s + " #Button", action, id);
        return s;
    }

    /** Binds a button that submits up to three text fields as Text, Text2 and Text3. */
    void bindWithTexts(String selector, String action, String value, String... inputs) {
        EventData data = EventData.of("Action", action).append("Value", value == null ? "" : value);
        String[] keys = {PageEvent.TEXT_KEY, PageEvent.TEXT2_KEY, PageEvent.TEXT3_KEY};
        for (int i = 0; i < Math.min(keys.length, inputs.length); i++) {
            data = data.append(keys[i], inputs[i] + ".Value");
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }

    /**
     * A market style row with an item icon, three columns, an optional text field and up to two
     * buttons. The first button submits the text field when one is shown. A non null field value shows
     * the field and prefills it. A null item id hides the icon.
     */
    String itemRow(String itemId, String name, String nameColor, String[] cols, String fieldValue, Btn first, Btn second) {
        String s = add(ITEM_ROW);
        if (itemId == null || itemId.isBlank()) {
            commands.set(s + " #Icon.Visible", false);
        } else {
            commands.set(s + " #Icon.ItemId", itemId);
        }
        commands.set(s + " #Name.Text", name == null ? "" : name);
        if (nameColor != null) {
            commands.set(s + " #Name.Style.TextColor", nameColor);
        }
        for (int i = 0; i < 3; i++) {
            String v = cols != null && i < cols.length && cols[i] != null ? cols[i] : "";
            commands.set(s + " #Col" + (i + 1) + ".Text", v);
        }
        boolean field = fieldValue != null;
        if (field) {
            commands.set(s + " #Qty.Visible", true);
            if (!fieldValue.isEmpty()) {
                commands.set(s + " #Qty.Value", fieldValue);
            }
        }
        if (first != null) {
            commands.set(s + " #B1.Visible", true);
            commands.set(s + " #B1.Text", first.label());
            if (field) {
                bindWithText(s + " #B1", first.action(), first.value(), s + " #Qty");
            } else {
                bind(s + " #B1", first.action(), first.value());
            }
        }
        if (second != null) {
            commands.set(s + " #B2.Visible", true);
            commands.set(s + " #B2.Text", second.label());
            bind(s + " #B2", second.action(), second.value());
        }
        return s;
    }

    /** One to three labelled text fields on a line with up to two buttons that submit all of them. */
    String form(String[] labels, Btn... buttons) {
        String s = add(FORM);
        int n = Math.min(3, labels.length);
        String[] inputs = new String[n];
        for (int i = 0; i < n; i++) {
            commands.set(s + " #L" + (i + 1) + ".Text", labels[i]);
            if (i > 0) {
                commands.set(s + " #L" + (i + 1) + ".Visible", true);
                commands.set(s + " #I" + (i + 1) + ".Visible", true);
            }
            inputs[i] = s + " #I" + (i + 1);
        }
        for (int i = 0; i < Math.min(2, buttons.length); i++) {
            Btn b = buttons[i];
            if (b == null) {
                continue;
            }
            String bs = s + " #G" + (i + 1);
            commands.set(bs + ".Visible", true);
            commands.set(bs + ".Text", b.label());
            bindWithTexts(bs, b.action(), b.value(), inputs);
        }
        return s;
    }

    /** Twenty four hour buttons. The selected hour is bracketed. Each sends its hour as the value. */
    String hours(int selected, String action) {
        String s = add(HOURS);
        for (int h = 0; h < 24; h++) {
            String label = (h < 10 ? "0" : "") + h;
            commands.set(s + " #H" + h + ".Text", h == selected ? "[" + label + "]" : label);
            bind(s + " #H" + h, action, String.valueOf(h));
        }
        return s;
    }

    void maxDots(int dots) {
        maxDots = Math.max(0, Math.min(MAX_DOTS, dots));
    }

    /** An upgrade with a description, level dots, a cost and an optional buy button. */
    String upgrade(String name, String description, int level, int max, String cost, Btn buy) {
        String s = add(UPGRADE);
        commands.set(s + " #Name.Text", name);
        commands.set(s + " #Desc.Text", description == null ? "" : description);
        int dots = Math.min(maxDots, Math.max(0, max));
        for (int i = 0; i < dots; i++) {
            commands.set(s + (i < level ? " #On" : " #Off") + i + ".Visible", true);
        }
        commands.set(s + " #Cost.Text", cost == null ? "" : cost);
        if (buy != null) {
            commands.set(s + " #Buy.Visible", true);
            commands.set(s + " #Buy.Text", buy.label());
            bind(s + " #Buy", buy.action(), buy.value());
        }
        return s;
    }

    /** A mission card with an icon, a progress bar between 0 and 1 and a timer. */
    String card(String itemId, String name, String description, String reward, float progress, String value, String timer) {
        String s = add(CARD);
        if (itemId == null || itemId.isBlank()) {
            commands.set(s + " #Icon.Visible", false);
        } else {
            commands.set(s + " #Icon.ItemId", itemId);
        }
        commands.set(s + " #Name.Text", name == null ? "" : name);
        commands.set(s + " #Desc.Text", description == null ? "" : description);
        commands.set(s + " #Reward.Text", reward == null ? "" : reward);
        commands.set(s + " #Bar.Value", Math.max(0f, Math.min(1f, progress)));
        commands.set(s + " #Value.Text", value == null ? "" : value);
        commands.set(s + " #Timer.Text", timer == null ? "" : timer);
        return s;
    }
}
