package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
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
import net.minecraft.world.HitResult;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.polyfrost.oneconfig.api.event.v1.events.PlayerInteractEvent;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

import java.util.ArrayList;
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
        if (event.getAction() != PlayerInteractEvent.Action.LEFT) return;
        if (event.getType() != PlayerInteractEvent.Type.BLOCK) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null) return;

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        if (!active && !debug) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.type != HitResult.Type.BLOCK) return;

        ItemStack held = mc.player.getItemInHand();
        Block block = McCompat.blockAt(mc.world, hit.getPos());
        if (block == null) return;

        if (block == Blocks.ENDER_CHEST) {
            if (held == null || !isTrackedItem(held.getItem())) return;
            pendingDeposits.add(new PendingDeposit(
                    held.getItem(),
                    countItemInInventory(mc, held.getItem())
            ));
        } else if (block == Blocks.CHEST) {
            teamChestInteractionWarmup = 100;
        }
    }

    private static void onChat(String raw) {
        if (raw == null) return;

        String msg = StringUtils.stripFormatting(raw);

        if (teamChestInteractionWarmup > 0 && msg.contains("Team Chest")) {
            Matcher totalMatcher = TOTAL_NUMBER_PATTERN.matcher(msg);
            if (totalMatcher.find()) {
                int total = Integer.parseInt(totalMatcher.group(1));

                if (MATERIAL_PATTERNS[0].matcher(msg).find()) {
                    teamChestIron = total;
                } else if (MATERIAL_PATTERNS[1].matcher(msg).find()) {
                    teamChestGold = total;
                } else if (MATERIAL_PATTERNS[2].matcher(msg).find()) {
                    teamChestDiamond = total;
                } else if (MATERIAL_PATTERNS[3].matcher(msg).find()) {
                    teamChestEmerald = total;
                }

                teamChestInteractionWarmup = 0;
            }
        }
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
        } else if (inTeamChestGui) {
            teamChestIron = teamCounts.iron;
            teamChestGold = teamCounts.gold;
            teamChestDiamond = teamCounts.diamond;
            teamChestEmerald = teamCounts.emerald;
        }
        wasTeamGui = inTeamChestGui;
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
