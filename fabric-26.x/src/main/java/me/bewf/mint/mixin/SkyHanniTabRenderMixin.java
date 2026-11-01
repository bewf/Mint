package me.bewf.mint.mixin;

import me.bewf.mint.extras.Sparkle;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// SkyHanni draws each compact tab line itself, so the name is dressed up right as the line is drawn.
// This covers whole lines (prefix included) and lines that never get a custom name, like "(Offline 46h)".
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.TabListRenderer")
public abstract class SkyHanniTabRenderMixin {
    private static Component mint$dress(Component line) {
        try {
            return line == null ? null : Sparkle.restoreAfterSkyHanni(line);
        } catch (Throwable t) {
            return line;
        }
    }

    // drawString(line, x, y, color) leaves out "shadow", so Kotlin calls the synthetic $default version
    @ModifyArg(method = "drawColumns", remap = false, require = 0, index = 1,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lat/hannibal2/skyhanni/utils/GuiRenderUtils;drawString$default(Lat/hannibal2/skyhanni/utils/GuiRenderUtils;Lnet/minecraft/network/chat/Component;FFIZILjava/lang/Object;)V"))
    private Component mint$drawDefault(Component line) {
        return mint$dress(line);
    }

    // Same thing if SkyHanni ever passes every argument
    @ModifyArg(method = "drawColumns", remap = false, require = 0, index = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lat/hannibal2/skyhanni/utils/GuiRenderUtils;drawString(Lnet/minecraft/network/chat/Component;FFIZ)V"))
    private Component mint$drawFull(Component line) {
        return mint$dress(line);
    }
}
