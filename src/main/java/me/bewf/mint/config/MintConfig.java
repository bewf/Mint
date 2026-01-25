package me.bewf.mint.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Checkbox;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.DualOption;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.core.OneColor;
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

    @DualOption(
            name = "Layout",
            left = "Vertical",
            right = "Horizontal",
            category = "HUD",
            subcategory = "Display"
    )
    public boolean horizontalLayout = false;

    @Checkbox(
            name = "Compact Numbers",
            description = "Shortens numbers like 1200 to 1.2k.",
            category = "HUD",
            subcategory = "Display"
    )
    public boolean compactNumbers = true;

    @Checkbox(
            name = "Hide When Zero",
            description = "Hides a resource if its total (inventory + ender chest) is 0.",
            category = "HUD",
            subcategory = "Display"
    )
    public boolean hideWhenZero = false;

    @Checkbox(
            name = "Storage Colors",
            description = "Colors inventory, ender chest, and total to make it easier to read.",
            category = "HUD",
            subcategory = "Colors"
    )
    public boolean storageColors = true;

    @Color(
            name = "Inventory Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor inventoryColor = new OneColor(232, 217, 194); // light beige

    @Color(
            name = "Ender Chest Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor enderChestColor = new OneColor(190, 63, 255); // #be3fff

    @Color(
            name = "Total Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor totalColor = new OneColor(255, 255, 255); // white

    @Checkbox(
            name = "Show Iron",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showIron = true;

    @Checkbox(
            name = "Show Gold",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showGold = true;

    @Checkbox(
            name = "Show Diamond",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showDiamond = true;

    @Checkbox(
            name = "Show Emerald",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showEmerald = true;

    @Checkbox(
            name = "Always Show HUD",
            description = "Mainly for debugging. If this is required for the HUD to appear, please open an issue on GitHub.",
            category = "Debug"
    )
    public boolean showOutsideBedwars = false;

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
