package me.bewf.mint.sync;

import java.util.List;

/**
 * Picks the player's own chest out of the chests around their spawn point.
 * <p>
 * The team chest sits within a few blocks of the spawn, usually to the side or in front and at
 * about the same height, so the search is wide but ignores chests at a very different height and
 * dislikes ones behind the player. A chest on another island is simply out of range.
 */
public final class ChestPicker {

    private ChestPicker() {
    }

    /** How far to look sideways, in blocks. Wide on purpose so odd maps still work. */
    public static final double MAX_HORIZONTAL = 20.0;
    /** How far above or below the player a chest may be. */
    public static final int MAX_VERTICAL = 4;

    private static final double BEHIND_PENALTY = 10.0;
    private static final double SIDEWAYS_BACK_PENALTY = 2.0;
    private static final double HEIGHT_WEIGHT = 0.75;

    /**
     * @param yawDegrees the player's yaw as Minecraft reports it, 0 meaning facing +Z
     * @param candidates chest block positions as {x, y, z}
     * @return the best candidate, or null when none is in range
     */
    public static int[] pick(double px, double py, double pz, double yawDegrees, List<int[]> candidates) {
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);

        int[] best = null;
        double bestScore = Double.MAX_VALUE;

        for (int[] c : candidates) {
            double dx = c[0] + 0.5 - px;
            double dz = c[2] + 0.5 - pz;
            int dy = c[1] - (int) Math.floor(py);
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dy) > MAX_VERTICAL || horizontal > MAX_HORIZONTAL) continue;

            double penalty = 0.0;
            if (horizontal > 0.5) {
                double facing = (dx * fx + dz * fz) / horizontal;
                if (facing < -0.5) penalty = BEHIND_PENALTY;
                else if (facing < 0.0) penalty = SIDEWAYS_BACK_PENALTY;
            }

            double score = horizontal + HEIGHT_WEIGHT * Math.abs(dy) + penalty;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** True when two chest positions touch, which is how the halves of a double chest sit. */
    public static boolean sameChest(int[] a, int[] b) {
        return a != null && b != null
                && Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) <= 1;
    }
}
