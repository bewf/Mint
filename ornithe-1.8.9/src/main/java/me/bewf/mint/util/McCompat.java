package me.bewf.mint.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;

final class McCompat {
    private McCompat() {
    }

    private static Method blockStateMethod;
    private static Method inventoryNameMethod;

    static Block blockAt(World world, BlockPos pos) {
        try {
            if (blockStateMethod == null) {
                for (Method m : World.class.getMethods()) {
                    if (m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == BlockPos.class
                            && m.getReturnType() == BlockState.class) {
                        blockStateMethod = m;
                        break;
                    }
                }
                if (blockStateMethod == null) return null;
            }
            BlockState state = (BlockState) blockStateMethod.invoke(world, pos);
            return state == null ? null : state.getBlock();
        } catch (Throwable t) {
            return null;
        }
    }

    /** {x, y, z, yaw} of an entity, or null when the fields cannot be read. */
    static double[] playerPos(Object player) {
        try {
            double x = number(player, "x", "getX");
            double y = number(player, "y", "getY");
            double z = number(player, "z", "getZ");
            double yaw = number(player, "yaw", "getYaw");
            return new double[]{x, y, z, yaw};
        } catch (Throwable t) {
            return null;
        }
    }

    private static double number(Object target, String field, String getter) throws Exception {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), field);
            if (f != null) {
                f.setAccessible(true);
                return ((Number) f.get(target)).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        Method m = target.getClass().getMethod(getter);
        return ((Number) m.invoke(target)).doubleValue();
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Sends a chat line or command as the player. */
    static boolean sendChat(Object player, String message) {
        for (String name : new String[]{"sendChat", "sendChatMessage"}) {
            try {
                Method m = player.getClass().getMethod(name, String.class);
                m.invoke(player, message);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    static String playerName(Object player) {
        try {
            return String.valueOf(player.getClass().getMethod("getName").invoke(player));
        } catch (Throwable t) {
            return "";
        }
    }

    /** The scoreboard team of a player name, or null when unknown. */
    static Object teamOf(Object world, String playerName) {
        try {
            Object scoreboard = world.getClass().getMethod("getScoreboard").invoke(world);
            for (Method m : scoreboard.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && (m.getName().equals("getTeamOfMember") || m.getName().equals("getPlayerTeam") || m.getName().equals("getPlayersTeam"))) {
                    return m.invoke(scoreboard, playerName);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static String inventoryName(Inventory inventory) {
        try {
            if (inventoryNameMethod == null) {
                for (Method m : Inventory.class.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                        inventoryNameMethod = m;
                        break;
                    }
                }
                if (inventoryNameMethod == null) return "";
            }
            Object name = inventoryNameMethod.invoke(inventory);
            return name == null ? "" : name.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
