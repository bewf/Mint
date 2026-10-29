package me.bewf.mint.mixin;

import me.bewf.mint.extras.Sparkle;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private void mint$polish(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        try {
            Component original = cir.getReturnValue();

            // Text replace first: also works for servers (SkyBlock) that draw fake tab entries
            Component replaced = Sparkle.replaceNames(original, true, false);
            if (replaced != original) {
                cir.setReturnValue(replaced);
            } else if (Sparkle.wants(info.getProfile().id())) {
                cir.setReturnValue(Sparkle.name(info.getProfile().id(), info.getProfile().name()));
            }
        } catch (Throwable ignored) {
        }
    }
}
