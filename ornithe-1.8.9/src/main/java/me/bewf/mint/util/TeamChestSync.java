package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestBuffer;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.PartyQuery;
import me.bewf.mint.sync.PartyServers;
import me.bewf.mint.sync.PartyState;
import me.bewf.mint.sync.RecentMessages;
import me.bewf.mint.sync.TeamChestMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import java.util.List;
import java.util.Map;

public final class TeamChestSync {
    private TeamChestSync() {
    }

    private static final ChestBuffer BUFFER = new ChestBuffer(600L, 2500L, 1000L);

    private static PartyState party = PartyState.UNKNOWN;
    private static final PartyQuery QUERY = new PartyQuery();
    private static String lastHost = "";
    private static int nextSeq = 0;
    private static boolean tipPending = false;

    private static final String SETTINGS_COMMAND = "/mint settings";

    private static final RecentMessages SENT = new RecentMessages(15000L);

    private static final RecentMessages RECENT = new RecentMessages(30000L);

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
        McCompat.sendChat("/" + PartyQuery.COMMAND);
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

        if (McCompat.sendChat("/" + command + " " + body)) requestTip();
    }

    private static void onIncoming(ChatEvent.Receive event) {
        try {
            String stripped = StringUtils.stripFormatting(event.getFullyUnformattedMessage());
            boolean partyListReply = QUERY.hides(stripped, System.currentTimeMillis());
            party = party.after(stripped);
            if (partyListReply) {
                event.cancelled = true;
                return;
            }

            TeamChestMessage.PartyLine line = TeamChestMessage.party(stripped);
            if (line == null) return;
            TeamChestMessage.Parsed update = TeamChestMessage.parse(line.body);
            if (update == null) return;

            boolean echo = SENT.consumeIfOurs(line.body, System.currentTimeMillis());
            if (!echo || isOtherPlayer(line.sender)) applyFrom(line.sender, update);
            if (MintConfig.INSTANCE.readPartySync) requestTip();
            if (MintConfig.INSTANCE.hideSyncMessages) {
                event.cancelled = true;
            } else {
                recolor(event, line.body);
            }
        } catch (Throwable ignored) {
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

    private static void recolor(ChatEvent.Receive event, String body) {
        List<TeamChestMessage.Segment> segments = TeamChestMessage.segments(body, palette());
        if (segments == null) return;

        TextComponent.Builder colored = Component.text();
        for (TeamChestMessage.Segment segment : segments) {
            colored.append(Component.text(segment.text, NamedTextColor.nearestTo(TextColor.color(segment.rgb))));
        }
        event.setMessage(event.getMessage().replaceText(TextReplacementConfig.builder()
                .matchLiteral(body)
                .once()
                .replacement(colored.build())
                .build()));
    }

    private static void applyFrom(String sender, TeamChestMessage.Parsed update) {
        if (!MintConfig.INSTANCE.readPartySync || partyCommand() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (sender.equalsIgnoreCase(McCompat.playerName())) return;
        String what = sender + " " + TeamChestMessage.format(update.delta, -1);
        if (!ResourceTracker.isActive() && !BedwarsDetector.onHypixel() && !MintConfig.INSTANCE.showOutsideBedwars) {
            System.out.println("[Mint] Party update from " + what + " ignored, not in a game");
            return;
        }
        String otherTeam = teamMismatch(sender);
        if (otherTeam != null) {
            System.out.println("[Mint] Party update from " + what + " ignored, " + otherTeam);
            return;
        }

        if (update.seq >= 0) {
            Integer last = LAST_SEQ.get(sender.toLowerCase());
            if (last != null && last.intValue() == update.seq) return;
            LAST_SEQ.put(sender.toLowerCase(), Integer.valueOf(update.seq));
        }

        String skipped = ResourceTracker.applyTeammateUpdate(update.delta);
        System.out.println("[Mint] Party update from " + what + (skipped == null ? " applied" : " ignored, " + skipped));
    }

    private static boolean isOtherPlayer(String sender) {
        if (sender == null || sender.isEmpty() || sender.equalsIgnoreCase(McCompat.playerName())) return false;
        return McCompat.teamOf(sender) != null;
    }

    private static String teamMismatch(String sender) {
        Object theirs = McCompat.teamOf(sender);
        Object mine = McCompat.teamOf(McCompat.playerName());
        if (theirs == null || mine == null || theirs == mine) return null;
        String a = McCompat.teamPrefix(theirs);
        String b = McCompat.teamPrefix(mine);
        // Some servers give every player their own team, so matching prefixes count as the same team
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) return null;
        return "different team (theirs \"" + a + "\", yours \"" + b + "\")";
    }

    private static void requestTip() {
        MintConfig c = MintConfig.INSTANCE;
        if (c.hasShownSyncTip) return;
        c.hasShownSyncTip = true;
        MintConfig.saveConfig();
        tipPending = true;
    }

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
