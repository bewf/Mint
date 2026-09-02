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
