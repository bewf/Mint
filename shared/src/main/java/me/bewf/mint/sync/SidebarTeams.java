package me.bewf.mint.sync;

import java.util.List;
import java.util.regex.Pattern;

public final class SidebarTeams {
    private SidebarTeams() {
    }

    private static final Pattern OWN_TEAM = Pattern.compile(
            "^(?:[A-Za-z] )?(?:Red|Blue|Green|Yellow|Aqua|White|Pink|Gr[ae]y|Orange|Purple|Cyan)\\b.*\\(?\\bYOU\\b\\)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    public static boolean hasOwnTeamLine(List<String> lines) {
        if (lines == null) return false;
        for (String line : lines) {
            if (line != null && OWN_TEAM.matcher(line.trim()).matches()) return true;
        }
        return false;
    }
}
