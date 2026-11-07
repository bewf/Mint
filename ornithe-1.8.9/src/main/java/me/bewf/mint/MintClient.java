package me.bewf.mint;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.extras.Sparkle;
import me.bewf.mint.hud.ResourceIconHud;
import me.bewf.mint.render.WaypointRenderer;
import me.bewf.mint.util.ResourceTracker;
import me.bewf.mint.util.TeamChestSync;
import me.bewf.mint.util.UpdateCheckListener;
import net.fabricmc.api.ClientModInitializer;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;

public class MintClient implements ClientModInitializer {
    private static ResourceIconHud hud;

    /** The HUD, for the colours picked in its settings. Null until the mod has started. */
    public static ResourceIconHud hud() {
        return hud;
    }

    @Override
    public void onInitializeClient() {
        MintConfig.init();

        hud = new ResourceIconHud();
        HudManager.register(hud, MintConstants.ID, "assets/mint/minticon.png");

        ResourceTracker.register();
        TeamChestSync.register();
        UpdateCheckListener.register();
        Sparkle.register();
        WaypointRenderer.register();

        System.out.println("Mint loaded - bewf on top");
    }
}
