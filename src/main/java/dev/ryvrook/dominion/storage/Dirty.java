package dev.ryvrook.dominion.storage;

import java.util.concurrent.atomic.AtomicBoolean;

/** A tiny dirty flag shared by anything that autosaves. */
public final class Dirty {
    private final AtomicBoolean flag = new AtomicBoolean();

    public void mark() {
        flag.set(true);
    }

    public boolean clear() {
        return flag.getAndSet(false);
    }

    public boolean is() {
        return flag.get();
    }
}
