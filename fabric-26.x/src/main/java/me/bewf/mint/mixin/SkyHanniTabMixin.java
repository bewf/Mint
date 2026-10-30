package me.bewf.mint.mixin;

import me.bewf.mint.extras.Sparkle;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SkyHanni's Advanced Player List rebuilds every tab line from plain text, which cannot hold
 * custom colours, so it gets the plain Minecraft colour version first. This puts the custom
 * colours back on the finished line, which SkyHanni then draws as a normal Component.
 * Only used while that SkyHanni feature runs. If SkyHanni changes and this does not apply,
 * the tab list simply keeps the plain Minecraft colours.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.AdvancedPlayerList")
public abstract class SkyHanniTabMixin {
    @Inject(method = "createCustomName", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void mint$polish(@Coerce Object playerData, CallbackInfoReturnable<Component> cir) {
        try {
            Component original = cir.getReturnValue();
            Component upgraded = Sparkle.upgradeDecoratedNames(original, true);
            if (upgraded != original) cir.setReturnValue(upgraded);
        } catch (Throwable ignored) {
        }
    }
}
