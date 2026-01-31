package me.bewf.mint.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
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

    private UpdateChecker() {}

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
                if (latest == null) {
                    System.out.println("[" + displayName + "] Update check: no matching versions for " + mcVersion + " (" + loader + ")");
                    return;
                }

                if (!isNewer(latest, currentVersion)) {
                    System.out.println("[" + displayName + "] Update check: up to date (" + currentVersion + ")");
                    return;
                }

                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (Minecraft.getMinecraft().thePlayer == null) return;
                    Minecraft.getMinecraft().thePlayer.addChatMessage(buildMessage(projectSlug, displayName, latest, currentVersion));
                });

                System.out.println("[" + displayName + "] Update check: " + latest + " available (current " + currentVersion + ")");
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
        con.setRequestProperty("User-Agent", "MintUpdateChecker");

        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            System.out.println("[Mint] Update check HTTP " + code);
            return null;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);

            JsonElement parsed = new JsonParser().parse(sb.toString());
            if (!parsed.isJsonArray()) return null;

            JsonArray arr = parsed.getAsJsonArray();
            if (arr.size() == 0) return null;

            String best = null;
            int[] bestV = null;

            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                JsonElement vEl = obj.get("version_number");
                if (vEl == null) continue;

                String ver = vEl.getAsString();
                int[] pv = parseVersion(ver);

                if (best == null) {
                    best = ver;
                    bestV = pv;
                    continue;
                }

                if (compareVersion(pv, bestV) > 0) {
                    best = ver;
                    bestV = pv;
                }
            }

            return best;
        }
    }

    private static IChatComponent buildMessage(String projectSlug, String displayName, String latest, String current) {
        String versionsUrl = "https://modrinth.com/mod/" + projectSlug + "/versions";

        ChatComponentText root = new ChatComponentText("");

        IChatComponent prefix = new ChatComponentText(EnumChatFormatting.AQUA + "[" + displayName + "] ");
        IChatComponent text = new ChatComponentText(
                EnumChatFormatting.YELLOW + "A new update is available: " +
                        EnumChatFormatting.GOLD + latest +
                        EnumChatFormatting.YELLOW + " (current " +
                        EnumChatFormatting.GOLD + current +
                        EnumChatFormatting.YELLOW + "). " +
                        EnumChatFormatting.GREEN + "Click to download"
        );

        ChatStyle style = new ChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, versionsUrl))
                .setChatHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(EnumChatFormatting.GRAY + versionsUrl)
                ));

        text.setChatStyle(style);

        root.appendSibling(prefix);
        root.appendSibling(text);
        return root;
    }

    private static boolean isNewer(String latest, String current) {
        int[] a = parseVersion(latest);
        int[] b = parseVersion(current);
        return compareVersion(a, b) > 0;
    }

    private static int compareVersion(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        int[] out = new int[]{0, 0, 0};
        if (v == null) return out;

        String clean = v.trim();
        int dash = clean.indexOf('-');
        if (dash >= 0) clean = clean.substring(0, dash);

        String[] parts = clean.split("\\.");
        for (int i = 0; i < out.length && i < parts.length; i++) {
            try {
                String num = parts[i].replaceAll("[^0-9]", "");
                out[i] = num.isEmpty() ? 0 : Integer.parseInt(num);
            } catch (Throwable ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}
