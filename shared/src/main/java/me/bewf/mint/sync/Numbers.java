package me.bewf.mint.sync;

/** Number formatting shared by the labels, matching the HUD's compact numbers. */
public final class Numbers {

    private Numbers() {
    }

    public static String compact(int n) {
        if (n < 1000) return Integer.toString(n);
        double k = n / 1000.0;
        if (k < 10.0) {
            double oneDecimal = Math.round(k * 10.0) / 10.0;
            String s = Double.toString(oneDecimal);
            if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
            return s + "k";
        }
        return Math.round(k) + "k";
    }
}
