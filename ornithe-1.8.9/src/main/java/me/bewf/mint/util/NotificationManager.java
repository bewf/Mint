package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;

public final class NotificationManager {
    private NotificationManager() {
    }

    public static void showUpdateNotificationWithConfigTip() {
        if (MintConfig.INSTANCE == null || MintConfig.INSTANCE.hasShownConfigTip) return;

        MintConfig.INSTANCE.hasShownConfigTip = true;
        MintConfig.saveConfig();

        Chat.send("\u00A7b[Mint] \u00A77You can disable update notifications in the OneConfig menu under \u00A78Debug > Updates");
        Chat.send("");
    }
}
