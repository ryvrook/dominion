package dev.ryvrook.dominion.map;

/**
 * Builds world map marker ids. The engine keys markers by id across every provider of a world, so
 * each id starts with dominion_ and the kind of marker. Ids use only letters, digits and _ . : -
 * and stay within 64 characters, the same rule map mods such as Atlas apply to marker ids. A part
 * with other characters, such as a world or hill name with spaces, is cleaned, and an id that would
 * run long keeps its start and ends in a hash of the whole so two long ids never collide.
 */
public final class MarkerIds {
    public static final int MAX_LENGTH = 64;
    private static final String PREFIX = "dominion_";

    private MarkerIds() {}

    /** An id for the given kind and parts, such as of("claim", world, index). */
    public static String of(String kind, Object... parts) {
        StringBuilder raw = new StringBuilder(PREFIX).append(clean(kind));
        for (Object part : parts) {
            raw.append('_').append(clean(String.valueOf(part)));
        }
        String id = raw.toString();
        if (id.length() <= MAX_LENGTH) {
            return id;
        }
        String hash = Long.toHexString(fnv(id));
        return id.substring(0, MAX_LENGTH - hash.length() - 1) + "." + hash;
    }

    /** True when the id follows the rule above. */
    public static boolean valid(String id) {
        return id != null && !id.isEmpty() && id.length() <= MAX_LENGTH && id.matches("[A-Za-z0-9_.:-]+");
    }

    static String clean(String part) {
        if (part == null || part.isEmpty()) {
            return "x";
        }
        StringBuilder sb = new StringBuilder(part.length());
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == ':' || c == '-';
            sb.append(ok ? c : '-');
        }
        return sb.toString();
    }

    private static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
