package me.bewf.mint.sync;

/**
 * Turns a point in the world into a point on the screen, for labels that float above a block.
 * Works on plain numbers so it behaves the same on every Minecraft version.
 */
public final class Projection {

    private Projection() {
    }

    /** Beyond this many blocks no label is drawn. */
    public static final double MAX_DISTANCE = 64.0;

    /**
     * @param yawDegrees   camera yaw as Minecraft reports it
     * @param pitchDegrees camera pitch, positive looking down
     * @param fovDegrees   the vertical field of view
     * @param width        screen width in the same units the result should use
     * @param height       screen height in the same units
     * @return {screenX, screenY, distance} or null when the point is behind the camera
     */
    public static double[] project(double ex, double ey, double ez,
                                   double yawDegrees, double pitchDegrees, double fovDegrees,
                                   double width, double height,
                                   double wx, double wy, double wz) {
        return project(ex, ey, ez, yawDegrees, pitchDegrees, fovDegrees, width, height, wx, wy, wz, null);
    }

    /**
     * Same as the version above, for a camera that is also shaken by walking (view bobbing).
     *
     * @param bob {moveRight, moveUp, rollDegrees, pitchDegrees} as the game applies them to the whole
     *            view after the camera is aimed, or null for none. The move is in blocks.
     */
    public static double[] project(double ex, double ey, double ez,
                                   double yawDegrees, double pitchDegrees, double fovDegrees,
                                   double width, double height,
                                   double wx, double wy, double wz, double[] bob) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);

        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);

        // up = right x forward
        double ux = 0.0 * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - 0.0 * fx;

        double dx = wx - ex;
        double dy = wy - ey;
        double dz = wz - ez;

        double depth = dx * fx + dy * fy + dz * fz;
        double right = dx * rx + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;

        if (bob != null) {
            // The game turns the view about its x axis, then its z axis, then slides it. In view space
            // x is right, y is up and z points back toward the player, so the depth is minus z.
            double z = -depth;
            double a = Math.toRadians(bob[3]);
            double y1 = up * Math.cos(a) - z * Math.sin(a);
            double z1 = up * Math.sin(a) + z * Math.cos(a);
            double b = Math.toRadians(bob[2]);
            double x2 = right * Math.cos(b) - y1 * Math.sin(b);
            double y2 = right * Math.sin(b) + y1 * Math.cos(b);
            right = x2 + bob[0];
            up = y2 + bob[1];
            depth = -z1;
        }
        if (depth <= 0.05) return null;

        double focal = (height / 2.0) / Math.tan(Math.toRadians(fovDegrees) / 2.0);
        double sx = width / 2.0 + right / depth * focal;
        double sy = height / 2.0 - up / depth * focal;
        return new double[]{sx, sy, Math.sqrt(dx * dx + dy * dy + dz * dz)};
    }

    /** Same as {@link #scaleFor(double)}, or always full size when shrinking is switched off. */
    public static float scaleFor(double distance, boolean shrink) {
        return shrink ? scaleFor(distance) : 1.0f;
    }

    /** Label size: full size up close, shrinking with distance, never vanishing completely. */
    public static float scaleFor(double distance) {
        double s = 6.0 / Math.max(distance, 1.0);
        if (s > 1.0) s = 1.0;
        if (s < 0.45) s = 0.45;
        return (float) s;
    }
}
