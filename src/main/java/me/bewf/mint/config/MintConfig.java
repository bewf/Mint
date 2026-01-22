package me.bewf.mint.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Checkbox;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import me.bewf.mint.hud.ResourceIconHud;

public class MintConfig extends Config {

    public static final MintConfig INSTANCE = new MintConfig();

    @HUD(
            name = "Resource Tracker",
            category = "HUD"
    )
    public ResourceIconHud resourceHud = new ResourceIconHud();

    @Checkbox(
            name = "Show HUD outside Bedwars",
            description = "Debug only. Keep this off during normal gameplay.",
            category = "Debug"
    )
    public boolean showOutsideBedwars = false;

    @Checkbox(name = "Show Iron", category = "HUD")
    public boolean showIron = true;

    @Checkbox(name = "Show Gold", category = "HUD")
    public boolean showGold = true;

    @Checkbox(name = "Show Diamond", category = "HUD")
    public boolean showDiamond = true;

    @Checkbox(name = "Show Emerald", category = "HUD")
    public boolean showEmerald = true;

    public MintConfig() {
        super(
                new Mod(
                        "Mint",
                        ModType.HYPIXEL,
                        "/assets/mint/minticon.png"
                ),
                "mint.json"
        );

        initialize();
    }
}
