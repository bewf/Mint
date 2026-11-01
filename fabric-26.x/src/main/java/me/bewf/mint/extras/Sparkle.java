package me.bewf.mint.extras;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.bewf.mint.config.MintConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class Sparkle {
    // A negative shine2 means a single sweep.
    private record Look(String prefix, int base, int shine, int shine2) {
    }

    private static final Look LORD = new Look("Lord and Saviour ", 0xBE3FFF, 0xFFF5FF, -1);
    private static final Look SCOOP = new Look("Scoop Master ", 0x9932CC, 0xFFF5FF, -1);

    private static final Map<UUID, Look> PLAYERS = Map.of(
            uuid("2913ac77e515490a96eb5183963f6169"), LORD,
            uuid("f299854cd6954a3cbaae6ab0e4b62dc8"), LORD,
            uuid("e02ca8dea5d740ffaa85d29a83a2eefb"), SCOOP
    );

    // Also matched by name in text, for servers where the UUID does not match
    private static final Map<String, Look> NAMES = new ConcurrentHashMap<>(Map.of(
            "biwf", LORD,
            "bewf", LORD,
            "stalbet", SCOOP
    ));

    private record Cached(String name, long builtAt, Component component) {
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();

    private static UUID uuid(String hex) {
        return UUID.fromString(hex.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    private static final long CACHE_MS = 50L;
    private static final long PERIOD_MS = 2400L;
    private static final long SHINE_MS = 480L;

    private static final float SHINE_RADIUS = 1.5F;

    private Sparkle() {
    }

    public static boolean wants(UUID uuid) {
        return uuid != null && PLAYERS.containsKey(uuid) && MintConfig.INSTANCE.fancyNames;
    }

    public static Component name(UUID uuid, String playerName) {
        Look look = PLAYERS.get(uuid);
        if (look == null) return Component.literal(playerName);
        return render(look, playerName);
    }

    private static Component render(Look look, String playerName) {
        long now = System.currentTimeMillis();

        String key = playerName.toLowerCase();
        Cached hit = CACHE.get(key);
        if (hit != null && playerName.equals(hit.name()) && now - hit.builtAt() < CACHE_MS) {
            return hit.component();
        }

        String text = look.prefix() + playerName;
        MutableComponent out = Component.empty();
        int length = text.length();

        long phase = now % PERIOD_MS;
        boolean shining = phase < SHINE_MS;

        float trail = look.shine2() >= 0 ? 4.0F : 0.0F;
        float progress = shining ? phase / (float) SHINE_MS : 0.0F;
        float center = -SHINE_RADIUS + progress * (length + SHINE_RADIUS * 2.0F + trail);

        // One component per run of equal colour, not per letter
        int runStart = 0;
        int runColor = -1;

        for (int i = 0; i < length; i++) {
            int color = look.base();

            if (shining) {
                color = blend(color, look.shine(), sweep(i, center));
                if (look.shine2() >= 0) {
                    color = blend(color, look.shine2(), sweep(i, center - trail));
                }
            }

            if (i == 0) {
                runColor = color;
            } else if (color != runColor) {
                out.append(Component.literal(text.substring(runStart, i))
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(runColor))));
                runStart = i;
                runColor = color;
            }
        }
        out.append(Component.literal(text.substring(runStart))
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(runColor))));

        CACHE.put(key, new Cached(playerName, now, out));

        return out;
    }

    private static float sweep(int index, float center) {
        float strength = Math.max(0.0F, 1.0F - Math.abs(index - center) / SHINE_RADIUS);
        return strength * strength * (3.0F - 2.0F * strength);
    }

    private static Component staticName(Look look, String playerName) {
        return Component.literal(look.prefix() + playerName)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(look.base())));
    }

    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set<String> LOGGED_CALLERS = ConcurrentHashMap.newKeySet();

    private enum Reader {
        GAME, SKYHANNI, OTHER
    }


    private static Reader reader(String hookedMethod) {
        try {
            return WALKER.walk(frames -> {
                boolean seen = false;
                Reader found = null;
                for (StackWalker.StackFrame f : (Iterable<StackWalker.StackFrame>) frames.limit(16)::iterator) {
                    boolean hooked = f.getMethodName().equals(hookedMethod);
                    if (!seen) {
                        seen = hooked;
                        continue;
                    }
                    if (hooked) continue;
                    String owner = f.getClassName();
                    if (owner.startsWith("at.hannibal2.skyhanni.")) return Reader.SKYHANNI;
                    if (found == null) {
                        found = owner.startsWith("net.minecraft.") ? Reader.GAME : Reader.OTHER;
                        if (found == Reader.GAME) return found;
                        if (LOGGED_CALLERS.add(owner)) {
                            System.out.println("[Mint] " + hookedMethod + " is read by " + owner + ", using Minecraft colours for it");
                        }
                    }
                }
                return found == null ? Reader.GAME : found;
            });
        } catch (Throwable t) {
            return Reader.GAME;
        }
    }

    public static Component smartName(UUID uuid, String playerName, String hookedMethod) {
        Look look = PLAYERS.get(uuid);
        if (look == null) return Component.literal(playerName);
        Reader reader = reader(hookedMethod);
        return reader == Reader.GAME ? render(look, playerName) : legacyName(look, playerName, reader == Reader.OTHER);
    }

    public static Component tabName(Component message) {
        Reader reader = reader("getNameForDisplay");
        return replaceNames(message, reader == Reader.GAME, false, reader);
    }

    // SkyHanniy's advanced player list splits each tab line on spaces and takes the first word as the players name so it only colours the first block.
    public static Component restoreAfterSkyHanni(Component line) {
        if (!MintConfig.INSTANCE.fancyTabList) return line;
        return replaceNames(line, true, false, Reader.GAME);
    }

    private static Component legacyName(Look look, String playerName, boolean withPrefix) {
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (ChatFormatting f : ChatFormatting.values()) {
            TextColor legacy = TextColor.fromLegacyFormat(f);
            if (legacy == null) continue;
            int c = legacy.getValue();
            long dr = ((c >> 16) & 255) - ((look.base() >> 16) & 255);
            long dg = ((c >> 8) & 255) - ((look.base() >> 8) & 255);
            long db = (c & 255) - (look.base() & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = f;
            }
        }
        return Component.literal(withPrefix ? look.prefix() + playerName : playerName).withStyle(best);
    }

    public static Component decorateChat(Component message) {
        return replaceNames(message, false, true, Reader.GAME);
    }

    private static volatile Pattern namePattern;

    static {
        rebuildPattern();
    }

    private static void rebuildPattern() {
        StringBuilder alt = new StringBuilder();
        for (String n : NAMES.keySet()) {
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(n));
        }
        // A name may sit right behind a raw colour code (SkyHanni builds "§dbewf"), so a code counts as a boundary
        namePattern = Pattern.compile("(?:(?<![A-Za-z0-9_])|(?<=\u00A7[0-9A-Fa-fK-Ok-oRr]))(" + alt + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    // Resolves the current name of each listed UUID so renamed accounts keep working
    public static void startNameLookup() {
        Thread t = new Thread(() -> {
            for (Map.Entry<UUID, Look> e : PLAYERS.entrySet()) {
                try {
                    String id = e.getKey().toString().replace("-", "");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) java.net.URI
                            .create("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
                            .toURL().openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestProperty("User-Agent", "Mint");
                    if (c.getResponseCode() != 200) continue;
                    try (java.io.InputStreamReader r = new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                        String name = com.google.gson.JsonParser.parseReader(r).getAsJsonObject().get("name").getAsString();
                        NAMES.put(name.toLowerCase(), e.getValue());
                        System.out.println("[Mint] resolved " + id + " -> " + name);
                    }
                } catch (Throwable ignored) {
                }
            }
            rebuildPattern();
        }, "Mint-NameLookup");
        t.setDaemon(true);
        t.start();
    }

    private static final Pattern LIST_HEADER = Pattern.compile(
            "(party (leader|members|moderators)|partying with)\\s*$", Pattern.CASE_INSENSITIVE);

    public static Component replaceNames(Component message, boolean animated, boolean senderOnly) {
        return replaceNames(message, animated, senderOnly, Reader.GAME);
    }

    private static Component replaceNames(Component message, boolean animated, boolean senderOnly, Reader reader) {
        try {
            if (message == null || !MintConfig.INSTANCE.fancyNames) return message;

            String flat = message.getString();
            if (!namePattern.matcher(flat).find()) return message;

            // Text after the first colon is message body
            int limit = Integer.MAX_VALUE;
            if (senderOnly) {
                int colon = flat.indexOf(':');
                if (colon >= 0 && !LIST_HEADER.matcher(flat.substring(0, colon)).find()) limit = colon;
            }
            final int senderEnd = limit;

            MutableComponent out = Component.empty();
            boolean[] changed = {false};
            int[] offset = {0};
            message.visit((style, text) -> {
                Matcher m = namePattern.matcher(text);
                int last = 0;
                while (m.find()) {
                    if (offset[0] + m.start() >= senderEnd) break;
                    Look look = NAMES.get(m.group(1).toLowerCase());
                    // Check the whole line so a prefix split across several runs is still seen
                    String before = flat.substring(0, Math.min(flat.length(), offset[0] + m.start()));
                    if (before.replaceAll("\u00A7.", "").endsWith(look.prefix())) continue;
                    if (m.start() > last) {
                        out.append(Component.literal(text.substring(last, m.start())).setStyle(style));
                    }
                    out.append(reader != Reader.GAME ? legacyName(look, m.group(1), reader == Reader.OTHER)
                            : animated ? render(look, m.group(1)) : staticName(look, m.group(1)));
                    changed[0] = true;
                    last = m.end();
                }
                if (last < text.length()) {
                    out.append(Component.literal(text.substring(last)).setStyle(style));
                }
                offset[0] += text.length();
                return Optional.empty();
            }, Style.EMPTY);

            return changed[0] ? out : message;
        } catch (Throwable t) {
            return message;
        }
    }

    private static int blend(int base, int shine, float amount) {
        int r = mix((base >> 16) & 255, (shine >> 16) & 255, amount);
        int g = mix((base >> 8) & 255, (shine >> 8) & 255, amount);
        int b = mix(base & 255, shine & 255, amount);

        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float amount) {
        return Math.round(a + (b - a) * amount);
    }
}