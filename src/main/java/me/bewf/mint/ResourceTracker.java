package me.bewf.mint.tracker;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.util.BedwarsDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ResourceTracker {

    public static int ironInv = 0;
    public static int goldInv = 0;
    public static int diaInv = 0;
    public static int emeInv = 0;

    public static int ironEc = 0;
    public static int goldEc = 0;
    public static int diaEc = 0;
    public static int emeEc = 0;

    private static boolean active = false;
    private static Object lastWorldRef = null;

    private static int lastIronBase = 0;
    private static int lastGoldBase = 0;
    private static int lastDiaBase = 0;
    private static int lastEmeBase = 0;

    private static int ironPending = 0;
    private static int goldPending = 0;
    private static int diaPending = 0;
    private static int emePending = 0;

    private static int punchTicksLeft = 0;
    private static Item punchItem = null;
    private static int punchInvBefore = 0;

    private static boolean wasEnderGui = false;
    private static int enderGuiWarmup = 0;

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0) return;
        if (!event.buttonstate) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        if (!active && !debug) return;

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        if (mc.theWorld.getBlockState(mop.getBlockPos()).getBlock() != Blocks.ender_chest) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return;

        Item item = held.getItem();
        if (!isTrackedItem(item)) return;

        punchItem = item;
        punchInvBefore = countItemInInventory(mc, item);
        punchTicksLeft = 6;
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

        handlePunchWindow(mc);

        boolean inEnderGui = isEnderChestGuiOpen(mc);

        if (inEnderGui && !wasEnderGui) {
            enderGuiWarmup = 2; // avoid the first empty-tick flash
        }

        if (inEnderGui) {
            Counts base = countOpenEnderChestContainer(mc);

            if (enderGuiWarmup > 0) {
                enderGuiWarmup--;
                // keep showing the last known base while warmup runs
            } else {
                reconcilePending(base);

                lastIronBase = base.iron;
                lastGoldBase = base.gold;
                lastDiaBase = base.diamond;
                lastEmeBase = base.emerald;
            }

            ironEc = lastIronBase + ironPending;
            goldEc = lastGoldBase + goldPending;
            diaEc = lastDiaBase + diaPending;
            emeEc = lastEmeBase + emePending;
        } else {
            ironEc = lastIronBase + ironPending;
            goldEc = lastGoldBase + goldPending;
            diaEc = lastDiaBase + diaPending;
            emeEc = lastEmeBase + emePending;
        }

        wasEnderGui = inEnderGui;
    }

    private void handlePunchWindow(Minecraft mc) {
        if (punchTicksLeft <= 0) return;

        punchTicksLeft--;

        if (punchItem == null) {
            punchTicksLeft = 0;
            return;
        }

        int now = countItemInInventory(mc, punchItem);
        int diff = punchInvBefore - now;

        if (diff > 0) {
            addPending(punchItem, diff);

            punchTicksLeft = 0;
            punchItem = null;
            punchInvBefore = 0;
            return;
        }

        if (punchTicksLeft == 0) {
            punchItem = null;
            punchInvBefore = 0;
        }
    }

    private void reconcilePending(Counts base) {
        int ironInc = base.iron - lastIronBase;
        int goldInc = base.gold - lastGoldBase;
        int diaInc = base.diamond - lastDiaBase;
        int emeInc = base.emerald - lastEmeBase;

        if (ironInc > 0) ironPending = Math.max(0, ironPending - ironInc);
        if (goldInc > 0) goldPending = Math.max(0, goldPending - goldInc);
        if (diaInc > 0) diaPending = Math.max(0, diaPending - diaInc);
        if (emeInc > 0) emePending = Math.max(0, emePending - emeInc);
    }

    private boolean isEnderChestGuiOpen(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest)) return false;

        Container container = mc.thePlayer.openContainer;
        if (!(container instanceof ContainerChest)) return false;

        ContainerChest chest = (ContainerChest) container;
        IInventory lower = chest.getLowerChestInventory();

        String name = lower.getDisplayName() != null ? lower.getDisplayName().getUnformattedText() : "";
        name = StringUtils.stripControlCodes(name).toLowerCase();

        return name.contains("ender chest");
    }

    private Counts countOpenEnderChestContainer(Minecraft mc) {
        Counts c = new Counts();

        Container container = mc.thePlayer.openContainer;
        if (!(container instanceof ContainerChest)) return c;

        ContainerChest chest = (ContainerChest) container;
        IInventory lower = chest.getLowerChestInventory();

        int size = lower.getSizeInventory();
        for (int i = 0; i < size; i++) {
            ItemStack stack = lower.getStackInSlot(i);
            if (stack == null) continue;

            if (stack.getItem() == Items.iron_ingot) c.iron += stack.stackSize;
            else if (stack.getItem() == Items.gold_ingot) c.gold += stack.stackSize;
            else if (stack.getItem() == Items.diamond) c.diamond += stack.stackSize;
            else if (stack.getItem() == Items.emerald) c.emerald += stack.stackSize;
        }

        return c;
    }

    private void addPending(Item item, int amount) {
        if (amount <= 0) return;

        if (item == Items.iron_ingot) ironPending += amount;
        else if (item == Items.gold_ingot) goldPending += amount;
        else if (item == Items.diamond) diaPending += amount;
        else if (item == Items.emerald) emePending += amount;
    }

    private boolean isTrackedItem(Item item) {
        return item == Items.iron_ingot
                || item == Items.gold_ingot
                || item == Items.diamond
                || item == Items.emerald;
    }

    private int countItemInInventory(Minecraft mc, Item item) {
        int total = 0;
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (ItemStack stack : inv) {
            if (stack == null) continue;
            if (stack.getItem() == item) total += stack.stackSize;
        }
        return total;
    }

    private Counts countPlayerInventory(Minecraft mc) {
        Counts c = new Counts();

        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (ItemStack stack : inv) {
            if (stack == null) continue;

            if (stack.getItem() == Items.iron_ingot) c.iron += stack.stackSize;
            else if (stack.getItem() == Items.gold_ingot) c.gold += stack.stackSize;
            else if (stack.getItem() == Items.diamond) c.diamond += stack.stackSize;
            else if (stack.getItem() == Items.emerald) c.emerald += stack.stackSize;
        }

        return c;
    }

    private void resetAll() {
        ironInv = goldInv = diaInv = emeInv = 0;
        ironEc = goldEc = diaEc = emeEc = 0;

        lastIronBase = lastGoldBase = lastDiaBase = lastEmeBase = 0;
        ironPending = goldPending = diaPending = emePending = 0;

        punchTicksLeft = 0;
        punchItem = null;
        punchInvBefore = 0;

        wasEnderGui = false;
        enderGuiWarmup = 0;
    }

    private static class Counts {
        int iron = 0;
        int gold = 0;
        int diamond = 0;
        int emerald = 0;
    }
}
