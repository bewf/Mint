package me.bewf.mint.util;

import net.minecraft.client.Minecraft;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public final class Chat {
    private Chat() {
    }

    public static void send(String legacyFormattedText) {
        send(new LiteralText(legacyFormattedText));
    }

    public static void send(Text text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.sendMessage(text);
    }
}
