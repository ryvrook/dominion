package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.service.ClaimService;

/** The claim service over a test context, for tests outside the land package. */
public final class ClaimAccess {
    private ClaimAccess() {}

    public static ClaimService of(DominionContext ctx) {
        return new ClaimServiceImpl(ctx);
    }
}
