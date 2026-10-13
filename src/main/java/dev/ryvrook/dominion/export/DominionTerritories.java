package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.api.Territory;
import dev.ryvrook.dominion.api.TerritoryProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Dominion's own territory provider for map mods. */
public final class DominionTerritories implements TerritoryProvider {
    private final MapData data;

    DominionTerritories(MapData data) {
        this.data = data;
    }

    @Override
    public String id() {
        return "dominion";
    }

    @Override
    public Collection<Territory> territories(String world) {
        List<Territory> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        for (MapData.Zone z : data.zones(world)) {
            out.add(new Territory(z.faction().id(), z.faction().tag(), z.faction().name(), z.faction().color(), world,
                    z.chunks(), z.raidable(), z.breached().length > 0, z.faction().type().name()));
        }
        return out;
    }

    @Override
    public Collection<PointOfInterest> points(String world) {
        return world == null ? List.of() : data.points(world);
    }
}
