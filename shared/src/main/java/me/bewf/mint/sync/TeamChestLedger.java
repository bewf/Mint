package me.bewf.mint.sync;

public final class TeamChestLedger {
    public static final long RACE_MS = 3500;

    private final int[] counts = new int[Delta.COUNT];
    private final boolean[] known = new boolean[Delta.COUNT];
    private long lastLookMs = Long.MIN_VALUE / 2;

    public int get(int resource) {
        return counts[resource];
    }

    public boolean isKnown(int resource) {
        return known[resource];
    }

    public void look(int iron, int gold, int diamond, int emerald, long now) {
        counts[Delta.IRON] = iron;
        counts[Delta.GOLD] = gold;
        counts[Delta.DIAMOND] = diamond;
        counts[Delta.EMERALD] = emerald;
        for (int r = 0; r < Delta.COUNT; r++) known[r] = true;
        lastLookMs = now;
    }

    public void total(int resource, int total, long now) {
        counts[resource] = Math.max(0, total);
        known[resource] = true;
        lastLookMs = now;
    }

    public long sinceLook(long now) {
        return now - lastLookMs;
    }

    public boolean applyRemote(Delta d, long now, boolean chestOpen) {
        if (chestOpen) return false;
        // A teammate's message arrives after the change it describes. A look in the last few seconds
        // already includes that change, so applying the message too would count it twice.
        if (now - lastLookMs < RACE_MS) return false;
        for (int r = 0; r < Delta.COUNT; r++) {
            counts[r] = Math.max(0, counts[r] + d.get(r));
        }
        return true;
    }

    public void reset() {
        for (int r = 0; r < Delta.COUNT; r++) {
            counts[r] = 0;
            known[r] = false;
        }
        lastLookMs = Long.MIN_VALUE / 2;
    }
}
