package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.text.Messages;

import java.util.function.Function;

/**
 * One sibling's life in Dominion. {@link #start} loads the bridge by name when the choice says so, {@link #poll}
 * notices a sibling that was reloaded, removed or added after start, and {@link #stop} unlinks. The bridge is the
 * only class that names a sibling type, so nothing here fails on a server without the sibling.
 */
final class Binding {
    static final String RELOAD = "/plugin reload Ryvrook:Dominion";

    private final Choice choice;
    private final Presence.Lookup lookup;
    private volatile Link link;
    private volatile boolean stopped;
    private volatile boolean changed;
    private volatile String failure;

    Binding(Choice choice, Presence.Lookup lookup) {
        this.choice = choice;
        this.lookup = lookup;
    }

    Choice choice() {
        return choice;
    }

    /** Loads the bridge. {@code loader} turns the bridge class name into a link and may throw. */
    void start(Function<String, Link> loader, Host host) {
        if (stopped || !choice.bind()) {
            return;
        }
        try {
            link = loader.apply(choice.sibling().bridgeClass());
        } catch (RuntimeException | LinkageError e) {
            failure = e.getClass().getSimpleName();
            host.warn(e, "Dominion could not link to " + choice.sibling().pluginName() + " " + choice.found().version()
                    + " and runs without it");
        }
    }

    /** Uses this link as if start had loaded it, for the tests. */
    void attach(Link l) {
        this.link = l;
    }

    /**
     * Every 5 seconds. Returns one line to log the first time the sibling's plugin object is not the one seen at
     * start, else null. A reloaded sibling has new classes in a new loader that the bridge cannot follow.
     */
    String poll() {
        if (stopped || choice.mode() == Mode.NEVER) {
            return null;
        }
        Link l = link;
        if (l != null) {
            try {
                l.poll();
            } catch (RuntimeException | LinkageError ignored) {
                // the bridge guards itself, this is a last net
            }
        }
        if (changed) {
            return null;
        }
        Presence.Found now;
        try {
            now = lookup.find(choice.sibling());
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        Object seen = choice.found().plugin();
        if (now.plugin() == seen) {
            return null;
        }
        changed = true;
        String name = choice.sibling().pluginName();
        if (seen == null) {
            return name + " " + now.version() + " was installed after Dominion started. Run " + RELOAD + " to link to it";
        }
        if (!choice.bind()) {
            return null;
        }
        return now.plugin() == null
                ? name + " was unloaded, so Dominion stopped using it until " + RELOAD + " runs with it back"
                : name + " was reloaded, so Dominion lost its link to it. Run " + RELOAD + " to link again";
    }

    void stop() {
        stopped = true;
        Link l = link;
        link = null;
        if (l != null) {
            try {
                l.stop();
            } catch (RuntimeException | LinkageError ignored) {
                // the sibling is already gone
            }
        }
    }

    boolean bound() {
        Link l = link;
        return !stopped && !changed && l != null && l.bound();
    }

    /** One short status word for the admin list. */
    String state() {
        return switch (choice.outcome()) {
            case OFF -> "off";
            case MISSING -> "missing";
            case TOO_OLD -> "too old";
            case BIND -> bound() ? "bound" : changed ? "changed" : failure != null ? "failed" : "waiting";
        };
    }

    /** The admin status line. */
    String statusLine() {
        String name = choice.sibling().pluginName();
        String setting = choice.sibling().settingKey();
        String mode = choice.mode().id();
        return switch (choice.outcome()) {
            case OFF -> Messages.get(IntegrationText.STATUS_OFF, "plugin", name, "setting", setting, "mode", mode);
            case MISSING -> Messages.get(IntegrationText.STATUS_MISSING, "plugin", name, "setting", setting, "mode", mode);
            case TOO_OLD -> Messages.get(IntegrationText.STATUS_TOO_OLD, "plugin", name, "version", choice.found().version(),
                    "minimum", choice.sibling().minimum(), "setting", setting, "mode", mode);
            case BIND -> {
                Link l = link;
                String v = l != null && l.version() != null ? l.version() : choice.found().version();
                if (bound()) {
                    yield Messages.get(IntegrationText.STATUS_BOUND, "plugin", name, "version", v, "setting", setting, "mode", mode);
                }
                if (changed) {
                    yield Messages.get(IntegrationText.STATUS_CHANGED, "plugin", name, "command", RELOAD);
                }
                yield failure != null ? Messages.get(IntegrationText.STATUS_FAILED, "plugin", name, "version", v)
                        : Messages.get(IntegrationText.STATUS_WAITING, "plugin", name, "version", v);
            }
        };
    }
}
