package me.bewf.mint.extras;

import me.bewf.mint.config.MintConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Sparkle {
    private Sparkle() {
    }

    private static final class Look {
        final String prefix;
        final int base;

        Look(String prefix, int base) {
            this.prefix = prefix;
            this.base = base;
        }
    }

    private static final Look LORD = new Look("Lord and Saviour ", 0xBE3FFF);
    private static final Look SCOOP = new Look("Scoop Master ", 0x9932CC);

    private static final Map<UUID, Look> PLAYERS = new HashMap<UUID, Look>();

    private static final Map<String, Look> NAMES = new ConcurrentHashMap<String, Look>();

    static {
        PLAYERS.put(uuid("2913ac77e515490a96eb5183963f6169"), LORD);
        PLAYERS.put(uuid("f299854cd6954a3cbaae6ab0e4b62dc8"), LORD);
        PLAYERS.put(uuid("e02ca8dea5d740ffaa85d29a83a2eefb"), SCOOP);
        NAMES.put("biwf", LORD);
        NAMES.put("bewf", LORD);
        NAMES.put("stalbet", SCOOP);
        rebuildPattern();
    }

    private static final Pattern LIST_HEADER = Pattern.compile(
            "(party (leader|members|moderators)|partying with)\\s*$", Pattern.CASE_INSENSITIVE);

    private static volatile Pattern namePattern;

    private static UUID uuid(String hex) {
        return UUID.fromString(hex.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    private static void rebuildPattern() {
        StringBuilder alt = new StringBuilder();
        for (String n : NAMES.keySet()) {
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(n));
        }
        namePattern = Pattern.compile(
                "(?:(?<![A-Za-z0-9_])|(?<=§[0-9a-fk-orA-FK-OR]))(" + alt + ")(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
    }

    public static void register() {
        EventManager.register(ChatEvent.Receive.class, event -> onChat(event));
        startNameLookup();
    }

    private static void startNameLookup() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Look> e : PLAYERS.entrySet()) {
                    try {
                        String id = e.getKey().toString().replace("-", "");
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                                "https://sessionserver.mojang.com/session/minecraft/profile/" + id).openConnection();
                        c.setConnectTimeout(5000);
                        c.setReadTimeout(5000);
                        c.setRequestProperty("User-Agent", "Mint");
                        if (c.getResponseCode() != 200) continue;
                        java.io.InputStreamReader r = new java.io.InputStreamReader(c.getInputStream(), "UTF-8");
                        try {
                            String name = new com.google.gson.JsonParser().parse(r).getAsJsonObject().get("name").getAsString();
                            NAMES.put(name.toLowerCase(), e.getValue());
                        } finally {
                            r.close();
                        }
                    } catch (Throwable ignored) {
                    }
                }
                rebuildPattern();
            }
        }, "Mint-NameLookup");
        t.setDaemon(true);
        t.start();
    }

    private static void onChat(ChatEvent.Receive event) {
        try {
            if (!MintConfig.INSTANCE.fancyNames || event.cancelled) return;
            Component message = event.getMessage();
            Component decorated = decorate(message);
            if (decorated != message) event.setMessage(decorated);
        } catch (Throwable ignored) {
        }
    }

    private static Component decorate(Component message) {
        StringBuilder flatBuilder = new StringBuilder();
        flatten(message, flatBuilder);
        String flat = flatBuilder.toString();
        if (!namePattern.matcher(flat).find()) return message;

        int limit = Integer.MAX_VALUE;
        int colon = flat.indexOf(':');
        if (colon >= 0 && !LIST_HEADER.matcher(flat.substring(0, colon)).find()) limit = colon;

        boolean[] changed = {false};
        Component out = rewrite(message, flat, limit, new int[]{0}, changed);
        return changed[0] ? out : message;
    }

    private static String ownText(Component c) {
        if (c instanceof TextComponent) return ((TextComponent) c).content();
        return Platform.i18n().getUnformattedText(c.children(Collections.<Component>emptyList()));
    }

    private static void flatten(Component c, StringBuilder out) {
        out.append(ownText(c));
        for (Component child : c.children()) flatten(child, out);
    }

    private static Component rewrite(Component c, String flat, int limit, int[] offset, boolean[] changed) {
        Component node;
        if (c instanceof TextComponent) {
            String text = ((TextComponent) c).content();
            node = split(text, c.style(), flat, limit, offset[0], changed);
            offset[0] += text.length();
        } else {
            node = c.children(Collections.<Component>emptyList());
            offset[0] += ownText(c).length();
        }
        List<Component> children = new ArrayList<Component>(node.children());
        for (Component child : c.children()) children.add(rewrite(child, flat, limit, offset, changed));
        return node.children(children);
    }

    private static Component split(String text, Style style, String flat, int limit, int start, boolean[] changed) {
        Matcher m = namePattern.matcher(text);
        int last = 0;
        List<Component> parts = new ArrayList<Component>();
        while (m.find()) {
            if (start + m.start() >= limit) break;
            Look look = NAMES.get(m.group(1).toLowerCase());
            if (look == null) continue;
            String before = flat.substring(0, Math.min(flat.length(), start + m.start()));
            if (endsWithTitle(before, look.prefix)) continue;
            if (m.start() > last) parts.add(Component.text(text.substring(last, m.start())));
            parts.add(Component.text(look.prefix + m.group(1), NamedTextColor.nearestTo(TextColor.color(look.base))));
            last = m.end();
        }
        if (parts.isEmpty()) return Component.text(text, style);

        changed[0] = true;
        if (last < text.length()) parts.add(Component.text(text.substring(last)));
        return Component.text("", style).children(parts);
    }

    private static boolean endsWithTitle(String before, String prefix) {
        String b = before.replaceAll("§.", "").replace(' ', ' ').replaceAll("\\s+", " ").trim().toLowerCase();
        String p = prefix.replace(' ', ' ').replaceAll("\\s+", " ").trim().toLowerCase();
        return b.endsWith(p);
    }
}
