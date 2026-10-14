package dev.ryvrook.dominion.importer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A small read only reader for SQLite 3 database files, written against the published file
 * format so Dominion needs no JDBC driver. It copies the file into memory once and walks table
 * b-trees, following overflow pages. It never opens the file for writing and takes no locks.
 * Index b-trees, WITHOUT ROWID tables and the write ahead log are not read.
 */
final class SqliteFile {
    private static final byte[] MAGIC = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    static final long MAX_BYTES = 512L * 1024 * 1024;

    private final byte[] data;
    private final int pageSize;
    private final int usable;
    private final long pageCount;
    private final Charset charset;
    private final Map<String, Table> tables = new HashMap<>();

    private record Table(long root, List<String> columns, int rowidAlias) {}

    private SqliteFile(byte[] data) throws IOException {
        this.data = data;
        if (data.length < 100) {
            throw new IOException("the database file is too short");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new IOException("the file is not an SQLite 3 database");
            }
        }
        int ps = u16(16);
        this.pageSize = ps == 1 ? 65536 : ps;
        if (pageSize < 512 || Integer.bitCount(pageSize) != 1) {
            throw new IOException("the database page size is not valid");
        }
        this.usable = pageSize - (data[20] & 0xff);
        if (usable < 480) {
            throw new IOException("the database page size is not valid");
        }
        this.pageCount = data.length / pageSize;
        int enc = (int) u32(56);
        this.charset = enc == 2 ? StandardCharsets.UTF_16LE : enc == 3 ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_8;
        readSchema();
    }

    static SqliteFile open(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_BYTES) {
            throw new IOException("the database is larger than 512 MB");
        }
        return new SqliteFile(Files.readAllBytes(file));
    }

    boolean hasTable(String name) {
        return tables.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** Every row of a table as column name to value. Values are Long, Double, String, byte[] or null. */
    List<Map<String, Object>> rows(String table) throws IOException {
        Table t = tables.get(table.toLowerCase(Locale.ROOT));
        if (t == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        walk(t.root(), new HashSet<>(), 0, (rowid, values) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < t.columns().size(); i++) {
                Object v = i < values.size() ? values.get(i) : null;
                if (i == t.rowidAlias() && v == null) {
                    v = rowid;
                }
                row.put(t.columns().get(i), v);
            }
            out.add(row);
        });
        return out;
    }

    // ---- schema ----------------------------------------------------------

    private void readSchema() throws IOException {
        walk(1, new HashSet<>(), 0, (rowid, v) -> {
            if (v.size() < 5 || !"table".equals(v.get(0)) || v.get(2) == null || !(v.get(3) instanceof Long root)
                    || !(v.get(4) instanceof String sql)) {
                return;
            }
            if (sql.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").contains("WITHOUT ROWID")) {
                return;
            }
            List<String> cols = new ArrayList<>();
            int alias = -1;
            for (String def : definitions(sql)) {
                String[] parts = def.strip().split("\\s+");
                if (parts.length == 0 || parts[0].isEmpty()) {
                    continue;
                }
                String first = parts[0].toUpperCase(Locale.ROOT);
                if (first.equals("PRIMARY") || first.equals("FOREIGN") || first.equals("UNIQUE")
                        || first.equals("CHECK") || first.equals("CONSTRAINT")) {
                    continue;
                }
                String upper = def.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (upper.matches("\\S+ INTEGER PRIMARY KEY.*")) {
                    alias = cols.size();
                }
                cols.add(parts[0].replaceAll("[\"`\\[\\]]", "").toLowerCase(Locale.ROOT));
            }
            tables.put(v.get(2).toString().toLowerCase(Locale.ROOT), new Table(root, cols, alias));
        });
    }

    private static List<String> definitions(String sql) {
        List<String> out = new ArrayList<>();
        int a = sql.indexOf('(');
        int b = sql.lastIndexOf(')');
        if (a < 0 || b <= a) {
            return out;
        }
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = a + 1; i < b; i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ---- b-tree ----------------------------------------------------------

    private interface RowSink {
        void row(long rowid, List<Object> values) throws IOException;
    }

    private void walk(long page, Set<Long> seen, int depth, RowSink sink) throws IOException {
        if (page < 1 || page > pageCount || depth > 40 || !seen.add(page)) {
            throw new IOException("the database has a damaged page tree");
        }
        try {
            int base = (int) ((page - 1) * pageSize);
            int h = base + (page == 1 ? 100 : 0);
            int type = data[h] & 0xff;
            int cells = u16(h + 3);
            if (type == 0x0d) {
                for (int i = 0; i < cells; i++) {
                    int[] pos = { base + u16(h + 8 + i * 2) };
                    long payload = varint(pos);
                    long rowid = varint(pos);
                    sink.row(rowid, record(payload(pos[0], payload)));
                }
            } else if (type == 0x05) {
                for (int i = 0; i < cells; i++) {
                    int cell = base + u16(h + 12 + i * 2);
                    walk(u32(cell), seen, depth + 1, sink);
                }
                walk(u32(h + 8), seen, depth + 1, sink);
            } else {
                throw new IOException("the database has a page of an unexpected type");
            }
        } catch (RuntimeException e) {
            // Any malformed byte sequence surfaces as an unchecked exception, which the reader turns into a readable reason.
            throw new IOException("the database file is truncated or damaged", e);
        } catch (OutOfMemoryError e) {
            throw new IOException("the database has a record too large to read", e);
        }
    }

    private byte[] payload(int start, long size) throws IOException {
        if (size < 0 || size > data.length) {
            throw new IOException("the database has a record of impossible size");
        }
        int p = (int) size;
        int x = usable - 35;
        int local;
        if (p <= x) {
            local = p;
        } else {
            int m = ((usable - 12) * 32 / 255) - 23;
            int k = m + ((p - m) % (usable - 4));
            local = k <= x ? k : m;
        }
        byte[] out = new byte[p];
        System.arraycopy(data, start, out, 0, local);
        int done = local;
        long next = done < p ? u32(start + local) : 0;
        Set<Long> seen = new HashSet<>();
        while (done < p) {
            if (next < 1 || next > pageCount || !seen.add(next)) {
                throw new IOException("the database has a damaged overflow chain");
            }
            int base = (int) ((next - 1) * pageSize);
            int n = Math.min(p - done, usable - 4);
            System.arraycopy(data, base + 4, out, done, n);
            done += n;
            next = u32(base);
        }
        return out;
    }

    private List<Object> record(byte[] rec) {
        int[] pos = { 0 };
        long headerSize = varint(rec, pos);
        List<Long> types = new ArrayList<>();
        while (pos[0] < headerSize) {
            types.add(varint(rec, pos));
        }
        if (headerSize > rec.length) {
            throw new IndexOutOfBoundsException("record header past the end");
        }
        int at = (int) headerSize;
        List<Object> values = new ArrayList<>(types.size());
        for (long t : types) {
            if (t >= 12 && (t - 12) / 2 > rec.length - at) {
                throw new IndexOutOfBoundsException("record value past the end");
            }
            if (t == 0) {
                values.add(null);
            } else if (t >= 1 && t <= 6) {
                int len = t == 5 ? 6 : t == 6 ? 8 : (int) t;
                long v = rec[at];
                for (int i = 1; i < len; i++) {
                    v = (v << 8) | (rec[at + i] & 0xff);
                }
                values.add(v);
                at += len;
            } else if (t == 7) {
                long bits = 0;
                for (int i = 0; i < 8; i++) {
                    bits = (bits << 8) | (rec[at + i] & 0xff);
                }
                values.add(Double.longBitsToDouble(bits));
                at += 8;
            } else if (t == 8 || t == 9) {
                values.add(t == 8 ? 0L : 1L);
            } else if (t >= 12 && t % 2 == 0) {
                int len = (int) ((t - 12) / 2);
                byte[] b = new byte[len];
                System.arraycopy(rec, at, b, 0, len);
                values.add(b);
                at += len;
            } else if (t >= 13) {
                int len = (int) ((t - 13) / 2);
                values.add(new String(rec, at, len, charset));
                at += len;
            } else {
                values.add(null);
            }
        }
        return values;
    }

    // ---- primitives ------------------------------------------------------

    private int u16(int off) {
        return ((data[off] & 0xff) << 8) | (data[off + 1] & 0xff);
    }

    private long u32(int off) {
        return ((long) (data[off] & 0xff) << 24) | ((data[off + 1] & 0xff) << 16) | ((data[off + 2] & 0xff) << 8) | (data[off + 3] & 0xff);
    }

    private long varint(int[] pos) {
        return varint(data, pos);
    }

    private static long varint(byte[] b, int[] pos) {
        long v = 0;
        for (int i = 0; i < 9; i++) {
            int c = b[pos[0]++] & 0xff;
            if (i == 8) {
                return (v << 8) | c;
            }
            v = (v << 7) | (c & 0x7f);
            if ((c & 0x80) == 0) {
                return v;
            }
        }
        return v;
    }
}
