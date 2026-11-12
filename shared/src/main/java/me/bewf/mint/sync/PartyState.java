package me.bewf.mint.sync;

import java.util.regex.Pattern;

/**
 * Follows whether the player is in a party by reading the chat Hypixel sends. While the state is
 * unknown on Hypixel nothing is sent to the party; {@link PartyQuery} asks the server quietly with
 * {@code /party list} and the reply moves the state to IN or OUT.
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

    // Someone else leaving may or may not end the party, so the state has to be checked again
    private static final Pattern[] RECHECK = {
            Pattern.compile("^.+ has left the party\\.$"),
            Pattern.compile("^.+ (?:was|has been) removed from the party\\.$"),
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
        for (Pattern p : RECHECK) if (p.matcher(stripped).matches()) return UNKNOWN;
        for (Pattern p : JOINED) if (p.matcher(stripped).matches()) return IN;
        return this;
    }

    /**
     * True when an update may be sent.
     *
     * @param needsAnswer true on a server where the party can be checked (Hypixel): an unknown state
     *                    does not send until the check has answered
     */
    public boolean canSend(boolean needsAnswer) {
        return this == IN || (this == UNKNOWN && !needsAnswer);
    }
}
