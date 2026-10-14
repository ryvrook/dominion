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
        // Written under a temporary name first, so a crash never leaves a broken archive that
        // counts as a backup and pushes a good one out.
        Path partial = backups.resolve(archive.getFileName() + ".part");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dataDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(backups))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .filter(p -> !p.getFileName().toString().contains(".corrupt-"))
                    .toList();
        }
        try (OutputStream out = Files.newOutputStream(partial); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path file : files) {
                String entry = dataDir.relativize(file).toString().replace('\\', '/');
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    zip.putNextEntry(new ZipEntry(entry));
                    zip.write(bytes);
                    zip.closeEntry();
                } catch (java.nio.file.NoSuchFileException gone) {
                    // Replaced or deleted by a save while the backup ran. The next backup has it.
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        Files.move(partial, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
