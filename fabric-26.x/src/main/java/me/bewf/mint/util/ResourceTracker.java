package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.ChestPicker;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.TeamChestLedger;
import me.bewf.mint.sync.TeamChestMessage;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
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

    // True while the home team chest is open on screen
    private static boolean homeGuiOpen = false;
    // Inventory when the team chest was opened or last changed, to see what this player put in or took out
    private static Counts guiInvCheckpoint = null;

    private static boolean active = false;
    private static Object lastWorldRef = null;

    private static int lastIronBase = 0;
    private static int lastGoldBase = 0;
    private static int lastDiaBase = 0;
    private static int lastEmeBase = 0;

    private static boolean wasEnderGui = false;
    private static int enderGuiWarmup = 0;

    // The server sends the contents of a chest a moment after the window opens. Until something shows up
    // (or a second has passed) an empty window is not read as an empty chest.
    private static final int LOAD_WAIT_TICKS = 20;
    private static int enderOpenTicks = 0;
    private static boolean enderLoaded = false;
    private static int teamOpenTicks = 0;
    private static boolean teamLoaded = false;

    // The sidebar can go missing for a moment, so the numbers are only wiped after it has been gone a while
    private static final int INACTIVE_RESET_TICKS = 200;
    private static int inactiveTicks = 0;

    // The chest the team chest numbers belong to. It is found by looking around the spawn point when
    // the game starts, so walking over to an enemy base and opening their chest cannot replace it.
    // Other chests are ignored so they cannot overwrite the numbers.
    private static BlockPos homeChestPos = null;
    private static BlockPos enderChestPos = null;
    private static BlockPos lastChestPos = null;
    private static final Set<BlockPos> ignoredChestsLogged = new HashSet<>();

    // True once the server itself said "Team Chest" about homeChestPos
    private static boolean homeConfirmed = false;

    private static BlockPos spawnPos = null;
    private static float spawnYaw = 0f;
    // Ticks until the spawn point is read after the game start message, so the teleport to the base is done
    // True once the sidebar or the start message says the game is running
    private static boolean gameStartSeen = false;
    private static int spawnCaptureCountdown = -1;
    // Ticks left in which the area around the spawn is still searched (chunks may arrive late)
    private static int scanTicksLeft = 0;
    private static int ticksSinceChestTouch = 1000;
    private static int ticksSinceEnderTouch = 1000;

    private static BlockPos lastTickPos = null;
    private static final int TELEPORT_BLOCKS = 30;

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int SCAN_WINDOW_TICKS = 200;
    // How far from the spawn a chest may be when nothing was found by the search
    private static final int FALLBACK_RADIUS = 24;

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

    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private static class PendingDeposit {
        Item item;
        int inventorySnapshot;
        int age = 0;

        PendingDeposit(Item item, int snapshot) {
            this.item = item;
            this.inventorySnapshot = snapshot;
        }
    }

    private static final List<PendingDeposit> pendingDeposits = new ArrayList<>();

    public static boolean isActive() {
        return active;
    }

    public static BlockPos homeChest() {
        return homeChestPos;
    }

    public static BlockPos homeEnderChest() {
        return enderChestPos;
    }

    public void register() {
        EventManager.INSTANCE.register(TickEvent.End.class, event -> onClientTick());
        AttackBlockCallback.EVENT.register(this::onAttackBlock);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block == Blocks.CHEST) {
                lastChestPos = hit.getBlockPos().immutable();
                ticksSinceChestTouch = 0;
            } else if (block == Blocks.ENDER_CHEST) {
                ticksSinceEnderTouch = 0;
            }
            return InteractionResult.PASS;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> onChat(message.getString()));
    }

    private InteractionResult onAttackBlock(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level world, InteractionHand hand, BlockPos pos, net.minecraft.core.Direction direction) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return InteractionResult.PASS;

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        if (!active && !debug) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);

        if (world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
            ticksSinceEnderTouch = 0;
            if (held.isEmpty() || !isTrackedItem(held.getItem())) return InteractionResult.PASS;
            // Several punches before the server removes the items would each count the same deposit
            for (PendingDeposit existing : pendingDeposits) {
                if (existing.item == held.getItem()) return InteractionResult.PASS;
            }
            pendingDeposits.add(new PendingDeposit(
                    held.getItem(),
                    countItemInInventory(mc, held.getItem())
            ));
        } else if (world.getBlockState(pos).getBlock() == Blocks.CHEST) {
            lastChestPos = pos.immutable();
            ticksSinceChestTouch = 0;
            teamChestInteractionWarmup = 100;
        }

        return InteractionResult.PASS;
    }

    private void onChat(String raw) {
        if (raw == null) return;

        String msg = StringUtil.stripColor(raw);

        // Players are sent to their bases when the game starts. Read the spawn a moment later.
        if (msg.contains("Protect your bed and destroy the enemy beds")) {
            gameStartSeen = true;
            spawnCaptureCountdown = 20;
        }

        if (teamChestInteractionWarmup > 0 && msg.contains("Team Chest")) {

            Matcher totalMatcher = TOTAL_NUMBER_PATTERN.matcher(msg);
            if (totalMatcher.find()) {
                // A deposit line keeps the window open, so a second quick punch is read as well
                teamChestInteractionWarmup = TeamChestMessage.depositAmount(msg) > 0 ? 40 : 0;

                confirmHomeFromServer();

                // A deposit into some other chest reports that chest's total, which must not
                // replace the numbers of the home team chest
                if (!acceptTeamChest(lastChestPos)) return;

                int total = Integer.parseInt(totalMatcher.group(1));
                long now = System.currentTimeMillis();

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
                    ledger.total(resource, total, now);

                    // A punch deposit is a change the rest of the party wants to hear about
                    int deposited = TeamChestMessage.depositAmount(msg);
                    if (deposited > 0) {
                        Delta change = new Delta();
                        change.add(resource, deposited);
                        TeamChestSync.record(change);
                    }
                }
            }
        }
    }

    /** A teammate changed the team chest and told the party. */
    static void applyTeammateUpdate(Delta change) {
        if (ledger.applyRemote(change, System.currentTimeMillis(), homeGuiOpen)) {
            publishLedger();
        }
    }

    private static void publishLedger() {
        teamChestIron = ledger.get(Delta.IRON);
        teamChestGold = ledger.get(Delta.GOLD);
        teamChestDiamond = ledger.get(Delta.DIAMOND);
        teamChestEmerald = ledger.get(Delta.EMERALD);
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

    private void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            active = false;
            resetAll();
            lastWorldRef = null;
            return;
        }

        if (lastWorldRef != mc.level) {
            lastWorldRef = mc.level;
            resetAll();
        }

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        active = BedwarsDetector.isInHypixelBedwars();

        if (!active && !debug) {
            if (++inactiveTicks >= INACTIVE_RESET_TICKS) resetAll();
            return;
        }
        inactiveTicks = 0;

        ticksSinceChestTouch++;
        ticksSinceEnderTouch++;
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
            enderOpenTicks = 0;
            enderLoaded = false;
            // The open chest is the real source of truth, so drop unconfirmed deposits
            pendingDeposits.clear();
        }

        Counts chestCounts =
                inEnderGui ? countOpenChestContainer(mc)
                        : new Counts();

        if (inEnderGui) {
            enderOpenTicks++;
            if (!enderLoaded && (chestHasItems(mc) || enderOpenTicks >= LOAD_WAIT_TICKS)) enderLoaded = true;
        }

        if (enderGuiWarmup > 0) {
            enderGuiWarmup--;
        } else if (inEnderGui && enderLoaded) {
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
        if (inTeamChestGui && !wasTeamGui) {
            // Give the server a moment to send the chest contents before reading them
            teamChestGuiWarmup = 2;
            teamOpenTicks = 0;
            teamLoaded = false;
        }
        if (inTeamChestGui) {
            teamOpenTicks++;
            if (!teamLoaded && (chestHasItems(mc) || teamOpenTicks >= LOAD_WAIT_TICKS)) teamLoaded = true;
        }
        if (inTeamChestGui) {
            teamChestInteractionWarmup = 100;
        } else if (teamChestInteractionWarmup > 0) {
            teamChestInteractionWarmup--;
        }
        Counts teamCounts = inTeamChestGui ? countOpenChestContainer(mc) : new Counts();
        if (teamChestGuiWarmup > 0) {
            teamChestGuiWarmup--;
        } else if (inTeamChestGui && teamLoaded && acceptTeamChest(lastChestPos)) {
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

    /**
     * True when the chest at pos is the home team chest. The home chest is found by looking around
     * the spawn point. When that found nothing, the first chest touched close to the spawn is used.
     * A double chest has two positions next to each other, so neighbours match too.
     */
    private static boolean acceptTeamChest(BlockPos pos) {
        if (pos == null) return true; // unknown chest, keep the old behaviour
        if (homeChestPos != null) {
            if (pos.distManhattan(homeChestPos) <= 1) return true;
            logIgnored(pos);
            return false;
        }
        if (spawnPos == null || withinFallbackRange(pos)) {
            homeChestPos = pos;
            System.out.println("[Mint] Team chest set to " + pos.toShortString() + " (first chest touched)");
            return true;
        }
        logIgnored(pos);
        return false;
    }

    private static void logIgnored(BlockPos pos) {
        if (ignoredChestsLogged.add(pos)) {
            System.out.println("[Mint] Ignoring other chest at " + pos.toShortString()
                    + (homeChestPos != null ? " (team chest is " + homeChestPos.toShortString() + ")" : ""));
        }
    }

    private static boolean withinFallbackRange(BlockPos pos) {
        if (spawnPos == null) return true;
        int dx = pos.getX() - spawnPos.getX();
        int dz = pos.getZ() - spawnPos.getZ();
        return dx * dx + dz * dz <= FALLBACK_RADIUS * FALLBACK_RADIUS
                && Math.abs(pos.getY() - spawnPos.getY()) <= ChestPicker.MAX_VERTICAL + 2;
    }

    /**
     * "Deposited ... into Team Chest" right after punching a chest means the server itself calls that
     * chest the team chest. That outranks the search around the spawn, as long as the punch was recent.
     */
    private static void confirmHomeFromServer() {
        if (lastChestPos == null || ticksSinceChestTouch > 40) return;
        if (homeChestPos != null && lastChestPos.distManhattan(homeChestPos) <= 1) {
            homeConfirmed = true;
            return;
        }
        if (homeConfirmed) return;
        homeChestPos = lastChestPos;
        homeConfirmed = true;
        System.out.println("[Mint] Team chest confirmed by the server at " + homeChestPos.toShortString());
    }

    /** Reads the spawn point when the game starts and searches around it for the team chest and Ender Chest. */
    private void updateHomeChests(Minecraft mc) {
        // Team chest search is for Hypixel only. Other servers keep the first chest touched.
        if (!BedwarsDetector.onHypixel()) return;
        if (!gameStartSeen && BedwarsDetector.hasOwnTeamLine()) {
            gameStartSeen = true;
            if (spawnCaptureCountdown <= 0) spawnCaptureCountdown = 20;
        }

        // A jump of many blocks in one tick is a teleport, such as lobby to base. If the team chest is
        // still unknown, read the spawn again shortly after it, even when the start message was missed.
        BlockPos here = mc.player.blockPosition();
        if (lastTickPos != null && homeChestPos == null && spawnCaptureCountdown < 0 && gameStartSeen
                && here.distManhattan(lastTickPos) > TELEPORT_BLOCKS) {
            spawnCaptureCountdown = 20;
        }
        lastTickPos = here;

        if (spawnCaptureCountdown > 0 && --spawnCaptureCountdown == 0) {
            spawnCaptureCountdown = -1;
            captureSpawn(mc);
        } else if (spawnPos == null && gameStartSeen) {
            // Joined a game that is already running, so no start message will come
            captureSpawn(mc);
        }

        if (scanTicksLeft > 0) {
            if (scanTicksLeft % SCAN_INTERVAL_TICKS == 0) scanForChests(mc);
            scanTicksLeft--;
        }
    }

    private static void captureSpawn(Minecraft mc) {
        if (mc.player == null) return;
        spawnPos = mc.player.blockPosition().immutable();
        spawnYaw = mc.player.getYRot();
        if (!homeConfirmed) homeChestPos = null;
        enderChestPos = null;
        scanTicksLeft = SCAN_WINDOW_TICKS;
        System.out.println("[Mint] Spawn point " + spawnPos.toShortString() + ", looking for the team chest");
    }

    private static void scanForChests(Minecraft mc) {
        if (spawnPos == null || mc.level == null) return;

        int range = (int) Math.ceil(ChestPicker.MAX_HORIZONTAL);
        int vertical = ChestPicker.MAX_VERTICAL;
        List<int[]> chests = new ArrayList<>();
        List<int[]> enders = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = spawnPos.getX() - range; x <= spawnPos.getX() + range; x++) {
            for (int z = spawnPos.getZ() - range; z <= spawnPos.getZ() + range; z++) {
                for (int y = spawnPos.getY() - vertical; y <= spawnPos.getY() + vertical; y++) {
                    Block block = mc.level.getBlockState(cursor.set(x, y, z)).getBlock();
                    if (block == Blocks.CHEST) chests.add(new int[]{x, y, z});
                    else if (block == Blocks.ENDER_CHEST) enders.add(new int[]{x, y, z});
                }
            }
        }

        double px = spawnPos.getX() + 0.5;
        double pz = spawnPos.getZ() + 0.5;

        if (!homeConfirmed) {
            int[] chest = ChestPicker.pick(px, spawnPos.getY(), pz, spawnYaw, chests);
            if (chest != null) {
                BlockPos found = new BlockPos(chest[0], chest[1], chest[2]);
                if (!found.equals(homeChestPos)) {
                    homeChestPos = found;
                    System.out.println("[Mint] Team chest set to " + found.toShortString() + " (found near spawn)");
                }
            }
        }

        int[] ender = ChestPicker.pick(px, spawnPos.getY(), pz, spawnYaw, enders);
        if (ender != null) enderChestPos = new BlockPos(ender[0], ender[1], ender[2]);
    }

    private void handlePendingDeposits(Minecraft mc) {
        Iterator<PendingDeposit> it = pendingDeposits.iterator();

        while (it.hasNext()) {
            PendingDeposit pd = it.next();

            // The punch did not deposit anything (chest full, wrong item, etc.)
            if (++pd.age > 100) {
                it.remove();
                continue;
            }

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

    private String openChestTitle(Minecraft mc) {
        Object screenObj = ReflectUtil.getScreen(mc);
        if (!(screenObj instanceof AbstractContainerScreen<?> screen)) return null;
        if (!(mc.player.containerMenu instanceof ChestMenu)) return null;

        String name = screen.getTitle() != null ? screen.getTitle().getString() : "";
        return StringUtil.stripColor(name).toLowerCase();
    }

    // A language pack can rename the chest windows ("Ender coffer"), so the block the player clicked
    // decides when the title is no help. A window opens within a second or two of the click.
    private static final int OPEN_WINDOW_TICKS = 40;

    private static boolean openedEnderBlock() {
        return ticksSinceEnderTouch < OPEN_WINDOW_TICKS && ticksSinceEnderTouch <= ticksSinceChestTouch;
    }

    private static boolean openedChestBlock() {
        return ticksSinceChestTouch < OPEN_WINDOW_TICKS && ticksSinceChestTouch < ticksSinceEnderTouch;
    }

    private boolean isEnderChestGuiOpen(Minecraft mc) {
        String name = openChestTitle(mc);
        return name != null && (name.contains("ender") || (!name.contains("chest") && openedEnderBlock()));
    }

    private boolean isTeamChestGuiOpen(Minecraft mc) {
        String name = openChestTitle(mc);
        return name != null && !name.contains("ender") && (name.contains("chest") || openedChestBlock());
    }

    /** True when any slot of the open chest window holds an item, so the server has sent its contents. */
    private boolean chestHasItems(Minecraft mc) {
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (!(menu instanceof ChestMenu)) return false;
        int chestSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOTS;
        for (int i = 0; i < chestSlotCount; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) return true;
        }
        return false;
    }

    private Counts countOpenChestContainer(Minecraft mc) {
        Counts c = new Counts();

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (!(menu instanceof ChestMenu)) return c;

        int chestSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOTS;

        for (int i = 0; i < chestSlotCount; i++) {
            ItemStack stack = menu.getSlot(i).getItem();

            if (stack.isEmpty())
                continue;

            if (stack.getItem() == Items.IRON_INGOT)
                c.iron += stack.getCount();

            else if (stack.getItem() == Items.GOLD_INGOT)
                c.gold += stack.getCount();

            else if (stack.getItem() == Items.DIAMOND)
                c.diamond += stack.getCount();

            else if (stack.getItem() == Items.EMERALD)
                c.emerald += stack.getCount();
        }

        return c;
    }

    private boolean isTrackedItem(Item item) {
        return item == Items.IRON_INGOT
                || item == Items.GOLD_INGOT
                || item == Items.DIAMOND
                || item == Items.EMERALD;
    }

    private int countItemInInventory(Minecraft mc, Item item) {
        int total = 0;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty())
                continue;

            if (stack.getItem() == item)
                total += stack.getCount();
        }

        return total;
    }

    private Counts countPlayerInventory(Minecraft mc) {
        Counts c = new Counts();

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty())
                continue;

            if (stack.getItem() == Items.IRON_INGOT)
                c.iron += stack.getCount();

            else if (stack.getItem() == Items.GOLD_INGOT)
                c.gold += stack.getCount();

            else if (stack.getItem() == Items.DIAMOND)
                c.diamond += stack.getCount();

            else if (stack.getItem() == Items.EMERALD)
                c.emerald += stack.getCount();
        }

        return c;
    }

    private void resetAll() {

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

        lastIronBase = 0;
        lastGoldBase = 0;
        lastDiaBase = 0;
        lastEmeBase = 0;

        pendingDeposits.clear();

        wasEnderGui = false;
        enderGuiWarmup = 0;
        enderOpenTicks = 0;
        enderLoaded = false;
        teamOpenTicks = 0;
        teamLoaded = false;

        teamChestInteractionWarmup = 0;
        wasTeamGui = false;
        teamChestGuiWarmup = 0;

        homeChestPos = null;
        enderChestPos = null;
        homeConfirmed = false;
        spawnPos = null;
        lastTickPos = null;
        spawnCaptureCountdown = -1;
        scanTicksLeft = 0;
        ticksSinceChestTouch = 1000;
        ticksSinceEnderTouch = 1000;
        lastChestPos = null;
        ignoredChestsLogged.clear();
        gameStartSeen = false;
    }

    private static class Counts {

        int iron = 0;
        int gold = 0;
        int diamond = 0;
        int emerald = 0;
    }
}
