package me.bewf.mint.sync;

public final class Projection {
    private Projection() {
    }

    public static final double MAX_DISTANCE = 64.0;

    public static double[] project(double ex, double ey, double ez,
                                   double yawDegrees, double pitchDegrees, double fovDegrees,
                                   double width, double height,
                                   double wx, double wy, double wz) {
        return project(ex, ey, ez, yawDegrees, pitchDegrees, fovDegrees, width, height, wx, wy, wz, null);
    }

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

    public static float scaleFor(double distance, boolean shrink) {
        return shrink ? scaleFor(distance) : 1.0f;
    }

    public static float scaleFor(double distance) {
        double t = (distance - 10.0) / 50.0;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;
        return (float) (1.0 - 0.55 * t);
    }
}
