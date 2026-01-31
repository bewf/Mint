package me.bewf.mint.util;

import me.bewf.mint.Mint;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class UpdateCheckListener {
    private boolean started = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        if (started) return;
        started = true;

        UpdateChecker.checkOnce(
                Mint.MODRINTH_PROJECT_ID,
                Mint.MODRINTH_SLUG,
                Mint.NAME,
                Mint.VERSION,
                Mint.MC_VERSION,
                Mint.LOADER
        );
    }
}
