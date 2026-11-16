package me.bewf.mint.sync;

import java.util.regex.Pattern;

public enum PartyState {
    UNKNOWN, IN, OUT;

    private static final Pattern[] JOINED = {
            Pattern.compile("^You have joined .+ party!$"),
            Pattern.compile("^You'll be partying with:.*"),
            Pattern.compile("^Party (?:Leader|Moderators|Members)(?: \\(\\d+\\))?(?::.*)?$"),
            Pattern.compile("^.+ joined the party\\.$"),
            Pattern.compile("^Party > .*"),
    };

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

    public PartyState after(String stripped) {
        if (stripped == null) return this;
        for (Pattern p : LEFT) if (p.matcher(stripped).matches()) return OUT;
        for (Pattern p : RECHECK) if (p.matcher(stripped).matches()) return UNKNOWN;
        for (Pattern p : JOINED) if (p.matcher(stripped).matches()) return IN;
        return this;
    }

    public boolean canSend(boolean needsAnswer) {
        return this == IN || (this == UNKNOWN && !needsAnswer);
    }
}
