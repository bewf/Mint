package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestBuffer;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.PartyState;
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

/**
 * Shares team chest changes with the party and reads the ones teammates share.
 * <p>
 * Changes are collected in a small buffer and sent as one party message, for example
 * {@code [Mint | Team Chest: +10 iron, -4 dia #7]}. Nothing is sent outside a party, and Hypixel's
 * own "not in a party" reply stops further tries. The message format and all the timing rules live in
 * the shared {@code me.bewf.mint.sync} package.
 */
public final class TeamChestSync {

    private TeamChestSync() {
    }

    // A burst of punches leaves as one message after 1.5 seconds, and two messages are never closer than a second
    private static final ChestBuffer BUFFER = new ChestBuffer(1500L, 1000L);

    private static PartyState party = PartyState.UNKNOWN;
    private static int nextSeq = 0;
    private static boolean tipPending = false;
    private static boolean sendFailureLogged = false;

    // The command the "click here" in the one time tip runs. It is caught before it reaches the server.
    private static final String SETTINGS_COMMAND = "mint settings";

    // The last counter seen from each sender, so a repeated message is not applied twice
    private static final Map<String, Integer> LAST_SEQ = new HashMap<>();

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            return onIncoming(message.getString());
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : colorize(message));
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String c = command.startsWith("/") ? command.substring(1) : command;
            if (!c.equals(SETTINGS_COMMAND)) return true;
            SettingsLink.open();
            return false;
        });
    }

    /** The player changed the team chest. Deposits are positive, withdrawals negative. */
    public static void record(Delta change) {
        if (!MintConfig.INSTANCE.partySync || !party.canSend()) return;
        BUFFER.add(change, System.currentTimeMillis());
    }

    /** The player closed the chest: send what is waiting as soon as the minimum gap allows. */
    public static void flush() {
        BUFFER.requestFlush();
    }

    /** Called every tick while in a game. */
    public static void tick() {
        if (tipPending) {
            tipPending = false;
            showTip();
        }
        Delta ready = BUFFER.poll(System.currentTimeMillis());
        if (ready == null) return;
        if (!MintConfig.INSTANCE.partySync || !party.canSend()) return;
        send(ready);
    }

    public static void resetGame() {
        BUFFER.reset();
        LAST_SEQ.clear();
    }

    private static void send(Delta d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String body = TeamChestMessage.format(d, nextSeq);
        if (body == null) return;
        nextSeq = (nextSeq + 1) % 100;

        Object connection = mc.getConnection();
        boolean sent = connection != null && ReflectUtil.tryCall(connection, "sendCommand", "pc " + body);
        if (!sent) {
            if (!sendFailureLogged) {
                sendFailureLogged = true;
                System.out.println("[Mint] Could not send the team chest update to party chat");
            }
            return;
        }

        requestTip();
    }

    /** Asks for the one time tip. It shows on the next tick, after the message that caused it. */
    private static void requestTip() {
        MintConfig c = MintConfig.INSTANCE;
        if (c.hasShownSyncTip) return;
        c.hasShownSyncTip = true;
        MintConfig.saveConfig();
        tipPending = true;
    }

    /** Shown once, the first time Mint sends or receives a team chest update. */
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
        // The method name differs between versions, so it is looked up by name like the other chat messages
        if (!ReflectUtil.tryCall(mc.player, "displayClientMessage", tip, false)) {
            ReflectUtil.tryCall(mc.player, "sendSystemMessage", tip);
        }
    }

    /** Looks at one chat line. Returns false to hide it from the chat. */
    private static boolean onIncoming(String raw) {
        String stripped = StringUtil.stripColor(raw);
        party = party.after(stripped);

        TeamChestMessage.PartyLine line = TeamChestMessage.party(stripped);
        if (line == null) return true;
        TeamChestMessage.Parsed update = TeamChestMessage.parse(line.body);
        if (update == null) return true;

        applyFrom(line.sender, update);
        if (MintConfig.INSTANCE.readPartySync) requestTip();
        return !MintConfig.INSTANCE.hideSyncMessages;
    }

    private static void applyFrom(String sender, TeamChestMessage.Parsed update) {
        if (!MintConfig.INSTANCE.readPartySync) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Our own message comes back from the server, and we already know about it
        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
        if (!ResourceTracker.isActive() && !MintConfig.INSTANCE.showOutsideBedwars) return;
        if (!onMyTeam(mc, sender)) return;

        if (update.seq >= 0) {
            Integer last = LAST_SEQ.get(sender.toLowerCase());
            if (last != null && last == update.seq) return;
            LAST_SEQ.put(sender.toLowerCase(), update.seq);
        }

        ResourceTracker.applyTeammateUpdate(update.delta);
    }

    /**
     * A party can hold players from different teams, and their chests are not ours. Compares the
     * scoreboard teams. When either one is unknown the update is accepted.
     */
    private static boolean onMyTeam(Minecraft mc, String sender) {
        try {
            PlayerTeam theirs = mc.level.getScoreboard().getPlayersTeam(sender);
            PlayerTeam mine = mc.level.getScoreboard().getPlayersTeam(mc.player.getName().getString());
            if (theirs == null || mine == null) return true;
            return theirs == mine;
        } catch (Throwable t) {
            return true;
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

    /** Colours a shown update: green deposits, red withdrawals, each resource in its own colour. */
    private static Component colorize(Component message) {
        try {
            String flat = message.getString();
            TeamChestMessage.PartyLine line = TeamChestMessage.party(StringUtil.stripColor(flat));
            if (line == null) return message;
            List<TeamChestMessage.Segment> segments = TeamChestMessage.segments(line.body, palette());
            if (segments == null) return message;

            int bodyStart = flat.lastIndexOf(line.body);
            if (bodyStart < 0) return message;

            // Keep everything before the update (the "Party > [rank] name:" part) exactly as it was
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
