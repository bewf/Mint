package me.bewf.mint.util;

import me.bewf.mint.sync.SidebarTeams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BedwarsDetector {
    private static final int SIDEBAR_SLOT = 1;

    public static boolean isInHypixelBedwars() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.world == null || mc.world.getScoreboard() == null) return false;

        Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective sidebar = sb.getDisplayObjective(SIDEBAR_SLOT);
        if (sidebar == null || sidebar.getDisplayName() == null) return false;

        String title = clean(sidebar.getDisplayName()).toUpperCase();
        List<String> lines = getSidebarLines(sb, sidebar);

        return isBedwarsText(title, lines);
    }

    /** The address of the server the player is on, lower case, or empty. */
    public static String serverAddress() {
        try {
            Minecraft mc = Minecraft.getInstance();
            ServerListEntry server = mc == null ? null : mc.getCurrentServerEntry();
            return server == null || server.ip == null ? "" : server.ip.toLowerCase();
        } catch (Throwable t) {
            return "";
        }
    }

    /** True while connected to Hypixel. The team chest features only run there for now. */
    public static boolean onHypixel() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;
            ServerListEntry server = mc.getCurrentServerEntry();
            return server != null && server.ip != null && server.ip.toLowerCase().contains("hypixel");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True once a game is running. The sidebar then lists every team and marks the player's own with YOU.
     * The lines are rebuilt from the scoreboard team's prefix and suffix, because that is where the text is.
     */
    public static boolean hasOwnTeamLine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.world == null || mc.world.getScoreboard() == null) return false;

        Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective sidebar = sb.getDisplayObjective(SIDEBAR_SLOT);
        if (sidebar == null) return false;

        List<String> lines = new ArrayList<String>();
        int count = 0;
        for (ScoreboardScore s : sb.getScores(sidebar)) {
            if (s == null || s.getOwner() == null) continue;
            Team team = sb.getTeamOfMember(s.getOwner());
            lines.add(clean(Team.getMemberDisplayName(team, s.getOwner())));
            if (++count >= 15) break;
        }
        return SidebarTeams.hasOwnTeamLine(lines);
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

    private static List<String> getSidebarLines(Scoreboard sb, ScoreboardObjective obj) {
        List<String> out = new ArrayList<String>();
        Collection<ScoreboardScore> scores = sb.getScores(obj);

        int count = 0;
        for (ScoreboardScore s : scores) {
            if (s == null) continue;
            String name = s.getOwner();
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
        return StringUtils.stripFormatting(s).replace("\u00A0", " ").trim();
    }
}
