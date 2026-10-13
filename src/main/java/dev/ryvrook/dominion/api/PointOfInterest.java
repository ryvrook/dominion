package dev.ryvrook.dominion.api;

import java.util.UUID;

/** A point drawn on maps. Kind is home, warp, siege, hill, outpost or envoy. */
public record PointOfInterest(String id, String kind, String label, String world, double x, double y, double z,
                              UUID ownerId, String color, long expiresAt) {}
