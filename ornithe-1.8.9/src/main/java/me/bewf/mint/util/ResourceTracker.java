package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestPicker;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.TeamChestLedger;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.StringUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.polyfrost.oneconfig.api.event.v1.events.PlayerInteractEvent;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceTracker {
    public static int ironInv = 0;
    public static int goldInv = 0;
    public static int diaInv = 0;
    public static int emeInv = 0;

    public static int ironEc = 0;
    public static int goldEc = 0;
    public static int diaEc = 0;
    public static int emeEc = 0;

    public static int teamChestIron = 0;
    public static int teamChestGold = 0;
    public static int teamChestDiamond = 0;
    public static int teamChestEmerald = 0;

    // What this player believes the team chest holds. Looks at the chest are the truth, updates from
    // teammates move the numbers in between. The public team chest fields above mirror it.
    private static final TeamChestLedger ledger = new TeamChestLedger();
    private static boolean homeGuiOpen = false;
    // Inventory when the team chest was opened or last changed, to see what this player put in or took out
    private static Counts guiInvCheckpoint = null;

    // The chest the team chest numbers belong to. It is found by looking around the spawn point when
    // the game starts, so opening a chest in an enemy base cannot replace it.
    private static BlockPos homeChestPos = null;
    private static BlockPos enderChestPos = null;
    private static BlockPos lastChestPos = null;
    private static final Set<BlockPos> ignoredChestsLogged = new HashSet<BlockPos>();
    private static boolean homeConfirmed = false;

    private static BlockPos spawnPos = null;
    private static double spawnYaw = 0.0;
    // True once the sidebar or the start message says the game is running
    private static boolean gameStartSeen = false;
    private static int spawnCaptureCountdown = -1;
    private static int scanTicksLeft = 0;
    private static int ticksSinceChestTouch = 1000;
    private static BlockPos lastTickPos = null;

    private static final int TELEPORT_BLOCKS = 30;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int SCAN_WINDOW_TICKS = 200;
    private static final int FALLBACK_RADIUS = 24;

    private static boolean active = false;
    private static Object lastWorldRef = null;

    private static int lastIronBase = 0;
    private static int lastGoldBase = 0;
    private static int lastDiaBase = 0;
    private static int lastEmeBase = 0;

    private static boolean wasEnderGui = false;
    private static int enderGuiWarmup = 0;

    public static int teamChestInteractionWarmup = 0;
    private static boolean wasTeamGui = false;
    private static int teamChestGuiWarmup = 0;

    private static final Pattern TOTAL_NUMBER_PATTERN = Pattern.compile("\\((\\d+)");
    private static final Pattern[] MATERIAL_PATTERNS = {
            Pattern.compile("iron", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gold", Pattern.CASE_INSENSITIVE),
            Pattern.compile("diamond", Pattern.CASE_INSENSITIVE),
            Pattern.compile("emerald", Pattern.CASE_INSENSITIVE)
    };

    // "Deposited 64 Iron into Team Chest! (128 total)": only the first number is needed here
    private static final Pattern TEAM_DEPOSIT_PATTERN = Pattern.compile("Deposited (\\d+) ", Pattern.CASE_INSENSITIVE);

    private static class PendingDeposit {
        Item item;
        int inventorySnapshot;

        PendingDeposit(Item item, int snapshot) {
            this.item = item;
            this.inventorySnapshot = snapshot;
        }
    }

    private static final List<PendingDeposit> pendingDeposits = new ArrayList<PendingDeposit>();

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        EventManager.register(TickEvent.End.class, event -> onClientTick());
        EventManager.register(PlayerInteractEvent.class, ResourceTracker::onInteract);
        EventManager.register(ChatEvent.Receive.class, event -> onChat(event.getFullyUnformattedMessage()));
    }

    private static void onInteract(PlayerInteractEvent event) {
        if (event.getType() != PlayerInteractEvent.Type.BLOCK) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null) return;

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        if (!active && !debug) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.type != HitResult.Type.BLOCK) return;

        Block block = McCompat.blockAt(mc.world, hit.getPos());
        if (block == null) return;

        boolean punch = event.getAction() == PlayerInteractEvent.Action.LEFT;

        if (block == Blocks.ENDER_CHEST) {
            if (!punch) return;
            ItemStack held = mc.player.getItemInHand();
            if (held == null || !isTrackedItem(held.getItem())) return;
            pendingDeposits.add(new PendingDeposit(
                    held.getItem(),
                    countItemInInventory(mc, held.getItem())
            ));
        } else if (block == Blocks.CHEST) {
            lastChestPos = hit.getPos();
            ticksSinceChestTouch = 0;
            if (punch) teamChestInteractionWarmup = 100;
        }
    }

    private static void onChat(String raw) {
        if (raw == null) return;

        String msg = StringUtils.stripFormatting(raw);

        // Players are sent to their bases when the game starts. Read the spawn a moment later.
        if (msg.contains("Protect your bed and destroy the enemy beds")) {
            gameStartSeen = true;
            spawnCaptureCountdown = 20;
        }

        if (teamChestInteractionWarmup > 0 && msg.contains("Team Chest")) {
            Matcher totalMatcher = TOTAL_NUMBER_PATTERN.matcher(msg);
            if (totalMatcher.find()) {
                teamChestInteractionWarmup = 0;

                confirmHomeFromServer();

                // A deposit into some other chest reports that chest's total, which must not
                // replace the numbers of the home team chest
                if (!acceptTeamChest(lastChestPos)) return;

                int total = Integer.parseInt(totalMatcher.group(1));
                int resource = -1;
                if (MATERIAL_PATTERNS[0].matcher(msg).find()) {
                    teamChestIron = total;
                    resource = Delta.IRON;
                } else if (MATERIAL_PATTERNS[1].matcher(msg).find()) {
                    teamChestGold = total;
                    resource = Delta.GOLD;
                } else if (MATERIAL_PATTERNS[2].matcher(msg).find()) {
                    teamChestDiamond = total;
                    resource = Delta.DIAMOND;
                } else if (MATERIAL_PATTERNS[3].matcher(msg).find()) {
                    teamChestEmerald = total;
                    resource = Delta.EMERALD;
                }

                if (resource >= 0) {
                    ledger.total(resource, total, System.currentTimeMillis());

                    // A punch deposit is a change the rest of the party wants to hear about
                    Matcher deposit = TEAM_DEPOSIT_PATTERN.matcher(msg);
                    if (deposit.find()) {
                        Delta change = new Delta();
                        change.add(resource, Integer.parseInt(deposit.group(1)));
                        TeamChestSync.record(change);
                    }
                }
            }
        }
    }

    /** A teammate changed the team chest and told the party. */
    static void applyTeammateUpdate(Delta change) {
        if (ledger.applyRemote(change, System.currentTimeMillis(), homeGuiOpen)) {
            teamChestIron = ledger.get(Delta.IRON);
            teamChestGold = ledger.get(Delta.GOLD);
            teamChestDiamond = ledger.get(Delta.DIAMOND);
            teamChestEmerald = ledger.get(Delta.EMERALD);
        }
    }

    /** Works out what this player put into or took out of the open team chest from their own inventory. */
    private static void trackOwnChanges(Counts invNow) {
        if (guiInvCheckpoint == null) {
            guiInvCheckpoint = copyOf(invNow);
            return;
        }
        Delta mine = new Delta();
        mine.add(Delta.IRON, guiInvCheckpoint.iron - invNow.iron);
        mine.add(Delta.GOLD, guiInvCheckpoint.gold - invNow.gold);
        mine.add(Delta.DIAMOND, guiInvCheckpoint.diamond - invNow.diamond);
        mine.add(Delta.EMERALD, guiInvCheckpoint.emerald - invNow.emerald);
        if (mine.isZero()) return;

        TeamChestSync.record(mine);
        guiInvCheckpoint = copyOf(invNow);
    }

    private static Counts copyOf(Counts c) {
        Counts out = new Counts();
        out.iron = c.iron;
        out.gold = c.gold;
        out.diamond = c.diamond;
        out.emerald = c.emerald;
        return out;
    }

    private static boolean sameChest(BlockPos a, BlockPos b) {
        return a != null && b != null
                && Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    /**
     * True when the chest at pos is the home team chest. The home chest is found by looking around
     * the spawn point. When that found nothing, the first chest touched close to the spawn is used.
     * A double chest has two positions next to each other, so neighbours match too.
     */
    private static boolean acceptTeamChest(BlockPos pos) {
        if (pos == null) return true;
        if (homeChestPos != null) {
            if (sameChest(pos, homeChestPos)) return true;
            logIgnored(pos);
            return false;
        }
        if (spawnPos == null || withinFallbackRange(pos)) {
            homeChestPos = pos;
            System.out.println("[Mint] Team chest set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (first chest touched)");
            return true;
        }
        logIgnored(pos);
        return false;
    }

    private static void logIgnored(BlockPos pos) {
        if (ignoredChestsLogged.add(pos)) {
            System.out.println("[Mint] Ignoring other chest at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
    }

    private static boolean withinFallbackRange(BlockPos pos) {
        if (spawnPos == null) return true;
        int dx = pos.getX() - spawnPos.getX();
        int dz = pos.getZ() - spawnPos.getZ();
        return dx * dx + dz * dz <= FALLBACK_RADIUS * FALLBACK_RADIUS
                && Math.abs(pos.getY() - spawnPos.getY()) <= ChestPicker.MAX_VERTICAL + 2;
    }

    /** "Deposited ... into Team Chest" right after punching a chest means the server itself calls that chest the team chest. */
    private static void confirmHomeFromServer() {
        if (lastChestPos == null || ticksSinceChestTouch > 40) return;
        if (homeChestPos != null && sameChest(lastChestPos, homeChestPos)) {
            homeConfirmed = true;
            return;
        }
        if (homeConfirmed) return;
        homeChestPos = lastChestPos;
        homeConfirmed = true;
        System.out.println("[Mint] Team chest confirmed by the server");
    }

    /** Reads the spawn point when the game starts and searches around it for the team chest and Ender Chest. */
    private static void updateHomeChests(Minecraft mc) {
        // Team chest search is for Hypixel only. Other servers keep the first chest touched.
        if (!BedwarsDetector.onHypixel()) return;
        if (!gameStartSeen && BedwarsDetector.hasOwnTeamLine()) {
            gameStartSeen = true;
            if (spawnCaptureCountdown <= 0) spawnCaptureCountdown = 20;
        }

        double[] pos = McCompat.playerPos(mc.player);
        if (pos == null) return;
        BlockPos here = new BlockPos((int) Math.floor(pos[0]), (int) Math.floor(pos[1]), (int) Math.floor(pos[2]));

        // A jump of many blocks in one tick is a teleport, such as lobby to base
        if (lastTickPos != null && homeChestPos == null && spawnCaptureCountdown < 0 && gameStartSeen
                && Math.abs(here.getX() - lastTickPos.getX()) + Math.abs(here.getZ() - lastTickPos.getZ()) > TELEPORT_BLOCKS) {
            spawnCaptureCountdown = 20;
        }
        lastTickPos = here;

        if (spawnCaptureCountdown > 0 && --spawnCaptureCountdown == 0) {
            spawnCaptureCountdown = -1;
            captureSpawn(here, pos[3]);
        } else if (spawnPos == null && gameStartSeen) {
            captureSpawn(here, pos[3]);
        }

        if (scanTicksLeft > 0) {
            if (scanTicksLeft % SCAN_INTERVAL_TICKS == 0) scanForChests(mc);
            scanTicksLeft--;
        }
    }

    private static void captureSpawn(BlockPos here, double yaw) {
        spawnPos = here;
        spawnYaw = yaw;
        if (!homeConfirmed) homeChestPos = null;
        enderChestPos = null;
        scanTicksLeft = SCAN_WINDOW_TICKS;
    }

    private static void scanForChests(Minecraft mc) {
        if (spawnPos == null || mc.world == null) return;

        int range = (int) Math.ceil(ChestPicker.MAX_HORIZONTAL);
        int vertical = ChestPicker.MAX_VERTICAL;
        List<int[]> chests = new ArrayList<int[]>();
        List<int[]> enders = new ArrayList<int[]>();

        for (int x = spawnPos.getX() - range; x <= spawnPos.getX() + range; x++) {
            for (int z = spawnPos.getZ() - range; z <= spawnPos.getZ() + range; z++) {
                for (int y = spawnPos.getY() - vertical; y <= spawnPos.getY() + vertical; y++) {
                    Block block = McCompat.blockAt(mc.world, new BlockPos(x, y, z));
                    if (block == Blocks.CHEST) chests.add(new int[]{x, y, z});
                    else if (block == Blocks.ENDER_CHEST) enders.add(new int[]{x, y, z});
                }
            }
        }

        double px = spawnPos.getX() + 0.5;
        double pz = spawnPos.getZ() + 0.5;

        if (!homeConfirmed) {
            int[] chest = ChestPicker.pick(px, spawnPos.getY(), pz, spawnYaw, chests);
            if (chest != null) homeChestPos = new BlockPos(chest[0], chest[1], chest[2]);
        }
        int[] ender = ChestPicker.pick(px, spawnPos.getY(), pz, spawnYaw, enders);
        if (ender != null) enderChestPos = new BlockPos(ender[0], ender[1], ender[2]);
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.world == null) {
            active = false;
            resetAll();
            lastWorldRef = null;
            return;
        }

        if (lastWorldRef != mc.world) {
            lastWorldRef = mc.world;
            resetAll();
        }

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        active = BedwarsDetector.isInHypixelBedwars();

        if (!active && !debug) {
            resetAll();
            return;
        }

        ticksSinceChestTouch++;
        updateHomeChests(mc);

        Counts invNow = countPlayerInventory(mc);

        ironInv = invNow.iron;
        goldInv = invNow.gold;
        diaInv = invNow.diamond;
        emeInv = invNow.emerald;

        handlePendingDeposits(mc);

        boolean inEnderGui = isEnderChestGuiOpen(mc);

        if (inEnderGui && !wasEnderGui) {
            enderGuiWarmup = 2;
        }

        Counts chestCounts = inEnderGui ? countOpenChestContainer(mc) : new Counts();

        if (enderGuiWarmup > 0) {
            enderGuiWarmup--;
        } else if (inEnderGui) {
            lastIronBase = chestCounts.iron;
            lastGoldBase = chestCounts.gold;
            lastDiaBase = chestCounts.diamond;
            lastEmeBase = chestCounts.emerald;
        }

        ironEc = lastIronBase;
        goldEc = lastGoldBase;
        diaEc = lastDiaBase;
        emeEc = lastEmeBase;

        wasEnderGui = inEnderGui;

        boolean inTeamChestGui = isTeamChestGuiOpen(mc);
        if (inTeamChestGui) {
            teamChestInteractionWarmup = 100;
        }
        Counts teamCounts = inTeamChestGui ? countOpenChestContainer(mc) : new Counts();
        if (teamChestGuiWarmup > 0) {
            teamChestGuiWarmup--;
        } else if (inTeamChestGui && acceptTeamChest(lastChestPos)) {
            teamChestIron = teamCounts.iron;
            teamChestGold = teamCounts.gold;
            teamChestDiamond = teamCounts.diamond;
            teamChestEmerald = teamCounts.emerald;
            ledger.look(teamCounts.iron, teamCounts.gold, teamCounts.diamond, teamCounts.emerald, System.currentTimeMillis());
            homeGuiOpen = true;
            trackOwnChanges(invNow);
        } else {
            homeGuiOpen = false;
        }

        if (!inTeamChestGui) {
            homeGuiOpen = false;
            if (wasTeamGui && guiInvCheckpoint != null) {
                // The chest just closed: count the last change and send what is waiting
                trackOwnChanges(invNow);
                TeamChestSync.flush();
            }
            guiInvCheckpoint = null;
        }
        wasTeamGui = inTeamChestGui;

        TeamChestSync.tick();
    }

    private static void handlePendingDeposits(Minecraft mc) {
        Iterator<PendingDeposit> it = pendingDeposits.iterator();

        while (it.hasNext()) {
            PendingDeposit pd = it.next();

            int now = countItemInInventory(mc, pd.item);
            int diff = pd.inventorySnapshot - now;

            if (diff > 0) {
                if (pd.item == Items.IRON_INGOT)
                    lastIronBase += diff;

                else if (pd.item == Items.GOLD_INGOT)
                    lastGoldBase += diff;

                else if (pd.item == Items.DIAMOND)
                    lastDiaBase += diff;

                else if (pd.item == Items.EMERALD)
                    lastEmeBase += diff;

                it.remove();
            }
        }
    }

    private static Inventory openChestInventory(Minecraft mc) {
        if (!(mc.screen instanceof ChestScreen)) return null;
        if (!(mc.player.menu instanceof ChestMenu)) return null;
        return ((ChestMenu) mc.player.menu).getChest();
    }

    private static String openChestTitle(Minecraft mc) {
        Inventory lower = openChestInventory(mc);
        if (lower == null) return null;

        String name = McCompat.inventoryName(lower);

        return StringUtils.stripFormatting(name).toLowerCase().replace(" ", "");
    }

    private static boolean isEnderChestGuiOpen(Minecraft mc) {
        String name = openChestTitle(mc);
        return name != null && name.contains("enderchest");
    }

    private static boolean isTeamChestGuiOpen(Minecraft mc) {
        String name = openChestTitle(mc);
        return name != null && name.contains("chest") && !name.contains("ender");
    }

    private static Counts countOpenChestContainer(Minecraft mc) {
        Counts c = new Counts();

        Inventory lower = openChestInventory(mc);
        if (lower == null) return c;

        for (int i = 0; i < lower.getSize(); i++) {
            addStack(c, lower.getItem(i));
        }

        return c;
    }

    private static boolean isTrackedItem(Item item) {
        return item == Items.IRON_INGOT
                || item == Items.GOLD_INGOT
                || item == Items.DIAMOND
                || item == Items.EMERALD;
    }

    private static int countItemInInventory(Minecraft mc, Item item) {
        int total = 0;

        for (ItemStack stack : mc.player.inventory.items) {
            if (stack == null) continue;

            if (stack.getItem() == item)
                total += stack.size;
        }

        return total;
    }

    private static Counts countPlayerInventory(Minecraft mc) {
        Counts c = new Counts();

        for (ItemStack stack : mc.player.inventory.items) {
            addStack(c, stack);
        }

        return c;
    }

    private static void addStack(Counts c, ItemStack stack) {
        if (stack == null) return;

        Item item = stack.getItem();

        if (item == Items.IRON_INGOT)
            c.iron += stack.size;

        else if (item == Items.GOLD_INGOT)
            c.gold += stack.size;

        else if (item == Items.DIAMOND)
            c.diamond += stack.size;

        else if (item == Items.EMERALD)
            c.emerald += stack.size;
    }

    private static void resetAll() {
        ironInv = 0;
        goldInv = 0;
        diaInv = 0;
        emeInv = 0;

        ironEc = 0;
        goldEc = 0;
        diaEc = 0;
        emeEc = 0;

        teamChestIron = 0;
        teamChestGold = 0;
        teamChestDiamond = 0;
        teamChestEmerald = 0;
        ledger.reset();
        homeGuiOpen = false;
        guiInvCheckpoint = null;
        TeamChestSync.resetGame();

        homeChestPos = null;
        enderChestPos = null;
        lastChestPos = null;
        homeConfirmed = false;
        spawnPos = null;
        lastTickPos = null;
        spawnCaptureCountdown = -1;
        scanTicksLeft = 0;
        ticksSinceChestTouch = 1000;
        ignoredChestsLogged.clear();
        gameStartSeen = false;

        lastIronBase = 0;
        lastGoldBase = 0;
        lastDiaBase = 0;
        lastEmeBase = 0;

        pendingDeposits.clear();

        wasEnderGui = false;
        enderGuiWarmup = 0;

        teamChestInteractionWarmup = 0;
        wasTeamGui = false;
        teamChestGuiWarmup = 0;
    }

    private static class Counts {
        int iron = 0;
        int gold = 0;
        int diamond = 0;
        int emerald = 0;
    }
}
