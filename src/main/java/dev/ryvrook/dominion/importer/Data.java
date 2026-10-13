package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.util.Keys;
import org.bson.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read only helpers for foreign data files. Nothing here writes, moves or locks a source file,
 * which is why Dominion's own JsonFiles is not used for reading (it moves unreadable files aside).
 */
final class Data {
    private Data() {}

    static final long MAX_JSON_BYTES = 64L * 1024 * 1024;

    static Document json(Path file) throws IOException {
        if (Files.size(file) > MAX_JSON_BYTES) {
            throw new IOException("the file is larger than 64 MB");
        }
        String text = Files.readString(file, StandardCharsets.UTF_8).strip();
        if (text.isEmpty()) {
            return new Document();
        }
        if (!text.startsWith("{")) {
            throw new IOException("the file does not hold a JSON object");
        }
        return Document.parse(text);
    }

    static boolean isFile(Path p) {
        return p != null && Files.isRegularFile(p) && Files.isReadable(p);
    }

    static boolean isDir(Path p) {
        return p != null && Files.isDirectory(p);
    }

    static List<Path> list(Path dir, String glob) {
        List<Path> out = new ArrayList<>();
        if (!isDir(dir)) {
            return out;
        }
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, glob)) {
            for (Path p : s) {
                out.add(p);
            }
        } catch (IOException ignored) {
            // an unreadable folder is reported as not found by the caller
        }
        out.sort(null);
        return out;
    }

    /** Sub folders of dir whose lower case name contains one of the needles. */
    static List<Path> foldersLike(Path dir, String... needles) {
        List<Path> out = new ArrayList<>();
        for (Path p : list(dir, "*")) {
            if (!Files.isDirectory(p)) {
                continue;
            }
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (n.contains(needle)) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    // ---- value access ---------------------------------------------------

    static String str(Document d, String key) {
        Object v = d == null ? null : d.get(key);
        return v == null ? null : v.toString();
    }

    static Long num(Document d, String key) {
        Object v = d == null ? null : d.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Document doc(Document d, String key) {
        Object v = d == null ? null : d.get(key);
        return v instanceof Document x ? x : null;
    }

    static List<?> list(Document d, String key) {
        Object v = d == null ? null : d.get(key);
        return v instanceof List<?> l ? l : List.of();
    }

    static List<Document> docs(Document d, String key) {
        List<Document> out = new ArrayList<>();
        for (Object o : list(d, key)) {
            if (o instanceof Document x) {
                out.add(x);
            }
        }
        return out;
    }

    static UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(o.toString().strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String shortId(UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }

    /** Converts an ARGB or RGB integer to a Dominion hex colour. */
    static String color(Long argb) {
        return argb == null ? null : String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
    }

    static String hexColor(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip();
        if (t.matches("#?[0-9a-fA-F]{6}")) {
            return (t.startsWith("#") ? t : "#" + t).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** Epoch millis from a value that may be seconds or millis. Zero when unknown. */
    static long epochMillis(Long v) {
        if (v == null || v <= 0) {
            return 0;
        }
        return v < 100_000_000_000L ? v * 1000 : v;
    }

    // ---- coordinates ----------------------------------------------------

    /**
     * Dominion chunk indexes covered by one source cell. cellSize is the edge length of the
     * source's unit in blocks, so 1 means block coordinates, 32 means Hytale chunk coordinates and
     * 16 means the 16 block cells some mods use. Hytale chunks are Keys.chunkSize() blocks wide.
     */
    static long[] chunks(int x, int z, int cellSize) {
        int size = Keys.chunkSize();
        if (cellSize == size) {
            return new long[] { Keys.chunk(x, z) };
        }
        if (cellSize <= 1) {
            return new long[] { Keys.chunkAtBlock(x, z) };
        }
        long minX = (long) x * cellSize;
        long minZ = (long) z * cellSize;
        long maxX = minX + cellSize - 1;
        long maxZ = minZ + cellSize - 1;
        if (minX < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE || minZ < Integer.MIN_VALUE || maxZ > Integer.MAX_VALUE) {
            return new long[0];
        }
        int cx0 = Math.floorDiv((int) minX, size);
        int cx1 = Math.floorDiv((int) maxX, size);
        int cz0 = Math.floorDiv((int) minZ, size);
        int cz1 = Math.floorDiv((int) maxZ, size);
        long[] out = new long[(cx1 - cx0 + 1) * (cz1 - cz0 + 1)];
        int i = 0;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                out[i++] = Keys.chunk(cx, cz);
            }
        }
        return out;
    }
}
