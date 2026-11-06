package me.bewf.mint.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Checkbox;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.DualOption;
import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Slider;
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
    public boolean hideWhenZero = true;

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
            name = "Share Team Chest In Party Chat",
            description = "Use at your own risk. Send a message in party chat when you deposit or take things out of the team chest. This will automatically update the HUD for other people with this mod. Only sent while you are in a party.",
            category = "Team Chest",
            subcategory = "Party Sync"
    )
    public boolean partySync = true;

    @Checkbox(
            name = "Read Teammates' Updates",
            description = "Update your team chest numbers when teammates with this mod deposit or take things out of the team chest.",
            category = "Team Chest",
            subcategory = "Party Sync"
    )
    public boolean readPartySync = true;

    @Checkbox(
            name = "Hide Update Messages",
            description = "Hide these messages in your chat. Your HUD will still update. If you leave them showing, you can pick their colors below.",
            category = "Team Chest",
            subcategory = "Party Sync"
    )
    public boolean hideSyncMessages = false;

    @Text(
            name = "Server 1 Address",
            description = "The server's address, like hypixel.net. Its subdomains count too. Hypixel is filled in as an example.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer1Address = "hypixel.net";

    @Text(
            name = "Server 1 Party Command",
            description = "The command that sends a message to party chat on that server, like /pc.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer1Command = "/pc";

    @Text(
            name = "Server 2 Address",
            description = "Another server to send team chest updates on. Leave it empty to skip it.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer2Address = "";

    @Text(
            name = "Server 2 Party Command",
            description = "The command that sends a message to party chat on that server.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer2Command = "";

    @Text(
            name = "Server 3 Address",
            description = "Another server to send team chest updates on. Leave it empty to skip it.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer3Address = "";

    @Text(
            name = "Server 3 Party Command",
            description = "The command that sends a message to party chat on that server.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer3Command = "";

    @Text(
            name = "Server 4 Address",
            description = "Another server to send team chest updates on. Leave it empty to skip it.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer4Address = "";

    @Text(
            name = "Server 4 Party Command",
            description = "The command that sends a message to party chat on that server.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer4Command = "";

    @Color(name = "Deposit Color", category = "Team Chest", subcategory = "Party Sync Colors")
    public OneColor syncPlusColor = new OneColor(85, 255, 85);

    @Color(name = "Withdrawal Color", category = "Team Chest", subcategory = "Party Sync Colors")
    public OneColor syncMinusColor = new OneColor(255, 85, 85);

    @Color(name = "Iron Color", category = "Team Chest", subcategory = "Resource Colors")
    public OneColor ironColor = new OneColor(255, 255, 255);

    @Color(name = "Gold Color", category = "Team Chest", subcategory = "Resource Colors")
    public OneColor goldColor = new OneColor(255, 170, 0);

    @Color(name = "Diamond Color", category = "Team Chest", subcategory = "Resource Colors")
    public OneColor diamondColor = new OneColor(85, 255, 255);

    @Color(name = "Emerald Color", category = "Team Chest", subcategory = "Resource Colors")
    public OneColor emeraldColor = new OneColor(85, 255, 85);

    @Checkbox(
            name = "Team Chest Waypoint",
            description = "Show a small label above your team chest with what is inside it. This is separate from Track Team Chest.",
            category = "Team Chest",
            subcategory = "Team Chest Waypoint"
    )
    public boolean teamChestWaypoint = true;

    @Dropdown(
            name = "Team Chest Amount",
            description = "Chest only shows what is in the chest. Chest + inventory adds what you are carrying.",
            category = "Team Chest",
            subcategory = "Team Chest Waypoint",
            options = {"Chest only", "Chest + inventory"}
    )
    public int teamChestWaypointAmount = 0;

    @Checkbox(name = "Show Iron", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointIron = true;

    @Checkbox(name = "Show Gold", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointGold = true;

    @Checkbox(name = "Show Diamond", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointDiamond = true;

    @Checkbox(name = "Show Emerald", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointEmerald = true;

    @Checkbox(
            name = "Ender Chest Waypoint",
            description = "Show a small label above your Ender Chest with what is inside it.",
            category = "Team Chest",
            subcategory = "Ender Chest Waypoint"
    )
    public boolean enderChestWaypoint = true;

    @Dropdown(
            name = "Ender Chest Amount",
            description = "Chest only shows what is in the Ender Chest. Chest + inventory adds what you are carrying.",
            category = "Team Chest",
            subcategory = "Ender Chest Waypoint",
            options = {"Chest only", "Chest + inventory"}
    )
    public int enderChestWaypointAmount = 0;

    @Checkbox(name = "Show Iron", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointIron = true;

    @Checkbox(name = "Show Gold", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointGold = true;

    @Checkbox(name = "Show Diamond", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointDiamond = true;

    @Checkbox(name = "Show Emerald", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointEmerald = true;

    @Slider(
            name = "Waypoint Size",
            description = "Makes both labels bigger or smaller.",
            category = "Team Chest",
            subcategory = "Waypoint Style",
            min = 0.5f, max = 2f
    )
    public float waypointScale = 1f;

    @Slider(
            name = "Max Distance",
            description = "Labels further away than this many blocks are hidden.",
            category = "Team Chest",
            subcategory = "Waypoint Style",
            min = 16f, max = 128f
    )
    public float waypointMaxDistance = 64f;

    @Checkbox(
            name = "Shrink With Distance",
            description = "Labels get smaller the further away they are. Off keeps them one size.",
            category = "Team Chest",
            subcategory = "Waypoint Style"
    )
    public boolean waypointShrink = true;

    @Checkbox(
            name = "Show Letters",
            description = "Put a small letter after each amount (i, g, d, e). Off colors the amount with the resource color instead.",
            category = "Team Chest",
            subcategory = "Waypoint Style"
    )
    public boolean waypointLetters = true;

    @Checkbox(
            name = "Text Shadow",
            category = "Team Chest",
            subcategory = "Waypoint Style"
    )
    public boolean waypointShadow = true;

    @Checkbox(
            name = "Sparkle",
            category = "Debug"
    )
    public boolean fancyNames = true;

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

    // Set after the one time party sync tip has been shown (hidden from UI)
    public boolean hasShownSyncTip = false;

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