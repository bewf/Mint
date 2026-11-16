package me.bewf.mint.sync;

public final class PartyServers {
    private PartyServers() {
    }

    public static final int SLOTS = 4;

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

    public static String command(String raw) {
        if (raw == null) return null;
        String c = raw.trim();
        while (c.startsWith("/")) c = c.substring(1);
        c = c.trim();
        if (c.isEmpty() || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) return null;
        return c;
    }

    public static boolean isHypixel(String address) {
        String host = host(address);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
