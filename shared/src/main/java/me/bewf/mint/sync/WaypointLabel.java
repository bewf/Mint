package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.List;

/**
 * What a floating chest label says: the resources that are switched on and not zero, each as a count
 * with a marker next to it. The count is either what is in the chest, or the chest plus your inventory.
 * {@link #rows} says how they are laid out.
 */
public final class WaypointLabel {

    private WaypointLabel() {
    }

    public static final char[] LETTERS = {'i', 'g', 'd', 'e'};

    /** The label shows what is in the chest. */
    public static final int AMOUNT_CHEST = 0;
    /** The label shows the chest plus the inventory. */
    public static final int AMOUNT_TOTAL = 1;

    /** What goes next to each amount: the item, a one letter tag, or nothing. */
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

    /**
     * @param chest     what is in the chest, in {@link Delta} order (iron, gold, diamond, emerald)
     * @param inventory what the player is carrying, same order
     * @param show      which resources may appear, same order
     * @param amount    {@link #AMOUNT_CHEST} or {@link #AMOUNT_TOTAL}
     */
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

    /**
     * Lays the parts out in rows, top row first. One or two go side by side. Three put two on the bottom and
     * one on top. Four put iron and gold on the bottom and diamond and emerald on top. The bottom row stays
     * where a single row would be and the rest stack above it. The parts keep their resource order, so
     * a resource never swaps places with a later one.
     */
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
