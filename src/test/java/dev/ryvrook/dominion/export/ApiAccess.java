package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionApi;

/** The API over a test context, for tests outside the export package. */
public final class ApiAccess {
    private ApiAccess() {}

    public static DominionApi of(DominionContext ctx) {
        return new ApiImpl(ctx, null);
    }
}
