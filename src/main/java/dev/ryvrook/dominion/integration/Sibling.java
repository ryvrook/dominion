package dev.ryvrook.dominion.integration;

/** The sibling mods Dominion can bind to, with their plugin ids, the oldest version bound and the setting. */
public enum Sibling {
    TRACKER("TrackerAPI", "1.0.0", "integrations.tracker", "dev.ryvrook.dominion.integration.tracker.TrackerBridge"),
    FEATS("Feats", "1.5.0", "integrations.feats", "dev.ryvrook.dominion.integration.feats.FeatsBridge"),
    ATLAS("Atlas", "1.6.0", "integrations.atlas", "dev.ryvrook.dominion.integration.atlas.AtlasBridge");

    public static final String GROUP = "Ryvrook";

    private final String pluginName;
    private final String minimum;
    private final String settingKey;
    private final String bridgeClass;

    Sibling(String pluginName, String minimum, String settingKey, String bridgeClass) {
        this.pluginName = pluginName;
        this.minimum = minimum;
        this.settingKey = settingKey;
        this.bridgeClass = bridgeClass;
    }

    /** The plugin name in the manifest, such as TrackerAPI. Also the name shown to admins. */
    public String pluginName() {
        return pluginName;
    }

    public String minimum() {
        return minimum;
    }

    public String settingKey() {
        return settingKey;
    }

    /** The one class that uses the sibling's types, loaded by name only after the presence check. */
    public String bridgeClass() {
        return bridgeClass;
    }
}
