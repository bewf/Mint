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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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

    private static void render(GuiGraphicsExtractor g, float partialTick) {
        MintConfig cfg = MintConfig.INSTANCE;
        if (!cfg.teamChestWaypoint && !cfg.enderChestWaypoint) return;
        if (!BedwarsDetector.onHypixel()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!ResourceTracker.isActive() && !cfg.showOutsideBedwars) return;

        double[] cam = ViewInfo.camera(mc);
        Vec3 eye;
        double yaw;
        double pitch;
        if (mc.options.getCameraType().isFirstPerson()) {
            eye = cam != null ? new Vec3(cam[0], cam[1], cam[2]) : mc.player.getEyePosition(partialTick);
            yaw = mc.player.getViewYRot(partialTick);
            pitch = mc.player.getViewXRot(partialTick);
        } else if (cam != null) {
            eye = new Vec3(cam[0], cam[1], cam[2]);
            yaw = cam[3];
            pitch = cam[4];
        } else {
            return;
        }
        double fov = ViewInfo.fov(mc, partialTick, mc.options.fov().get());
        double[] bob = ViewInfo.bob(mc, partialTick);
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        if (cfg.waypointDebug) drawDebug(g, mc, fov, cam != null, bob != null, yaw, pitch);
        fov = Math.max(10.0, Math.min(170.0, fov + cfg.waypointFovAdjust));

        int[] inventory = {ResourceTracker.ironInv, ResourceTracker.goldInv, ResourceTracker.diaInv, ResourceTracker.emeInv};
        List<WaypointLabel.Part> team = null;
        List<WaypointLabel.Part> ender = null;
        if (cfg.teamChestWaypoint) {
            team = WaypointLabel.parts(
                    new int[]{ResourceTracker.teamChestIron, ResourceTracker.teamChestGold,
                            ResourceTracker.teamChestDiamond, ResourceTracker.teamChestEmerald},
                    inventory,
                    new boolean[]{cfg.teamWaypointIron, cfg.teamWaypointGold, cfg.teamWaypointDiamond, cfg.teamWaypointEmerald},
                    cfg.teamChestWaypointAmount);
        }
        if (cfg.enderChestWaypoint) {
            ender = WaypointLabel.parts(
                    new int[]{ResourceTracker.ironEc, ResourceTracker.goldEc, ResourceTracker.diaEc, ResourceTracker.emeEc},
                    inventory,
                    new boolean[]{cfg.enderWaypointIron, cfg.enderWaypointGold, cfg.enderWaypointDiamond, cfg.enderWaypointEmerald},
                    cfg.enderChestWaypointAmount);
        }

        Font font = mc.font;
        List<Target> targets = new ArrayList<>();
        targets.add(new Target(ResourceTracker.homeChest(), team, HudColors.teamRgb()));
        targets.add(new Target(ResourceTracker.homeEnderChest(), ender, HudColors.enderRgb()));
        if (ender != null) {
            for (BlockPos near : ResourceTracker.nearbyEnderChests()) {
                if (!near.equals(ResourceTracker.homeEnderChest())) targets.add(new Target(near, ender, HudColors.enderRgb()));
            }
        }
        if (cfg.otherChestWaypoints && cfg.teamChestWaypoint) {
            BlockPos home = ResourceTracker.homeChest();
            boolean[] shown = {cfg.teamWaypointIron, cfg.teamWaypointGold, cfg.teamWaypointDiamond, cfg.teamWaypointEmerald};
            for (ResourceTracker.OtherChest other : ResourceTracker.otherChests()) {
                if (home != null && other.pos.distManhattan(home) <= 1) continue;
                targets.add(new Target(other.pos,
                        WaypointLabel.parts(other.counts, inventory, shown, cfg.teamChestWaypointAmount),
                        HudColors.teamRgb()));
            }
        }

        for (Target t : targets) {
            if (t.pos == null || t.parts == null || t.parts.isEmpty()) continue;

            double[] p = Projection.project(eye.x, eye.y, eye.z, yaw, pitch, fov, width, height,
                    t.pos.getX() + 0.5, t.pos.getY() + WaypointLabel.HEIGHT_ABOVE_CHEST, t.pos.getZ() + 0.5, bob);
            if (p == null || p[2] > cfg.waypointMaxDistance) continue;
            float scale = Projection.scaleFor(p[2], cfg.waypointShrink) * cfg.waypointSize;
            float reachX = 60f * scale, reachY = 12f * scale;
            if (p[0] < -reachX || p[0] > width + reachX || p[1] < -reachY || p[1] > height + reachY + 10f * scale) continue;
            drawLabel(g, font, t, (float) p[0], (float) p[1], scale);
        }
    }

    private static void drawDebug(GuiGraphicsExtractor g, Minecraft mc, double fov, boolean camera, boolean bob, double yaw, double pitch) {
        String[] lines = {
                String.format("Mint waypoints: fov %.1f (%s), options fov %d", fov, ViewInfo.fovNote, mc.options.fov().get()),
                "camera: " + ViewInfo.cameraNote + ", bobbing: " + ViewInfo.bobNote,
                String.format("view yaw %.1f pitch %.1f, sprinting %b, sneaking %b", yaw, pitch, mc.player.isSprinting(), mc.player.isShiftKeyDown()),
                ResourceTracker.debugChest,
        };
        for (int i = 0; i < lines.length; i++) {
            drawText(g, mc.font, Component.literal(lines[i]), 4, 4 + i * 10, true);
        }
    }

    private static final int ICON = 10;

    private static ItemStack[] icons = null;

    private static ItemStack icon(int resource) {
        if (icons == null) {
            icons = new ItemStack[]{
                    new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT),
                    new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)};
        }
        return icons[resource];
    }

    private static void drawLabel(GuiGraphicsExtractor g, Font font, Target t, float x, float y, float scale) {
        MintConfig cfg = MintConfig.INSTANCE;
        int[] resourceRgb = {
                cfg.ironColor.getRawArgb() & 0xFFFFFF,
                cfg.goldColor.getRawArgb() & 0xFFFFFF,
                cfg.diamondColor.getRawArgb() & 0xFFFFFF,
                cfg.emeraldColor.getRawArgb() & 0xFFFFFF,
        };
        int marker = cfg.waypointMarker;

        int n = t.parts.size();
        Component[] counts = new Component[n];
        Component[] letters = new Component[n];
        int[] cellWidth = new int[n];
        int columnWidth = 0;
        for (int i = 0; i < n; i++) {
            WaypointLabel.Part part = t.parts.get(i);
            int countRgb = marker == WaypointLabel.MARKER_NONE ? resourceRgb[part.resource] : t.countRgb;
            counts[i] = Component.literal(part.count).withColor(countRgb);
            cellWidth[i] = font.width(counts[i]);
            if (marker == WaypointLabel.MARKER_ICON) {
                cellWidth[i] += 1 + ICON;
            } else if (marker == WaypointLabel.MARKER_LETTER) {
                letters[i] = Component.literal(String.valueOf(part.letter)).withColor(resourceRgb[part.resource]);
                cellWidth[i] += font.width(letters[i]);
            }
            columnWidth = Math.max(columnWidth, cellWidth[i]);
        }

        List<List<WaypointLabel.Part>> rows = WaypointLabel.rows(t.parts);
        float halfStep = (columnWidth + font.width(" ")) / 2f;

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        for (int row = 0; row < rows.size(); row++) {
            List<WaypointLabel.Part> cells = rows.get(row);
            int rowY = -(rows.size() - 1 - row) * (ICON + 2);
            for (int c = 0; c < cells.size(); c++) {
                int i = t.parts.indexOf(cells.get(c));
                float center = cells.size() == 1 ? 0f : (c == 0 ? -halfStep : halfStep);

                g.pose().pushMatrix();
                g.pose().translate(Math.round(center - cellWidth[i] / 2f), (float) rowY);
                int cursor = 0;
                drawText(g, font, counts[i], cursor, -9, cfg.waypointShadow);
                cursor += font.width(counts[i]);
                if (marker == WaypointLabel.MARKER_ICON) {
                    cursor += 1;
                    g.pose().pushMatrix();
                    g.pose().translate((float) cursor, (float) -ICON);
                    g.pose().scale(ICON / 16f, ICON / 16f);
                    drawItem(g, icon(t.parts.get(i).resource));
                    g.pose().popMatrix();
                } else if (marker == WaypointLabel.MARKER_LETTER) {
                    drawText(g, font, letters[i], cursor, -9, cfg.waypointShadow);
                }
                g.pose().popMatrix();
            }
        }

        g.pose().popMatrix();
    }

    private static void drawText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, boolean shadow) {
        g.text(font, text, x, y, 0xFFFFFFFF, shadow);
    }

    private static void drawItem(GuiGraphicsExtractor g, ItemStack stack) {
        g.item(stack, 0, 0);
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
