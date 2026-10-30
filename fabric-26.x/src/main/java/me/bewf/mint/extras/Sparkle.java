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
    /** shine2 < 0 means a single shine; otherwise a second sweep of that colour trails behind the first. */
    private record Look(String prefix, int base, int shine, int shine2) {
    }

    private static final Look LORD = new Look("Lord and Saviour ", 0xBE3FFF, 0xFFF5FF, -1);
    private static final Look SCOOP = new Look("Scoop Master ", 0x9932CC, 0xFFF5FF, -1);

    private static final Map<UUID, Look> PLAYERS = Map.of(
            uuid("2913ac77e515490a96eb5183963f6169"), LORD,
            uuid("f299854cd6954a3cbaae6ab0e4b62dc8"), LORD,
            uuid("e02ca8dea5d740ffaa85d29a83a2eefb"), SCOOP
    );

    // Names are matched in text too, so this also works when the player is on another server
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

        // Merge neighbouring characters with the same colour into one component, so the name is a
        // handful of components instead of one per letter (other mods choke on long colour chains)
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

    /** Single-colour version for places that do not redraw every frame (chat). */
    private static Component staticName(Look look, String playerName) {
        return Component.literal(look.prefix() + playerName)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(look.base())));
    }

    /**
     * SkyHanni (and similar mods) read names with Style colours turned into the literal text
     * "<#RRGGBB>" because they only know the 16 Minecraft colours. SkyHanni's Advanced Player List
     * reads the tab list that way in SkyBlock and then prints the tags. So only the game's own
     * renderer gets the custom colours, anything else that asks for the same name gets the closest
     * normal Minecraft colour.
     */
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set<String> LOGGED_CALLERS = ConcurrentHashMap.newKeySet();

    public static boolean calledByVanilla(String hookedMethod) {
        try {
            return WALKER.walk(frames -> {
                boolean seen = false;
                for (StackWalker.StackFrame f : (Iterable<StackWalker.StackFrame>) frames.limit(10)::iterator) {
                    boolean isHooked = f.getMethodName().equals(hookedMethod);
                    if (!seen) {
                        seen = isHooked;
                        continue;
                    }
                    if (isHooked) continue; // super calls inside the same method
                    boolean vanilla = f.getClassName().startsWith("net.minecraft.");
                    if (!vanilla && LOGGED_CALLERS.add(f.getClassName())) {
                        System.out.println("[Mint] " + hookedMethod + " is read by " + f.getClassName()
                                + ", using normal Minecraft colours for it");
                    }
                    return vanilla;
                }
                return true;
            });
        } catch (Throwable t) {
            return true;
        }
    }

    /** Name for hooks that other mods can also call: custom colours only for the game's own use. */
    public static Component smartName(UUID uuid, String playerName, String hookedMethod) {
        Look look = PLAYERS.get(uuid);
        if (look == null) return Component.literal(playerName);
        return calledByVanilla(hookedMethod) ? render(look, playerName) : legacyName(look, playerName);
    }

    private static Component legacyName(Look look, String playerName) {
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (ChatFormatting f : ChatFormatting.values()) {
            if (!f.isColor() || f.getColor() == null) continue;
            int c = f.getColor();
            long dr = ((c >> 16) & 255) - ((look.base() >> 16) & 255);
            long dg = ((c >> 8) & 255) - ((look.base() >> 8) & 255);
            long db = (c & 255) - (look.base() & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = f;
            }
        }
        return Component.literal(look.prefix() + playerName).withStyle(best);
    }


    private static final Pattern DECORATED_PATTERN = Pattern.compile(
            "(Lord and Saviour |Scoop Master )(biwf|bewf|stalbet)(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);

    /**
     * Turns names that were already decorated with plain Minecraft colours (what other mods get)
     * back into the full custom-coloured version. Used where another mod draws a Component itself,
     * so the custom colours cannot be mangled by it any more.
     */
    public static Component upgradeDecoratedNames(Component message, boolean animated) {
        try {
            if (message == null || !MintConfig.INSTANCE.fancyNames || !MintConfig.INSTANCE.fancyTabList) return message;
            if (!DECORATED_PATTERN.matcher(message.getString()).find()) return message;

            MutableComponent out = Component.empty();
            boolean[] changed = {false};
            message.visit((style, text) -> {
                Matcher m = DECORATED_PATTERN.matcher(text);
                int last = 0;
                while (m.find()) {
                    Look look = NAMES.get(m.group(2).toLowerCase());
                    if (look == null) continue;
                    if (m.start() > last) {
                        out.append(Component.literal(text.substring(last, m.start())).setStyle(style));
                    }
                    out.append(animated ? render(look, m.group(2)) : staticName(look, m.group(2)));
                    changed[0] = true;
                    last = m.end();
                }
                if (last < text.length()) {
                    out.append(Component.literal(text.substring(last)).setStyle(style));
                }
                return Optional.empty();
            }, Style.EMPTY);

            return changed[0] ? out : message;
        } catch (Throwable t) {
            return message;
        }
    }

    /** Chat lines: single colour, and only the sender part, not the message body. */
    public static Component decorateChat(Component message) {
        return replaceNames(message, false, true);
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
        namePattern = Pattern.compile("(?<![A-Za-z0-9_])(" + alt + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    /**
     * Looks up the current name of every listed UUID with Mojang, so names are recognised even
     * when the player is in a different world, and keep working after a name change.
     */
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

    /**
     * Replaces the names of special players found inside a component, keeping everything else
     * (ranks, team prefixes, colours) as it was.
     */
    public static Component replaceNames(Component message, boolean animated, boolean senderOnly) {
        return replaceNames(message, animated, senderOnly, false);
    }

    public static Component replaceNames(Component message, boolean animated, boolean senderOnly, boolean legacyColours) {
        try {
            if (message == null || !MintConfig.INSTANCE.fancyNames) return message;

            String flat = message.getString();
            if (!namePattern.matcher(flat).find()) return message;

            // Chat lines: names after the first colon are message text, leave them alone.
            // Party list lines ("Party Members: ...") are not chat, so they are replaced everywhere.
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
                    // Already decorated, do not apply twice
                    String before = text.substring(0, m.start());
                    if (before.endsWith(look.prefix())) continue;
                    if (m.start() > last) {
                        out.append(Component.literal(text.substring(last, m.start())).setStyle(style));
                    }
                    out.append(legacyColours ? legacyName(look, m.group(1))
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
