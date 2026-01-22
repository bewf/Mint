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
        int iconPad = Math.round(5f * scale);

        int row = 0;

        if (safeShowIron()) {
            drawRow(fr, ri, x, y + row * lineH, scale, iconScale, iconSize, iconPad,
                    new ItemStack(Items.iron_ingot),
                    example ? 10 : ResourceTracker.ironInv,
                    example ? 0 : ResourceTracker.ironEc
            );
            row++;
        }

        if (safeShowGold()) {
            drawRow(fr, ri, x, y + row * lineH, scale, iconScale, iconSize, iconPad,
                    new ItemStack(Items.gold_ingot),
                    example ? 10 : ResourceTracker.goldInv,
                    example ? 0 : ResourceTracker.goldEc
            );
            row++;
        }

        if (safeShowDiamond()) {
            drawRow(fr, ri, x, y + row * lineH, scale, iconScale, iconSize, iconPad,
                    new ItemStack(Items.diamond),
                    example ? 10 : ResourceTracker.diaInv,
                    example ? 0 : ResourceTracker.diaEc
            );
            row++;
        }

        if (safeShowEmerald()) {
            drawRow(fr, ri, x, y + row * lineH, scale, iconScale, iconSize, iconPad,
                    new ItemStack(Items.emerald),
                    example ? 10 : ResourceTracker.emeInv,
                    example ? 0 : ResourceTracker.emeEc
            );
        }
    }

    private void drawRow(FontRenderer fr, RenderItem ri,
                         float x, float y, float textScale, float iconScale, int iconSize, int iconPad,
                         ItemStack icon, int inv, int ec) {

        int total = inv + ec;
        String text = inv + " + " + ec + ": " + total;

        int fontH = fr.FONT_HEIGHT;

        // center icon vertically to the text line
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

        // center text vertically
        float textX = x + iconSize + iconPad;
        float textY = y + (rowH - (fontH * textScale)) / 2f;

        fr.drawStringWithShadow(text, textX, textY, 0xFFFFFF);
    }

    @Override
    public float getWidth(float scale, boolean example) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) {
            return 90f * scale;
        }

        int iconSize = Math.round(16f * (scale * 0.90f));
        int iconPad = Math.round(5f * scale);

        String sample = "128 + 128: 256";
        int textW = mc.fontRendererObj.getStringWidth(sample);

        return iconSize + iconPad + textW;
    }

    @Override
    public float getHeight(float scale, boolean example) {
        int lines = safeLineCount();
        if (lines <= 0) lines = 1;
        return Math.round(18f * scale) * lines;
    }

    private int safeLineCount() {
        int lines = 0;
        if (safeShowIron()) lines++;
        if (safeShowGold()) lines++;
        if (safeShowDiamond()) lines++;
        if (safeShowEmerald()) lines++;
        return lines;
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

    private MintConfig safeConfig() {
        try {
            return MintConfig.INSTANCE;
        } catch (Throwable t) {
            return null;
        }
    }
}
