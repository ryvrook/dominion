package dev.ryvrook.dominion.storage;

import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Atomic JSON document files. Every module persists through this so behaviour is uniform.
 *
 * <p>A write goes to a uniquely named temporary file next to the target, is forced to disk and
 * then moved over the target in one step, so a crash leaves either the old or the new file and
 * never half of one. Before the move the previous version is copied to {@code name.bak}. A file
 * that cannot be parsed is moved aside as {@code name.corrupt-<millis>} and the backup is read in
 * its place, so one damaged file never silently becomes an empty one.
 */
public final class JsonFiles {
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final AtomicLong TEMP_COUNTER = new AtomicLong();

    private final HytaleLogger logger;
    /** One lock per target file so two threads never interleave writes to the same path. */
    private final Map<Path, Object> locks = new ConcurrentHashMap<>();
    /** Generation of the last versioned write per file, see writeVersioned. */
    private final Map<Path, Long> lastGeneration = new ConcurrentHashMap<>();
    /** Files whose last write failed. The failure is logged once and again only after a success. */
    private final Set<Path> failing = ConcurrentHashMap.newKeySet();

    public JsonFiles(HytaleLogger logger) {
        this.logger = logger;
    }

    /** The backup copy kept next to a file. */
    public static Path backupOf(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }

    /** True for the temporary files a write leaves behind when the process dies mid write. */
    public static boolean isTemp(Path file) {
        return file.getFileName().toString().endsWith(TEMP_SUFFIX);
    }

    private Object lockFor(Path file) {
        return locks.computeIfAbsent(file.toAbsolutePath().normalize(), p -> new Object());
    }

    /**
     * Parses a file without touching it. Returns null when the file does not exist and throws when
     * it exists but is not a JSON document. Used by live reloads that must not move a file the
     * admin is still editing.
     */
    public Document parse(Path file) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return Document.parse(text);
        } catch (RuntimeException e) {
            throw new IOException("Not a JSON document. " + e.getMessage(), e);
        }
    }

    /** Reads a document, falling back to the backup when the file is damaged. Null when neither exists. */
    public Document read(Path file) {
        return read(file, d -> d);
    }

    /**
     * Reads and converts a document. When the file cannot be parsed as JSON it is moved aside and
     * the backup is tried with the same converter. When the JSON parses but the converter throws,
     * which points at a code problem or a file from another version rather than a damaged disk, the
     * file stays where it is, a copy is kept as name.unread-millis so a later save cannot lose it,
     * and null is returned so the caller skips that record. Returns null when nothing usable was found.
     */
    public <T> T read(Path file, Function<Document, T> converter) {
        synchronized (lockFor(file)) {
            if (Files.exists(file)) {
                Document d;
                try {
                    d = parse(file);
                } catch (Exception e) {
                    d = null;
                    Path aside = quarantine(file);
                    logger.at(Level.WARNING).withCause(e).log("Dominion file %s is damaged and was moved to %s",
                            file, aside == null ? "nowhere, it stays in place" : aside.getFileName());
                }
                if (d != null) {
                    return convert(file, d, converter);
                }
            }
            Path backup = backupOf(file);
            if (!Files.exists(backup)) {
                return null;
            }
            Document d;
            try {
                d = parse(backup);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("The backup %s is damaged too", backup);
                return null;
            }
            T value = d == null ? null : convert(backup, d, converter);
            if (value != null) {
                logger.at(Level.WARNING).log("Dominion restored %s from its backup copy", file.getFileName());
            }
            return value;
        }
    }

    private <T> T convert(Path file, Document d, Function<Document, T> converter) {
        try {
            return converter.apply(d);
        } catch (RuntimeException e) {
            Path copy = file.resolveSibling(file.getFileName() + ".unread-" + System.currentTimeMillis());
            try {
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                copy = null;
            }
            logger.at(Level.WARNING).withCause(e).log("Dominion could not use %s and skipped it. The file stays in place%s",
                    file, copy == null ? "." : " and a copy was kept as " + copy.getFileName() + ".");
            return null;
        }
    }

    /** Moves a damaged file aside so it can be inspected. Returns the new path or null. */
    private Path quarantine(Path file) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            return aside;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Writes a document only while the guard holds, checked under the same per file lock that
     * delete takes. A record removed on another thread is therefore never written back after its
     * file was deleted. The document is built inside the lock. Returns false when the guard failed
     * or the write failed.
     */
    public boolean writeIf(Path file, java.util.function.BooleanSupplier guard, java.util.function.Supplier<Document> document) {
        synchronized (lockFor(file)) {
            if (!guard.getAsBoolean()) {
                return false;
            }
            return write(file, document.get());
        }
    }

    /** Outcome of a versioned write. */
    public enum Versioned { WRITTEN, SUPERSEDED, REFUSED, FAILED }

    /**
     * Writes a snapshot of a record only when it is newer than the last one written to the file.
     * Callers take the generation and the document together while they hold the record's monitor,
     * so a higher generation always holds the newer state. Under the per file lock an older
     * snapshot that lost the race is dropped instead of overwriting a newer file, and the guard is
     * checked so a record deleted meanwhile is never written back.
     */
    public Versioned writeVersioned(Path file, long generation, java.util.function.BooleanSupplier guard, Document document) {
        Path key = file.toAbsolutePath().normalize();
        synchronized (lockFor(file)) {
            if (!guard.getAsBoolean()) {
                return Versioned.REFUSED;
            }
            Long last = lastGeneration.get(key);
            if (last != null && generation <= last) {
                return Versioned.SUPERSEDED;
            }
            if (!write(file, document)) {
                return Versioned.FAILED;
            }
            lastGeneration.put(key, generation);
            return Versioned.WRITTEN;
        }
    }

    /** Writes a document atomically and keeps the previous version as a backup. */
    public boolean write(Path file, Document document) {
        return writeText(file, document.toJson(PRETTY), true);
    }

    /**
     * Writes text atomically. With keepBackup the file being replaced is first copied to its
     * backup path. Returns false and logs when the write failed, leaving the old file in place.
     */
    public boolean writeText(Path file, String text, boolean keepBackup) {
        synchronized (lockFor(file)) {
            Path temp = file.resolveSibling(file.getFileName() + "." + ProcessHandle.current().pid() + "-"
                    + TEMP_COUNTER.incrementAndGet() + TEMP_SUFFIX);
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (FileChannel ch = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    ByteBuffer buf = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                    while (buf.hasRemaining()) {
                        ch.write(buf);
                    }
                    ch.force(true);
                }
                if (keepBackup && Files.isRegularFile(file)) {
                    Files.copy(file, backupOf(file), StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                if (failing.remove(file.toAbsolutePath().normalize())) {
                    logger.at(Level.INFO).log("Dominion can write %s again", file);
                }
                return true;
            } catch (IOException e) {
                if (failing.add(file.toAbsolutePath().normalize())) {
                    logger.at(Level.SEVERE).withCause(e).log("Could not write Dominion file %s. It stays unsaved and is retried "
                            + "on every save, this is only logged once", file);
                }
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // the next start cleans it up
                }
                return false;
            }
        }
    }

    /** Deletes a file and its backup. */
    public boolean delete(Path file) {
        synchronized (lockFor(file)) {
            try {
                Files.deleteIfExists(backupOf(file));
                return Files.deleteIfExists(file);
            } catch (IOException e) {
                logger.at(Level.WARNING).withCause(e).log("Could not delete Dominion file %s", file);
                return false;
            }
        }
    }

    public List<Path> list(Path directory, String glob) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not list %s", directory);
        }
        out.sort(null);
        return out;
    }

    /** Removes temporary files left by a write that never finished. Run once at start before anything is read. */
    public int sweep(Path directory) {
        int removed = 0;
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    if (!p.getFileName().toString().equals("backups")) {
                        removed += sweep(p);
                    }
                } else if (isTemp(p)) {
                    Files.deleteIfExists(p);
                    removed++;
                }
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not clean up %s", directory);
        }
        return removed;
    }
}
