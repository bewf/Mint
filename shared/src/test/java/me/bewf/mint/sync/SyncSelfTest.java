package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Plain checks for the shared sync code. Run with shared/test.sh, no test framework needed. */
public final class SyncSelfTest {

    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static void eq(Object a, Object b, String what) {
        check(a == null ? b == null : a.equals(b), what + " (got " + a + ", wanted " + b + ")");
    }

    public static void main(String[] args) {
        message();
        party();
        buffer();
        ledger();
        partyState();
        picker();
        projection();
        label();
        sidebar();
        recent();
        servers();
        deposits();
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    private static void message() {
        eq(TeamChestMessage.format(new Delta(10, 3, 0, 0), 7), "[Mint | Team Chest: +10 iron, +3 gold #7]", "format");
        eq(TeamChestMessage.format(new Delta(0, 0, -4, 0), -1), "[Mint | Team Chest: -4 dia]", "format without counter");
        eq(TeamChestMessage.format(new Delta(), 1), null, "empty delta sends nothing");
        eq(TeamChestMessage.format(new Delta(20000, 0, 0, 0), -1), "[Mint | Team Chest: +9999 iron]", "oversize is capped");
        eq(TeamChestMessage.format(new Delta(1, 1, 1, 1), 105), "[Mint | Team Chest: +1 iron, +1 gold, +1 dia, +1 em #5]", "counter wraps to two digits");

        Delta d = new Delta(10, -3, 0, 2);
        TeamChestMessage.Parsed p = TeamChestMessage.parse(TeamChestMessage.format(d, 42));
        check(p != null && p.delta.equals(d) && p.seq == 42, "round trip");

        check(TeamChestMessage.parse("[Mint | Team Chest: +1 iron, +2 iron]") == null, "duplicate resource rejected");
        check(TeamChestMessage.parse("[Mint | Team Chest: +0 iron]") == null, "zero rejected");
        check(TeamChestMessage.parse("[Mint | Team Chest: +10000 iron]") == null, "five digits rejected");
        check(TeamChestMessage.parse("[Mint | Team Chest: +5 wood]") == null, "unknown resource rejected");
        check(TeamChestMessage.parse("hello [Mint | Team Chest: +5 iron]") == null, "must be the whole line");
        check(TeamChestMessage.parse("[Mint | Team Chest: +5 iron] thanks") == null, "no trailing text");
        check(TeamChestMessage.parse("[Mint | Team Chest: ]") == null, "empty rejected");
        check(TeamChestMessage.parse("[Mint | Team Chest: +1 iron, +1 gold, +1 dia, +1 em, +1 iron]") == null, "five items rejected");
        check(TeamChestMessage.parse(null) == null, "null");

        TeamChestMessage.Palette pal = new TeamChestMessage.Palette();
        String body = "[Mint | Team Chest: +10 iron, -4 dia #7]";
        List<TeamChestMessage.Segment> segs = TeamChestMessage.segments(body, pal);
        StringBuilder joined = new StringBuilder();
        for (TeamChestMessage.Segment s : segs) joined.append(s.text);
        eq(joined.toString(), body, "segments join back to the body");
        boolean plusGreen = false, minusRed = false, diaAqua = false;
        for (TeamChestMessage.Segment s : segs) {
            if (s.text.equals("+10") && s.rgb == pal.plus) plusGreen = true;
            if (s.text.equals("-4") && s.rgb == pal.minus) minusRed = true;
            if (s.text.equals("dia") && s.rgb == pal.resource[Delta.DIAMOND]) diaAqua = true;
        }
        check(plusGreen && minusRed && diaAqua, "segment colours");
        check(TeamChestMessage.segments("not ours", pal) == null, "segments of a foreign line");
    }

    private static void party() {
        TeamChestMessage.PartyLine l = TeamChestMessage.party("Party > [MVP+] bewf: [Mint | Team Chest: +1 iron]");
        check(l != null && l.sender.equals("bewf") && l.body.equals("[Mint | Team Chest: +1 iron]"), "ranked sender");
        l = TeamChestMessage.party("Party > bewf: hi");
        check(l != null && l.sender.equals("bewf"), "unranked sender");
        l = TeamChestMessage.party("Party > [MVP++] a_b1: x: y");
        check(l != null && l.sender.equals("a_b1") && l.body.equals("x: y"), "colon in text");
        check(TeamChestMessage.party("Guild > bewf: hi") == null, "guild chat is not party chat");
        check(TeamChestMessage.party("[MVP+] bewf: Party > fake: hi") == null, "spoofed prefix inside normal chat");
        l = TeamChestMessage.party("[Party] bewf: [Mint | Team Chest: +1 iron]");
        check(l != null && l.sender.equals("bewf") && l.body.startsWith("[Mint"), "bracket party prefix of another server");
        l = TeamChestMessage.party("Party | [Rank] bewf: hi");
        check(l != null && l.sender.equals("bewf"), "bar party prefix of another server");
        check(TeamChestMessage.party("[MVP+] Party: [Mint | Team Chest: +1 iron]") == null, "a player called Party in normal chat");
    }

    private static void buffer() {
        ChestBuffer b = new ChestBuffer(1500, 1000);
        b.add(new Delta(10, 0, 0, 0), 0);
        b.add(new Delta(0, 3, 0, 0), 500);
        check(b.poll(1000) == null, "nothing before the window ends");
        Delta out = b.poll(1500);
        eq(out, new Delta(10, 3, 0, 0), "burst combined into one");
        check(b.poll(1600) == null, "nothing left after sending");

        // nine quick punches are still a single message
        ChestBuffer nine = new ChestBuffer(1500, 1000);
        for (int i = 0; i < 9; i++) nine.add(new Delta(64, 0, 0, 0), i * 100);
        int sent = 0;
        for (long t = 0; t < 5000; t += 50) if (nine.poll(t) != null) sent++;
        eq(sent, 1, "nine punches, one message");

        // a flush request goes out early but still waits for the minimum gap
        ChestBuffer f = new ChestBuffer(1500, 1000);
        f.add(new Delta(1, 0, 0, 0), 0);
        check(f.poll(1500) != null, "first message");
        f.add(new Delta(0, 1, 0, 0), 1600);
        f.requestFlush();
        check(f.poll(1700) == null, "flush waits for the minimum gap");
        check(f.poll(2500) != null, "flush goes out after the gap");

        // deposit then withdraw the same amount cancels
        ChestBuffer c = new ChestBuffer(1500, 1000);
        c.add(new Delta(5, 0, 0, 0), 0);
        c.add(new Delta(-5, 0, 0, 0), 100);
        check(c.poll(2000) == null, "cancelled out sends nothing");
        check(!c.hasPending(), "cancelled buffer is empty");
    }

    private static void ledger() {
        TeamChestLedger l = new TeamChestLedger();
        // a fresh game starts with an empty chest, so teammates' updates apply straight away
        check(l.applyRemote(new Delta(10, 0, 0, 0), 10_000, false), "applies at game start");
        eq(l.get(Delta.IRON), 10, "iron after update");

        check(!l.applyRemote(new Delta(5, 0, 0, 0), 10_100, true), "ignored while chest open");

        l.look(40, 0, 0, 0, 20_000);
        check(!l.applyRemote(new Delta(5, 0, 0, 0), 21_000, false), "ignored right after a look");
        eq(l.get(Delta.IRON), 40, "look wins");
        check(l.applyRemote(new Delta(-15, 0, 0, 0), 24_000, false), "applies later");
        eq(l.get(Delta.IRON), 25, "withdrawal applied");
        l.applyRemote(new Delta(-100, 0, 0, 0), 30_000, false);
        eq(l.get(Delta.IRON), 0, "never below zero");

        l.total(Delta.GOLD, 12, 40_000);
        eq(l.get(Delta.GOLD), 12, "punch total");
        check(!l.applyRemote(new Delta(0, 1, 0, 0), 41_000, false), "punch total counts as a look");
        l.reset();
        eq(l.get(Delta.GOLD), 0, "reset");
    }

    private static void partyState() {
        PartyState s = PartyState.UNKNOWN;
        check(s.canSend(), "unknown state still tries");
        s = s.after("You are not in a party right now.");
        eq(s, PartyState.OUT, "not in a party");
        check(!s.canSend(), "no sending when out");
        s = s.after("You have joined [MVP+] Steve's party!");
        eq(s, PartyState.IN, "joined");
        s = s.after("Party > [MVP+] Steve: You left the party.");
        eq(s, PartyState.IN, "party chat text cannot end the party");
        s = s.after("You left the party.");
        eq(s, PartyState.OUT, "left");
        s = s.after("[VIP] Alex joined the party.");
        eq(s, PartyState.IN, "someone joined");
        s = s.after("The party was disbanded because all invites expired and the party was empty.");
        eq(s, PartyState.OUT, "disbanded");
        s = s.after("Party Members (3)");
        eq(s, PartyState.IN, "party list");
        s = s.after("Just some other line");
        eq(s, PartyState.IN, "unrelated lines change nothing");
    }

    private static List<int[]> chests(int[]... c) {
        return new ArrayList<int[]>(Arrays.asList(c));
    }

    private static void picker() {
        // player at spawn facing +Z
        int[] team = {3, 64, 2};
        int[] enemy = {45, 64, 0};
        int[] above = {1, 72, 3};
        int[] behind = {0, 64, -3};
        int[] best = ChestPicker.pick(0.5, 64, 0.5, 0, chests(enemy, above, team));
        check(best == team, "picks the near chest, ignores the other island and the wrong height");

        best = ChestPicker.pick(0.5, 64, 0.5, 0, chests(behind, team));
        check(best == team, "prefers the one in front over one directly behind");

        best = ChestPicker.pick(0.5, 64, 0.5, 0, chests(behind));
        check(best == behind, "a chest behind is still better than none");

        check(ChestPicker.pick(0.5, 64, 0.5, 0, chests(enemy)) == null, "nothing in range");

        // chest to the right: facing +Z, right is -X
        int[] right = {-5, 64, 1};
        int[] left = {7, 64, 1};
        best = ChestPicker.pick(0.5, 64, 0.5, 0, chests(left, right));
        check(best == right, "closer side wins");

        // far chest wider than a map's usual base is still found
        int[] wide = {15, 65, 4};
        check(ChestPicker.pick(0.5, 64, 0.5, 0, chests(wide)) == wide, "wide search range");

        check(ChestPicker.sameChest(new int[]{0, 64, 0}, new int[]{1, 64, 0}), "double chest halves match");
        check(!ChestPicker.sameChest(new int[]{0, 64, 0}, new int[]{2, 64, 0}), "two blocks apart are different chests");
        check(!ChestPicker.sameChest(null, new int[]{0, 0, 0}), "null never matches");
    }

    private static void projection() {
        // looking along +Z, a point straight ahead lands in the middle
        double[] p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, 10);
        check(p != null && Math.abs(p[0] - 200) < 1e-6 && Math.abs(p[1] - 150) < 1e-6, "straight ahead is centred");
        eq(Math.round(p[2]), 10L, "distance");

        // facing +Z, the player's right is -X: a point at -X shows on the right half
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, -2, 64, 10);
        check(p != null && p[0] > 200 && Math.abs(p[1] - 150) < 1e-6, "right of the view is right on screen");
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 2, 64, 10);
        check(p != null && p[0] < 200, "left of the view is left on screen");

        // higher in the world is higher on the screen (smaller y)
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 66, 10);
        check(p != null && p[1] < 150, "above is up");

        // behind the camera
        check(Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, -5) == null, "behind the camera");

        // yaw 90 looks along -X
        p = Projection.project(0, 64, 0, 90, 0, 70, 400, 300, -10, 64, 0);
        check(p != null && Math.abs(p[0] - 200) < 1e-6, "yaw 90 faces -X");

        // looking down by 45 degrees puts a point at the same height below the centre line
        p = Projection.project(0, 64, 0, 0, 45, 70, 400, 300, 0, 64, 10);
        check(p != null && p[1] < 150, "looking down puts level points above centre");

        // the vertical field of view maps to the screen height
        p = Projection.project(0, 64, 0, 0, 0, 90, 400, 300, 0, 64 + 10, 10);
        check(p != null && Math.abs(p[1] - 0) < 1e-6, "edge of the vertical field of view");

        check(Projection.scaleFor(3) == 1.0f, "full size up close");
        check(Projection.scaleFor(12) == 0.5f, "half size at 12 blocks");
        check(Projection.scaleFor(500) == 0.45f, "never smaller than the floor");
    }

    private static void label() {
        boolean[] all = {true, true, true, true};
        int[] none = {0, 0, 0, 0};
        List<WaypointLabel.Part> parts = WaypointLabel.parts(new int[]{64, 0, 3, 8}, none, all, WaypointLabel.AMOUNT_CHEST);
        eq(parts.size(), 3, "zero resources are left out");
        eq(parts.get(0).count + parts.get(0).letter, "64i", "iron part");
        eq(parts.get(2).count + parts.get(2).letter, "8e", "emerald part");
        eq(WaypointLabel.parts(new int[]{64, 0, 3, 8}, none, new boolean[]{true, true, true, false},
                WaypointLabel.AMOUNT_CHEST).size(), 2, "a resource switched off is left out");
        eq(WaypointLabel.parts(new int[]{0, 5, 0, 0}, none, new boolean[]{false, false, false, true},
                WaypointLabel.AMOUNT_CHEST).size(), 0, "only emeralds on and none in the chest");
        eq(WaypointLabel.parts(new int[]{0, 0, 0, 9}, none, new boolean[]{false, false, false, true},
                WaypointLabel.AMOUNT_CHEST).get(0).letter, 'e', "emeralds can show on any chest");
        eq(WaypointLabel.parts(none, none, all, WaypointLabel.AMOUNT_CHEST).size(), 0, "empty chest has no label");

        // chest plus inventory
        int[] inv = {10, 0, 2, 0};
        List<WaypointLabel.Part> total = WaypointLabel.parts(new int[]{64, 0, 3, 8}, inv, all, WaypointLabel.AMOUNT_TOTAL);
        eq(total.get(0).count, "74", "iron is chest plus inventory");
        eq(total.get(1).count + total.get(1).letter, "5d", "diamonds are chest plus inventory");
        eq(total.get(2).count + total.get(2).letter, "8e", "emeralds with nothing carried");
        eq(WaypointLabel.parts(new int[]{64, 0, 3, 8}, inv, all, WaypointLabel.AMOUNT_CHEST).get(0).count, "64",
                "chest only ignores the inventory");
        eq(WaypointLabel.parts(none, inv, all, WaypointLabel.AMOUNT_TOTAL).get(0).count, "10",
                "total counts the inventory even when the chest is empty");

        check(Projection.scaleFor(50, false) == 1.0f, "no shrinking keeps full size");
        check(Projection.scaleFor(12, true) == 0.5f, "shrinking matches scaleFor");
        eq(Numbers.compact(1200), "1.2k", "compact 1200");
        eq(Numbers.compact(999), "999", "compact 999");
        eq(Numbers.compact(12500), "13k", "compact 12500");
        eq(Numbers.compact(2000), "2k", "compact 2000");
    }

    private static void sidebar() {
        check(SidebarTeams.hasOwnTeamLine(Arrays.asList("Diamond II in 5:58", "R Red: \u2713", "G Green: \u2713 YOU")), "hypixel own team line");
        check(SidebarTeams.hasOwnTeamLine(Arrays.asList("Diamond Upgrade:", "Yellow (You)")), "pika own team line");
        check(!SidebarTeams.hasOwnTeamLine(Arrays.asList("R Red: \u2713", "G Green: \u2713", "Players: 7/8")), "no own team line");
        check(!SidebarTeams.hasOwnTeamLine(Arrays.asList("Players: 7/8", "Waiting...", "Map: Yoyo YOU")), "lobby lines are not a team");
        check(!SidebarTeams.hasOwnTeamLine(null), "null lines");
    }

    private static void deposits() {
        // The real lines Hypixel printed after punch deposits
        check(TeamChestMessage.depositAmount("Deposited x24 Iron Ingot into Team Chest! (24 Total)") == 24, "iron deposit line");
        check(TeamChestMessage.depositAmount("Deposited x4 Gold Ingot into Team Chest! (4 Total)") == 4, "gold deposit line");
        check(TeamChestMessage.depositAmount("Deposited x33 Iron Ingot into Team Chest! (33 Total)") == 33, "second iron deposit line");
        check(TeamChestMessage.depositAmount("Deposited 64 Iron into Team Chest! (128 total)") == 64, "a line without the x still works");
        check(TeamChestMessage.depositAmount("[MVP+] bewf: Deposited x5 things") == 0, "a player typing it is ignored");
        check(TeamChestMessage.depositAmount("Party > bewf: [Mint | Team Chest: +10 iron]") == 0, "an update is not a deposit line");
        check(TeamChestMessage.depositAmount("You deposited items") == 0, "other text");
        check(TeamChestMessage.depositAmount(null) == 0, "no text");
    }

    private static void servers() {
        String[] addr = {"hypixel.net", "play.example.com:25565", "", ""};
        String[] cmd = {"/pc", "party chat", "/x", ""};
        eq(PartyServers.commandFor("mc.hypixel.net", addr, cmd), "pc", "subdomain of a listed server");
        eq(PartyServers.commandFor("HYPIXEL.NET.", addr, cmd), "pc", "case and trailing dot are ignored");
        eq(PartyServers.commandFor("play.example.com", addr, cmd), "party chat", "port in the setting is ignored");
        check(PartyServers.commandFor("example.com", addr, cmd) == null, "a parent of a listed server is not listed");
        check(PartyServers.commandFor("nothypixel.net", addr, cmd) == null, "a name that only ends the same is not listed");
        check(PartyServers.commandFor("", addr, cmd) == null, "singleplayer has no address");
        check(PartyServers.commandFor(null, addr, cmd) == null, "no address");
        check(PartyServers.commandFor("hypixel.net", new String[]{"hypixel.net"}, new String[]{"  "}) == null, "an empty command turns it off");
        eq(PartyServers.command("//pc "), "pc", "slashes and spaces are trimmed");
        check(PartyServers.command("pc\nop") == null, "line breaks are refused");
    }

    private static void recent() {
        RecentMessages r = new RecentMessages(10000);
        r.remember("[Mint | Team Chest: +10 iron #3]", 1000);
        check(!r.consumeIfOurs("[Mint | Team Chest: +4 gold #4]", 2000), "someone else's message is not ours");
        check(r.consumeIfOurs("[Mint | Team Chest: +10 iron #3]", 2000), "our echo is recognised");
        check(!r.consumeIfOurs("[Mint | Team Chest: +10 iron #3]", 2500), "an echo is only dropped once");
        r.remember("[Mint | Team Chest: -1 dia #5]", 3000);
        check(!r.consumeIfOurs("[Mint | Team Chest: -1 dia #5]", 20000), "old messages are forgotten");

        RecentMessages seen = new RecentMessages(30000);
        seen.remember("[Mint | Team Chest: +10 iron]", 1000);
        check(seen.has("[Mint | Team Chest: +10 iron]", 5000), "a recent message is found");
        check(seen.has("[Mint | Team Chest: +10 iron]", 6000), "looking does not use it up");
        check(!seen.has("[Mint | Team Chest: +9 iron]", 5000), "a different message is not found");
        check(!seen.has("[Mint | Team Chest: +10 iron]", 40000), "an old message is not found");
    }
}
