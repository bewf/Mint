package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.List;

public final class WaypointLabel {
    public static final double HEIGHT_ABOVE_CHEST = 2.6;

    private WaypointLabel() {
    }

    public static final char[] LETTERS = {'i', 'g', 'd', 'e'};

    public static final int AMOUNT_CHEST = 0;
    public static final int AMOUNT_TOTAL = 1;

    public static final int MARKER_ICON = 0;
    public static final int MARKER_LETTER = 1;
    public static final int MARKER_NONE = 2;

    public static final class Part {
        public final String count;
        public final char letter;
        public final int resource;

        Part(String count, char letter, int resource) {
            this.count = count;
            this.letter = letter;
            this.resource = resource;
        }
    }

    public static List<Part> parts(int[] chest, int[] inventory, boolean[] show, int amount) {
        List<Part> out = new ArrayList<Part>();
        for (int r = 0; r < Delta.COUNT; r++) {
            if (!show[r]) continue;
            int v = chest[r] + (amount == AMOUNT_TOTAL ? inventory[r] : 0);
            if (v <= 0) continue;
            out.add(new Part(Numbers.compact(v), LETTERS[r], r));
        }
        return out;
    }

    public static List<List<Part>> rows(List<Part> parts) {
        List<List<Part>> out = new ArrayList<List<Part>>();
        int n = parts.size();
        if (n == 0) return out;
        if (n <= 2) {
            out.add(new ArrayList<Part>(parts));
            return out;
        }
        out.add(new ArrayList<Part>(parts.subList(2, n)));
        out.add(new ArrayList<Part>(parts.subList(0, 2)));
        return out;
    }
}
