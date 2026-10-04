package me.bewf.mint;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.hud.ResourceIconHud;
import me.bewf.mint.util.ResourceTracker;
import me.bewf.mint.util.UpdateCheckListener;
import net.fabricmc.api.ClientModInitializer;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;

public class MintClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MintConfig.init();

        HudManager.register(new ResourceIconHud(), MintConstants.ID, "assets/mint/minticon.png");

        ResourceTracker.register();
        UpdateCheckListener.register();

        System.out.println("Mint loaded - bewf on top");
    }
}
