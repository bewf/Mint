package me.bewf.mint.util;

import me.bewf.mint.sync.SidebarTeams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.StringUtil;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BedwarsDetector {

    public static boolean isInHypixelBedwars() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.level.getScoreboard() == null) return false;

        Scoreboard sb = mc.level.getScoreboard();
        Objective sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null || sidebar.getDisplayName() == null) return false;

        String title = clean(sidebar.getDisplayName().getString()).toUpperCase();
        List<String> lines = getSidebarLines(sb, sidebar);

        if (isHypixel(mc) && isBedwarsText(title, lines)) {
            return true;
        }

        return isBedwarsText(title, lines);
    }

    /** True while connected to Hypixel. The team chest features only run there for now. */
    public static boolean onHypixel() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && isHypixel(mc);
    }

    /**
     * True once a game is running. The sidebar then lists every team and marks the player's own with YOU.
     * The lines are rebuilt from the scoreboard team's prefix and suffix, because that is where the text is.
     */
    public static boolean hasOwnTeamLine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.level.getScoreboard() == null) return false;

        Scoreboard sb = mc.level.getScoreboard();
        Objective sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return false;

        List<String> lines = new ArrayList<>();
        int count = 0;
        for (PlayerScoreEntry entry : sb.listPlayerScores(sidebar)) {
            if (entry == null || entry.owner() == null) continue;
            String name = entry.owner();
            net.minecraft.world.scores.PlayerTeam team = sb.getPlayersTeam(name);
            String full = team == null ? name
                    : team.getPlayerPrefix().getString() + name + team.getPlayerSuffix().getString();
            lines.add(clean(full));
            if (++count >= 15) break;
        }
        return SidebarTeams.hasOwnTeamLine(lines);
    }

    private static boolean isHypixel(Minecraft mc) {
        try {
            ServerData server = mc.getCurrentServer();
            if (server == null || server.ip == null) return false;
            String ip = server.ip.toLowerCase();
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

            if (u.contains("MODE:") && u.contains("DUEL") && containsBedMode(u)) return true;

            if (containsBedMode(u)) return true;
        }

        return false;
    }

    private static boolean containsBedMode(String s) {
        if (s == null) return false;

        if (s.contains("BEDWARS")) return true;
        if (s.contains("BED WARS")) return true;

        if (s.contains("BEDFIGHT")) return true;
        if (s.contains("BED FIGHT")) return true;

        if (s.contains("BED-WARS")) return true;
        if (s.contains("BED-FIGHT")) return true;

        return false;
    }

    private static List<String> getSidebarLines(Scoreboard sb, Objective obj) {
        List<String> out = new ArrayList<>();
        Collection<PlayerScoreEntry> entries = sb.listPlayerScores(obj);

        int count = 0;
        for (PlayerScoreEntry entry : entries) {
            if (entry == null) continue;
            String name = entry.owner();
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
        return StringUtil.stripColor(s).replace("\u00A0", " ").trim();
    }
}
