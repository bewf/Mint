package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestBuffer;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.PartyQuery;
import me.bewf.mint.sync.PartyServers;
import me.bewf.mint.sync.PartyState;
import me.bewf.mint.sync.RecentMessages;
import me.bewf.mint.sync.TeamChestMessage;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringUtil;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TeamChestSync {
    private TeamChestSync() {
    }

    private static final ChestBuffer BUFFER = new ChestBuffer(600L, 2500L, 1000L);

    private static PartyState party = PartyState.UNKNOWN;
    private static final PartyQuery QUERY = new PartyQuery();
    private static String lastHost = "";
    private static int nextSeq = 0;
    private static boolean tipPending = false;
    private static boolean sendFailureLogged = false;

    private static final String SETTINGS_COMMAND = "mint settings";

    private static final RecentMessages SENT = new RecentMessages(15000L);

    private static final RecentMessages RECENT = new RecentMessages(30000L);

    private static final Map<String, Integer> LAST_SEQ = new HashMap<>();

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            try {
                return onIncoming(message.getString());
            } catch (Throwable t) {
                return true;
            }
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : colorize(message));
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !handleSettingsCommand(command));
    }

    public static boolean handleSettingsCommand(String command) {
        if (command == null) return false;
        String c = command.startsWith("/") ? command.substring(1) : command;
        if (!c.trim().equals(SETTINGS_COMMAND)) return false;
        SettingsLink.open();
        return true;
    }

    private static String partyCommand() {
        MintConfig c = MintConfig.INSTANCE;
        return PartyServers.commandFor(BedwarsDetector.serverAddress(),
                new String[]{c.partyServer1Address, c.partyServer2Address, c.partyServer3Address, c.partyServer4Address},
                new String[]{c.partyServer1Command, c.partyServer2Command, c.partyServer3Command, c.partyServer4Command});
    }

    public static void record(Delta change) {
        watchServer();
        if (!MintConfig.INSTANCE.partySync || party == PartyState.OUT || partyCommand() == null) return;
        BUFFER.add(change, System.currentTimeMillis());
    }

    public static void flush() {
        BUFFER.requestFlush();
    }

    public static void tick() {
        watchServer();
        if (tipPending) {
            tipPending = false;
            showTip();
        }
        if (!MintConfig.INSTANCE.partySync || party == PartyState.OUT) {
            BUFFER.reset();
            return;
        }
        if (!BUFFER.hasPending()) return;

        long now = System.currentTimeMillis();
        if (!party.canSend(checksParty())) {
            if (QUERY.shouldAsk(party, now)) askParty(now);
            return;
        }
        Delta ready = BUFFER.poll(now);
        if (ready != null) send(ready);
    }

    private static void watchServer() {
        String host = PartyServers.host(BedwarsDetector.serverAddress());
        if (host.equals(lastHost)) return;
        lastHost = host;
        party = PartyState.UNKNOWN;
        QUERY.reset();
    }

    private static boolean checksParty() {
        return PartyServers.isHypixel(BedwarsDetector.serverAddress());
    }

    private static void askParty(long now) {
        QUERY.asked(now);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Object connection = mc.getConnection();
        if (connection != null) ReflectUtil.tryCall(connection, "sendCommand", PartyQuery.COMMAND);
    }

    public static void resetGame() {
        BUFFER.reset();
        QUERY.reset();
        LAST_SEQ.clear();
        SENT.clear();
        RECENT.clear();
    }

    private static void send(Delta d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String command = partyCommand();
        if (command == null) return;

        long now = System.currentTimeMillis();
        String plain = TeamChestMessage.format(d, -1);
        if (plain == null) return;
        String body = plain;
        // Hypixel drops a message that repeats one just sent, so only a repeat gets a counter
        if (RECENT.has(plain, now)) {
            body = TeamChestMessage.format(d, nextSeq);
            nextSeq = (nextSeq + 1) % 100;
        }
        RECENT.remember(plain, now);
        SENT.remember(body, now);

        Object connection = mc.getConnection();
        boolean sent = connection != null && ReflectUtil.tryCall(connection, "sendCommand", command + " " + body);
        if (!sent) {
            if (!sendFailureLogged) {
                sendFailureLogged = true;
                System.out.println("[Mint] Could not send the team chest update to party chat");
            }
            return;
        }

        requestTip();
    }

    private static void requestTip() {
        MintConfig c = MintConfig.INSTANCE;
        if (c.hasShownSyncTip) return;
        c.hasShownSyncTip = true;
        MintConfig.saveConfig();
        tipPending = true;
    }

    private static void showTip() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component click = Component.literal("click here")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(SETTINGS_COMMAND)));

        Component tip = Component.literal("[Mint] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Mint shares team chest changes in party chat so teammates with the mod stay up to date. "
                        + "Want to hide these messages or stop them from being sent? ").withStyle(ChatFormatting.GRAY))
                .append(click)
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
        if (!ReflectUtil.tryCall(mc.player, "displayClientMessage", tip, false)) {
            ReflectUtil.tryCall(mc.player, "sendSystemMessage", tip);
        }
    }

    private static boolean onIncoming(String raw) {
        String stripped = StringUtil.stripColor(raw);
        boolean partyListReply = QUERY.hides(stripped, System.currentTimeMillis());
        party = party.after(stripped);
        if (partyListReply) return false;

        TeamChestMessage.PartyLine line = TeamChestMessage.party(stripped);
        if (line == null) return true;
        TeamChestMessage.Parsed update = TeamChestMessage.parse(line.body);
        if (update == null) return true;

        boolean echo = SENT.consumeIfOurs(line.body, System.currentTimeMillis());
        if (!echo || isOtherPlayer(line.sender)) applyFrom(line.sender, update);
        if (MintConfig.INSTANCE.readPartySync) requestTip();
        return !MintConfig.INSTANCE.hideSyncMessages;
    }

    private static void applyFrom(String sender, TeamChestMessage.Parsed update) {
        if (!MintConfig.INSTANCE.readPartySync || partyCommand() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
        String what = sender + " " + TeamChestMessage.format(update.delta, -1);
        if (!ResourceTracker.isActive() && !BedwarsDetector.onHypixel() && !MintConfig.INSTANCE.showOutsideBedwars) {
            System.out.println("[Mint] Party update from " + what + " ignored, not in a game");
            return;
        }
        String otherTeam = teamMismatch(mc, sender);
        if (otherTeam != null) {
            System.out.println("[Mint] Party update from " + what + " ignored, " + otherTeam);
            return;
        }

        if (update.seq >= 0) {
            Integer last = LAST_SEQ.get(sender.toLowerCase());
            if (last != null && last == update.seq) return;
            LAST_SEQ.put(sender.toLowerCase(), update.seq);
        }

        String skipped = ResourceTracker.applyTeammateUpdate(update.delta);
        System.out.println("[Mint] Party update from " + what + (skipped == null ? " applied" : " ignored, " + skipped));
    }

    private static boolean isOtherPlayer(String sender) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || sender == null || sender.isEmpty()) return false;
            if (sender.equalsIgnoreCase(mc.player.getName().getString())) return false;
            return mc.level.getScoreboard().getPlayersTeam(sender) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String teamMismatch(Minecraft mc, String sender) {
        try {
            PlayerTeam theirs = mc.level.getScoreboard().getPlayersTeam(sender);
            PlayerTeam mine = mc.level.getScoreboard().getPlayersTeam(mc.player.getName().getString());
            if (theirs == null || mine == null || theirs == mine) return null;
            String a = StringUtil.stripColor(theirs.getPlayerPrefix().getString()).trim();
            String b = StringUtil.stripColor(mine.getPlayerPrefix().getString()).trim();
            // Some servers give every player their own team, so matching prefixes count as the same team
            if (a.isEmpty() || b.isEmpty() || a.equals(b)) return null;
            return "different team (theirs " + theirs.getName() + " \"" + a + "\", yours " + mine.getName() + " \"" + b + "\")";
        } catch (Throwable t) {
            return null;
        }
    }

    private static TeamChestMessage.Palette palette() {
        MintConfig c = MintConfig.INSTANCE;
        TeamChestMessage.Palette p = new TeamChestMessage.Palette();
        p.plus = c.syncPlusColor.getRawArgb() & 0xFFFFFF;
        p.minus = c.syncMinusColor.getRawArgb() & 0xFFFFFF;
        p.resource[Delta.IRON] = c.ironColor.getRawArgb() & 0xFFFFFF;
        p.resource[Delta.GOLD] = c.goldColor.getRawArgb() & 0xFFFFFF;
        p.resource[Delta.DIAMOND] = c.diamondColor.getRawArgb() & 0xFFFFFF;
        p.resource[Delta.EMERALD] = c.emeraldColor.getRawArgb() & 0xFFFFFF;
        return p;
    }

    private static Component colorize(Component message) {
        try {
            String flat = message.getString();
            TeamChestMessage.PartyLine line = TeamChestMessage.party(StringUtil.stripColor(flat));
            if (line == null) return message;
            List<TeamChestMessage.Segment> segments = TeamChestMessage.segments(line.body, palette());
            if (segments == null) return message;

            int bodyStart = flat.lastIndexOf(line.body);
            if (bodyStart < 0) return message;

            MutableComponent out = Component.empty();
            int[] offset = {0};
            message.visit((style, text) -> {
                int start = offset[0];
                if (start < bodyStart) {
                    out.append(Component.literal(text.substring(0, Math.min(text.length(), bodyStart - start))).setStyle(style));
                }
                offset[0] = start + text.length();
                return Optional.empty();
            }, Style.EMPTY);

            for (TeamChestMessage.Segment s : segments) {
                out.append(Component.literal(s.text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(s.rgb))));
            }
            return out;
        } catch (Throwable t) {
            return message;
        }
    }
}
