package me.bewf.mint.mixin;

import me.bewf.mint.extras.Sparkle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keeps SkyHanni's column widths in step with the longer "Lord and Saviour" names it draws
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.TabLine")
public abstract class SkyHanniTabLineMixin {
    @Shadow(remap = false)
    private Component component;

    @Shadow(remap = false)
    private Component customName;

    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void mint$width(CallbackInfoReturnable<Integer> cir) {
        try {
            Component source = customName != null ? customName : component;
            if (source == null) return;
            Component replaced = Sparkle.restoreAfterSkyHanni(source);
            if (replaced == source) return;
            Font font = Minecraft.getInstance().font;
            cir.setReturnValue(cir.getReturnValue() + font.width(replaced) - font.width(source));
        } catch (Throwable ignored) {
        }
    }
}
