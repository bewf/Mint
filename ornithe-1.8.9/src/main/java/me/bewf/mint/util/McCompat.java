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
