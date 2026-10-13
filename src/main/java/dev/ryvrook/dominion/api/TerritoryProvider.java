package dev.ryvrook.dominion.api;

import java.util.Collection;

/** Implemented by Dominion and by adapters for other claim systems. Consumed by map mods. */
public interface TerritoryProvider {
    String id();

    Collection<Territory> territories(String world);

    Collection<PointOfInterest> points(String world);
}
