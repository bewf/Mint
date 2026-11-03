package me.bewf.mint.sync;

import java.util.regex.Pattern;

/**
 * Follows whether the player is in a party by reading the chat Hypixel sends. Nothing is sent to
 * find out. When the state is unknown the first update is simply tried, and the server's
 * "not in a party" reply moves it to OUT.
 */
public enum PartyState {
    UNKNOWN, IN, OUT;

    private static final Pattern[] JOINED = {
            Pattern.compile("^You have joined .+ party!$"),
            Pattern.compile("^You'll be partying with:.*"),
            Pattern.compile("^Party (?:Leader|Moderators|Members)(?: \\(\\d+\\))?(?::.*)?$"),
            Pattern.compile("^.+ joined the party\\.$"),
            Pattern.compile("^Party > .*"),
    };

    private static final Pattern[] LEFT = {
            Pattern.compile("^You left the party\\.?$"),
            Pattern.compile("^The party was disbanded.*"),
            Pattern.compile("^You have been kicked from the party.*"),
            Pattern.compile("^.+ has disbanded the party!?$"),
            Pattern.compile("^You are not (?:currently )?in a party.*"),
            Pattern.compile("^You are not in a party.*"),
    };

    /** The state after one chat line (colour codes already removed). */
    public PartyState after(String stripped) {
        if (stripped == null) return this;
        for (Pattern p : LEFT) if (p.matcher(stripped).matches()) return OUT;
        for (Pattern p : JOINED) if (p.matcher(stripped).matches()) return IN;
        return this;
    }

    /** True when sending an update is worth trying. */
    public boolean canSend() {
        return this != OUT;
    }
}
