package me.bewf.mint.util;

import me.bewf.mint.MintConstants;
import me.bewf.mint.config.MintConfig;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

public final class UpdateCheckListener {

    private boolean started = false;

    public void register() {
        EventManager.INSTANCE.register(TickEvent.End.class, event -> onClientTick());
    }

    private void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (started) return;
        started = true;

        if (MintConfig.INSTANCE != null && !MintConfig.INSTANCE.updateCheckerEnabled) {
            System.out.println("[Mint] Update checker disabled in config");
            return;
        }

        UpdateChecker.checkOnce(
                MintConstants.MODRINTH_PROJECT_ID,
                MintConstants.MODRINTH_SLUG,
                MintConstants.NAME,
                MintConstants.VERSION,
                RuntimeInfo.currentMinecraftVersion(),
                MintConstants.LOADER
        );
    }
}
