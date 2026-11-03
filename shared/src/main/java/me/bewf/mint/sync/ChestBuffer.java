package me.bewf.mint.sync;

/**
 * Collects team chest changes and hands them out in one go, so a burst of punch deposits becomes a
 * single party message instead of one per punch.
 * <p>
 * The window starts at the first change and is fixed, so a long burst still goes out after
 * {@code windowMs}. Opposite changes cancel out and send nothing. A minimum gap between two
 * messages keeps the chat rate low however fast the player works.
 */
public final class ChestBuffer {

    private final long windowMs;
    private final long minGapMs;

    private final Delta pending = new Delta();
    private long firstMs = -1;
    private long lastEmitMs = Long.MIN_VALUE / 2;
    private boolean flushRequested = false;

    public ChestBuffer(long windowMs, long minGapMs) {
        this.windowMs = windowMs;
        this.minGapMs = minGapMs;
    }

    public void add(Delta d, long now) {
        if (d == null || d.isZero()) return;
        if (firstMs < 0) firstMs = now;
        pending.addAll(d);
    }

    /** Asks for the pending changes to go out as soon as the minimum gap allows. */
    public void requestFlush() {
        if (firstMs >= 0) flushRequested = true;
    }

    public boolean hasPending() {
        return firstMs >= 0;
    }

    /** Returns the changes to send now, or null when it is not time yet or everything cancelled out. */
    public Delta poll(long now) {
        if (firstMs < 0) return null;
        boolean windowOver = now - firstMs >= windowMs;
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
        flushRequested = false;
        lastEmitMs = Long.MIN_VALUE / 2;
    }
}
