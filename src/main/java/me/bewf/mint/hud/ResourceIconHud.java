package me.bewf.mint.hud;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.util.ResourceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor;
import org.polyfrost.oneconfig.api.hud.v1.LegacyHud;

import java.util.ArrayList;
import java.util.List;

public class ResourceIconHud extends LegacyHud {

    private static final HudAnchor[] ANCHOR_OPTIONS = {
            HudAnchor.Auto, HudAnchor.Left, HudAnchor.Right, HudAnchor.Top, HudAnchor.Bottom
    };

    @Dropdown(
            title = "Anchor",
            description = "Pins this HUD to one side of the screen so it only grows away from that edge.",
            options = {"Auto", "Left", "Right", "Top", "Bottom"}
    )
    public int anchorSide = 0;

    public ResourceIconHud() {
        super("mint_resource_hud.json", "Resource Tracker", Hud.Category.getPLAYER());
    }

    @Override
    public void setup() {
        applyAnchorSide();
        addCallback("anchorSide", this::applyAnchorSide);
    }

    private void applyAnchorSide() {
        int idx = anchorSide;
        if (idx < 0 || idx >= ANCHOR_OPTIONS.length) idx = 0;
        setGrowthAnchorKeepingPosition(ANCHOR_OPTIONS[idx]);
    }

    @Override
    public boolean update() {
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics) {
        boolean example = isExamplePreview();
        boolean allow = ResourceTracker.isActive() || safeShowOutsideBedwars();
        if (!allow && !example) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        Font font = mc.font;

        int lineH = 18;
        int iconSize = 16;
        int iconPad = 2;
        int horizontalGap = 3;

        boolean horizontal = safeHorizontalLayout();

        List<Row> rows = buildRows(example);
        if (rows.isEmpty()) return;

        if (horizontal) {
            int cx = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int rowW = drawRow(graphics, font, cx, 0, iconSize, iconPad, row.icon, row.inv, row.ec, row.tc);
                cx += rowW;
                if (i != rows.size() - 1) cx += horizontalGap;
            }
        } else {
            int r = 0;
            for (Row row : rows) {
                drawRow(graphics, font, 0, r * lineH, iconSize, iconPad, row.icon, row.inv, row.ec, row.tc);
                r++;
            }
        }
    }

    private List<Row> buildRows(boolean example) {
        List<Row> rows = new ArrayList<>();

        boolean teamChest = safeTrackTeamChest();

        int ironInv = example ? 10 : ResourceTracker.ironInv;
        int goldInv = example ? 10 : ResourceTracker.goldInv;
        int diaInv = example ? 10 : ResourceTracker.diaInv;
        int emeInv = example ? 10 : ResourceTracker.emeInv;

        int ironEc = example ? 0 : ResourceTracker.ironEc;
        int goldEc = example ? 0 : ResourceTracker.goldEc;
        int diaEc = example ? 0 : ResourceTracker.diaEc;
        int emeEc = example ? 0 : ResourceTracker.emeEc;

        int ironTc = example ? 0 : (teamChest ? ResourceTracker.teamChestIron : 0);
        int goldTc = example ? 0 : (teamChest ? ResourceTracker.teamChestGold : 0);
        int diaTc = example ? 0 : (teamChest ? ResourceTracker.teamChestDiamond : 0);
        int emeTc = example ? 0 : (teamChest ? ResourceTracker.teamChestEmerald : 0);

        boolean hideZero = safeHideWhenZero();

        if (safeShowIron() && shouldShowRow(ironInv + ironEc + ironTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.IRON_INGOT), ironInv, ironEc, ironTc));
        }
        if (safeShowGold() && shouldShowRow(goldInv + goldEc + goldTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.GOLD_INGOT), goldInv, goldEc, goldTc));
        }
        if (safeShowDiamond() && shouldShowRow(diaInv + diaEc + diaTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.DIAMOND), diaInv, diaEc, diaTc));
        }
        if (safeShowEmerald() && shouldShowRow(emeInv + emeEc + emeTc, hideZero, example)) {
            rows.add(new Row(new ItemStack(Items.EMERALD), emeInv, emeEc, emeTc));
        }

        return rows;
    }

    private boolean shouldShowRow(int total, boolean hideWhenZero, boolean example) {
        if (example) return true;
        if (!hideWhenZero) return true;
        return total != 0;
    }

    private int drawRow(GuiGraphicsExtractor graphics, Font font,
                        int x, int y, int iconSize, int iconPad,
                        ItemStack icon, int inv, int ec, int tc) {

        int fontH = font.lineHeight;
        int rowH = 18;
        int iconY = y + (rowH - iconSize) / 2;

        graphics.item(icon, x, iconY);

        int textX = x + iconSize + iconPad;
        int textY = y + (rowH - fontH) / 2;

        int drawn;

        if (!safeStorageColors()) {
            String full = buildPlainText(inv, ec, tc);
            graphics.text(font, full, textX, textY, 0xFFFFFFFF, true);
            drawn = font.width(full);
        } else {
            drawn = drawColoredText(graphics, font, textX, textY, inv, ec, tc);
        }

        return iconSize + iconPad + drawn;
    }

    private int drawColoredText(GuiGraphicsExtractor graphics, Font font, int x, int y, int inv, int ec, int tc) {
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

        int cx = x;

        if (inv > 0 && ec <= 0 && tc <= 0) {
            graphics.text(font, invS, cx, y, invColor, true);
            return font.width(invS);
        }
        if (inv <= 0 && ec > 0 && tc <= 0) {
            graphics.text(font, ecS, cx, y, ecColor, true);
            return font.width(ecS);
        }
        if (inv <= 0 && ec <= 0 && tc > 0) {
            graphics.text(font, tcS, cx, y, tcColor, true);
            return font.width(tcS);
        }

        if (inv <= 0 && ec <= 0 && tc <= 0) {
            graphics.text(font, "0", cx, y, totalColor, true);
            return font.width("0");
        }

        boolean hasInv = inv > 0;
        boolean hasEc = ec > 0;
        boolean hasTc = tc > 0;

        if (hasInv) {
            graphics.text(font, invS, cx, y, invColor, true);
            cx += font.width(invS);
        }

        if (hasEc) {
            if (hasInv) {
                graphics.text(font, add, cx, y, sepColor, true);
                cx += font.width(add);
            }
            graphics.text(font, ecS, cx, y, ecColor, true);
            cx += font.width(ecS);
        }

        if (hasTc) {
            if (hasInv || hasEc) {
                graphics.text(font, add, cx, y, sepColor, true);
                cx += font.width(add);
            }
            graphics.text(font, tcS, cx, y, tcColor, true);
            cx += font.width(tcS);
        }

        graphics.text(font, eq, cx, y, sepColor, true);
        cx += font.width(eq);

        graphics.text(font, totalS, cx, y, totalColor, true);
        cx += font.width(totalS);

        return cx - x;
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

    public float getWidth() {
        Minecraft mc = Minecraft.getInstance();
        boolean example = isExamplePreview();

        if (mc == null || mc.font == null) return 90;

        int iconSize = 16;
        int iconPad = 2;
        int horizontalGap = 3;

        boolean horizontal = safeHorizontalLayout();
        List<Row> rows = buildRows(example);

        if (rows.isEmpty() && !example) return 0;
        if (rows.isEmpty()) rows = buildRows(true);

        if (horizontal) {
            int sum = 0;

            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                String text = buildPlainText(r.inv, r.ec, r.tc);

                sum += iconSize + iconPad + mc.font.width(text);

                if (i != rows.size() - 1) {
                    sum += horizontalGap;
                }
            }

            return sum;

        } else {
            int max = 0;

            for (Row r : rows) {
                String text = buildPlainText(r.inv, r.ec, r.tc);

                int w = iconSize + iconPad + mc.font.width(text);

                if (w > max) {
                    max = w;
                }
            }

            return max;
        }
    }

    public float getHeight() {
        boolean example = isExamplePreview();
        boolean horizontal = safeHorizontalLayout();
        int lineH = 18;

        List<Row> rows = buildRows(example);

        if (rows.isEmpty() && !example) return 0;

        if (horizontal) {
            return lineH;
        } else {
            return lineH * (rows.isEmpty() ? 1 : rows.size());
        }
    }

    private boolean isExamplePreview() {
        return false;
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
        return cfg == null ? 0xFFE8D9C2 : cfg.inventoryColor;
    }

    private int safeEnderChestColor() {
        MintConfig cfg = safeConfig();
        return cfg == null ? 0xFFBE3FFF : cfg.enderChestColor;
    }

    private int safeTeamChestColor() {
        MintConfig cfg = safeConfig();
        return cfg == null ? 0xFF55AAFF : cfg.teamChestColor;
    }

    private int safeTotalColor() {
        MintConfig cfg = safeConfig();
        return cfg == null ? 0xFFFFFFFF : cfg.totalColor;
    }

    private int safeSeparatorColor() {
        MintConfig cfg = safeConfig();
        return cfg == null ? 0xFFAAAAAA : cfg.separatorColor;
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