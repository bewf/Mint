package me.bewf.mint.sync;

import java.util.Arrays;

/**
 * A change to the four tracked resources, in the order iron, gold, diamond, emerald.
 * Positive numbers are deposits into the team chest, negative numbers are withdrawals.
 */
public final class Delta {

    public static final int IRON = 0;
    public static final int GOLD = 1;
    public static final int DIAMOND = 2;
    public static final int EMERALD = 3;
    public static final int COUNT = 4;

    private final int[] values = new int[COUNT];

    public Delta() {
    }

    public Delta(int iron, int gold, int diamond, int emerald) {
        values[IRON] = iron;
        values[GOLD] = gold;
        values[DIAMOND] = diamond;
        values[EMERALD] = emerald;
    }

    public int get(int resource) {
        return values[resource];
    }

    public void add(int resource, int amount) {
        values[resource] += amount;
    }

    public void addAll(Delta other) {
        for (int i = 0; i < COUNT; i++) values[i] += other.values[i];
    }

    public boolean isZero() {
        for (int v : values) if (v != 0) return false;
        return true;
    }

    public Delta copy() {
        Delta d = new Delta();
        System.arraycopy(values, 0, d.values, 0, COUNT);
        return d;
    }

    public void clear() {
        Arrays.fill(values, 0);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Delta && Arrays.equals(values, ((Delta) o).values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Delta" + Arrays.toString(values);
    }
}
