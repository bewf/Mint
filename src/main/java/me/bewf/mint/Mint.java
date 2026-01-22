package me.bewf.mint;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.tracker.ResourceTracker;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = Mint.MODID, name = Mint.NAME, version = Mint.VERSION, clientSideOnly = true)
public class Mint {
    public static final String MODID = "mint";
    public static final String NAME = "Mint";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MintConfig.INSTANCE.getClass(); // loads config + HUD
        MinecraftForge.EVENT_BUS.register(new ResourceTracker());
    }
}
