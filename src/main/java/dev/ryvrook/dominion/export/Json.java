package dev.ryvrook.dominion.export;

/** Tiny JSON writer so the snapshot schema stays exact and numbers stay plain. */
final class Json {
    private final StringBuilder sb = new StringBuilder(8192);
    private boolean needComma;

    Json beginObject() { comma(); sb.append('{'); needComma = false; return this; }
    Json endObject() { sb.append('}'); needComma = true; return this; }
    Json beginArray() { comma(); sb.append('['); needComma = false; return this; }
    Json endArray() { sb.append(']'); needComma = true; return this; }

    Json key(String k) {
        comma();
        string(k);
        sb.append(':');
        needComma = false;
        return this;
    }

    Json value(String v) {
        comma();
        if (v == null) {
            sb.append("null");
        } else {
            string(v);
        }
        needComma = true;
        return this;
    }

    Json value(long v) { comma(); sb.append(v); needComma = true; return this; }
    Json value(boolean v) { comma(); sb.append(v); needComma = true; return this; }

    Json value(double v) {
        comma();
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            sb.append('0');
        } else {
            sb.append(Math.round(v * 100.0) / 100.0);
        }
        needComma = true;
        return this;
    }

    Json field(String k, String v) { return key(k).value(v); }
    Json field(String k, long v) { return key(k).value(v); }
    Json field(String k, double v) { return key(k).value(v); }
    Json field(String k, boolean v) { return key(k).value(v); }

    Json pair(int a, int b) {
        return beginArray().value(a).value(b).endArray();
    }

    private void comma() {
        if (needComma) {
            sb.append(',');
        }
    }

    private void string(String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                // Line and paragraph separators are valid JSON but break consumers that evaluate it as script.
                case '\u2028' -> sb.append("\\u2028");
                case '\u2029' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
