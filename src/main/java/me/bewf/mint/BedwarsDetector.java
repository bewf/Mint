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

        if (mc.getCurrentServerData() == null || mc.getCurrentServerData().serverIP == null) return false;
        String ip = mc.getCurrentServerData().serverIP.toLowerCase();
        if (!ip.contains("hypixel")) return false;

        if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) return false;

        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective sidebar = sb.getObjectiveInDisplaySlot(1);
        if (sidebar == null || sidebar.getDisplayName() == null) return false;

        String title = clean(sidebar.getDisplayName()).toUpperCase();
        List<String> lines = getSidebarLines(sb, sidebar);

        // Normal Bedwars (most reliable signal is the title)
        if (title.contains("BED WARS") || title.contains("BEDWARS")) {
            return true;
        }

        // Duels variants sometimes still use BED WARS title, but if it ever changes,
        // this catches it from the sidebar content.
        for (String line : lines) {
            String u = line.toUpperCase();

            // Matches:
            // "Mode: Bed Wars Duel"
            // "Mode: Bed Rush Duel"
            if (u.contains("MODE:") && u.contains("DUEL") && (u.contains("BED WARS") || u.contains("BEDWARS") || u.contains("BED RUSH") || u.contains("BEDRUSH"))) {
                return true;
            }

            // Extra safety: some layouts might omit "Mode:" but still show the mode text
            if ((u.contains("BED WARS DUEL") || u.contains("BEDWARS DUEL") || u.contains("BED RUSH DUEL") || u.contains("BEDRUSH DUEL"))) {
                return true;
            }
        }

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
