package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

/** Opens Mint's page in the OneConfig menu. Looked up by name so a different OneConfig build cannot break the compile. */
public final class SettingsLink {

    private SettingsLink() {
    }

    public static void open() {
        // The click comes from the chat screen, so open the menu on the next tick instead of inside it
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                try {
                    Method open = MintConfig.INSTANCE.getClass().getMethod("openGui");
                    open.invoke(MintConfig.INSTANCE);
                } catch (Throwable t) {
                    System.out.println("[Mint] Could not open the OneConfig menu: " + t);
                }
            }
        });
    }
}
