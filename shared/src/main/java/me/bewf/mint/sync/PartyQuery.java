package me.bewf.mint.sync;

import java.util.regex.Pattern;

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

    public boolean shouldAsk(PartyState state, long now) {
        if (state != PartyState.UNKNOWN) return false;
        return !asked || now - askedAt >= COOLDOWN_MS;
    }

    public void asked(long now) {
        asked = true;
        askedAt = now;
        dashLines = 0;
    }

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
