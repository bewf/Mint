package me.bewf.mint.render;

import me.bewf.mint.MintConstants;
import me.bewf.mint.config.MintConfig;
import me.bewf.mint.hud.HudColors;
import me.bewf.mint.sync.Delta;
import me.bewf.mint.sync.Projection;
import me.bewf.mint.sync.WaypointLabel;
import me.bewf.mint.util.BedwarsDetector;
import me.bewf.mint.util.ResourceTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >= 26.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Small floating labels above the team chest and the Ender Chest, showing what is inside.
 * <p>
 * The label is drawn on the HUD layer. Its screen position comes from {@link Projection}, which
 * turns the chest's position in the world into a point on the screen. That keeps this class free
 * of the world rendering code that changes with every Minecraft version.
 */
public final class WaypointRenderer {

    private WaypointRenderer() {
    }

    private static boolean failureLogged = false;

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MintConstants.ID, "chest_waypoints"),
                (graphics, tickCounter) -> {
                    try {
                        render(graphics, tickCounter.getGameTimeDeltaPartialTick(false));
                    } catch (Throwable t) {
                        if (!failureLogged) {
                            failureLogged = true;
                            System.out.println("[Mint] Chest waypoints failed to draw: " + t);
                        }
                    }
                });
    }

    //? if >= 26.2 {
    private static void render(GuiGraphicsExtractor g, float partialTick) {
    //?} else {
    /*private static void render(GuiGraphics g, float partialTick) {
    *///?}
        MintConfig cfg = MintConfig.INSTANCE;
        if (!cfg.teamChestWaypoint && !cfg.enderChestWaypoint) return;
        if (!BedwarsDetector.onHypixel()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!ResourceTracker.isActive() && !cfg.showOutsideBedwars) return;
        // The projection assumes the camera sits at the player's eyes
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Vec3 eye = mc.player.getEyePosition(partialTick);
        double yaw = mc.player.getViewYRot(partialTick);
        double pitch = mc.player.getViewXRot(partialTick);
        double fov = mc.options.fov().get();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        List<WaypointLabel.Part> team = null;
        List<WaypointLabel.Part> ender = null;
        if (cfg.teamChestWaypoint) {
            team = WaypointLabel.parts(ResourceTracker.teamChestIron, ResourceTracker.teamChestGold,
                    ResourceTracker.teamChestDiamond, ResourceTracker.teamChestEmerald, true);
        }
        if (cfg.enderChestWaypoint) {
            ender = WaypointLabel.parts(ResourceTracker.ironEc, ResourceTracker.goldEc,
                    ResourceTracker.diaEc, ResourceTracker.emeEc, false);
        }

        Font font = mc.font;
        Target[] targets = {
                new Target(ResourceTracker.homeChest(), team, HudColors.teamRgb()),
                new Target(ResourceTracker.homeEnderChest(), ender, HudColors.enderRgb()),
        };

        for (Target t : targets) {
            if (t.pos == null || t.parts == null || t.parts.isEmpty()) continue;

            // Just above the top of the chest, in the middle
            double[] p = Projection.project(eye.x, eye.y, eye.z, yaw, pitch, fov, width, height,
                    t.pos.getX() + 0.5, t.pos.getY() + 1.3, t.pos.getZ() + 0.5);
            if (p == null || p[2] > Projection.MAX_DISTANCE) continue;
            if (p[0] < -60 || p[0] > width + 60 || p[1] < -30 || p[1] > height + 30) continue;

            Component text = labelText(t.parts, t.countRgb);
            float scale = Projection.scaleFor(p[2]);
            int textWidth = font.width(text);

            g.pose().pushMatrix();
            g.pose().translate((float) p[0], (float) p[1]);
            g.pose().scale(scale, scale);
            //? if >= 26.2 {
            g.text(font, text, -textWidth / 2, -9, 0xFFFFFFFF, true);
            //?} else {
            /*g.drawString(font, text, -textWidth / 2, -9, 0xFFFFFFFF, true);
            *///?}
            g.pose().popMatrix();
        }
    }

    /** The count in the chest's colour, followed by a one letter tag in the resource's colour. */
    private static Component labelText(List<WaypointLabel.Part> parts, int countRgb) {
        MintConfig cfg = MintConfig.INSTANCE;
        int[] resourceRgb = {
                cfg.ironColor.getRawArgb() & 0xFFFFFF,
                cfg.goldColor.getRawArgb() & 0xFFFFFF,
                cfg.diamondColor.getRawArgb() & 0xFFFFFF,
                cfg.emeraldColor.getRawArgb() & 0xFFFFFF,
        };

        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            WaypointLabel.Part part = parts.get(i);
            if (i > 0) line.append(Component.literal(" "));
            line.append(Component.literal(part.count).withColor(countRgb));
            line.append(Component.literal(String.valueOf(part.letter)).withColor(resourceRgb[part.resource]));
        }
        return line;
    }

    private static final class Target {
        final BlockPos pos;
        final List<WaypointLabel.Part> parts;
        final int countRgb;

        Target(BlockPos pos, List<WaypointLabel.Part> parts, int countRgb) {
            this.pos = pos;
            this.parts = parts;
            this.countRgb = countRgb;
        }
    }
}
