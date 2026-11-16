package me.bewf.mint.sync;

public final class ChestBuffer {
    private final long windowMs;
    private final long quietMs;
    private final long minGapMs;

    private final Delta pending = new Delta();
    private long firstMs = -1;
    private long lastAddMs = -1;
    private long lastEmitMs = Long.MIN_VALUE / 2;
    private boolean flushRequested = false;

    public ChestBuffer(long windowMs, long minGapMs) {
        this.windowMs = windowMs;
        this.quietMs = Long.MAX_VALUE;
        this.minGapMs = minGapMs;
    }

    public ChestBuffer(long quietMs, long maxMs, long minGapMs) {
        this.windowMs = maxMs;
        this.quietMs = quietMs;
        this.minGapMs = minGapMs;
    }

    public void add(Delta d, long now) {
        if (d == null || d.isZero()) return;
        if (firstMs < 0) firstMs = now;
        lastAddMs = now;
        pending.addAll(d);
    }

    public void requestFlush() {
        if (firstMs >= 0) flushRequested = true;
    }

    public boolean hasPending() {
        return firstMs >= 0;
    }

    public Delta poll(long now) {
        if (firstMs < 0) return null;
        // Goes out after a quiet gap, or after windowMs at the latest so a long run of deposits still sends
        boolean windowOver = now - firstMs >= windowMs
                || (quietMs != Long.MAX_VALUE && now - lastAddMs >= quietMs);
        if (!windowOver && !flushRequested) return null;
        if (now - lastEmitMs < minGapMs) return null;

        Delta out = pending.copy();
        pending.clear();
        firstMs = -1;
        flushRequested = false;
        if (out.isZero()) return null;
        lastEmitMs = now;
        return out;
    }

    public void reset() {
        pending.clear();
        firstMs = -1;
        lastAddMs = -1;
        flushRequested = false;
        lastEmitMs = Long.MIN_VALUE / 2;
    }
}
