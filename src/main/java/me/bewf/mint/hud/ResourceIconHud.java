package me.bewf.mint.hud;

import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import me.bewf.mint.config.MintConfig;
import me.bewf.mint.tracker.ResourceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ResourceIconHud extends BasicHud {

    public ResourceIconHud() {
        super(true);
    }

    @Override
    public void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
        boolean allow = ResourceTracker.isActive() || safeShowOutsideBedwars();
        if (!allow && !example) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;

        FontRenderer fr = mc.fontRendererObj;
        RenderItem ri = mc.getRenderItem();

        float iconScale = scale * 0.90f;

        int lineH = Math.round(18f * scale);
        int iconSize = Math.round(16f * iconScale);

        int iconPad = Math.round(2f * scale);
        int horizontalGap = Math.round(3f * scale);

        boolean horizontal = safeHorizontalLayout();

        List<Row> rows = buildRows(example);
        if (rows.isEmpty()) return;

        if (horizontal) {
            float cx = x;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                float rowW = drawRow(fr, ri, cx, y, scale, iconScale, iconSize, iconPad, row.icon, row.inv, row.ec);
                cx += rowW;
                if (i != rows.size() - 1) cx += horizontalGap;
            }
        } else {
            int r = 0;
            for (Row row : rows) {
                drawRow(fr, ri, x, y + r * lineH, scale, iconScale, iconSize, iconPad, row.icon, row.inv, row.ec);
                r++;
            }
        }
    }

    private List<Row> buildRows(boolean example) {
        List<Row> rows = new ArrayList<Row>();

        int ironInv = example ? 10 : ResourceTracker.ironInv;
        int goldInv = example ? 10 : ResourceTracker.goldInv;
        int diaInv = example ? 10 : ResourceTracker.diaInv;
        int emeInv = example ? 10 : ResourceTracker.emeInv;

        int ironEc = example ? 0 : ResourceTracker.ironEc;
        int goldEc = example ? 0 : ResourceTracker.goldEc;
        int diaEc = example ? 0 : ResourceTracker.diaEc;
        int emeEc = example ? 0 : ResourceTracker.emeEc;

        boolean hideZero = safeHideWhenZero();

        if (safeShowIron() && shouldShowRow(ironInv + ironEc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.iron_ingot), ironInv, ironEc));
        }
        if (safeShowGold() && shouldShowRow(goldInv + goldEc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.gold_ingot), goldInv, goldEc));
        }
        if (safeShowDiamond() && shouldShowRow(diaInv + diaEc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.diamond), diaInv, diaEc));
        }
        if (safeShowEmerald() && shouldShowRow(emeInv + emeEc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.emerald), emeInv, emeEc));
        }

        return rows;
    }

    private boolean shouldShowRow(int total, boolean hideWhenZero, boolean example) {
        if (example) return true;
        if (!hideWhenZero) return true;
        return total != 0;
    }

    private float drawRow(FontRenderer fr, RenderItem ri,
                          float x, float y, float textScale, float iconScale, int iconSize, int iconPad,
                          ItemStack icon, int inv, int ec) {

        int fontH = fr.FONT_HEIGHT;

        float rowH = 18f * textScale;
        float iconY = y + (rowH - (16f * iconScale)) / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, iconY, 0f);
        GlStateManager.scale(iconScale, iconScale, 1f);

        RenderHelper.enableGUIStandardItemLighting();
        ri.renderItemAndEffectIntoGUI(icon, 0, 0);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.popMatrix();

        float textX = x + iconSize + iconPad;
        float textY = y + (rowH - (fontH * textScale)) / 2f;

        if (!safeStorageColors()) {
            // Colors off: always show explicit format so storage is not ambiguous
            String full = buildPlainText(inv, ec);
            fr.drawStringWithShadow(full, textX, textY, 0xFFFFFF);
            return (iconSize + iconPad + fr.getStringWidth(full));
        }

        // Colors on: hide 0-side and color segments
        float drawn = drawColoredText(fr, textX, textY, inv, ec);
        return (iconSize + iconPad + drawn);
    }

    private float drawColoredText(FontRenderer fr, float x, float y, int inv, int ec) {
        int invColor = safeInventoryColor();
        int ecColor = safeEnderChestColor();
        int totalColor = safeTotalColor();

        String invS = formatCount(inv);
        String ecS = formatCount(ec);
        String totalS = formatCount(inv + ec);

        float cx = x;

        if (inv > 0 && ec <= 0) {
            fr.drawStringWithShadow(invS, cx, y, invColor);
            return fr.getStringWidth(invS);
        }

        if (inv <= 0 && ec > 0) {
            fr.drawStringWithShadow(ecS, cx, y, ecColor);
            return fr.getStringWidth(ecS);
        }

        if (inv <= 0 && ec <= 0) {
            fr.drawStringWithShadow("0", cx, y, totalColor);
            return fr.getStringWidth("0");
        }

        // both > 0: inv(beige)+ec(purple): total(white)
        fr.drawStringWithShadow(invS, cx, y, invColor);
        cx += fr.getStringWidth(invS);

        fr.drawStringWithShadow("+", cx, y, 0xFFFFFF);
        cx += fr.getStringWidth("+");

        fr.drawStringWithShadow(ecS, cx, y, ecColor);
        cx += fr.getStringWidth(ecS);

        fr.drawStringWithShadow(": ", cx, y, 0xFFFFFF);
        cx += fr.getStringWidth(": ");

        fr.drawStringWithShadow(totalS, cx, y, totalColor);
        cx += fr.getStringWidth(totalS);

        return (cx - x);
    }

    private String buildPlainText(int inv, int ec) {
        // Colors off: always show inv+ec: total so storage is clear
        String invS = formatCount(inv);
        String ecS = formatCount(ec);
        String totalS = formatCount(inv + ec);
        return invS + "+" + ecS + ": " + totalS;
    }

    private String buildDisplayTextForWidth(int inv, int ec) {
        // Must match draw behavior so the HUD background sizes correctly
        if (!safeStorageColors()) {
            return buildPlainText(inv, ec);
        }

        if (inv > 0 && ec <= 0) return formatCount(inv);
        if (inv <= 0 && ec > 0) return formatCount(ec);
        if (inv <= 0 && ec <= 0) return "0";

        return formatCount(inv) + "+" + formatCount(ec) + ": " + formatCount(inv + ec);
    }

    private String formatCount(int n) {
        if (!safeCompactNumbers()) return String.valueOf(n);
        if (n < 1000) return String.valueOf(n);

        double k = n / 1000.0;

        if (k < 10.0) {
            double oneDec = Math.round(k * 10.0) / 10.0;
            String s = String.valueOf(oneDec);
            if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
            return s + "k";
        }

        long rounded = Math.round(k);
        return rounded + "k";
    }

    @Override
    public float getWidth(float scale, boolean example) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return 90f * scale;

        int iconSize = Math.round(16f * (scale * 0.90f));
        int iconPad = Math.round(2f * scale);
        int horizontalGap = Math.round(3f * scale);

        boolean horizontal = safeHorizontalLayout();

        List<Row> rows = buildRows(example);

        if (rows.isEmpty() && !example) return 0f;
        if (rows.isEmpty()) rows = buildRows(true);

        if (horizontal) {
            int sum = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                String text = buildDisplayTextForWidth(r.inv, r.ec);
                sum += iconSize + iconPad + mc.fontRendererObj.getStringWidth(text);
                if (i != rows.size() - 1) sum += horizontalGap;
            }
            return sum;
        } else {
            int max = 0;
            for (Row r : rows) {
                String text = buildDisplayTextForWidth(r.inv, r.ec);
                int w = iconSize + iconPad + mc.fontRendererObj.getStringWidth(text);
                if (w > max) max = w;
            }
            return max;
        }
    }

    @Override
    public float getHeight(float scale, boolean example) {
        boolean horizontal = safeHorizontalLayout();
        int lineH = Math.round(18f * scale);

        List<Row> rows = buildRows(example);

        if (rows.isEmpty() && !example) return 0f;

        if (horizontal) {
            return lineH;
        } else {
            int lines = rows.isEmpty() ? 1 : rows.size();
            return lineH * lines;
        }
    }

    private boolean safeShowOutsideBedwars() {
        MintConfig cfg = safeConfig();
        return cfg != null && cfg.showOutsideBedwars;
    }

    private boolean safeShowIron() {
        MintConfig cfg = safeConfig();
        return cfg == null || cfg.showIron;
    }

    private boolean safeShowGold() {
        MintConfig cfg = safeConfig();
        return cfg == null || cfg.showGold;
    }

    private boolean safeShowDiamond() {
        MintConfig cfg = safeConfig();
        return cfg == null || cfg.showDiamond;
    }

    private boolean safeShowEmerald() {
        MintConfig cfg = safeConfig();
        return cfg == null || cfg.showEmerald;
    }

    private boolean safeHorizontalLayout() {
        MintConfig cfg = safeConfig();
        return cfg != null && cfg.horizontalLayout;
    }

    private boolean safeCompactNumbers() {
        MintConfig cfg = safeConfig();
        return cfg == null || cfg.compactNumbers;
    }

    private boolean safeHideWhenZero() {
        MintConfig cfg = safeConfig();
        return cfg != null && cfg.hideWhenZero;
    }

    private boolean safeStorageColors() {
        MintConfig cfg = safeConfig();
        return cfg == null || cfg.storageColors;
    }

    private int safeInventoryColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.inventoryColor == null) return 0xE8D9C2;
        try {
            return cfg.inventoryColor.getRGB();
        } catch (Throwable t) {
            return 0xE8D9C2;
        }
    }

    private int safeEnderChestColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.enderChestColor == null) return 0xBE3FFF;
        try {
            return cfg.enderChestColor.getRGB();
        } catch (Throwable t) {
            return 0xBE3FFF;
        }
    }

    private int safeTotalColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.totalColor == null) return 0xFFFFFF;
        try {
            return cfg.totalColor.getRGB();
        } catch (Throwable t) {
            return 0xFFFFFF;
        }
    }

    private MintConfig safeConfig() {
        try {
            return MintConfig.INSTANCE;
        } catch (Throwable t) {
            return null;
        }
    }

    private static class Row {
        final ItemStack icon;
        final int inv;
        final int ec;

        Row(ItemStack icon, int inv, int ec) {
            this.icon = icon;
            this.inv = inv;
            this.ec = ec;
        }
    }
}
