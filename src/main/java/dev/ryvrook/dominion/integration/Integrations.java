package dev.ryvrook.dominion.integration;

import java.util.ArrayList;
import java.util.List;

/**
 * The integration state for admins. Registered as a service under its own class, so /fa integrations can read it
 * without knowing any sibling.
 */
public final class Integrations {
    private final List<Binding> bindings;

    Integrations(List<Binding> bindings) {
        this.bindings = bindings;
    }

    /** One line per sibling, in the order TrackerAPI, Feats, Atlas, Coinage. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Binding b : bindings) {
            out.add(b.statusLine());
        }
        return out;
    }

    /** bound, missing, off, too old, waiting, changed or failed for a sibling, or null when it is unknown. */
    public String state(Sibling sibling) {
        for (Binding b : bindings) {
            if (b.choice().sibling() == sibling) {
                return b.state();
            }
        }
        return null;
    }
}
