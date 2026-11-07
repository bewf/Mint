package me.bewf.mint.render;

import me.bewf.mint.util.ReflectUtil;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Two things that make the world move on screen without the camera moving: the sway of walking
 * (view bobbing) and the wider view while sprinting. The waypoint labels need both to stay on their
 * chest. They are read by name from the game, and anything that cannot be read is simply left out,
 * which gives the plain label position.
 */
final class ViewInfo {

    private ViewInfo() {
    }

    /**
     * The sway of walking as {moveRight, moveUp, rollDegrees, pitchDegrees}, the way the game applies it
     * to the view, or null when view bobbing is off or cannot be read.
     */
    static double[] bob(Minecraft mc, float partial) {
        try {
            if (mc.player == null || !bobbingOn(mc)) return null;
            float walk = number(mc.player, "walkDist");
            float walkOld = number(mc.player, "walkDistO");
            float bob = number(mc.player, "bob");
            float bobOld = number(mc.player, "oBob");
            if (Float.isNaN(walk) || Float.isNaN(walkOld) || Float.isNaN(bob) || Float.isNaN(bobOld)) return null;

            double phase = -(walk + (walk - walkOld) * partial) * Math.PI;
            double strength = bobOld + (bob - bobOld) * partial;
            double sin = Math.sin(phase);
            double cos = Math.cos(phase);
            double[] out = {
                    sin * strength * 0.5,
                    -Math.abs(cos * strength),
                    sin * strength * 3.0,
                    Math.abs(Math.cos(phase - 0.2) * strength) * 5.0,
            };
            // Walking never moves the view by more than a fraction of a block or a few degrees
            if (Math.abs(out[0]) > 1.0 || Math.abs(out[1]) > 1.0 || Math.abs(out[2]) > 20.0 || Math.abs(out[3]) > 30.0) return null;
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean bobbingOn(Minecraft mc) {
        Object option = ReflectUtil.tryGetNoArg(mc.options, "bobView");
        Object value = ReflectUtil.tryGetNoArg(option, "get");
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static boolean fovTried = false;
    private static Method fovMethod = null;

    /** The vertical field of view the game is using this frame, or the fallback when that cannot be read. */
    static double fov(Minecraft mc, float partial, double fallback) {
        try {
            Object renderer = field(mc, "gameRenderer");
            if (renderer == null) return fallback;
            Object camera = ReflectUtil.tryGetNoArg(renderer, "getMainCamera");
            if (camera == null) return fallback;

            if (!fovTried) {
                fovTried = true;
                for (Method m : renderer.getClass().getDeclaredMethods()) {
                    if (!m.getName().equals("getFov") || m.getParameterCount() != 3) continue;
                    Class<?>[] types = m.getParameterTypes();
                    if (types[0].isInstance(camera) && types[1] == float.class && types[2] == boolean.class) {
                        m.setAccessible(true);
                        fovMethod = m;
                        break;
                    }
                }
            }
            if (fovMethod == null) return fallback;

            double fov = ((Number) fovMethod.invoke(renderer, camera, Float.valueOf(partial), Boolean.TRUE)).doubleValue();
            return fov >= 10.0 && fov <= 170.0 ? fov : fallback;
        } catch (Throwable t) {
            fovMethod = null;
            return fallback;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /** A number field of the object or one of its parents, or NaN when there is none. */
    private static float number(Object target, String name) {
        try {
            Object value = field(target, name);
            return value instanceof Number ? ((Number) value).floatValue() : Float.NaN;
        } catch (Throwable t) {
            return Float.NaN;
        }
    }
}
