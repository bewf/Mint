package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Remembers the party messages we sent ourselves. The server sends them back to us, and that echo
 * must not be counted a second time because the chest change is already in our own numbers.
 */
public final class RecentMessages {

    private static final class Entry {
        final String body;
        final long at;

        Entry(String body, long at) {
            this.body = body;
            this.at = at;
        }
    }

    private final long keepMs;
    private final List<Entry> sent = new ArrayList<Entry>();

    public RecentMessages(long keepMs) {
        this.keepMs = keepMs;
    }

    public void remember(String body, long now) {
        prune(now);
        sent.add(new Entry(body, now));
    }

    /** True once for each remembered message, so the echo is dropped and later identical text from someone else is not. */
    public boolean consumeIfOurs(String body, long now) {
        prune(now);
        for (Iterator<Entry> it = sent.iterator(); it.hasNext(); ) {
            if (it.next().body.equals(body)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public void clear() {
        sent.clear();
    }

    private void prune(long now) {
        for (Iterator<Entry> it = sent.iterator(); it.hasNext(); ) {
            if (now - it.next().at > keepMs) it.remove();
        }
    }
}
