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
        double fov = mc.options.fov;

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

        Target[] targets = {
                new Target(ResourceTracker.homeChest(), team, hudRgb(true)),
                new Target(ResourceTracker.homeEnderChest(), ender, hudRgb(false)),
        };

        GlState state = GlState.begin(width, height);
        try {
            for (Target t : targets) {
                if (t.pos == null || t.parts == null || t.parts.isEmpty()) continue;

                // Just above the top of the chest, in the middle
                double[] p = Projection.project(ex, ey, ez, yaw, pitch, fov, width, height,
                        t.pos.getX() + 0.5, t.pos.getY() + 1.3, t.pos.getZ() + 0.5);
                if (p == null || p[2] > cfg.waypointMaxDistance) continue;
                if (p[0] < -200 || p[0] > width + 200 || p[1] < -100 || p[1] > height + 100) continue;

                float scale = guiScale * Projection.scaleFor(p[2], cfg.waypointShrink) * cfg.waypointScale;
                drawLabel(mc, t, (float) p[0], (float) p[1], scale);
            }
        } finally {
            state.end();
        }
    }

    private static void drawLabel(Minecraft mc, Target t, float x, float y, float scale) {
        MintConfig cfg = MintConfig.INSTANCE;
        int[] resourceRgb = {
                cfg.ironColor.getRawArgb() & 0xFFFFFF,
                cfg.goldColor.getRawArgb() & 0xFFFFFF,
                cfg.diamondColor.getRawArgb() & 0xFFFFFF,
                cfg.emeraldColor.getRawArgb() & 0xFFFFFF,
        };

        int space = mc.textRenderer.getWidth(" ");
        int total = 0;
        for (int i = 0; i < t.parts.size(); i++) {
            WaypointLabel.Part part = t.parts.get(i);
            if (i > 0) total += space;
            total += mc.textRenderer.getWidth(part.count);
            if (cfg.waypointLetters) total += mc.textRenderer.getWidth(String.valueOf(part.letter));
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        GL11.glScalef(scale, scale, 1f);

        float cursor = -total / 2f;
        boolean shadow = cfg.waypointShadow;
        for (int i = 0; i < t.parts.size(); i++) {
            WaypointLabel.Part part = t.parts.get(i);
            if (i > 0) cursor += space;
            if (cfg.waypointLetters) {
                mc.textRenderer.draw(part.count, cursor, -9f, 0xFF000000 | t.countRgb, shadow);
                cursor += mc.textRenderer.getWidth(part.count);
                String letter = String.valueOf(part.letter);
                mc.textRenderer.draw(letter, cursor, -9f, 0xFF000000 | resourceRgb[part.resource], shadow);
                cursor += mc.textRenderer.getWidth(letter);
            } else {
                mc.textRenderer.draw(part.count, cursor, -9f, 0xFF000000 | resourceRgb[part.resource], shadow);
                cursor += mc.textRenderer.getWidth(part.count);
            }
        }

        GL11.glPopMatrix();
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
