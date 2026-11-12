package me.bewf.mint.render;

import me.bewf.mint.util.ReflectUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * What the waypoint labels need to know about the view to stay on their chest: where the camera really
 * is and which way it faces (third person puts it away from the player's eyes), the sway of walking
 * (view bobbing) and the wider view while sprinting. These are read from the game, by type where a name
 * may change between versions. Anything that cannot be read is left out, which gives the plain position.
 */
final class ViewInfo {

    private ViewInfo() {
    }

    // What the last frame managed to read, for the waypoint debug line
    static String fovNote = "not read yet";
    static String cameraNote = "not read yet";
    static String bobNote = "not read yet";

    /**
     * The sway of walking as {moveRight, moveUp, rollDegrees, pitchDegrees}, the way the game applies it
     * to the view, or null when view bobbing is off or cannot be read.
     */
    static double[] bob(Minecraft mc, float partial) {
        try {
            if (mc.player == null) return null;
            if (!bobbingOn(mc)) {
                bobNote = "off in options";
                return null;
            }
            float walk = number(mc.player, "walkDist");
            float walkOld = number(mc.player, "walkDistO");
            float bob = number(mc.player, "bob");
            float bobOld = number(mc.player, "oBob");
            if (Float.isNaN(walk) || Float.isNaN(walkOld) || Float.isNaN(bob) || Float.isNaN(bobOld)) {
                bobNote = "fields not found";
                return null;
            }

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
            if (Math.abs(out[0]) > 1.0 || Math.abs(out[1]) > 1.0 || Math.abs(out[2]) > 20.0 || Math.abs(out[3]) > 30.0) {
                bobNote = "values out of range";
                return null;
            }
            bobNote = String.format("ok, strength %.2f", strength);
            return out;
        } catch (Throwable t) {
            bobNote = "error " + t;
            return null;
        }
    }

    private static boolean bobbingOn(Minecraft mc) {
        Object option = ReflectUtil.tryGetNoArg(mc.options, "bobView");
        Object value = ReflectUtil.tryGetNoArg(option, "get");
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static Field rendererField = null;
    private static boolean rendererSearched = false;
    private static Method cameraGetter = null;
    private static boolean cameraGetterSearched = false;

    private static Object gameRenderer(Minecraft mc) throws Exception {
        if (!rendererSearched) {
            rendererSearched = true;
            for (Class<?> k = mc.getClass(); k != null && rendererField == null; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getName().equals("gameRenderer") || f.getType().getName().endsWith(".GameRenderer")) {
                        f.setAccessible(true);
                        rendererField = f;
                        break;
                    }
                }
            }
        }
        return rendererField == null ? null : rendererField.get(mc);
    }

    private static Object mainCamera(Object renderer) throws Exception {
        if (!cameraGetterSearched) {
            cameraGetterSearched = true;
            for (Method m : renderer.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                if (m.getName().equals("getMainCamera") || m.getReturnType().getName().endsWith(".Camera")) {
                    m.setAccessible(true);
                    cameraGetter = m;
                    if (m.getName().equals("getMainCamera")) break;
                }
            }
        }
        return cameraGetter == null ? null : cameraGetter.invoke(renderer);
    }

    private static Method positionGetter = null;
    private static Method yawGetter = null;
    private static Method pitchGetter = null;
    private static boolean cameraMembersSearched = false;

    /**
     * Where the game's camera is this frame as {x, y, z, yaw, pitch}, or null when that cannot be read.
     * In first person this is the player's eyes. In third person it is the spot behind or in front of
     * the player, looking the way the game turns it.
     */
    static double[] camera(Minecraft mc) {
        try {
            Object renderer = gameRenderer(mc);
            if (renderer == null) {
                cameraNote = "game renderer not found";
                return null;
            }
            Object camera = mainCamera(renderer);
            if (camera == null) {
                cameraNote = "camera not found";
                return null;
            }

            if (!cameraMembersSearched) {
                cameraMembersSearched = true;
                for (Method m : camera.getClass().getMethods()) {
                    if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                    String n = m.getName();
                    if (m.getReturnType() == Vec3.class && (n.equals("position") || n.equals("getPosition"))) positionGetter = m;
                    if (m.getReturnType() == float.class && (n.equals("yRot") || n.equals("getYRot"))) yawGetter = m;
                    if (m.getReturnType() == float.class && (n.equals("xRot") || n.equals("getXRot"))) pitchGetter = m;
                }
            }

            double x, y, z;
            if (positionGetter != null) {
                Vec3 at = (Vec3) positionGetter.invoke(camera);
                x = at.x;
                y = at.y;
                z = at.z;
            } else {
                Object at = field(camera, "position");
                if (!(at instanceof Vec3)) {
                    cameraNote = "position not found";
                    return null;
                }
                x = ((Vec3) at).x;
                y = ((Vec3) at).y;
                z = ((Vec3) at).z;
            }
            float yaw = yawGetter != null ? ((Number) yawGetter.invoke(camera)).floatValue() : number(camera, "yRot");
            float pitch = pitchGetter != null ? ((Number) pitchGetter.invoke(camera)).floatValue() : number(camera, "xRot");
            if (Float.isNaN(yaw) || Float.isNaN(pitch)) {
                cameraNote = "rotation not found";
                return null;
            }
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                cameraNote = "position invalid";
                return null;
            }
            cameraNote = "ok";
            return new double[]{x, y, z, yaw, pitch};
        } catch (Throwable t) {
            cameraNote = "error " + t;
            return null;
        }
    }

    private static boolean fovTried = false;
    private static Method fovMethod = null;

    /** The vertical field of view the game is using this frame, or the fallback when that cannot be read. */
    static double fov(Minecraft mc, float partial, double fallback) {
        try {
            Object renderer = gameRenderer(mc);
            Object camera = renderer == null ? null : mainCamera(renderer);
            if (camera == null) {
                fovNote = "options value, no camera";
                return fallback;
            }

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
            if (fovMethod == null) {
                fovNote = "options value, getFov not found";
                return fallback;
            }

            double fov = ((Number) fovMethod.invoke(renderer, camera, Float.valueOf(partial), Boolean.TRUE)).doubleValue();
            if (fov >= 10.0 && fov <= 170.0) {
                fovNote = "from the game";
                return fov;
            }
            fovNote = "options value, game gave " + fov;
            return fallback;
        } catch (Throwable t) {
            fovMethod = null;
            fovNote = "options value, error " + t;
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
