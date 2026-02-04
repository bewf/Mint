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
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    private static boolean wasEnderGui = false;
    private static int enderGuiWarmup = 0;

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

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        boolean debug = MintConfig.INSTANCE.showOutsideBedwars;
        if (!active && !debug) return;

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        if (mc.theWorld.getBlockState(mop.getBlockPos()).getBlock() != Blocks.ender_chest) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !isTrackedItem(held.getItem())) return;

        pendingDeposits.add(new PendingDeposit(held.getItem(), countItemInInventory(mc, held.getItem())));
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

        // update inventory HUD
        Counts invNow = countPlayerInventory(mc);
        ironInv = invNow.iron;
        goldInv = invNow.gold;
        diaInv = invNow.diamond;
        emeInv = invNow.emerald;

        handlePendingDeposits(mc);

        boolean inEnderGui = isEnderChestGuiOpen(mc);
        if (inEnderGui && !wasEnderGui) enderGuiWarmup = 2;

        Counts chestCounts = inEnderGui ? countOpenEnderChestContainer(mc) : new Counts();

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
    }

    private void handlePendingDeposits(Minecraft mc) {
        Iterator<PendingDeposit> it = pendingDeposits.iterator();
        while (it.hasNext()) {
            PendingDeposit pd = it.next();
            int now = countItemInInventory(mc, pd.item);
            int diff = pd.inventorySnapshot - now;
            if (diff > 0) {
                // update hidden enderchest amount directly
                if (pd.item == Items.iron_ingot) lastIronBase += diff;
                else if (pd.item == Items.gold_ingot) lastGoldBase += diff;
                else if (pd.item == Items.diamond) lastDiaBase += diff;
                else if (pd.item == Items.emerald) lastEmeBase += diff;

                it.remove(); // processed
            }
        }
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

        for (int i = 0; i < lower.getSizeInventory(); i++) {
            ItemStack stack = lower.getStackInSlot(i);
            if (stack == null) continue;

            if (stack.getItem() == Items.iron_ingot) c.iron += stack.stackSize;
            else if (stack.getItem() == Items.gold_ingot) c.gold += stack.stackSize;
            else if (stack.getItem() == Items.diamond) c.diamond += stack.stackSize;
            else if (stack.getItem() == Items.emerald) c.emerald += stack.stackSize;
        }

        return c;
    }

    private boolean isTrackedItem(Item item) {
        return item == Items.iron_ingot || item == Items.gold_ingot || item == Items.diamond || item == Items.emerald;
    }

    private int countItemInInventory(Minecraft mc, Item item) {
        int total = 0;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack == null) continue;
            if (stack.getItem() == item) total += stack.stackSize;
        }
        return total;
    }

    private Counts countPlayerInventory(Minecraft mc) {
        Counts c = new Counts();
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
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

        pendingDeposits.clear();

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
