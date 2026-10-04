package me.bewf.mint;

import net.fabricmc.loader.api.FabricLoader;

public final class MintConstants {
    private MintConstants() {
    }

    public static final String ID = "mint";
    public static final String NAME = "Mint";

    public static final String MC_VERSION = "1.8.9";
    public static final String LOADER = "fabric";

    public static final String MODRINTH_PROJECT_ID = "Xy7IQDty";
    public static final String MODRINTH_SLUG = "mint-bedwars";

    public static String version() {
        return FabricLoader.getInstance()
                .getModContainer(ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
