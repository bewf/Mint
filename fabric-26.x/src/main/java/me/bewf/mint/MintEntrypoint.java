package me.bewf.mint;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.hud.ResourceIconHud;
import me.bewf.mint.util.ResourceTracker;
import me.bewf.mint.util.UpdateCheckListener;
import me.bewf.mint.extras.Sparkle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;

public class MintEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        MintConfig.INSTANCE.preload();

        HudManager.INSTANCE.register(new ResourceIconHud(), MintConstants.ID, "assets/mint/minticon.png");

        Sparkle.startNameLookup();

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : Sparkle.decorateChat(message));

        new ResourceTracker().register();
        new UpdateCheckListener().register();

        System.out.println("Mint loaded - bewf on top");
    }
}
