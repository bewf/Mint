package me.bewf.mint.config;

import me.bewf.mint.MintConstants;
import me.bewf.mint.util.Chat;
import me.bewf.mint.util.NotificationManager;
import me.bewf.mint.util.UpdateChecker;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Checkbox;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

public class MintConfig extends Config {
    public static MintConfig INSTANCE;

    @Switch(
            title = "Share Team Chest In Party Chat",
            description = "The following settings are used at your own risk. Send a message in party chat when you deposit or take things out of the team chest. This will automatically update the HUD for other people with this mod. Only sent while you are in a party.",
            category = "Team Chest",
            subcategory = "Party Sync"
    )
    public boolean partySync = true;

    @Switch(
            title = "Read Teammates' Updates",
            description = "Update your team chest numbers when teammates with this mod deposit or take things out of the team chest.",
            category = "Team Chest",
            subcategory = "Party Sync"
    )
    public boolean readPartySync = true;

    @Switch(
            title = "Hide Update Messages",
            description = "Hide these messages in your chat. Your HUD will still update. If you leave them showing, you can pick their colors below.",
            category = "Team Chest",
            subcategory = "Party Sync"
    )
    public boolean hideSyncMessages = false;

    @Text(
            title = "Server 1 Address",
            description = "The server's address, like hypixel.net. Its subdomains count too. Hypixel is filled in as an example. Away from Hypixel the mod sends updates whether or not you are in a party.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer1Address = "hypixel.net";

    @Text(
            title = "Server 1 Party Command",
            description = "The command that sends a message to party chat on that server, like /pc.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer1Command = "/pc";

    @Text(
            title = "Server 2 Address",
            description = "Another server to send team chest updates on. Leave it empty to skip it.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer2Address = "";

    @Text(
            title = "Server 2 Party Command",
            description = "The command that sends a message to party chat on that server.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer2Command = "";

    @Text(
            title = "Server 3 Address",
            description = "Another server to send team chest updates on. Leave it empty to skip it.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer3Address = "";

    @Text(
            title = "Server 3 Party Command",
            description = "The command that sends a message to party chat on that server.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer3Command = "";

    @Text(
            title = "Server 4 Address",
            description = "Another server to send team chest updates on. Leave it empty to skip it.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer4Address = "";

    @Text(
            title = "Server 4 Party Command",
            description = "The command that sends a message to party chat on that server.",
            category = "Team Chest",
            subcategory = "Party Servers"
    )
    public String partyServer4Command = "";

    @Color(title = "Deposit Color", category = "Team Chest", subcategory = "Party Sync Colors")
    public PolyColor syncPlusColor = new PolyColor(0xFF55FF55);

    @Color(title = "Withdrawal Color", category = "Team Chest", subcategory = "Party Sync Colors")
    public PolyColor syncMinusColor = new PolyColor(0xFFFF5555);

    @Color(title = "Iron Color", category = "Team Chest", subcategory = "Resource Colors")
    public PolyColor ironColor = new PolyColor(0xFFFFFFFF);

    @Color(title = "Gold Color", category = "Team Chest", subcategory = "Resource Colors")
    public PolyColor goldColor = new PolyColor(0xFFFFAA00);

    @Color(title = "Diamond Color", category = "Team Chest", subcategory = "Resource Colors")
    public PolyColor diamondColor = new PolyColor(0xFF55FFFF);

    @Color(title = "Emerald Color", category = "Team Chest", subcategory = "Resource Colors")
    public PolyColor emeraldColor = new PolyColor(0xFF55FF55);

    @Switch(
            title = "Team Chest Waypoint",
            description = "Show a small label above your team chest with what is inside it. This is separate from Track Team Chest.",
            category = "Team Chest",
            subcategory = "Team Chest Waypoint"
    )
    public boolean teamChestWaypoint = true;

    @Dropdown(
            title = "Team Chest Amount",
            description = "Chest only shows what is in the chest. Chest + inventory adds your inventory.",
            category = "Team Chest",
            subcategory = "Team Chest Waypoint",
            options = {"Chest only", "Chest + inventory"}
    )
    public int teamChestWaypointAmount = 0;

    @Switch(title = "Show Iron", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointIron = true;

    @Switch(title = "Show Gold", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointGold = true;

    @Switch(title = "Show Diamond", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointDiamond = true;

    @Switch(title = "Show Emerald", category = "Team Chest", subcategory = "Team Chest Waypoint")
    public boolean teamWaypointEmerald = true;

    @Switch(
            title = "Other Chest Waypoints",
            description = "Also show a label above other chests you have opened, each with its own contents. The HUD still only follows your own team chest.",
            category = "Team Chest",
            subcategory = "Team Chest Waypoint"
    )
    public boolean otherChestWaypoints = true;

    @Switch(
            title = "Ender Chest Waypoint",
            description = "Show a small label above your Ender Chest with what is inside it.",
            category = "Team Chest",
            subcategory = "Ender Chest Waypoint"
    )
    public boolean enderChestWaypoint = true;

    @Dropdown(
            title = "Ender Chest Amount",
            description = "Chest only shows what is in the Ender Chest. Chest + inventory adds your inventory.",
            category = "Team Chest",
            subcategory = "Ender Chest Waypoint",
            options = {"Chest only", "Chest + inventory"}
    )
    public int enderChestWaypointAmount = 0;

    @Switch(title = "Show Iron", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointIron = true;

    @Switch(title = "Show Gold", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointGold = true;

    @Switch(title = "Show Diamond", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointDiamond = true;

    @Switch(title = "Show Emerald", category = "Team Chest", subcategory = "Ender Chest Waypoint")
    public boolean enderWaypointEmerald = true;

    @Slider(
            title = "Waypoint Size",
            description = "Makes both labels bigger or smaller. 1 is the size of normal game text.",
            category = "Team Chest",
            subcategory = "Waypoint Style",
            min = 0.5f, max = 8f, step = 0.1f
    )
    public float waypointSize = 2.5f;

    @Slider(
            title = "Max Distance",
            description = "Labels further away than this many blocks are hidden.",
            category = "Team Chest",
            subcategory = "Waypoint Style",
            min = 16f, max = 512f, step = 4f
    )
    public float waypointMaxDistance = 128f;

    @Switch(
            title = "Shrink With Distance",
            description = "Labels get a little smaller the further away they are. Off keeps them one size.",
            category = "Team Chest",
            subcategory = "Waypoint Style"
    )
    public boolean waypointShrink = true;

    @Dropdown(
            title = "Resource Marker",
            description = "What goes next to each amount. Icon shows a small picture of the resource, Letter shows i, g, d or e, and None colors the amount with its resource color.",
            category = "Team Chest",
            subcategory = "Waypoint Style",
            options = {"Icon", "Letter", "None"}
    )
    public int waypointMarker = 0;

    @Switch(
            title = "Text Shadow",
            category = "Team Chest",
            subcategory = "Waypoint Style"
    )
    public boolean waypointShadow = true;

    @Checkbox(
            title = "Always Show HUD",
            description = "Mainly for debugging. If this is required for the HUD to appear, please open an issue on GitHub.",
            category = "Debug"
    )
    public boolean showOutsideBedwars = false;

    @Checkbox(
            title = "Sparkle",
            description = "Gives a few names a colour and a title in chat.",
            category = "Debug"
    )
    public boolean fancyNames = true;

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
        Chat.send("\u00A7b[Mint] \u00A76Checking for updates...");

        new Thread(() -> {
            try {
                UpdateChecker.resetRanFlag();
                UpdateChecker.setUpdateMessageSent(false);

                UpdateChecker.checkOnce(
                        MintConstants.MODRINTH_PROJECT_ID,
                        MintConstants.MODRINTH_SLUG,
                        MintConstants.NAME,
                        MintConstants.version(),
                        MintConstants.MC_VERSION,
                        MintConstants.LOADER
                );

                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        if (!UpdateChecker.isUpdateMessageSent()) {
                            Chat.send("\u00A7b[Mint] \u00A7aUp to date");
                        }
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            } catch (Exception e) {
                Chat.send("\u00A7b[Mint] \u00A7cFailed to check for updates: " + e.getMessage());
            }
        }).start();
    }

    @Include
    public boolean hasShownConfigTip = false;

    @Include
    public boolean hasShownSyncTip = false;

    private MintConfig() {
        super("mint.json", "assets/mint/minticon.png", "Mint", Category.HYPIXEL);
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new MintConfig();
            INSTANCE.preload();
        }
    }

    public static void saveConfig() {
        if (INSTANCE != null) INSTANCE.save();
    }
}
