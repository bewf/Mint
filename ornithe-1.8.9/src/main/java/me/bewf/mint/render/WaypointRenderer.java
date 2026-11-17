package me.bewf.mint.render;

import me.bewf.mint.MintClient;
import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.Projection;
import me.bewf.mint.sync.WaypointLabel;
import me.bewf.mint.util.BedwarsDetector;
import me.bewf.mint.util.ResourceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

import java.util.ArrayList;
import java.util.List;

public final class WaypointRenderer {
    private WaypointRenderer() {
    }

    private static boolean failureLogged = false;
    private static long tickStartNanos = System.nanoTime();

    public static void register() {
        EventManager.register(TickEvent.Start.class, event -> tickStartNanos = System.nanoTime());
        EventManager.register(FramebufferRenderEvent.Start.class, event -> render());
    }

    private static void render() {
        try {
            draw();
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                System.out.println("[Mint] Chest waypoints failed to draw: " + t);
            }
        }
    }

    private static void draw() {
        MintConfig cfg = MintConfig.INSTANCE;
        if (cfg == null || (!cfg.teamChestWaypoint && !cfg.enderChestWaypoint)) return;
        if (!BedwarsDetector.onHypixel()) return;
        if (!ResourceTracker.isActive() && !cfg.showOutsideBedwars) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.screen != null || mc.options.hideGui) return;
        if (mc.options.perspective != 0) return;

        double partial = Math.max(0.0, Math.min(1.0, (System.nanoTime() - tickStartNanos) / 50000000.0));
        double ex = mc.player.prevX + (mc.player.x - mc.player.prevX) * partial;
        double ey = mc.player.prevY + (mc.player.y - mc.player.prevY) * partial + mc.player.getEyeHeight();
        double ez = mc.player.prevZ + (mc.player.z - mc.player.prevZ) * partial;
        double yaw = mc.player.yaw;
        double pitch = mc.player.pitch;
        double fov = Math.max(10.0, Math.min(170.0, mc.options.fov + cfg.waypointFovAdjust));

        int width = Display.getWidth();
        int height = Display.getHeight();
        if (width <= 0 || height <= 0) return;
        int guiScale = Math.max(1, new Window(mc).getScale());

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

        List<Target> targets = new ArrayList<Target>();
        targets.add(new Target(ResourceTracker.homeChest(), team, hudRgb(true)));
        targets.add(new Target(ResourceTracker.homeEnderChest(), ender, hudRgb(false)));
        if (ender != null) {
            for (BlockPos near : ResourceTracker.nearbyEnderChests()) {
                if (!near.equals(ResourceTracker.homeEnderChest())) targets.add(new Target(near, ender, hudRgb(false)));
            }
        }
        if (cfg.otherChestWaypoints && cfg.teamChestWaypoint) {
            BlockPos home = ResourceTracker.homeChest();
            boolean[] shown = {cfg.teamWaypointIron, cfg.teamWaypointGold, cfg.teamWaypointDiamond, cfg.teamWaypointEmerald};
            for (ResourceTracker.OtherChest other : ResourceTracker.otherChests()) {
                if (home != null && Math.abs(other.pos.getX() - home.getX()) + Math.abs(other.pos.getY() - home.getY())
                        + Math.abs(other.pos.getZ() - home.getZ()) <= 1) continue;
                targets.add(new Target(other.pos,
                        WaypointLabel.parts(other.counts, inventory, shown, cfg.teamChestWaypointAmount),
                        hudRgb(true)));
            }
        }

        GlState state = GlState.begin(width, height);
        try {
            if (cfg.waypointDebug) drawDebug(mc, guiScale, fov, yaw, pitch);
            for (Target t : targets) {
                if (t.pos == null || t.parts == null || t.parts.isEmpty()) continue;

                double[] p = Projection.project(ex, ey, ez, yaw, pitch, fov, width, height,
                        t.pos.getX() + 0.5, t.pos.getY() + WaypointLabel.HEIGHT_ABOVE_CHEST, t.pos.getZ() + 0.5);
                if (p == null || p[2] > cfg.waypointMaxDistance) continue;
                if (p[0] < -200 || p[0] > width + 200 || p[1] < -100 || p[1] > height + 100) continue;

                float scale = guiScale * Projection.scaleFor(p[2], cfg.waypointShrink) * cfg.waypointSize;
                drawLabel(mc, t, (float) p[0], (float) p[1], scale);
            }
        } finally {
            state.end();
        }
    }

    private static void drawDebug(Minecraft mc, int guiScale, double fov, double yaw, double pitch) {
        String[] lines = {
                String.format("Mint waypoints: fov %.1f, options fov %.1f", fov, mc.options.fov),
                String.format("view yaw %.1f pitch %.1f, sprinting %b, sneaking %b", yaw, pitch, mc.player.isSprinting(), mc.player.isSneaking()),
                ResourceTracker.debugChest,
        };
        GL11.glPushMatrix();
        GL11.glTranslatef(4f * guiScale, 4f * guiScale, 0f);
        GL11.glScalef(guiScale, guiScale, 1f);
        for (int i = 0; i < lines.length; i++) {
            mc.textRenderer.draw(lines[i], 0f, i * 10f, 0xFFFFFFFF, true);
        }
        GL11.glPopMatrix();
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

    private static final java.nio.FloatBuffer COLOR = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static void drawLabel(Minecraft mc, Target t, float x, float y, float scale) {
        MintConfig cfg = MintConfig.INSTANCE;
        int[] resourceRgb = {
                cfg.ironColor.getRawArgb() & 0xFFFFFF,
                cfg.goldColor.getRawArgb() & 0xFFFFFF,
                cfg.diamondColor.getRawArgb() & 0xFFFFFF,
                cfg.emeraldColor.getRawArgb() & 0xFFFFFF,
        };
        int marker = cfg.waypointMarker;

        int n = t.parts.size();
        int[] cellWidth = new int[n];
        int columnWidth = 0;
        for (int i = 0; i < n; i++) {
            WaypointLabel.Part part = t.parts.get(i);
            cellWidth[i] = mc.textRenderer.getWidth(part.count);
            if (marker == WaypointLabel.MARKER_ICON) {
                cellWidth[i] += 1 + ICON;
            } else if (marker == WaypointLabel.MARKER_LETTER) {
                cellWidth[i] += mc.textRenderer.getWidth(String.valueOf(part.letter));
            }
            columnWidth = Math.max(columnWidth, cellWidth[i]);
        }

        List<List<WaypointLabel.Part>> rows = WaypointLabel.rows(t.parts);
        float halfStep = (columnWidth + mc.textRenderer.getWidth(" ")) / 2f;

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        GL11.glScalef(scale, scale, 1f);

        boolean shadow = cfg.waypointShadow;
        for (int row = 0; row < rows.size(); row++) {
            List<WaypointLabel.Part> cells = rows.get(row);
            float rowY = -(rows.size() - 1 - row) * (ICON + 2);
            for (int c = 0; c < cells.size(); c++) {
                WaypointLabel.Part part = cells.get(c);
                float center = cells.size() == 1 ? 0f : (c == 0 ? -halfStep : halfStep);
                float cursor = Math.round(center - cellWidth[t.parts.indexOf(part)] / 2f);

                int countRgb = marker == WaypointLabel.MARKER_NONE ? resourceRgb[part.resource] : t.countRgb;
                mc.textRenderer.draw(part.count, cursor, rowY - 9f, 0xFF000000 | countRgb, shadow);
                cursor += mc.textRenderer.getWidth(part.count);
                if (marker == WaypointLabel.MARKER_ICON) {
                    cursor += 1;
                    drawIcon(mc, cursor, rowY - ICON, part.resource);
                } else if (marker == WaypointLabel.MARKER_LETTER) {
                    mc.textRenderer.draw(String.valueOf(part.letter), cursor, rowY - 9f, 0xFF000000 | resourceRgb[part.resource], shadow);
                }
            }
        }

        GL11.glPopMatrix();
    }

    private static void drawIcon(Minecraft mc, float x, float y, int resource) {
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        float r = COLOR.get(0);
        float g = COLOR.get(1);
        float b = COLOR.get(2);
        float a = COLOR.get(3);

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        float size = ICON / 16f;
        GL11.glScalef(size, size, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.getItemRenderer().renderGuiItem(icon(resource), 0, 0);
        GL11.glPopMatrix();

        GL11.glColor4f(r, g, b, a);
    }

    private static int hudRgb(boolean team) {
        try {
            if (MintClient.hud() != null) {
                return (team ? MintClient.hud().getTeamChestColor() : MintClient.hud().getEnderChestColor()).getRawArgb() & 0xFFFFFF;
            }
        } catch (Throwable ignored) {
        }
        return team ? 0x55AAFF : 0xBE3FFF;
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

    private static final class GlState {
        private boolean depth;
        private boolean lighting;
        private boolean cull;
        private boolean blend;
        private boolean texture;
        private int matrixMode;
        private int srcRgb;
        private int dstRgb;
        private int srcAlpha;
        private int dstAlpha;

        static GlState begin(int width, int height) {
            GlState s = new GlState();
            s.depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            s.lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            s.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            s.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            s.texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            s.matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            s.srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            s.dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            s.srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            s.dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, width, height, 0.0, -1000.0, 1000.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            set(GL11.GL_DEPTH_TEST, false);
            set(GL11.GL_LIGHTING, false);
            set(GL11.GL_CULL_FACE, false);
            set(GL11.GL_BLEND, true);
            set(GL11.GL_TEXTURE_2D, true);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            return s;
        }

        void end() {
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            set(GL11.GL_TEXTURE_2D, texture);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_CULL_FACE, cull);
            set(GL11.GL_LIGHTING, lighting);
            set(GL11.GL_DEPTH_TEST, depth);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
        }

        private static void set(int capability, boolean on) {
            if (on) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }
    }
}
