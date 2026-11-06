package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The party chat line Mint uses to share team chest changes, for example
 * {@code [Mint | Team Chest: +10 iron, -4 dia]}.
 * <p>
 * The sender is not part of the body. Hypixel already prefixes party chat with the sender's name,
 * so the receiver reads it from there, which also stops anyone faking another player's update.
 * The optional {@code #n} counter keeps two identical updates from being swallowed by the server's
 * duplicate message filter, and lets a receiver drop a repeat.
 */
public final class TeamChestMessage {

    private TeamChestMessage() {
    }

    public static final String[] TAGS = {"iron", "gold", "dia", "em"};

    /** Largest single number accepted. A double chest holds well under this per resource. */
    public static final int MAX_ABS = 9999;

    public static final String PREFIX = "[Mint | Team Chest: ";

    private static final String ITEM = "[+-]\\d{1,4} (?:iron|gold|dia|em)";
    private static final Pattern BODY = Pattern.compile(
            "^\\[Mint \\| Team Chest: (" + ITEM + "(?:, " + ITEM + "){0,3})(?: #(\\d{1,2}))?\\]$");
    private static final Pattern ITEM_PATTERN = Pattern.compile("([+-])(\\d{1,4}) (iron|gold|dia|em)");

    // "Party > [MVP+] name: text" or "Party > name: text", colour codes already removed
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16}): (.*)$");

    public static final class Parsed {
        public final Delta delta;
        /** The rolling counter, or -1 when the message carried none. */
        public final int seq;

        Parsed(Delta delta, int seq) {
            this.delta = delta;
            this.seq = seq;
        }
    }

    public static final class PartyLine {
        public final String sender;
        public final String body;

        PartyLine(String sender, String body) {
            this.sender = sender;
            this.body = body;
        }
    }

    /** Builds the message body, or null when there is nothing to report. */
    public static String format(Delta d, int seq) {
        if (d == null || d.isZero()) return null;
        StringBuilder sb = new StringBuilder(PREFIX);
        boolean first = true;
        for (int r = 0; r < Delta.COUNT; r++) {
            int v = d.get(r);
            if (v == 0) continue;
            if (Math.abs(v) > MAX_ABS) v = v > 0 ? MAX_ABS : -MAX_ABS;
            if (!first) sb.append(", ");
            first = false;
            sb.append(v > 0 ? '+' : '-').append(Math.abs(v)).append(' ').append(TAGS[r]);
        }
        if (seq >= 0) sb.append(" #").append(seq % 100);
        return sb.append(']').toString();
    }

    /** Parses a message body. Returns null for anything that is not exactly a Mint update. */
    public static Parsed parse(String body) {
        if (body == null) return null;
        Matcher m = BODY.matcher(body.trim());
        if (!m.matches()) return null;

        Delta d = new Delta();
        boolean[] seen = new boolean[Delta.COUNT];
        Matcher items = ITEM_PATTERN.matcher(m.group(1));
        while (items.find()) {
            int r = indexOf(items.group(3));
            if (seen[r]) return null;
            seen[r] = true;
            int n = Integer.parseInt(items.group(2));
            if (n == 0) return null;
            d.add(r, items.group(1).equals("-") ? -n : n);
        }
        int seq = m.group(2) == null ? -1 : Integer.parseInt(m.group(2));
        return new Parsed(d, seq);
    }

    /** Splits a party chat line into sender and text, or returns null when it is not party chat. */
    public static PartyLine party(String stripped) {
        if (stripped == null) return null;
        Matcher m = PARTY_LINE.matcher(stripped);
        if (!m.matches()) return null;
        return new PartyLine(m.group(1), m.group(2));
    }

    private static int indexOf(String tag) {
        for (int i = 0; i < TAGS.length; i++) if (TAGS[i].equals(tag)) return i;
        return -1;
    }

    /** Colours for a shown update. All values are 0xRRGGBB. */
    public static final class Palette {
        public int frame = 0xAAAAAA;
        public int plus = 0x55FF55;
        public int minus = 0xFF5555;
        public int[] resource = {0xFFFFFF, 0xFFAA00, 0x55FFFF, 0x55FF55};
    }

    public static final class Segment {
        public final String text;
        public final int rgb;

        Segment(String text, int rgb) {
            this.text = text;
            this.rgb = rgb;
        }
    }

    /**
     * Splits a valid message body into coloured pieces. The pieces joined together are the body
     * again, so a loader can build its own text component from them. Returns null when the body is
     * not a Mint update.
     */
    public static List<Segment> segments(String body, Palette p) {
        if (parse(body) == null) return null;
        List<Segment> out = new ArrayList<Segment>();
        String trimmed = body.trim();
        int itemsStart = PREFIX.length();
        out.add(new Segment(PREFIX, p.frame));

        Matcher m = BODY.matcher(trimmed);
        m.matches();
        String items = m.group(1);
        int pos = 0;
        Matcher im = ITEM_PATTERN.matcher(items);
        while (im.find()) {
            if (im.start() > pos) out.add(new Segment(items.substring(pos, im.start()), p.frame));
            boolean minus = im.group(1).equals("-");
            out.add(new Segment(im.group(1) + im.group(2), minus ? p.minus : p.plus));
            out.add(new Segment(" ", p.frame));
            out.add(new Segment(im.group(3), p.resource[indexOf(im.group(3))]));
            pos = im.end();
        }
        String tail = trimmed.substring(itemsStart + items.length());
        if (!tail.isEmpty()) out.add(new Segment(tail, p.frame));
        return out;
    }
}
