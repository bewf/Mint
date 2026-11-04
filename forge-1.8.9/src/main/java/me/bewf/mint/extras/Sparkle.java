package me.bewf.mint.extras;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.util.LegacyColors;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A few names get a colour and a title in chat and over their head. Minecraft 1.8.9 only has sixteen
 * chat colours, so the shine is a sweep between the closest colour and white instead of a smooth blend.
 */
public class Sparkle {

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

    // Also matched by name in text, for servers where the UUID does not match
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

    private static final long PERIOD_MS = 2400L;
    private static final long SHINE_MS = 480L;
    private static final float SHINE_RADIUS = 1.5F;

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
        namePattern = Pattern.compile("(?<![A-Za-z0-9_])(" + alt + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    /** Resolves the current name of each listed UUID so renamed accounts keep working. */
    public static void startNameLookup() {
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

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        try {
            if (event.message == null || event.type == 2 || !MintConfig.INSTANCE.fancyNames) return;
            event.message = decorate(event.message);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        try {
            if (!MintConfig.INSTANCE.fancyNames || event.entityPlayer == null) return;
            Look look = PLAYERS.get(event.entityPlayer.getUniqueID());
            if (look == null) return;
            event.displayname = animated(look, event.username);
        } catch (Throwable ignored) {
        }
    }

    /** The title and name with a bright spot that sweeps across now and then. */
    private static String animated(Look look, String name) {
        String text = look.prefix + name;
        int length = text.length();
        long phase = System.currentTimeMillis() % PERIOD_MS;
        boolean shining = phase < SHINE_MS;
        float progress = shining ? phase / (float) SHINE_MS : 0f;
        float center = -SHINE_RADIUS + progress * (length + SHINE_RADIUS * 2f);

        EnumChatFormatting base = LegacyColors.nearest(look.base);
        StringBuilder out = new StringBuilder();
        EnumChatFormatting current = null;
        for (int i = 0; i < length; i++) {
            boolean bright = shining && Math.abs(i - center) < SHINE_RADIUS * 0.6f;
            EnumChatFormatting color = bright ? EnumChatFormatting.WHITE : base;
            if (color != current) {
                out.append(color.toString());
                current = color;
            }
            out.append(text.charAt(i));
        }
        return out.append(EnumChatFormatting.RESET.toString()).toString();
    }

    private static IChatComponent decorate(IChatComponent message) {
        String flat = message.getUnformattedText();
        if (!namePattern.matcher(flat).find()) return message;

        // Text after the first colon is message body, so only the sender is changed
        int limit = Integer.MAX_VALUE;
        int colon = flat.indexOf(':');
        if (colon >= 0 && !LIST_HEADER.matcher(flat.substring(0, colon)).find()) limit = colon;

        boolean[] changed = {false};
        IChatComponent out = rewrite(message, flat, limit, new int[]{0}, changed);
        return changed[0] ? out : message;
    }

    private static IChatComponent rewrite(IChatComponent c, String flat, int limit, int[] offset, boolean[] changed) {
        IChatComponent node;
        if (c instanceof ChatComponentText) {
            String text = ((ChatComponentText) c).getChatComponentText_TextValue();
            node = split(text, c.getChatStyle(), flat, limit, offset[0], changed);
            offset[0] += text.length();
        } else {
            node = c.createCopy();
            node.getSiblings().clear();
            offset[0] += c.getUnformattedTextForChat().length();
        }
        for (Object sibling : c.getSiblings()) {
            node.appendSibling(rewrite((IChatComponent) sibling, flat, limit, offset, changed));
        }
        return node;
    }

    /** Cuts a piece of text around the names in it. The pieces inherit the style of the original. */
    private static IChatComponent split(String text, ChatStyle style, String flat, int limit, int start, boolean[] changed) {
        Matcher m = namePattern.matcher(text);
        int last = 0;
        ChatComponentText root = new ChatComponentText("");
        root.setChatStyle(style.createShallowCopy());
        boolean any = false;
        while (m.find()) {
            if (start + m.start() >= limit) break;
            Look look = NAMES.get(m.group(1).toLowerCase());
            if (look == null) continue;
            String before = flat.substring(0, Math.min(flat.length(), start + m.start()));
            if (before.endsWith(look.prefix)) continue;
            if (m.start() > last) root.appendSibling(new ChatComponentText(text.substring(last, m.start())));
            ChatComponentText name = new ChatComponentText(look.prefix + m.group(1));
            name.setChatStyle(new ChatStyle().setColor(LegacyColors.nearest(look.base)));
            root.appendSibling(name);
            last = m.end();
            any = true;
        }
        if (!any) {
            ChatComponentText same = new ChatComponentText(text);
            same.setChatStyle(style.createShallowCopy());
            return same;
        }
        changed[0] = true;
        if (last < text.length()) root.appendSibling(new ChatComponentText(text.substring(last)));
        return root;
    }
}
