package me.bewf.mint.util;

import me.bewf.mint.config.MintConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
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
import net.minecraft.world.level.block.Blocks;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
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

    private static final Pattern TEAM_DEPOSIT_PATTERN = Pattern.compile(
            "Deposited \\d+ (.+) into Team Chest!? ?\\((\\d+)\\)? ?total?",
            Pattern.CASE_INSENSITIVE
    );

    private static final int PLAYER_INVENTORY_SLOTS = 36;

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

    public void register() {
        EventManager.INSTANCE.register(TickEvent.End.class, event -> onClientTick());
        AttackBlockCallback.EVENT.register(this::onAttackBlock);
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
            if (held.isEmpty() || !isTrackedItem(held.getItem())) return InteractionResult.PASS;
            pendingDeposits.add(new PendingDeposit(
                    held.getItem(),
                    countItemInInventory(mc, held.getItem())
            ));
        } else if (world.getBlockState(pos).getBlock() == Blocks.CHEST) {
            teamChestInteractionWarmup = 100;
        }

        return InteractionResult.PASS;
    }

    private void onChat(String raw) {
        if (raw == null) return;

        String msg = StringUtil.stripColor(raw);

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
                inEnderGui ? countOpenChestContainer(mc)
                        : new Counts();

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

    private void handlePendingDeposits(Minecraft mc) {
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

    private String openChestTitle(Minecraft mc) {
        Object screenObj = ReflectUtil.getScreen(mc);
        if (!(screenObj instanceof AbstractContainerScreen<?> screen)) return null;
        if (!(mc.player.containerMenu instanceof ChestMenu)) return null;

        String name = screen.getTitle() != null ? screen.getTitle().getString() : "";
        return StringUtil.stripColor(name).toLowerCase();
    }

    private boolean isEnderChestGuiOpen(Minecraft mc) {
        String name = openChestTitle(mc);
        return name != null && name.contains("ender chest");
    }

    private boolean isTeamChestGuiOpen(Minecraft mc) {
        String name = openChestTitle(mc);
        return name != null && name.contains("chest") && !name.contains("ender");
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
