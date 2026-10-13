package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.ryvrook.dominion.ui.PageEvent;

/** Appends the shop row templates into one container and binds their buttons. */
final class ShopBody {
    static final String PAGE = "Pages/Dominion_Shop_Page.ui";
    static final String TAB = "Pages/Dominion_Shop_Tab.ui";
    static final String ROW = "Pages/Dominion_Shop_Row.ui";
    static final String TEXT = "Pages/Dominion_Shop_Text.ui";
    static final String SECTION = "Pages/Dominion_Shop_Section.ui";
    static final String BUTTONS = "Pages/Dominion_Shop_Buttons.ui";
    static final String FORM = "Pages/Dominion_Shop_Form.ui";

    /** A button label with the action and value it sends. */
    record Btn(String label, String action, String value) {
        static Btn of(String label, String action, String value) {
            return new Btn(label, action, value);
        }
    }

    private final UICommandBuilder commands;
    private final UIEventBuilder events;
    private final String root;
    private int count;

    ShopBody(UICommandBuilder commands, UIEventBuilder events, String root) {
        this.commands = commands;
        this.events = events;
        this.root = root;
        commands.clear(root);
    }

    private String add(String template) {
        String selector = root + "[" + count++ + "]";
        commands.append(root, template);
        return selector;
    }

    static void on(UIEventBuilder events, String selector, String action, String value) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Value", value == null ? "" : value), false);
    }

    void bind(String selector, String action, String value) {
        on(events, selector, action, value);
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

    String section(String text) {
        String s = add(SECTION);
        commands.set(s + " #Text.Text", text);
        return s;
    }

    String text(String text, String color) {
        String s = add(TEXT);
        commands.set(s + " #Text.Text", text);
        if (color != null) {
            commands.set(s + " #Text.Style.TextColor", color);
        }
        return s;
    }

    String tab(String label, String count, boolean selected, String action, String value) {
        String s = add(TAB);
        commands.set(s + " #Button.Text", label);
        commands.set(s + " #Selected.Text", label);
        commands.set(s + " #Button.Visible", !selected);
        commands.set(s + " #Selected.Visible", selected);
        commands.set(s + " #Count.Text", count == null ? "" : count);
        bind(s + " #Button", action, value);
        return s;
    }

    /** Most buttons in a button row, so a label and every button fit the body. */
    static final int MAX_ROW_BUTTONS = 4;

    /** A row of up to four buttons with an optional leading label. */
    String buttons(String label, Btn... buttons) {
        String s = add(BUTTONS);
        if (label != null) {
            commands.set(s + " #Label.Visible", true);
            commands.set(s + " #Label.Text", label);
        }
        for (int i = 0; i < Math.min(MAX_ROW_BUTTONS, buttons.length); i++) {
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

    /**
     * An item row with an icon, three columns, up to two price fields and buttons. Without fields a
     * row has up to three buttons, with fields it has up to two so the name keeps its room. When
     * fields are shown, the first button submits them as Text and Text2.
     */
    String row(String itemId, String name, String nameColor, String[] cols, String[] fields, Btn... buttons) {
        String s = add(ROW);
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
        int nFields = fields == null ? 0 : Math.min(2, fields.length);
        String[] inputs = new String[nFields];
        for (int i = 0; i < nFields; i++) {
            String f = s + " #P" + (i + 1);
            commands.set(f + ".Visible", true);
            if (fields[i] != null && !fields[i].isEmpty()) {
                commands.set(f + ".Value", fields[i]);
            }
            inputs[i] = f;
        }
        int maxButtons = nFields > 0 ? 2 : 3;
        for (int i = 0; i < Math.min(maxButtons, buttons.length); i++) {
            Btn b = buttons[i];
            if (b == null) {
                continue;
            }
            String bs = s + " #B" + (i + 1);
            commands.set(bs + ".Visible", true);
            commands.set(bs + ".Text", b.label());
            if (i == 0 && nFields > 0) {
                bindWithTexts(bs, b.action(), b.value(), inputs);
            } else {
                bind(bs, b.action(), b.value());
            }
        }
        return s;
    }

    /** One to three labelled text fields with up to two buttons that submit all of them. */
    String form(String[] labels, String[] initial, Btn... buttons) {
        String s = add(FORM);
        int n = Math.min(3, labels.length);
        String[] inputs = new String[n];
        for (int i = 0; i < n; i++) {
            commands.set(s + " #L" + (i + 1) + ".Text", labels[i]);
            if (i > 0) {
                commands.set(s + " #L" + (i + 1) + ".Visible", true);
                commands.set(s + " #I" + (i + 1) + ".Visible", true);
            }
            if (initial != null && i < initial.length && initial[i] != null && !initial[i].isEmpty()) {
                commands.set(s + " #I" + (i + 1) + ".Value", initial[i]);
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
}
