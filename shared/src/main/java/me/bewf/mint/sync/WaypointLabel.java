package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.List;

/**
 * What a floating chest label says: only the resources that are actually in the chest, as a count
 * followed by a one letter tag. The ender chest label leaves out emeralds.
 */
public final class WaypointLabel {

    private WaypointLabel() {
    }

    public static final char[] LETTERS = {'i', 'g', 'd', 'e'};

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

    public static List<Part> parts(int iron, int gold, int diamond, int emerald, boolean includeEmerald) {
        int[] v = {iron, gold, diamond, emerald};
        List<Part> out = new ArrayList<Part>();
        for (int r = 0; r < Delta.COUNT; r++) {
            if (r == Delta.EMERALD && !includeEmerald) continue;
            if (v[r] <= 0) continue;
            out.add(new Part(Numbers.compact(v[r]), LETTERS[r], r));
        }
        return out;
    }
}
