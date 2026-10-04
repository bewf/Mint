package me.bewf.mint.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Checkbox;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.DualOption;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Text;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.libs.universal.UChat;
import me.bewf.mint.Mint;
import me.bewf.mint.hud.ResourceIconHud;
import me.bewf.mint.util.UpdateChecker;
import net.minecraft.util.EnumChatFormatting;

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

    /*
     * NEW: Team chest tracking toggle
     * Default OFF
     */

    @Checkbox(
            name = "Track Team Chest",
            description = "Tracks team chest. Note that the HUD will not update if a teammate takes something in or out of the chest.",
            category = "HUD",
            subcategory = "Storage"
    )
    public boolean trackTeamChest = false;

    @Checkbox(
            name = "Storage Colors",
            description = "Colors inventory, ender chest, total, and separators.",
            category = "HUD",
            subcategory = "Colors"
    )
    public boolean storageColors = true;

    @Color(
            name = "Inventory Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor inventoryColor = new OneColor(232, 217, 194);

    @Color(
            name = "Ender Chest Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor enderChestColor = new OneColor(190, 63, 255);

    /*
     * NEW: Team chest color
     */

    @Color(
            name = "Team Chest Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor teamChestColor = new OneColor(85, 170, 255);

    @Color(
            name = "Total Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor totalColor = new OneColor(255, 255, 255);

    @Color(
            name = "Separator Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public OneColor separatorColor = new OneColor(120, 120, 120);

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

    @Checkbox(
            name = "Update Checker",
            description = "Show update notification when joining the game.",
            category = "Debug",
            subcategory = "Updates"
    )
    public boolean updateCheckerEnabled = true;

    @Button(
            name = "Manual Check",
            text = "Check for Updates",
            category = "Debug",
            subcategory = "Updates"
    )
    public Runnable manualUpdateCheck = () -> {
        UChat.chat(EnumChatFormatting.AQUA + "[Mint] " + EnumChatFormatting.GOLD + "Checking for updates...");

        new Thread(() -> {
            try {
                UpdateChecker.resetRanFlag();
                UpdateChecker.setUpdateMessageSent(false);

                UpdateChecker.checkOnce(
                        Mint.MODRINTH_PROJECT_ID,
                        Mint.MODRINTH_SLUG,
                        Mint.NAME,
                        Mint.VERSION,
                        Mint.MC_VERSION,
                        Mint.LOADER
                );

                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        if (!UpdateChecker.isUpdateMessageSent()) {
                            UChat.chat(EnumChatFormatting.AQUA + "[Mint] " + EnumChatFormatting.GREEN + "Up to date");
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }).start();

            } catch (Exception e) {
                UChat.chat(EnumChatFormatting.AQUA + "[Mint] " + EnumChatFormatting.RED + "Failed to check for updates: " + e.getMessage());
            }
        }).start();
    };

    // Internal flag to track if config tip has been shown (hidden from UI)
    public boolean hasShownConfigTip = false;

    @Text(
            name = "Addition Label",
            description = "Character(s) used between inventory and ender chest counts.",
            category = "HUD",
            subcategory = "Labels"
    )
    public String additionLabel = "+";

    @Text(
            name = "Equal Label",
            description = "Character(s) used between ender chest and total counts.",
            category = "HUD",
            subcategory = "Labels"
    )
    public String equalLabel = ":";

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

    public static void saveConfig() {
        if (INSTANCE != null) INSTANCE.save();
    }
}