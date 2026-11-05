package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestBuffer;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.PartyState;
import me.bewf.mint.sync.RecentMessages;
import me.bewf.mint.sync.TeamChestMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.StringUtils;
import net.minecraft.text.Text;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;

import java.util.HashMap;
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

    // The command the "click here" in the one time tip runs. It is caught before it reaches the server.
    private static final String SETTINGS_COMMAND = "/mint settings";

    // Party messages we sent ourselves, so the echo from the server is not counted a second time
    private static final RecentMessages SENT = new RecentMessages(15000L);

    private static final Map<String, Integer> LAST_SEQ = new HashMap<String, Integer>();

    public static void register() {
        EventManager.register(ChatEvent.Receive.class, event -> onIncoming(event));
        EventManager.register(ChatEvent.Send.class, event -> {
            if (SETTINGS_COMMAND.equals(event.message)) {
                event.cancelled = true;
                SettingsLink.open();
            }
        });
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String body = TeamChestMessage.format(d, nextSeq);
        if (body == null) return;
        nextSeq = (nextSeq + 1) % 100;
        SENT.remember(body, System.currentTimeMillis());

        if (McCompat.sendChat(mc.player, "/pc " + body)) requestTip();
    }

    private static void onIncoming(ChatEvent.Receive event) {
        try {
            String stripped = StringUtils.stripFormatting(event.getFullyUnformattedMessage());
            party = party.after(stripped);

            TeamChestMessage.PartyLine line = TeamChestMessage.party(stripped);
            if (line == null) return;
            TeamChestMessage.Parsed update = TeamChestMessage.parse(line.body);
            if (update == null) return;

            if (!SENT.consumeIfOurs(line.body, System.currentTimeMillis())) applyFrom(line.sender, update);
            if (MintConfig.INSTANCE.readPartySync) requestTip();
            // Shown messages keep the server's formatting. Recolouring them needs a text API this build avoids.
            if (MintConfig.INSTANCE.hideSyncMessages) event.cancelled = true;
        } catch (Throwable ignored) {
        }
    }

    private static void applyFrom(String sender, TeamChestMessage.Parsed update) {
        if (!MintConfig.INSTANCE.readPartySync || !BedwarsDetector.onHypixel()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Our own message comes back from the server, and we already know about it
        if (sender.equalsIgnoreCase(McCompat.playerName(mc.player))) return;
        if (!ResourceTracker.isActive() && !MintConfig.INSTANCE.showOutsideBedwars) return;

        // A party can hold players from different teams, and their chests are not ours
        Object theirs = McCompat.teamOf(mc.world, sender);
        Object mine = McCompat.teamOf(mc.world, McCompat.playerName(mc.player));
        if (theirs != null && mine != null && theirs != mine) return;

        if (update.seq >= 0) {
            Integer last = LAST_SEQ.get(sender.toLowerCase());
            if (last != null && last.intValue() == update.seq) return;
            LAST_SEQ.put(sender.toLowerCase(), Integer.valueOf(update.seq));
        }

        ResourceTracker.applyTeammateUpdate(update.delta);
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
        Text tip = new LiteralText("[Mint] ").setStyle(new Style().setColor(Formatting.AQUA));
        tip.append(new LiteralText("Mint shares team chest changes in party chat so teammates with the mod stay up to date. "
                + "Want to hide these messages or stop them from being sent? ").setStyle(new Style().setColor(Formatting.GRAY)));
        tip.append(new LiteralText("click here").setStyle(new Style()
                .setColor(Formatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, SETTINGS_COMMAND))));
        tip.append(new LiteralText(".").setStyle(new Style().setColor(Formatting.GRAY)));
        Chat.send(tip);
    }
}
