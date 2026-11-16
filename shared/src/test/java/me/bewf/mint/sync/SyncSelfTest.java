package me.bewf.mint.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        sliding();
        ledger();
        partyState();
        partyQuery();
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
        String u = "[Mint | Team Chest: +9 iron]";
        String[] lines = {
            "Party > bewf: " + u,
            "Party > [MVP++] bewf: " + u,
            "Party > [MVP+] Lord and Saviour bewf: " + u,
            "Party > [VIP] [Party Animal] [Lord] bewf: " + u,
            "Party > [YOUTUBE] bewf: " + u,
            "Party > [MVP+] bewf [TAG]: " + u,
            "Party >  [MVP+] bewf:   " + u + "  ",
            "[12:30] Party > [MVP+] bewf: " + u,
            "[Party] bewf: " + u,
            "Party | [Rank] bewf: " + u,
            "Party \u00BB [Rank] bewf: " + u,
        };
        for (String line : lines) {
            l = TeamChestMessage.party(line);
            check(l != null && l.sender.equals("bewf") && l.body.equals(u), "party line: " + line);
        }
        l = TeamChestMessage.party("Party > [MVP+] a_b1: " + u);
        check(l != null && l.sender.equals("a_b1"), "underscore and digit in name");
        check(TeamChestMessage.party("Party > bewf: hi") == null, "party chat without an update");
        check(TeamChestMessage.party("Guild > bewf: " + u) == null, "guild chat is not party chat");
        check(TeamChestMessage.party("[MVP+] bewf: Party > fake: " + u) == null, "spoofed prefix inside normal chat");
        check(TeamChestMessage.party("[MVP+] Party: " + u) == null, "a player called Party in normal chat");
        check(TeamChestMessage.party("Party > Steve: hello there: " + u) == null, "text before the update");
        check(TeamChestMessage.party("Party > bewf: " + u + " lol") == null, "text after the update");
        l = TeamChestMessage.party("Party > " + u);
        check(l != null && l.sender.isEmpty(), "unreadable name still gives the update");

        int[] held = TeamChestMessage.holdings("QUICK DEPOSIT! Chest now holds: 20 Iron Ingots, 2 Golden Ingots.");
        check(held != null && held[Delta.IRON] == 20 && held[Delta.GOLD] == 2 && held[Delta.DIAMOND] == -1, "chest now holds line");
        held = TeamChestMessage.holdings("Chest now holds: 1,204 Iron Ingots, 3 Diamonds, 7 Emeralds");
        check(held != null && held[Delta.IRON] == 1204 && held[Delta.DIAMOND] == 3 && held[Delta.EMERALD] == 7, "holds with commas and more items");
        check(TeamChestMessage.holdings("[MVP+] bewf: Chest now holds: 99 Iron Ingots") == null, "player chat is not a holds line");
        check(TeamChestMessage.holdings("Chest now holds: 4 Oak Logs") == null, "nothing tracked");
    }

    private static void buffer() {
        ChestBuffer b = new ChestBuffer(1500, 1000);
        b.add(new Delta(10, 0, 0, 0), 0);
        b.add(new Delta(0, 3, 0, 0), 500);
        check(b.poll(1000) == null, "nothing before the window ends");
        Delta out = b.poll(1500);
        eq(out, new Delta(10, 3, 0, 0), "burst combined into one");
        check(b.poll(1600) == null, "nothing left after sending");

        ChestBuffer nine = new ChestBuffer(1500, 1000);
        for (int i = 0; i < 9; i++) nine.add(new Delta(64, 0, 0, 0), i * 100);
        int sent = 0;
        for (long t = 0; t < 5000; t += 50) if (nine.poll(t) != null) sent++;
        eq(sent, 1, "nine punches, one message");

        ChestBuffer f = new ChestBuffer(1500, 1000);
        f.add(new Delta(1, 0, 0, 0), 0);
        check(f.poll(1500) != null, "first message");
        f.add(new Delta(0, 1, 0, 0), 1600);
        f.requestFlush();
        check(f.poll(1700) == null, "flush waits for the minimum gap");
        check(f.poll(2500) != null, "flush goes out after the gap");

        ChestBuffer c = new ChestBuffer(1500, 1000);
        c.add(new Delta(5, 0, 0, 0), 0);
        c.add(new Delta(-5, 0, 0, 0), 100);
        check(c.poll(2000) == null, "cancelled out sends nothing");
        check(!c.hasPending(), "cancelled buffer is empty");
    }

    private static void sliding() {
        ChestBuffer b = new ChestBuffer(600, 2500, 1000);
        b.add(new Delta(2, 0, 0, 0), 0);
        check(b.poll(500) == null, "still collecting");
        b.add(new Delta(0, 1, 0, 0), 900);
        check(b.poll(1400) == null, "a later change extends the wait");
        eq(b.poll(1500), new Delta(2, 1, 0, 0), "one message after things go quiet");
        ChestBuffer endless = new ChestBuffer(600, 2500, 1000);
        for (long t = 0; t <= 2400; t += 300) endless.add(new Delta(1, 0, 0, 0), t);
        check(endless.poll(2400) == null, "not before the cap");
        check(endless.poll(2500) != null, "the cap forces it out");
    }

    private static void ledger() {
        TeamChestLedger l = new TeamChestLedger();
        check(!l.isKnown(Delta.IRON), "nothing known at first");
        l.total(Delta.IRON, 5, 0);
        check(l.isKnown(Delta.IRON) && !l.isKnown(Delta.GOLD), "a total makes one resource known");
        l.reset();
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
        check(s.canSend(false), "unknown state still tries away from Hypixel");
        check(!s.canSend(true), "unknown state waits for the check on Hypixel");
        s = s.after("You are not in a party right now.");
        eq(s, PartyState.OUT, "not in a party");
        check(!s.canSend(false) && !s.canSend(true), "no sending when out");
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
        check(s.canSend(true), "in a party sends on Hypixel");
        s = s.after("Just some other line");
        eq(s, PartyState.IN, "unrelated lines change nothing");
        s = s.after("[VIP] Alex has left the party.");
        eq(s, PartyState.UNKNOWN, "someone leaving needs a new check");
        s = s.after("Party Leader: [MVP+] Steve ?");
        eq(s, PartyState.IN, "check says party");
    }

    private static void partyQuery() {
        PartyQuery q = new PartyQuery();
        check(q.shouldAsk(PartyState.UNKNOWN, 1000), "asks the first time");
        check(!q.shouldAsk(PartyState.IN, 1000), "no check when the state is known");
        q.asked(1000);
        check(!q.shouldAsk(PartyState.UNKNOWN, 5000), "cooldown");
        check(q.shouldAsk(PartyState.UNKNOWN, 11000), "asks again after the cooldown");
        String dashes = "-----------------------------------------------------";
        check(q.hides(dashes, 1500), "first dash line hidden");
        check(q.hides("Party Members (2)", 1500), "header hidden");
        check(q.hides("", 1500), "blank hidden");
        check(q.hides("Party Leader: [MVP+] Steve ?", 1500), "leader hidden");
        check(!q.hides("Party > [MVP+] Steve: hello", 1500), "party chat is not hidden");
        check(q.hides(dashes, 1600), "closing dash line hidden");
        check(!q.hides(dashes, 1700), "a later dash line is shown");
        q.asked(20000);
        check(!q.hides("Party Members (2)", 24000), "nothing hidden after the window");
        q.asked(30000);
        check(q.hides("You are not currently in a party.", 30100), "not in a party reply hidden");
        check(PartyServers.isHypixel("mc.hypixel.net:25565"), "hypixel host");
        check(PartyServers.isHypixel("hypixel.net"), "bare hypixel host");
        check(!PartyServers.isHypixel("nothypixel.net"), "lookalike host");
        check(!PartyServers.isHypixel(null), "no address");
    }

    private static List<int[]> chests(int[]... c) {
        return new ArrayList<int[]>(Arrays.asList(c));
    }

    private static void picker() {
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

        int[] right = {-5, 64, 1};
        int[] left = {7, 64, 1};
        best = ChestPicker.pick(0.5, 64, 0.5, 0, chests(left, right));
        check(best == right, "closer side wins");

        int[] wide = {15, 65, 4};
        check(ChestPicker.pick(0.5, 64, 0.5, 0, chests(wide)) == wide, "wide search range");

        check(ChestPicker.sameChest(new int[]{0, 64, 0}, new int[]{1, 64, 0}), "double chest halves match");
        check(!ChestPicker.sameChest(new int[]{0, 64, 0}, new int[]{2, 64, 0}), "two blocks apart are different chests");
        check(!ChestPicker.sameChest(null, new int[]{0, 0, 0}), "null never matches");
    }

    private static void projection() {
        double[] p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, 10);
        check(p != null && Math.abs(p[0] - 200) < 1e-6 && Math.abs(p[1] - 150) < 1e-6, "straight ahead is centred");
        eq(Math.round(p[2]), 10L, "distance");

        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, -2, 64, 10);
        check(p != null && p[0] > 200 && Math.abs(p[1] - 150) < 1e-6, "right of the view is right on screen");
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 2, 64, 10);
        check(p != null && p[0] < 200, "left of the view is left on screen");

        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 66, 10);
        check(p != null && p[1] < 150, "above is up");

        check(Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, -5) == null, "behind the camera");

        p = Projection.project(0, 64, 0, 90, 0, 70, 400, 300, -10, 64, 0);
        check(p != null && Math.abs(p[0] - 200) < 1e-6, "yaw 90 faces -X");

        p = Projection.project(0, 64, 0, 0, 45, 70, 400, 300, 0, 64, 10);
        check(p != null && p[1] < 150, "looking down puts level points above centre");

        p = Projection.project(0, 64, 0, 0, 0, 90, 400, 300, 0, 64 + 10, 10);
        check(p != null && Math.abs(p[1] - 0) < 1e-6, "edge of the vertical field of view");

        double[] plain = Projection.project(0, 64, 0, 30, 10, 70, 400, 300, -3, 66, 8);
        double[] none = Projection.project(0, 64, 0, 30, 10, 70, 400, 300, -3, 66, 8, null);
        double[] zero = Projection.project(0, 64, 0, 30, 10, 70, 400, 300, -3, 66, 8, new double[]{0, 0, 0, 0});
        check(Math.abs(plain[0] - none[0]) < 1e-9 && Math.abs(plain[1] - none[1]) < 1e-9, "no bob is the plain result");
        check(Math.abs(plain[0] - zero[0]) < 1e-9 && Math.abs(plain[1] - zero[1]) < 1e-9, "zero bob is the plain result");

        double focal = 150.0 / Math.tan(Math.toRadians(35));
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, 10, new double[]{0.5, 0, 0, 0});
        check(p != null && Math.abs(p[0] - (200 + 0.5 / 10 * focal)) < 1e-6, "the view sliding right moves things right");
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, 10, new double[]{0, -0.2, 0, 0});
        check(p != null && Math.abs(p[1] - (150 + 0.2 / 10 * focal)) < 1e-6, "the view dipping moves things down");
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, -1, 64, 10, new double[]{0, 0, 90, 0});
        check(p != null && Math.abs(p[0] - 200) < 1e-6 && p[1] < 150, "a quarter turn of roll sends a point on the right to the top");
        p = Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, 10, new double[]{0, 0, 0, 5});
        check(p != null && p[1] < 150, "tilting the view moves a centred point");
        check(Projection.project(0, 64, 0, 0, 0, 70, 400, 300, 0, 64, 10, new double[]{0, 0, 0, 180}) == null, "turned all the way round is behind");

        check(Projection.scaleFor(3) == 1.0f, "full size up close");
        check(Projection.scaleFor(10) == 1.0f, "same size out to 10 blocks");
        check(Math.abs(Projection.scaleFor(35) - 0.725f) < 1e-5, "partway smaller at 35 blocks");
        check(Math.abs(Projection.scaleFor(500) - 0.45f) < 1e-5, "never smaller than 45 percent");
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

        int[] inv = {10, 0, 2, 0};
        List<WaypointLabel.Part> total = WaypointLabel.parts(new int[]{64, 0, 3, 8}, inv, all, WaypointLabel.AMOUNT_TOTAL);
        eq(total.get(0).count, "74", "iron is chest plus inventory");
        eq(total.get(1).count + total.get(1).letter, "5d", "diamonds are chest plus inventory");
        eq(total.get(2).count + total.get(2).letter, "8e", "emeralds with nothing carried");
        eq(WaypointLabel.parts(new int[]{64, 0, 3, 8}, inv, all, WaypointLabel.AMOUNT_CHEST).get(0).count, "64",
                "chest only ignores the inventory");
        eq(WaypointLabel.parts(none, inv, all, WaypointLabel.AMOUNT_TOTAL).get(0).count, "10",
                "total counts the inventory even when the chest is empty");

        int[] fullChest = {5, 6, 7, 8};
        List<WaypointLabel.Part> four = WaypointLabel.parts(fullChest, none, all, WaypointLabel.AMOUNT_CHEST);
        List<List<WaypointLabel.Part>> rows4 = WaypointLabel.rows(four);
        eq(rows4.size(), 2, "four resources make two rows");
        eq(rows4.get(0).get(0).resource + "" + rows4.get(0).get(1).resource, "23", "diamond and emerald on top");
        eq(rows4.get(1).get(0).resource + "" + rows4.get(1).get(1).resource, "01", "iron and gold on the bottom");
        List<List<WaypointLabel.Part>> rows3 = WaypointLabel.rows(WaypointLabel.parts(new int[]{5, 0, 7, 8}, none, all, WaypointLabel.AMOUNT_CHEST));
        eq(rows3.size(), 2, "three resources make two rows");
        eq(rows3.get(0).size(), 1, "one on top for three");
        eq(rows3.get(1).size(), 2, "two on the bottom for three");
        eq(rows3.get(1).get(0).resource + "" + rows3.get(1).get(1).resource, "02", "the first two go on the bottom");
        eq(rows3.get(0).get(0).resource, 3, "the last one goes on top");
        List<List<WaypointLabel.Part>> rows2 = WaypointLabel.rows(WaypointLabel.parts(new int[]{0, 6, 0, 8}, none, all, WaypointLabel.AMOUNT_CHEST));
        eq(rows2.size(), 1, "two resources share a row");
        eq(rows2.get(0).size(), 2, "both beside each other");
        eq(WaypointLabel.rows(WaypointLabel.parts(new int[]{0, 0, 7, 0}, none, all, WaypointLabel.AMOUNT_CHEST)).get(0).size(), 1, "one resource is one cell");
        eq(WaypointLabel.rows(WaypointLabel.parts(none, none, all, WaypointLabel.AMOUNT_CHEST)).size(), 0, "nothing makes no rows");

        check(Projection.scaleFor(50, false) == 1.0f, "no shrinking keeps full size");
        check(Projection.scaleFor(12, true) == Projection.scaleFor(12), "shrinking matches scaleFor");
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
