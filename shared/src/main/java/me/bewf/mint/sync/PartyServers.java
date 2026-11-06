package me.bewf.mint.sync;

/**
 * Which servers the team chest update may be sent on, and the command that sends a party chat message
 * there. The player lists them in the settings. Hypixel is filled in as the first one.
 */
public final class PartyServers {

    private PartyServers() {
    }

    /** How many servers the settings have room for. */
    public static final int SLOTS = 4;

    /**
     * @param address   the address of the server the player is on
     * @param addresses the listed server addresses
     * @param commands  the party command for each listed server, in the same order
     * @return the command to use here without its slash, or null when this server is not listed
     */
    public static String commandFor(String address, String[] addresses, String[] commands) {
        String host = host(address);
        if (host.isEmpty()) return null;
        for (int i = 0; i < addresses.length && i < commands.length; i++) {
            String entry = host(addresses[i]);
            if (entry.isEmpty() || !(host.equals(entry) || host.endsWith("." + entry))) continue;
            String command = command(commands[i]);
            if (command != null) return command;
        }
        return null;
    }

    /** Lower case, without a scheme, a port, a path or a trailing dot. */
    public static String host(String address) {
        if (address == null) return "";
        String h = address.trim().toLowerCase();
        int scheme = h.indexOf("://");
        if (scheme >= 0) h = h.substring(scheme + 3);
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int colon = h.indexOf(':');
        if (colon >= 0 && colon == h.lastIndexOf(':')) h = h.substring(0, colon);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    /** The command without its leading slash, or null when it is empty or has a line break. */
    public static String command(String raw) {
        if (raw == null) return null;
        String c = raw.trim();
        while (c.startsWith("/")) c = c.substring(1);
        c = c.trim();
        if (c.isEmpty() || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) return null;
        return c;
    }
}
