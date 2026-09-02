package me.bewf.mint.config;

import me.bewf.mint.MintConstants;
import me.bewf.mint.util.NotificationManager;
import me.bewf.mint.util.RuntimeInfo;
import me.bewf.mint.util.UpdateChecker;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Checkbox;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

public class MintConfig extends Config {

    public static final MintConfig INSTANCE = new MintConfig();

    @Checkbox(
            title = "Horizontal Layout",
            description = "Off = vertical rows, on = a single horizontal row. (Replaces the old Vertical/Horizontal dual-option toggle - OneConfig v1 doesn't have a dual-option control, so this is a plain switch instead.)",
            category = "HUD",
            subcategory = "Display"
    )
    public boolean horizontalLayout = false;

    @Checkbox(
            title = "Compact Numbers",
            description = "Shortens numbers like 1200 to 1.2k.",
            category = "HUD",
            subcategory = "Display"
    )
    public boolean compactNumbers = true;

    @Checkbox(
            title = "Hide When Zero",
            description = "Hides a resource if its total (inventory + ender chest) is 0.",
            category = "HUD",
            subcategory = "Display"
    )
    public boolean hideWhenZero = false;

    @Checkbox(
            title = "Track Team Chest",
            description = "Tracks team chest. Note that the HUD will not update if a teammate takes something in or out of the chest.",
            category = "HUD",
            subcategory = "Storage"
    )
    public boolean trackTeamChest = false;

    @Checkbox(
            title = "Storage Colors",
            description = "Colors inventory, ender chest, total, and separators.",
            category = "HUD",
            subcategory = "Colors"
    )
    public boolean storageColors = true;

    @Color(
            title = "Inventory Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public int inventoryColor = 0xFFE8D9C2;

    @Color(
            title = "Ender Chest Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public int enderChestColor = 0xFFBE3FFF;

    @Color(
            title = "Team Chest Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public int teamChestColor = 0xFF55AAFF;

    @Color(
            title = "Total Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public int totalColor = 0xFFFFFFFF;

    @Color(
            title = "Separator Color",
            category = "HUD",
            subcategory = "Colors"
    )
    public int separatorColor = 0xFF787878;

    @Checkbox(
            title = "Show Iron",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showIron = true;

    @Checkbox(
            title = "Show Gold",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showGold = true;

    @Checkbox(
            title = "Show Diamond",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showDiamond = true;

    @Checkbox(
            title = "Show Emerald",
            category = "HUD",
            subcategory = "Resources"
    )
    public boolean showEmerald = true;

    @Checkbox(
            title = "Always Show HUD",
            description = "Mainly for debugging. If this is required for the HUD to appear, please open an issue on GitHub.",
            category = "Debug"
    )
    public boolean showOutsideBedwars = false;

    @Checkbox(
            title = "Update Checker",
            description = "Show update notification when joining the game.",
            category = "Debug",
            subcategory = "Updates"
    )
    public boolean updateCheckerEnabled = true;

    @Button(
            title = "Manual Check",
            text = "Check for Updates",
            category = "Debug",
            subcategory = "Updates"
    )
    private void manualUpdateCheck() {
        RuntimeInfo.sendChatMessage(RuntimeInfo.PREFIX_AQUA + "[Mint] " + RuntimeInfo.GOLD + "Checking for updates...");

        new Thread(() -> {
            try {
                UpdateChecker.resetRanFlag();
                UpdateChecker.setUpdateMessageSent(false);

                UpdateChecker.checkOnce(
                        MintConstants.MODRINTH_PROJECT_ID,
                        MintConstants.MODRINTH_SLUG,
                        MintConstants.NAME,
                        MintConstants.VERSION,
                        RuntimeInfo.currentMinecraftVersion(),
                        MintConstants.LOADER
                );

                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        if (!UpdateChecker.isUpdateMessageSent()) {
                            RuntimeInfo.sendChatMessage(RuntimeInfo.PREFIX_AQUA + "[Mint] " + RuntimeInfo.GREEN + "Up to date");
                        }
                    } catch (InterruptedException ignored) {

                    }
                }).start();

            } catch (Exception e) {
                RuntimeInfo.sendChatMessage(RuntimeInfo.PREFIX_AQUA + "[Mint] " + RuntimeInfo.RED + "Failed to check for updates: " + e.getMessage());
            }
        }).start();
    }

    @Include
    public boolean hasShownConfigTip = false;

    @Text(
            title = "Addition Label",
            description = "Character(s) used between inventory and ender chest counts.",
            category = "HUD",
            subcategory = "Labels"
    )
    public String additionLabel = "+";

    @Text(
            title = "Equal Label",
            description = "Character(s) used between ender chest and total counts.",
            category = "HUD",
            subcategory = "Labels"
    )
    public String equalLabel = ":";

    public MintConfig() {
        super("mint.json", "/assets/mint/minticon.png", "Mint", Category.HYPIXEL);
    }

    public static void saveConfig() {
        if (INSTANCE != null) INSTANCE.save();
    }
}
