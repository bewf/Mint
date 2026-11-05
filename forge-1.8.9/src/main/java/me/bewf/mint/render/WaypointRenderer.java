package me.bewf.mint.render;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.Projection;
import me.bewf.mint.sync.WaypointLabel;
import me.bewf.mint.util.BedwarsDetector;
import me.bewf.mint.util.ResourceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
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

        if (cfg.teamChestWaypoint) {
            draw(mc, ResourceTracker.homeChest(), vx, vy, vz,
                    WaypointLabel.parts(ResourceTracker.teamChestIron, ResourceTracker.teamChestGold,
                            ResourceTracker.teamChestDiamond, ResourceTracker.teamChestEmerald, true),
                    rgb(cfg.teamChestColor, 0x55AAFF));
        }
        if (cfg.enderChestWaypoint) {
            draw(mc, ResourceTracker.homeEnderChest(), vx, vy, vz,
                    WaypointLabel.parts(ResourceTracker.ironEc, ResourceTracker.goldEc,
                            ResourceTracker.diaEc, ResourceTracker.emeEc, false),
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
        if (dist > Projection.MAX_DISTANCE) return;

        MintConfig cfg = MintConfig.INSTANCE;
        int[] resourceRgb = {
                rgb(cfg.ironColor, 0xFFFFFF), rgb(cfg.goldColor, 0xFFAA00),
                rgb(cfg.diamondColor, 0x55FFFF), rgb(cfg.emeraldColor, 0x55FF55)
        };

        // The label keeps one on-screen size up close and shrinks with distance (see Projection.scaleFor)
        float worldScale = 0.0105f * (float) Math.max(dist, 1.0) * Projection.scaleFor(dist);

        FontRenderer fr = mc.fontRendererObj;
        int total = 0;
        for (int i = 0; i < parts.size(); i++) {
            WaypointLabel.Part part = parts.get(i);
            if (i > 0) total += fr.getStringWidth(" ");
            total += fr.getStringWidth(part.count) + fr.getStringWidth(String.valueOf(part.letter));
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
        for (int i = 0; i < parts.size(); i++) {
            WaypointLabel.Part part = parts.get(i);
            if (i > 0) x += fr.getStringWidth(" ");
            x = fr.drawStringWithShadow(part.count, x, -fr.FONT_HEIGHT, 0xFF000000 | countRgb);
            String letter = String.valueOf(part.letter);
            x = fr.drawStringWithShadow(letter, x, -fr.FONT_HEIGHT, 0xFF000000 | resourceRgb[part.resource]);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
