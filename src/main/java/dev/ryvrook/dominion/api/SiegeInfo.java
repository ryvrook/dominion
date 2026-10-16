package dev.ryvrook.dominion.api;

import java.util.UUID;

/**
 * A siege as plain values, copied when the API is asked, so it never changes under the caller. phase is the
 * {@code Siege.Phase} name, CAPTURING or BREACHED while it runs and FAILED, DEFENDED or EXPIRED after. phaseEndsAt
 * and startedAt are epoch millis. The banner stands at x, y, z in the chunk chunkX, chunkZ of world. A tag is null
 * when that faction was disbanded since, and startedBy is null when the server started it.
 */
public record SiegeInfo(UUID id, UUID attackerId, String attackerTag, UUID defenderId, String defenderTag,
                        String world, long chunkIndex, int chunkX, int chunkZ, double x, double y, double z,
                        String phase, long phaseEndsAt, long startedAt, UUID startedBy, boolean active,
                        int blocksBroken, int containersOpened, int attackerDeaths, int defenderDeaths) {
}
