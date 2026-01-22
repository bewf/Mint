package me.bewf.mint.util;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.StringUtils;

public class BedwarsDetector {

    public static boolean isInHypixelBedwars() {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.getCurrentServerData() == null || mc.getCurrentServerData().serverIP == null) return false;
        String ip = mc.getCurrentServerData().serverIP.toLowerCase();
        if (!ip.contains("hypixel")) return false;

        if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) return false;
        ScoreObjective sidebar = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (sidebar == null || sidebar.getDisplayName() == null) return false;

        String title = StringUtils.stripControlCodes(sidebar.getDisplayName()).toUpperCase();
        return title.contains("BED WARS") || title.contains("BEDWARS");
    }
}
