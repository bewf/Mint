package me.bewf.mint.sync;

import java.util.regex.Pattern;

/**
 * Timing for the quiet {@code /party list} check. It is asked at most once per cooldown while the
 * party state is unknown, and for a few seconds afterwards the lines of its reply are hidden so the
 * player does not see a party list they never asked for.
 */
public final class PartyQuery {

    public static final String COMMAND = "party list";
    public static final long COOLDOWN_MS = 10000L;
    public static final long HIDE_MS = 3000L;

    private static final Pattern DASHES = Pattern.compile("^[-▬=]{10,}$");

    private static final Pattern[] REPLY = {
            Pattern.compile("^\\s*$"),
            Pattern.compile("^You are not (?:currently )?in a party.*"),
            Pattern.compile("^You'll be partying with:.*"),
            Pattern.compile("^Party (?:Leader|Moderators|Members)(?: \\(\\d+\\))?(?::.*)?$"),
    };

    private boolean asked = false;
    private long askedAt = 0;
    private int dashLines = 0;

    /** True when the check should be sent now. */
    public boolean shouldAsk(PartyState state, long now) {
        if (state != PartyState.UNKNOWN) return false;
        return !asked || now - askedAt >= COOLDOWN_MS;
    }

    /** Call right after the check was sent. */
    public void asked(long now) {
        asked = true;
        askedAt = now;
        dashLines = 0;
    }

    /**
     * True for a chat line that belongs to the reply of a check that was just sent. The reply sits
     * between two lines of dashes, so hiding stops after the second one.
     */
    public boolean hides(String stripped, long now) {
        if (!asked || stripped == null || now - askedAt > HIDE_MS || dashLines >= 2) return false;
        if (DASHES.matcher(stripped).matches()) {
            dashLines++;
            return true;
        }
        for (Pattern p : REPLY) if (p.matcher(stripped).matches()) return true;
        return false;
    }

    public void reset() {
        asked = false;
        askedAt = 0;
        dashLines = 0;
    }
}
