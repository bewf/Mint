package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestBuffer;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.PartyState;
import me.bewf.mint.sync.RecentMessages;
import me.bewf.mint.sync.TeamChestMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraft.event.ClickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // The command the "click here" in the one time tip runs (see MintCommand)
    private static final String SETTINGS_COMMAND = "/mint settings";

    // Party messages we sent ourselves, so the echo from the server is not counted a second time
    private static final RecentMessages SENT = new RecentMessages(15000L);

    // The last counter seen from each sender, so a repeated message is not applied twice
    private static final Map<String, Integer> LAST_SEQ = new HashMap<String, Integer>();

    /** Register an instance of this on the Forge event bus. */
    public static final class Listener {
        @SubscribeEvent
        public void onChat(ClientChatReceivedEvent event) {
            if (event.message == null || event.type == 2) return;
            try {
                handle(event);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void handle(ClientChatReceivedEvent event) {
        String stripped = StringUtils.stripControlCodes(event.message.getUnformattedText());
        party = party.after(stripped);

        TeamChestMessage.PartyLine line = TeamChestMessage.party(stripped);
        if (line == null) return;
        TeamChestMessage.Parsed update = TeamChestMessage.parse(line.body);
        if (update == null) return;

        if (!SENT.consumeIfOurs(line.body, System.currentTimeMillis())) applyFrom(line.sender, update);
        if (MintConfig.INSTANCE.readPartySync) requestTip();

        if (MintConfig.INSTANCE.hideSyncMessages) {
            event.setCanceled(true);
        } else {
            event.message = colorize(event.message);
        }
    }

    /** The player changed the team chest. Deposits are positive, withdrawals negative. */
    public static void record(Delta change) {
        if (!MintConfig.INSTANCE.partySync || !party.canSend() || !BedwarsDetector.onHypixel()) return;
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
        SENT.clear();
    }

    private static void send(Delta d) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String body = TeamChestMessage.format(d, nextSeq);
        if (body == null) return;
        nextSeq = (nextSeq + 1) % 100;
        SENT.remember(body, System.currentTimeMillis());

        mc.thePlayer.sendChatMessage("/pc " + body);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        ChatComponentText click = new ChatComponentText("click here");
        click.setChatStyle(new ChatStyle()
                .setColor(EnumChatFormatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, SETTINGS_COMMAND)));

        ChatComponentText tip = new ChatComponentText("[Mint] ");
        tip.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA));
        tip.appendSibling(gray("Mint shares team chest changes in party chat so teammates with the mod stay up to date. "
                + "Want to hide these messages or stop them from being sent? "));
        tip.appendSibling(click);
        tip.appendSibling(gray("."));
        mc.thePlayer.addChatMessage(tip);
    }

    private static IChatComponent gray(String text) {
        ChatComponentText c = new ChatComponentText(text);
        c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
        return c;
    }

    private static void applyFrom(String sender, TeamChestMessage.Parsed update) {
        if (!MintConfig.INSTANCE.readPartySync || !BedwarsDetector.onHypixel()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Our own message comes back from the server, and we already know about it
        if (sender.equalsIgnoreCase(mc.thePlayer.getName())) return;
        if (!ResourceTracker.isActive() && !MintConfig.INSTANCE.showOutsideBedwars) return;
        if (!onMyTeam(mc, sender)) return;

        if (update.seq >= 0) {
            Integer last = LAST_SEQ.get(sender.toLowerCase());
            if (last != null && last.intValue() == update.seq) return;
            LAST_SEQ.put(sender.toLowerCase(), Integer.valueOf(update.seq));
        }

        ResourceTracker.applyTeammateUpdate(update.delta);
    }

    /**
     * A party can hold players from different teams, and their chests are not ours. Compares the
     * scoreboard teams. When either one is unknown the update is accepted.
     */
    private static boolean onMyTeam(Minecraft mc, String sender) {
        try {
            ScorePlayerTeam theirs = mc.theWorld.getScoreboard().getPlayersTeam(sender);
            ScorePlayerTeam mine = mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
            if (theirs == null || mine == null) return true;
            return theirs == mine;
        } catch (Throwable t) {
            return true;
        }
    }

    private static int rgb(me.bewf.mint.config.MintConfig c, cc.polyfrost.oneconfig.config.core.OneColor color, int fallback) {
        try {
            return color.getRGB() & 0xFFFFFF;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static TeamChestMessage.Palette palette() {
        MintConfig c = MintConfig.INSTANCE;
        TeamChestMessage.Palette p = new TeamChestMessage.Palette();
        p.plus = rgb(c, c.syncPlusColor, p.plus);
        p.minus = rgb(c, c.syncMinusColor, p.minus);
        p.resource[Delta.IRON] = rgb(c, c.ironColor, p.resource[Delta.IRON]);
        p.resource[Delta.GOLD] = rgb(c, c.goldColor, p.resource[Delta.GOLD]);
        p.resource[Delta.DIAMOND] = rgb(c, c.diamondColor, p.resource[Delta.DIAMOND]);
        p.resource[Delta.EMERALD] = rgb(c, c.emeraldColor, p.resource[Delta.EMERALD]);
        return p;
    }

    /** Colours a shown update: green deposits, red withdrawals, each resource in its own colour. */
    private static IChatComponent colorize(IChatComponent message) {
        try {
            String stripped = StringUtils.stripControlCodes(message.getUnformattedText());
            TeamChestMessage.PartyLine line = TeamChestMessage.party(stripped);
            if (line == null) return message;
            List<TeamChestMessage.Segment> segments = TeamChestMessage.segments(line.body, palette());
            if (segments == null) return message;

            // Everything before the update (the "Party > [rank] name:" part) is kept as formatted text
            String formatted = message.getFormattedText();
            int bodyStart = formatted.lastIndexOf(line.body);
            if (bodyStart < 0) return message;

            ChatComponentText out = new ChatComponentText(formatted.substring(0, bodyStart));
            for (TeamChestMessage.Segment s : segments) {
                ChatComponentText part = new ChatComponentText(s.text);
                part.setChatStyle(new ChatStyle().setColor(LegacyColors.nearest(s.rgb)));
                out.appendSibling(part);
            }
            return out;
        } catch (Throwable t) {
            return message;
        }
    }
}
