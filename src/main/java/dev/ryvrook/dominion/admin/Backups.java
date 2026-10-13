package dev.ryvrook.dominion.admin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zips the data directory into backups and prunes old archives. */
public final class Backups {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREFIX = "dominion-";

    private Backups() {}

    /** Writes a new archive and returns its path. Files under backups are skipped. */
    public static Path create(Path dataDir, int keep) throws IOException {
        Path backups = dataDir.resolve("backups");
        Files.createDirectories(backups);
        Path archive = backups.resolve(PREFIX + STAMP.format(LocalDateTime.now()) + ".zip");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dataDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(backups))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .toList();
        }
        try (OutputStream out = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path file : files) {
                String entry = dataDir.relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entry));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        prune(backups, Math.max(1, keep));
        return archive;
    }

    private static void prune(Path backups, int keep) throws IOException {
        List<Path> archives = new ArrayList<>();
        try (Stream<Path> list = Files.list(backups)) {
            list.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(PREFIX) && n.endsWith(".zip");
            }).forEach(archives::add);
        }
        archives.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        for (int i = keep; i < archives.size(); i++) {
            Files.deleteIfExists(archives.get(i));
        }
    }
}
