package me.bewf.mint.sync;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the Bed Wars sidebar. Once a game is running, the sidebar lists every team and marks the
 * player's own one with "YOU" (Hypixel) or "(You)" (other servers), so that line means the game has started.
 */
public final class SidebarTeams {

    private SidebarTeams() {
    }

    // "G Green: ✓ YOU", "Yellow (You)", "Y Yellow: ✓ YOU"
    private static final Pattern OWN_TEAM = Pattern.compile(
            "^(?:[A-Za-z] )?(?:Red|Blue|Green|Yellow|Aqua|White|Pink|Gr[ae]y|Orange|Purple|Cyan)\\b.*\\(?\\bYOU\\b\\)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** True when one of the sidebar lines is the player's own team. Lines must already be free of colour codes. */
    public static boolean hasOwnTeamLine(List<String> lines) {
        if (lines == null) return false;
        for (String line : lines) {
            if (line != null && OWN_TEAM.matcher(line.trim()).matches()) return true;
        }
        return false;
    }
}
