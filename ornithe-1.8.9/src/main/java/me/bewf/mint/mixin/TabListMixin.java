package me.bewf.mint.mixin;

import me.bewf.mint.config.MintConfig;
import me.bewf.mint.extras.Sparkle;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void mint$polish(PlayerInfo info, CallbackInfoReturnable<String> cir) {
        try {
            if (!MintConfig.INSTANCE.fancyTabList) return;
            String original = cir.getReturnValue();
            String replaced = Sparkle.tabName(info.getProfile().getId(), info.getProfile().getName(), original);
            if (replaced != null && !replaced.equals(original)) cir.setReturnValue(replaced);
        } catch (Throwable ignored) {
        }
    }
}
