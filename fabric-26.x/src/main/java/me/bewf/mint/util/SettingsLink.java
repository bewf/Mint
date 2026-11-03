package me.bewf.mint.util;

import me.bewf.mint.MintConstants;

import java.lang.reflect.Method;

/**
 * Opens the OneConfig menu on Mint's page. Looked up by name so an older OneConfig that lacks the
 * page API just opens the main menu instead of failing.
 */
public final class SettingsLink {

    private SettingsLink() {
    }

    public static void open() {
        try {
            Class<?> pages = Class.forName("org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages");
            Object target = pages.getMethod("modConfig", String.class).invoke(null, MintConstants.ID);
            Class<?> ui = Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI");
            ui.getMethod("open", Object.class).invoke(null, target);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ui = Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI");
            Method open = ui.getMethod("open");
            open.invoke(null);
        } catch (Throwable t) {
            System.out.println("[Mint] Could not open the OneConfig menu: " + t);
        }
    }
}
