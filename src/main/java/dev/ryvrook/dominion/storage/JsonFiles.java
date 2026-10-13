package dev.ryvrook.dominion.storage;

import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Atomic JSON document files. Every module persists through this so behaviour is uniform. */
public final class JsonFiles {
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();

    private final HytaleLogger logger;

    public JsonFiles(HytaleLogger logger) {
        this.logger = logger;
    }

    public Document read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Document.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Unreadable Dominion file %s, moving it aside", file);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
            return null;
        }
    }

    public boolean write(Path file, Document document) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, document.toJson(PRETTY), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("Could not write Dominion file %s", file);
            return false;
        }
    }

    public boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not delete Dominion file %s", file);
            return false;
        }
    }

    public List<Path> list(Path directory, String glob) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path p : stream) {
                out.add(p);
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not list %s", directory);
        }
        return out;
    }
}
