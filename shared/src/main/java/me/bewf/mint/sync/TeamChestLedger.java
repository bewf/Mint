package me.bewf.mint.sync;

/**
 * The team chest totals this player believes in. A look inside the chest (or the total printed by a
 * punch deposit) is the truth. Messages from teammates only move the numbers in between.
 * <p>
 * A teammate's message arrives a moment after the change it describes. If this player looked at the
 * chest in that time, the look already includes the change, so applying the message would count it
 * twice. Messages are therefore ignored while the chest is open and for {@link #RACE_MS} after the
 * last look. The worst case is one missed update that the next look fixes.
 */
public final class TeamChestLedger {

    /** Longer than the sender's buffer window plus minimum gap plus a slow chat round trip. */
    public static final long RACE_MS = 3500;

    private final int[] counts = new int[Delta.COUNT];
    private long lastLookMs = Long.MIN_VALUE / 2;

    public int get(int resource) {
        return counts[resource];
    }

    /** The player looked at the whole chest. */
    public void look(int iron, int gold, int diamond, int emerald, long now) {
        counts[Delta.IRON] = iron;
        counts[Delta.GOLD] = gold;
        counts[Delta.DIAMOND] = diamond;
        counts[Delta.EMERALD] = emerald;
        lastLookMs = now;
    }

    /** The server told the player the new total of one resource. */
    public void total(int resource, int total, long now) {
        counts[resource] = Math.max(0, total);
        lastLookMs = now;
    }

    /** Applies a teammate's update. Returns false when it was ignored. */
    public boolean applyRemote(Delta d, long now, boolean chestOpen) {
        if (chestOpen) return false;
        if (now - lastLookMs < RACE_MS) return false;
        for (int r = 0; r < Delta.COUNT; r++) {
            counts[r] = Math.max(0, counts[r] + d.get(r));
        }
        return true;
    }

    public void reset() {
        for (int r = 0; r < Delta.COUNT; r++) counts[r] = 0;
        lastLookMs = Long.MIN_VALUE / 2;
    }
}
