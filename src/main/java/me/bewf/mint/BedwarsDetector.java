package me.bewf.mint.util;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BedwarsDetector {

    public static boolean isInHypixelBedwars() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.theWorld.getScoreboard() == null) return false;

        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective sidebar = sb.getObjectiveInDisplaySlot(1);
        if (sidebar == null || sidebar.getDisplayName() == null) return false;

        String title = clean(sidebar.getDisplayName()).toUpperCase();
        List<String> lines = getSidebarLines(sb, sidebar);

        // Hypixel fast-path (keeps existing behavior, but doesn't block other servers)
        if (isHypixel(mc) && isBedwarsText(title, lines)) {
            return true;
        }

        // Any-server fallback: if the scoreboard looks like Bedwars / Bedfight, treat it as active
        return isBedwarsText(title, lines);
    }

    private static boolean isHypixel(Minecraft mc) {
        try {
            if (mc.getCurrentServerData() == null || mc.getCurrentServerData().serverIP == null) return false;
            String ip = mc.getCurrentServerData().serverIP.toLowerCase();
            return ip.contains("hypixel");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isBedwarsText(String title, List<String> lines) {
        String t = title == null ? "" : title;
        if (containsBedMode(t)) return true;

        for (String line : lines) {
            String u = line.toUpperCase();

            // catches "Mode: Bed Wars Duel" / "Mode: Bed Rush Duel" / etc
            if (u.contains("MODE:") && u.contains("DUEL") && containsBedMode(u)) return true;

            if (containsBedMode(u)) return true;
        }

        return false;
    }

    private static boolean containsBedMode(String s) {
        if (s == null) return false;

        // common variants
        if (s.contains("BEDWARS")) return true;
        if (s.contains("BED WARS")) return true;

        if (s.contains("BEDFIGHT")) return true;
        if (s.contains("BED FIGHT")) return true;

        // some servers write "BED-WARS" or other separators
        if (s.contains("BED-WARS")) return true;
        if (s.contains("BED-FIGHT")) return true;

        return false;
    }

    private static List<String> getSidebarLines(Scoreboard sb, ScoreObjective obj) {
        List<String> out = new ArrayList<String>();
        Collection<Score> scores = sb.getSortedScores(obj);

        int count = 0;
        for (Score s : scores) {
            if (s == null) continue;
            String name = s.getPlayerName();
            if (name == null) continue;

            String cleaned = clean(name);
            if (cleaned.isEmpty()) continue;

            out.add(cleaned);
            count++;
            if (count >= 15) break;
        }

        return out;
    }

    private static String clean(String s) {
        return StringUtils.stripControlCodes(s).replace("\u00A0", " ").trim();
    }
}
