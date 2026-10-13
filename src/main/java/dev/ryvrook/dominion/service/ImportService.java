package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;

import java.util.List;
import java.util.UUID;

/** Imports claims and factions from other mods' data files. */
public interface ImportService {
    /** Source names such as simpleclaims, hyfactions, hyperfactions, ultimatefactions. */
    List<String> sources();

    /** Describes what an import would do without changing anything. A console preview passes no admin. */
    List<String> preview(UUID admin, String source);

    /** A preview that is not tied to an admin, as the console uses. */
    default List<String> preview(String source) {
        return preview(null, source);
    }

    Result run(UUID admin, String adminName, String source);
}
