package me.bewf.mint.sync;

/**
 * Collects team chest changes and hands them out in one go, so a burst of punch deposits becomes a
 * single party message instead of one per punch.
 * <p>
 * With the two argument constructor the window starts at the first change and is fixed. With the
 * three argument one the message goes out once nothing new has been added for {@code quietMs}, but
 * never later than {@code maxMs} after the first change, so punches a second apart still become one
 * message. Opposite changes cancel out and send nothing. A minimum gap between two messages keeps
 * the chat rate low however fast the player works.
 */
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
