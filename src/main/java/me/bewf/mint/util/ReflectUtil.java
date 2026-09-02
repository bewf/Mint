package me.bewf.mint.util;

import org.polyfrost.polyui.color.PolyColor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectUtil {

    private ReflectUtil() {
    }

    private static final String[] COLOR_UTIL_CLASSES = {
            "org.polyfrost.oneconfig.utils.v1.ColorUtils",
            "org.polyfrost.oneconfig.utils.v1.color.ColorUtils",
            "org.polyfrost.oneconfig.api.config.v1.utils.ColorUtils",
            "org.polyfrost.polyui.color.ColorUtils",
            "org.polyfrost.polyui.utils.ColorUtils"
    };

    public static PolyColor makeColor(int packedRgba) {
        for (String className : COLOR_UTIL_CLASSES) {
            try {
                Class<?> cls = Class.forName(className);
                for (Method m : cls.getMethods()) {
                    if (!m.getName().equals("rgba")) continue;
                    if (m.getParameterCount() != 1) continue;
                    if (!PolyColor.class.isAssignableFrom(m.getReturnType())) continue;
                    m.setAccessible(true);
                    Object result = m.invoke(null, packedRgba);
                    if (result instanceof PolyColor) return (PolyColor) result;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static int colorToArgb(PolyColor color, int fallbackArgb) {
        if (color == null) return fallbackArgb;

        String[] rgbaNames = {"getRGBA", "toRGBA", "rgba"};
        for (String name : rgbaNames) {
            Integer raw = callIntGetter(color, name);
            if (raw != null) {
                int r = (raw >>> 24) & 0xFF;
                int g = (raw >>> 16) & 0xFF;
                int b = (raw >>> 8) & 0xFF;
                int a = raw & 0xFF;
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        String[] argbNames = {"getARGB", "toARGB", "argb"};
        for (String name : argbNames) {
            Integer raw = callIntGetter(color, name);
            if (raw != null) return raw;
        }

        return fallbackArgb;
    }

    private static Integer callIntGetter(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            Object result = m.invoke(target);
            if (result instanceof Integer) return (Integer) result;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean tryCall(Object target, String methodName, Object... args) {
        if (target == null) return false;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            if (m.getParameterCount() != args.length) continue;
            try {
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static Object tryGetNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object getScreen(Object minecraftInstance) {
        if (minecraftInstance == null) return null;
        try {
            Field f = minecraftInstance.getClass().getField("screen");
            return f.get(minecraftInstance);
        } catch (Throwable ignored) {
        }
        for (Method m : minecraftInstance.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String lower = m.getName().toLowerCase();
            if (!lower.contains("screen")) continue;
            if (!m.getReturnType().getSimpleName().toLowerCase().contains("screen")) continue;
            try {
                m.setAccessible(true);
                return m.invoke(minecraftInstance);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
