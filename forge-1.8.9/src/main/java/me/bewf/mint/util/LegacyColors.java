package me.bewf.mint.util;

import net.minecraft.util.EnumChatFormatting;

/** Minecraft 1.8.9 chat only has the 16 legacy colours, so a chosen colour is matched to the closest one. */
public final class LegacyColors {

    private LegacyColors() {
    }

    // Same order as the colours at the start of EnumChatFormatting
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final EnumChatFormatting[] FORMATS = {
            EnumChatFormatting.BLACK, EnumChatFormatting.DARK_BLUE, EnumChatFormatting.DARK_GREEN,
            EnumChatFormatting.DARK_AQUA, EnumChatFormatting.DARK_RED, EnumChatFormatting.DARK_PURPLE,
            EnumChatFormatting.GOLD, EnumChatFormatting.GRAY, EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.BLUE, EnumChatFormatting.GREEN, EnumChatFormatting.AQUA,
            EnumChatFormatting.RED, EnumChatFormatting.LIGHT_PURPLE, EnumChatFormatting.YELLOW,
            EnumChatFormatting.WHITE
    };

    public static EnumChatFormatting nearest(int rgb) {
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            long dr = ((rgb >> 16) & 255) - ((RGB[i] >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((RGB[i] >> 8) & 255);
            long db = (rgb & 255) - (RGB[i] & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return FORMATS[best];
    }
}
