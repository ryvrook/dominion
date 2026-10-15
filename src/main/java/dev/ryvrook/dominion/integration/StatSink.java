package dev.ryvrook.dominion.integration;

import java.util.UUID;

/** Takes the faction counters. The TrackerAPI and Feats bridges are sinks. */
public interface StatSink {
    /** Adds to one counter of a player. Must not throw and must not block. */
    void add(UUID player, String key, long amount);
}
