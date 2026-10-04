package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;

public final class NotificationManager {

    private NotificationManager() {
    }

    public static void showUpdateNotificationWithConfigTip() {
        if (MintConfig.INSTANCE == null || MintConfig.INSTANCE.hasShownConfigTip) return;

        MintConfig.INSTANCE.hasShownConfigTip = true;
        MintConfig.saveConfig();

        RuntimeInfo.sendChatMessage(RuntimeInfo.PREFIX_AQUA + "[Mint] " + RuntimeInfo.GRAY +
                "You can disable update notifications in the OneConfig menu under " +
                RuntimeInfo.DARK_GRAY + "Debug > Updates");
        RuntimeInfo.sendChatMessage("");
    }
}
