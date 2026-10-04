package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceTracker {

    // Player inventory counts
    public static int ironInv = 0;
    public static int goldInv = 0;
    public static int diaInv = 0;
    public static int emeInv = 0;

    // Ender chest counts
    public static int ironEc = 0;
    public static int goldEc = 0;
    public static int diaEc = 0;
    public static int emeEc = 0;

    // Team chest counts
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

    // Regex patterns for parsing team chest messages
    private static final Pattern TOTAL_NUMBER_PATTERN = Pattern.compile("\\((\\d+)");
    private static final Pattern[] MATERIAL_PATTERNS = {
            Pattern.compile("iron", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gold", Pattern.CASE_INSENSITIVE),
            Pattern.compile("diamond", Pattern.CASE_INSENSITIVE),
            Pattern.compile("emerald", Pattern.CASE_INSENSITIVE)
    };

    // Regex for team chest deposit message
    private static final Pattern TEAM_DEPOSIT_PATTERN = Pattern.compile(
            "Deposited \\d+ (.+) into Team Chest!? ?\\((\\d+)\\)? ?total?",
            Pattern.CASE_INSENSITIVE
    );

    private static class PendingDeposit {
        Item item;
        int inventorySnapshot;

        PendingDeposit(Item item, int snapshot) {
            this.item = item;
            this.inventorySnapshot = snapshot;
        }
    }

    private static final List<PendingDeposit> pendingDeposits = new ArrayList<>();

    public static boolean isActive() {
        return active;
    }

    // Detect punch on ender chest
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        if (!active && !debug) return;

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        ItemStack held = mc.thePlayer.getHeldItem();

        if (mc.theWorld.getBlockState(mop.getBlockPos()).getBlock() == Blocks.ender_chest) {
            if (held == null || !isTrackedItem(held.getItem())) return;
            pendingDeposits.add(new PendingDeposit(
                    held.getItem(),
                    countItemInInventory(mc, held.getItem())
            ));
        } else if (mc.theWorld.getBlockState(mop.getBlockPos()).getBlock() == Blocks.chest) {
            teamChestInteractionWarmup = 100;
        }
    }

    // Read authoritative totals from chat
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.message == null) return;

        String raw = event.message.getUnformattedText();
        if (raw == null) return;

        String msg = StringUtils.stripControlCodes(raw);

        if (teamChestInteractionWarmup > 0 && msg.contains("Team Chest")) {
            // Extract the total number from (number) or (number total)
            Matcher totalMatcher = TOTAL_NUMBER_PATTERN.matcher(msg);
            if (totalMatcher.find()) {
                int total = Integer.parseInt(totalMatcher.group(1));
                
                // Search the entire message for material names
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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer == null || mc.theWorld == null) {
            active = false;
            resetAll();
            lastWorldRef = null;
            return;
        }

        if (lastWorldRef != mc.theWorld) {
            lastWorldRef = mc.theWorld;
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

        Counts chestCounts =
                inEnderGui ? countOpenEnderChestContainer(mc)
                        : new Counts();

        if (enderGuiWarmup > 0) {
            enderGuiWarmup--;
        }
        else if (inEnderGui) {
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
        Counts teamCounts = inTeamChestGui ? countOpenTeamChestContainer(mc) : new Counts();
        if (teamChestGuiWarmup > 0) {
            teamChestGuiWarmup--;
        }
        else if (inTeamChestGui) {
            teamChestIron = teamCounts.iron;
            teamChestGold = teamCounts.gold;
            teamChestDiamond = teamCounts.diamond;
            teamChestEmerald = teamCounts.emerald;
        }
        wasTeamGui = inTeamChestGui;
    }

    private void handlePendingDeposits(Minecraft mc) {
        Iterator<PendingDeposit> it = pendingDeposits.iterator();

        while (it.hasNext()) {
            PendingDeposit pd = it.next();

            int now = countItemInInventory(mc, pd.item);
            int diff = pd.inventorySnapshot - now;

            if (diff > 0) {

                if (pd.item == Items.iron_ingot)
                    lastIronBase += diff;

                else if (pd.item == Items.gold_ingot)
                    lastGoldBase += diff;

                else if (pd.item == Items.diamond)
                    lastDiaBase += diff;

                else if (pd.item == Items.emerald)
                    lastEmeBase += diff;

                it.remove();
            }
        }
    }

    private boolean isEnderChestGuiOpen(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest))
            return false;

        Container container = mc.thePlayer.openContainer;

        if (!(container instanceof ContainerChest))
            return false;

        ContainerChest chest =
                (ContainerChest) container;

        IInventory lower =
                chest.getLowerChestInventory();

        String name =
                lower.getDisplayName() != null
                        ? lower.getDisplayName()
                        .getUnformattedText()
                        : "";

        name =
                StringUtils
                        .stripControlCodes(name)
                        .toLowerCase();

        return name.contains("ender chest");
    }

    private boolean isTeamChestGuiOpen(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest))
            return false;

        Container container = mc.thePlayer.openContainer;

        if (!(container instanceof ContainerChest))
            return false;

        ContainerChest chest =
                (ContainerChest) container;

        IInventory lower =
                chest.getLowerChestInventory();

        String name =
                lower.getDisplayName() != null
                        ? lower.getDisplayName()
                        .getUnformattedText()
                        : "";

        name =
                StringUtils
                        .stripControlCodes(name)
                        .toLowerCase();

        return name.contains("chest") && !name.contains("ender");
    }

    private Counts countOpenEnderChestContainer(
            Minecraft mc
    ) {

        Counts c = new Counts();

        Container container =
                mc.thePlayer.openContainer;

        if (!(container instanceof ContainerChest))
            return c;

        ContainerChest chest =
                (ContainerChest) container;

        IInventory lower =
                chest.getLowerChestInventory();

        for (int i = 0;
             i < lower.getSizeInventory();
             i++) {

            ItemStack stack =
                    lower.getStackInSlot(i);

            if (stack == null)
                continue;

            if (stack.getItem()
                    == Items.iron_ingot)
                c.iron += stack.stackSize;

            else if (stack.getItem()
                    == Items.gold_ingot)
                c.gold += stack.stackSize;

            else if (stack.getItem()
                    == Items.diamond)
                c.diamond += stack.stackSize;

            else if (stack.getItem()
                    == Items.emerald)
                c.emerald += stack.stackSize;
        }

        return c;
    }

    private Counts countOpenTeamChestContainer(
            Minecraft mc
    ) {

        Counts c = new Counts();

        Container container =
                mc.thePlayer.openContainer;

        if (!(container instanceof ContainerChest))
            return c;

        ContainerChest chest =
                (ContainerChest) container;

        IInventory lower =
                chest.getLowerChestInventory();

        for (int i = 0;
             i < lower.getSizeInventory();
             i++) {

            ItemStack stack =
                    lower.getStackInSlot(i);

            if (stack == null)
                continue;

            if (stack.getItem()
                    == Items.iron_ingot)
                c.iron += stack.stackSize;

            else if (stack.getItem()
                    == Items.gold_ingot)
                c.gold += stack.stackSize;

            else if (stack.getItem()
                    == Items.diamond)
                c.diamond += stack.stackSize;

            else if (stack.getItem()
                    == Items.emerald)
                c.emerald += stack.stackSize;
        }

        return c;
    }

    private boolean isTrackedItem(Item item) {
        return item == Items.iron_ingot
                || item == Items.gold_ingot
                || item == Items.diamond
                || item == Items.emerald;
    }

    private int countItemInInventory(
            Minecraft mc,
            Item item
    ) {

        int total = 0;

        for (ItemStack stack :
                mc.thePlayer.inventory
                        .mainInventory) {

            if (stack == null)
                continue;

            if (stack.getItem() == item)
                total += stack.stackSize;
        }

        return total;
    }

    private Counts countPlayerInventory(
            Minecraft mc
    ) {

        Counts c = new Counts();

        for (ItemStack stack :
                mc.thePlayer.inventory
                        .mainInventory) {

            if (stack == null)
                continue;

            if (stack.getItem()
                    == Items.iron_ingot)
                c.iron += stack.stackSize;

            else if (stack.getItem()
                    == Items.gold_ingot)
                c.gold += stack.stackSize;

            else if (stack.getItem()
                    == Items.diamond)
                c.diamond += stack.stackSize;

            else if (stack.getItem()
                    == Items.emerald)
                c.emerald += stack.stackSize;
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
