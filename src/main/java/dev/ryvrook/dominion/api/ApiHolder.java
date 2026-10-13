package dev.ryvrook.dominion.api;

/** Holds the live API instance. Set by the plugin on start. */
public final class ApiHolder {
    private static volatile DominionApi instance;

    private ApiHolder() {}

    public static DominionApi get() {
        DominionApi api = instance;
        if (api == null) {
            throw new IllegalStateException("Dominion is not started");
        }
        return api;
    }

    public static void set(DominionApi api) {
        instance = api;
    }
}
