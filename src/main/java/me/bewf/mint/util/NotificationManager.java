package me.bewf.mint.util;

import cc.polyfrost.oneconfig.libs.universal.UChat;
import me.bewf.mint.config.MintConfig;
import net.minecraft.util.EnumChatFormatting;

public final class NotificationManager {

    private NotificationManager() {}

    public static void showUpdateNotificationWithConfigTip() {
        if (MintConfig.INSTANCE == null || MintConfig.INSTANCE.hasShownConfigTip) return;

        MintConfig.INSTANCE.hasShownConfigTip = true;
        MintConfig.saveConfig();

        UChat.chat(EnumChatFormatting.AQUA + "[Mint] " + EnumChatFormatting.GRAY +
                  "You can disable update notifications in the OneConfig menu under " +
                  EnumChatFormatting.DARK_GRAY + "Debug > Updates");
        UChat.chat("");
    }
}

