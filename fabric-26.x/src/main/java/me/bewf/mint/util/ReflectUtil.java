package me.bewf.mint.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectUtil {

    private ReflectUtil() {
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
        Class<?> c = minecraftInstance.getClass();

        // Field named "screen" on the class or any superclass, public or not
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField("screen");
                f.setAccessible(true);
                return f.get(minecraftInstance);
            } catch (Throwable ignored) {
            }
        }

        // 26.2+ moved the current screen onto Minecraft#gui (Gui#screen())
        Object gui = null;
        for (Class<?> k = c; k != null && gui == null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField("gui");
                f.setAccessible(true);
                gui = f.get(minecraftInstance);
            } catch (Throwable ignored) {
            }
        }
        if (gui != null) {
            Object viaMethod = tryGetNoArg(gui, "screen");
            if (viaMethod != null) return viaMethod;
            for (Class<?> k = gui.getClass(); k != null; k = k.getSuperclass()) {
                try {
                    Field f = k.getDeclaredField("screen");
                    f.setAccessible(true);
                    return f.get(gui);
                } catch (Throwable ignored) {
                }
            }
        }

        // Any field whose type is a Screen
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!isScreenType(f.getType()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    return f.get(minecraftInstance);
                } catch (Throwable ignored) {
                }
            }
        }

        // Zero-arg getter returning a Screen
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || !isScreenType(m.getReturnType())) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(minecraftInstance);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isScreenType(Class<?> t) {
        return t != null && t.getName().equals("net.minecraft.client.gui.screens.Screen");
    }
}
