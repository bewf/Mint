package me.bewf.mint.render;

import me.bewf.mint.MintClient;
import me.bewf.mint.config.MintConfig;
import me.bewf.mint.sync.Projection;
import me.bewf.mint.sync.WaypointLabel;
import me.bewf.mint.util.BedwarsDetector;
import me.bewf.mint.util.ResourceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Small floating labels above the team chest and the Ender Chest, showing what is inside.
 * <p>
 * This build has no hook inside the world render, so the labels are drawn flat on the screen just before
 * the frame is shown. The position comes from the same projection the Fabric build uses. Each piece of
 * OpenGL state the drawing changes is put back as it was, so the game does not notice.
 */
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
        // The projection assumes the camera sits at the player's eyes
        if (mc.options.perspective != 0) return;

        // How far into the current tick we are, so the labels move smoothly between ticks
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
            // Every Ender Chest close by opens the same inventory, so it gets the same label
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
            for (Target t : targets) {
                if (t.pos == null || t.parts == null || t.parts.isEmpty()) continue;

                // Just above the top of the chest, in the middle
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

    /** Size of an icon in text pixels. */
    private static final int ICON = 10;

    // Small hand drawn icons, because this build has no safe way to draw the game's own items.
    // o is the outline, h a highlight, b the body and s a shade.
    private static final String[] INGOT = {
            "........",
            "..ooooo.",
            ".ohhhhbo",
            "obbbbbbo",
            "obbbbbso",
            ".ooooooo",
            "........",
            "........",
    };
    private static final String[] GEM = {
            "........",
            "..oooo..",
            ".ohhhbo.",
            "ohhbbbbo",
            "obbbbbso",
            ".obbbso.",
            "..obso..",
            "...oo...",
    };
    private static final String[] OCTAGON = {
            "..oooo..",
            ".ohhhbo.",
            "ohhbbbbo",
            "ohbbbbso",
            "obbbbbso",
            "obbbssso",
            ".osssso.",
            "..oooo..",
    };
    private static final String[][] SHAPES = {INGOT, INGOT, GEM, OCTAGON};

    // outline, highlight, body, shade for iron, gold, diamond and emerald
    private static final int[][] PALETTE = {
            {0x5C5C5C, 0xFFFFFF, 0xD0D0D0, 0x9A9A9A},
            {0x8A5A00, 0xFFF6B0, 0xFFC71F, 0xD08A00},
            {0x137C86, 0xD5FFFB, 0x4DEBD6, 0x23B3B3},
            {0x0A6B24, 0xC9FFD9, 0x2BE06A, 0x14A84A},
    };

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

        // Up to two per row. A row of two uses two columns of the same width, so a pair lines up with the row above it.
        List<List<WaypointLabel.Part>> rows = WaypointLabel.rows(t.parts);
        float halfStep = (columnWidth + mc.textRenderer.getWidth(" ")) / 2f;

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        GL11.glScalef(scale, scale, 1f);

        boolean shadow = cfg.waypointShadow;
        for (int row = 0; row < rows.size(); row++) {
            List<WaypointLabel.Part> cells = rows.get(row);
            // The last row is the bottom one and sits where a single row would. The others stack above it.
            float rowY = -(rows.size() - 1 - row) * (ICON + 2);
            for (int c = 0; c < cells.size(); c++) {
                WaypointLabel.Part part = cells.get(c);
                float center = cells.size() == 1 ? 0f : (c == 0 ? -halfStep : halfStep);
                float cursor = Math.round(center - cellWidth[t.parts.indexOf(part)] / 2f);

                // The amount is in the chest's color when something marks the resource, and in the resource's color when nothing does
                int countRgb = marker == WaypointLabel.MARKER_NONE ? resourceRgb[part.resource] : t.countRgb;
                mc.textRenderer.draw(part.count, cursor, rowY - 9f, 0xFF000000 | countRgb, shadow);
                cursor += mc.textRenderer.getWidth(part.count);
                if (marker == WaypointLabel.MARKER_ICON) {
                    cursor += 1;
                    drawIcon(cursor, rowY - ICON, part.resource, shadow);
                } else if (marker == WaypointLabel.MARKER_LETTER) {
                    mc.textRenderer.draw(String.valueOf(part.letter), cursor, rowY - 9f, 0xFF000000 | resourceRgb[part.resource], shadow);
                }
            }
        }

        GL11.glPopMatrix();
    }

    /** Draws one icon from its pixels. The current color is put back afterwards, so the text after it is not tinted. */
    private static void drawIcon(float x, float y, int resource, boolean shadow) {
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        float r = COLOR.get(0);
        float g = COLOR.get(1);
        float b = COLOR.get(2);
        float a = COLOR.get(3);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        String[] shape = SHAPES[resource];
        float pixel = ICON / 8f;
        for (int pass = shadow ? 0 : 1; pass < 2; pass++) {
            float dx = pass == 0 ? 1f : 0f;
            GL11.glBegin(GL11.GL_QUADS);
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int slot = "ohbs".indexOf(shape[row].charAt(col));
                    if (slot < 0) continue;
                    int rgb = PALETTE[resource][slot];
                    if (pass == 0) {
                        GL11.glColor4f(0f, 0f, 0f, 0.35f);
                    } else {
                        GL11.glColor4f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
                    }
                    float left = x + col * pixel + dx;
                    float top = y + row * pixel + dx;
                    GL11.glVertex2f(left, top);
                    GL11.glVertex2f(left, top + pixel);
                    GL11.glVertex2f(left + pixel, top + pixel);
                    GL11.glVertex2f(left + pixel, top);
                }
            }
            GL11.glEnd();
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
    }

    /** The team chest or Ender Chest colour picked in the HUD settings, or the default when the HUD is not there. */
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

    /** Sets up flat drawing in screen pixels and remembers what to put back afterwards. */
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
