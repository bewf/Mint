package me.bewf.mint.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static boolean ran = false;

    private UpdateChecker() {
    }

    public static void checkOnce(String projectId,
                                 String projectSlug,
                                 String displayName,
                                 String currentVersion,
                                 String mcVersion,
                                 String loader) {
        if (ran) return;
        ran = true;

        EXEC.submit(() -> {
            try {
                String latest = fetchBestLatestVersion(projectId, mcVersion, loader);
                if (latest == null) return;

                if (!isNewer(latest, currentVersion)) return;

                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (Minecraft.getMinecraft().thePlayer == null) return;
                    Minecraft.getMinecraft().thePlayer.addChatMessage(
                            buildMessage(projectSlug, displayName, latest, currentVersion)
                    );
                });
            } catch (Throwable t) {
                System.err.println("[" + displayName + "] Update check failed: " + t);
            }
        });
    }

    private static String fetchBestLatestVersion(String projectId, String mcVersion, String loader) throws Exception {
        String gv = "[\"" + mcVersion + "\"]";
        String ld = "[\"" + loader + "\"]";

        String apiUrl =
                "https://api.modrinth.com/v2/project/" + projectId + "/version" +
                        "?limit=50" +
                        "&game_versions=" + URLEncoder.encode(gv, "UTF-8") +
                        "&loaders=" + URLEncoder.encode(ld, "UTF-8");

        HttpURLConnection con = (HttpURLConnection) new URL(apiUrl).openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(6000);
        con.setReadTimeout(6000);

        if (con.getResponseCode() < 200 || con.getResponseCode() >= 300) return null;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {

            JsonArray arr = new JsonParser().parse(br.readLine()).getAsJsonArray();

            String best = null;
            int[] bestV = null;

            for (JsonElement el : arr) {
                String ver = el.getAsJsonObject().get("version_number").getAsString();
                int[] pv = parseVersion(ver);

                if (best == null || compareVersion(pv, bestV) > 0) {
                    best = ver;
                    bestV = pv;
                }
            }
            return best;
        }
    }

    private static boolean isNewer(String latest, String current) {
        return compareVersion(parseVersion(latest), parseVersion(current)) > 0;
    }

    private static int compareVersion(int[] a, int[] b) {
        if (b == null) return 1;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? a[i] : 0;
            int bi = i < b.length ? b[i] : 0;
            if (ai != bi) return ai - bi;
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        int[] out = new int[]{0, 0, 0};
        if (v == null) return out;

        String clean = v.split("-")[0];
        String[] parts = clean.split("\\.");

        for (int i = 0; i < out.length && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static IChatComponent buildMessage(String slug, String name, String latest, String current) {
        String versionsUrl = "https://modrinth.com/mod/" + slug + "/versions";

        ChatComponentText root = new ChatComponentText("\n");

        IChatComponent prefix = new ChatComponentText(
                EnumChatFormatting.AQUA + "[" + name + "] "
        );

        IChatComponent line1 = new ChatComponentText(
                EnumChatFormatting.YELLOW + "Update available: " +
                        EnumChatFormatting.GOLD + latest +
                        EnumChatFormatting.YELLOW + " (current " +
                        EnumChatFormatting.GOLD + current +
                        EnumChatFormatting.YELLOW + ")"
        );

        IChatComponent line2 = new ChatComponentText(
                "\n" +
                        EnumChatFormatting.LIGHT_PURPLE +
                        EnumChatFormatting.BOLD.toString() +
                        "Click to download"
        );

        root.appendSibling(prefix);
        root.appendSibling(line1);
        root.appendSibling(line2);
        root.appendSibling(new ChatComponentText("\n"));

        line2.getChatStyle()
                .setChatClickEvent(new ClickEvent(
                        ClickEvent.Action.OPEN_URL,
                        versionsUrl
                ))
                .setChatHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(EnumChatFormatting.LIGHT_PURPLE + "Open versions page")
                ));

        return root;
    }
}