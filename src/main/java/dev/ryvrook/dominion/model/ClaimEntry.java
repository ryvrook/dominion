package dev.ryvrook.dominion.model;

import java.util.UUID;

/** Ownership of one chunk. */
public record ClaimEntry(UUID factionId, long claimedAt, UUID claimedBy, String zoneId) {}
