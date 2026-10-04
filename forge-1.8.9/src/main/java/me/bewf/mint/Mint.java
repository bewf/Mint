package me.bewf.mint;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.util.ResourceTracker;
import me.bewf.mint.util.UpdateCheckListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = Mint.MODID, name = Mint.NAME, version = "@MOD_VERSION@", clientSideOnly = true)
public class Mint {
    public static final String MODID = "mint";
    public static final String NAME = "Mint";
    public static final String VERSION = "@MOD_VERSION@";

    public static final String MC_VERSION = "1.8.9";
    public static final String LOADER = "forge";

    public static final String MODRINTH_PROJECT_ID = "Xy7IQDty";
    public static final String MODRINTH_SLUG = "mint-bedwars";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MintConfig.INSTANCE.getClass(); // loads config + HUD
        MinecraftForge.EVENT_BUS.register(new ResourceTracker());

        MinecraftForge.EVENT_BUS.register(new UpdateCheckListener());

        System.out.println("Mint loaded - bewf on top");
    }
}
