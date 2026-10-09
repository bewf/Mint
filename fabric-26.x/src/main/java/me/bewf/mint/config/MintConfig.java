package me.bewf.mint.config;

import me.bewf.mint.MintConstants;
import me.bewf.mint.util.RuntimeInfo;
import me.bewf.mint.util.UpdateChecker;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Checkbox;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;

public class MintConfig extends Config {

    public static final MintConfig INSTANCE = new MintConfig();

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

    public MintConfig() {
        super("mint.json", "assets/mint/minticon.png", "Mint", Category.HYPIXEL);
    }

    public static void saveConfig() {
        if (INSTANCE != null) INSTANCE.save();
    }
}
