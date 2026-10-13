package dev.ryvrook.dominion.service;

/**
 * Writes a JSON snapshot of territories, points, factions and sieges to the data folder for
 * external consumers such as the web map, which reads the file instead of linking to this jar.
 */
public interface ExportService {
    /** Path of the snapshot file relative to the server working directory. */
    String snapshotPath();

    /** Writes the snapshot now if anything changed since the last write. */
    void flush();

    /**
     * Records one public chat line for the snapshot. The service keeps the last 50 lines with a
     * timestamp and writes them as the top level chat array. Private channels must never be passed in.
     *
     * @param name the sender's username
     * @param tag the sender's faction tag, or an empty string when the sender has no faction
     * @param text the message as delivered
     */
    void recordChat(String name, String tag, String text);
}
