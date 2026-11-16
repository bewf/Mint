package me.bewf.mint.sync;

import java.util.List;

public final class ChestPicker {
    private ChestPicker() {
    }

    public static final double MAX_HORIZONTAL = 20.0;
    public static final int MAX_VERTICAL = 4;

    private static final double BEHIND_PENALTY = 10.0;
    private static final double SIDEWAYS_BACK_PENALTY = 2.0;
    private static final double HEIGHT_WEIGHT = 0.75;

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

    public static boolean sameChest(int[] a, int[] b) {
        return a != null && b != null
                && Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) <= 1;
    }
}
