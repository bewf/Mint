package me.bewf.mint.hud;

import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import me.bewf.mint.config.MintConfig;
import me.bewf.mint.util.ResourceTracker;
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
                float rowW = drawRow(fr, ri, cx, y, scale, iconScale, iconSize, iconPad, row.icon, row.inv, row.ec, row.tc);
                cx += rowW;
                if (i != rows.size() - 1) cx += horizontalGap;
            }
        } else {
            int r = 0;
            for (Row row : rows) {
                drawRow(fr, ri, x, y + r * lineH, scale, iconScale, iconSize, iconPad, row.icon, row.inv, row.ec, row.tc);
                r++;
            }
        }
    }

    private List<Row> buildRows(boolean example) {
        List<Row> rows = new ArrayList<Row>();

        boolean teamChest = safeTrackTeamChest();

        // inventory counts
        int ironInv = example ? 10 : ResourceTracker.ironInv;
        int goldInv = example ? 10 : ResourceTracker.goldInv;
        int diaInv  = example ? 10 : ResourceTracker.diaInv;
        int emeInv  = example ? 10 : ResourceTracker.emeInv;

        // ender chest counts
        int ironEc = example ? 0 : ResourceTracker.ironEc;
        int goldEc = example ? 0 : ResourceTracker.goldEc;
        int diaEc  = example ? 0 : ResourceTracker.diaEc;
        int emeEc  = example ? 0 : ResourceTracker.emeEc;

        // team chest counts
        int ironTc = example ? 0 : (teamChest ? ResourceTracker.teamChestIron : 0);
        int goldTc = example ? 0 : (teamChest ? ResourceTracker.teamChestGold : 0);
        int diaTc  = example ? 0 : (teamChest ? ResourceTracker.teamChestDiamond : 0);
        int emeTc  = example ? 0 : (teamChest ? ResourceTracker.teamChestEmerald : 0);

        boolean hideZero = safeHideWhenZero();

        if (safeShowIron() && shouldShowRow(ironInv + ironEc + ironTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.iron_ingot), ironInv, ironEc, ironTc));
        }
        if (safeShowGold() && shouldShowRow(goldInv + goldEc + goldTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.gold_ingot), goldInv, goldEc, goldTc));
        }
        if (safeShowDiamond() && shouldShowRow(diaInv + diaEc + diaTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.diamond), diaInv, diaEc, diaTc));
        }
        if (safeShowEmerald() && shouldShowRow(emeInv + emeEc + emeTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.emerald), emeInv, emeEc, emeTc));
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
                          ItemStack icon, int inv, int ec, int tc) {

        int fontH = fr.FONT_HEIGHT;

        float rowH = 18f * textScale;
        float iconY = y + (rowH - (16f * iconScale)) / 2f;

        // draw icon
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, iconY, 0f);
        GlStateManager.scale(iconScale, iconScale, 1f);

        RenderHelper.enableGUIStandardItemLighting();
        ri.renderItemAndEffectIntoGUI(icon, 0, 0);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.popMatrix();

        float textX = x + iconSize + iconPad;
        float textY = y + (rowH - (fontH * textScale)) / 2f;

        // draw text
        GlStateManager.pushMatrix();
        GlStateManager.translate(textX, textY, 0f);
        GlStateManager.scale(textScale, textScale, 1f);

        float drawn;

        if (!safeStorageColors()) {
            String full = buildPlainText(inv, ec, tc);
            fr.drawStringWithShadow(full, 0, 0, 0xFFFFFF);
            drawn = fr.getStringWidth(full) * textScale;
        } else {
            drawn = drawColoredText(fr, 0, 0, inv, ec, tc, textScale);
        }

        GlStateManager.popMatrix();

        return (iconSize + iconPad + drawn);
    }

    private float drawColoredText(FontRenderer fr, float x, float y, int inv, int ec, int tc, float textScale) {
        int invColor = safeInventoryColor();
        int ecColor = safeEnderChestColor();
        int tcColor = safeTeamChestColor();
        int totalColor = safeTotalColor();
        int sepColor = safeSeparatorColor();

        String add = safeAdditionLabel();
        String eq = safeEqualLabel();

        String invS = formatCount(inv);
        String ecS = formatCount(ec);
        String tcS = formatCount(tc);
        String totalS = formatCount(inv + ec + tc);

        float cx = x;

        // If only one has value, show it directly
        if (inv > 0 && ec <= 0 && tc <= 0) {
            fr.drawStringWithShadow(invS, cx, y, invColor);
            return fr.getStringWidth(invS) * textScale;
        }
        if (inv <= 0 && ec > 0 && tc <= 0) {
            fr.drawStringWithShadow(ecS, cx, y, ecColor);
            return fr.getStringWidth(ecS) * textScale;
        }
        if (inv <= 0 && ec <= 0 && tc > 0) {
            fr.drawStringWithShadow(tcS, cx, y, tcColor);
            return fr.getStringWidth(tcS) * textScale;
        }

        // If all zero
        if (inv <= 0 && ec <= 0 && tc <= 0) {
            fr.drawStringWithShadow("0", cx, y, totalColor);
            return fr.getStringWidth("0") * textScale;
        }

        // Build the expression
        boolean hasInv = inv > 0;
        boolean hasEc = ec > 0;
        boolean hasTc = tc > 0;

        if (hasInv) {
            fr.drawStringWithShadow(invS, cx, y, invColor);
            cx += fr.getStringWidth(invS);
        }

        if (hasEc) {
            if (hasInv) {
                fr.drawStringWithShadow(add, cx, y, sepColor);
                cx += fr.getStringWidth(add);
            }
            fr.drawStringWithShadow(ecS, cx, y, ecColor);
            cx += fr.getStringWidth(ecS);
        }

        if (hasTc) {
            if (hasInv || hasEc) {
                fr.drawStringWithShadow(add, cx, y, sepColor);
                cx += fr.getStringWidth(add);
            }
            fr.drawStringWithShadow(tcS, cx, y, tcColor);
            cx += fr.getStringWidth(tcS);
        }

        fr.drawStringWithShadow(eq, cx, y, sepColor);
        cx += fr.getStringWidth(eq);

        fr.drawStringWithShadow(totalS, cx, y, totalColor);
        cx += fr.getStringWidth(totalS);

        return (cx - x) * textScale;
    }

    private String buildPlainText(int inv, int ec, int tc) {
        String invS = formatCount(inv);
        String ecS = formatCount(ec);
        String tcS = formatCount(tc);
        String totalS = formatCount(inv + ec + tc);

        String add = safeAdditionLabel();
        String eq = safeEqualLabel();

        StringBuilder sb = new StringBuilder();
        boolean hasInv = inv > 0;
        boolean hasEc = ec > 0;
        boolean hasTc = tc > 0;

        if (hasInv) sb.append(invS);
        if (hasEc) {
            if (hasInv) sb.append(add);
            sb.append(ecS);
        }
        if (hasTc) {
            if (hasInv || hasEc) sb.append(add);
            sb.append(tcS);
        }
        sb.append(eq).append(totalS);

        return sb.toString();
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
                String text = buildPlainText(r.inv, r.ec, r.tc);

                sum += iconSize + iconPad + mc.fontRendererObj.getStringWidth(text);

                if (i != rows.size() - 1) {
                    sum += horizontalGap;
                }
            }

            return sum;

        } else {
            int max = 0;

            for (Row r : rows) {
                String text = buildPlainText(r.inv, r.ec, r.tc);

                int w = iconSize + iconPad + mc.fontRendererObj.getStringWidth(text);

                if (w > max) {
                    max = w;
                }
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
            return lineH * (rows.isEmpty() ? 1 : rows.size());
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

    private boolean safeTrackTeamChest() {
        MintConfig cfg = safeConfig();
        return cfg != null && cfg.trackTeamChest;
    }

    private int safeInventoryColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.inventoryColor == null) return 0xE8D9C2;
        try { return cfg.inventoryColor.getRGB(); } catch (Throwable t) { return 0xE8D9C2; }
    }

    private int safeEnderChestColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.enderChestColor == null) return 0xBE3FFF;
        try { return cfg.enderChestColor.getRGB(); } catch (Throwable t) { return 0xBE3FFF; }
    }

    private int safeTeamChestColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.teamChestColor == null) return 0x55AAFF;
        try { return cfg.teamChestColor.getRGB(); } catch (Throwable t) { return 0x55AAFF; }
    }

    private int safeTotalColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.totalColor == null) return 0xFFFFFF;
        try { return cfg.totalColor.getRGB(); } catch (Throwable t) { return 0xFFFFFF; }
    }

    private int safeSeparatorColor() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.separatorColor == null) return 0xAAAAAA;
        try { return cfg.separatorColor.getRGB(); } catch (Throwable t) { return 0xAAAAAA; }
    }

    private String safeAdditionLabel() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.additionLabel == null) return "+";
        return cfg.additionLabel;
    }

    private String safeEqualLabel() {
        MintConfig cfg = safeConfig();
        if (cfg == null || cfg.equalLabel == null) return ":";
        return cfg.equalLabel;
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
        final int tc;

        Row(ItemStack icon, int inv, int ec, int tc) {
            this.icon = icon;
            this.inv = inv;
            this.ec = ec;
            this.tc = tc;
        }
    }
}
