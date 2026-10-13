package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Payload sent back by every Dominion page. Action names the button, Value carries a row
 * argument and Text carries the contents of the TextField the button is bound to. Forms with
 * more than one field also send Text2 and Text3.
 */
public final class PageEvent {
    public static final String TEXT_KEY = "@Text";
    public static final String TEXT2_KEY = "@Text2";
    public static final String TEXT3_KEY = "@Text3";

    public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.builder(PageEvent.class, PageEvent::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (e, v, extra) -> e.action = v,
                    (e, extra) -> e.action)
            .add()
            .append(new KeyedCodec<>("Value", Codec.STRING),
                    (e, v, extra) -> e.value = v,
                    (e, extra) -> e.value)
            .add()
            .append(new KeyedCodec<>(TEXT_KEY, Codec.STRING),
                    (e, v, extra) -> e.text = v,
                    (e, extra) -> e.text)
            .add()
            .append(new KeyedCodec<>(TEXT2_KEY, Codec.STRING),
                    (e, v, extra) -> e.text2 = v,
                    (e, extra) -> e.text2)
            .add()
            .append(new KeyedCodec<>(TEXT3_KEY, Codec.STRING),
                    (e, v, extra) -> e.text3 = v,
                    (e, extra) -> e.text3)
            .add()
            .build();

    public String action;
    public String value;
    public String text;
    public String text2;
    public String text3;

    public String value() {
        return value == null ? "" : value;
    }

    public String text() {
        return text == null ? "" : text.trim();
    }

    public String text2() {
        return text2 == null ? "" : text2.trim();
    }

    public String text3() {
        return text3 == null ? "" : text3.trim();
    }
}
