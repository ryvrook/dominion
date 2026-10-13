package dev.ryvrook.dominion.api;

import java.util.UUID;

/** A territory as seen by map consumers such as the web map. */
public record Territory(UUID ownerId, String ownerTag, String ownerName, String color, String world,
                        long[] chunkIndexes, boolean raidable, boolean breached, String type) {}
