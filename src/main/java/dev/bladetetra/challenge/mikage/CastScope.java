package dev.bladetetra.challenge.mikage;

import java.util.ArrayDeque;

/** Resources owned by one cast; persistent arena resources have a separate owner. */
public final class CastScope {
    private final long id;
    private final ArrayDeque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;

    public CastScope(long id) { this.id = id; }
    public long id() { return id; }
    public boolean closed() { return closed; }

    public void own(Runnable release) {
        if (closed) release.run();
        else cleanup.addFirst(release);
    }

    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        while (!cleanup.isEmpty()) {
            try { cleanup.removeFirst().run(); }
            catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
