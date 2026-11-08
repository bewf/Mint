package me.bewf.mint.render;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.Projection;
import me.bewf.mint.sync.WaypointLabel;
import me.bewf.mint.util.BedwarsDetector;
import me.bewf.mint.util.ResourceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * Small floating labels above the team chest and the Ender Chest, showing what is inside.
 * They are drawn through walls, stay the same size up close and shrink with distance.
 */
public class WaypointRenderer {

    private boolean failureLogged = false;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        try {
            render(event.partialTicks);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                System.out.println("[Mint] Chest waypoints failed to draw: " + t);
            }
        }
    }

    private void render(float partialTicks) {
        MintConfig cfg = MintConfig.INSTANCE;
        if (!cfg.teamChestWaypoint && !cfg.enderChestWaypoint) return;
        if (!BedwarsDetector.onHypixel()) return;
        if (!ResourceTracker.isActive() && !cfg.showOutsideBedwars) return;

        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) return;

        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        int[] inventory = {ResourceTracker.ironInv, ResourceTracker.goldInv, ResourceTracker.diaInv, ResourceTracker.emeInv};
        if (cfg.teamChestWaypoint) {
            draw(mc, ResourceTracker.homeChest(), vx, vy, vz,
                    WaypointLabel.parts(
                            new int[]{ResourceTracker.teamChestIron, ResourceTracker.teamChestGold,
                                    ResourceTracker.teamChestDiamond, ResourceTracker.teamChestEmerald},
                            inventory,
                            new boolean[]{cfg.teamWaypointIron, cfg.teamWaypointGold, cfg.teamWaypointDiamond, cfg.teamWaypointEmerald},
                            cfg.teamChestWaypointAmount),
                    rgb(cfg.teamChestColor, 0x55AAFF));
        }
        if (cfg.enderChestWaypoint) {
            draw(mc, ResourceTracker.homeEnderChest(), vx, vy, vz,
                    WaypointLabel.parts(
                            new int[]{ResourceTracker.ironEc, ResourceTracker.goldEc, ResourceTracker.diaEc, ResourceTracker.emeEc},
                            inventory,
                            new boolean[]{cfg.enderWaypointIron, cfg.enderWaypointGold, cfg.enderWaypointDiamond, cfg.enderWaypointEmerald},
                            cfg.enderChestWaypointAmount),
                    rgb(cfg.enderChestColor, 0xBE3FFF));
        }
    }

    private static int rgb(cc.polyfrost.oneconfig.config.core.OneColor color, int fallback) {
        try {
            return color.getRGB() & 0xFFFFFF;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void draw(Minecraft mc, BlockPos pos, double vx, double vy, double vz,
                      List<WaypointLabel.Part> parts, int countRgb) {
        if (pos == null || parts == null || parts.isEmpty()) return;

        double dx = pos.getX() + 0.5 - vx;
        double dy = pos.getY() + 1.3 - vy;
        double dz = pos.getZ() + 0.5 - vz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        MintConfig cfg = MintConfig.INSTANCE;
        if (dist > cfg.waypointMaxDistance) return;

        int[] resourceRgb = {
                rgb(cfg.ironColor, 0xFFFFFF), rgb(cfg.goldColor, 0xFFAA00),
                rgb(cfg.diamondColor, 0x55FFFF), rgb(cfg.emeraldColor, 0x55FF55)
        };

        // The label is as big as normal game text up close and shrinks with distance (see Projection.scaleFor).
        // A text unit is one GUI pixel on screen, so the world size is that many pixels at this distance.
        double focal = (mc.displayHeight / 2.0) / Math.tan(Math.toRadians(mc.gameSettings.fovSetting) / 2.0);
        float worldScale = (float) (Math.max(dist, 1.0) * new ScaledResolution(mc).getScaleFactor() / focal)
                * Projection.scaleFor(dist, cfg.waypointShrink) * cfg.waypointSize;

        FontRenderer fr = mc.fontRendererObj;
        int marker = cfg.waypointMarker;
        int total = 0;
        for (int i = 0; i < parts.size(); i++) {
            WaypointLabel.Part part = parts.get(i);
            if (i > 0) total += fr.getStringWidth(" ");
            total += fr.getStringWidth(part.count);
            if (marker == WaypointLabel.MARKER_ICON) {
                total += 1 + ICON;
            } else if (marker == WaypointLabel.MARKER_LETTER) {
                total += fr.getStringWidth(String.valueOf(part.letter));
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) dx, (float) dy, (float) dz);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-worldScale, -worldScale, worldScale);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float x = -total / 2f;
        boolean shadow = cfg.waypointShadow;
        for (int i = 0; i < parts.size(); i++) {
            WaypointLabel.Part part = parts.get(i);
            if (i > 0) x += fr.getStringWidth(" ");
            // The amount is in the chest's color when something marks the resource, and in the resource's color when nothing does
            int amountRgb = marker == WaypointLabel.MARKER_NONE ? resourceRgb[part.resource] : countRgb;
            fr.drawString(part.count, x, -fr.FONT_HEIGHT, 0xFF000000 | amountRgb, shadow);
            x += fr.getStringWidth(part.count);
            if (marker == WaypointLabel.MARKER_ICON) {
                x += 1;
                drawIcon(mc, part.resource, x, -ICON);
                x += ICON;
            } else if (marker == WaypointLabel.MARKER_LETTER) {
                String letter = String.valueOf(part.letter);
                fr.drawString(letter, x, -fr.FONT_HEIGHT, 0xFF000000 | resourceRgb[part.resource], shadow);
                x += fr.getStringWidth(letter);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Size of an item icon in text units. Items are 16 units, so they are shrunk to sit beside the text. */
    private static final int ICON = 10;

    private static ItemStack[] icons = null;

    private static void drawIcon(Minecraft mc, int resource, float x, float y) {
        if (icons == null) {
            icons = new ItemStack[]{
                    new ItemStack(Items.iron_ingot), new ItemStack(Items.gold_ingot),
                    new ItemStack(Items.diamond), new ItemStack(Items.emerald)};
        }
        GlStateManager.pushMatrix();
        // The game moves GUI items 100 units forward, so move them back by the same amount
        GlStateManager.translate(x, y, -100f);
        GlStateManager.scale(ICON / 16f, ICON / 16f, 1f);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(icons[resource], 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
