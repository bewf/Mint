package me.bewf.mint.mixin;

import me.bewf.mint.config.MintConfig;
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
            if (!MintConfig.INSTANCE.fancyTabList) return;

            Component original = cir.getReturnValue();

            // Only the game's own tab list gets custom colours, mods that read the names
            // (SkyHanni's Advanced Player List) get plain Minecraft colours
            boolean vanilla = Sparkle.calledByVanilla("getNameForDisplay");

            // Text replace first: also works for servers (SkyBlock) that draw fake tab entries
            Component replaced = Sparkle.replaceNames(original, vanilla, false, !vanilla);
            if (replaced != original) {
                cir.setReturnValue(replaced);
            } else if (Sparkle.wants(info.getProfile().id())) {
                cir.setReturnValue(Sparkle.smartName(info.getProfile().id(), info.getProfile().name(), "getNameForDisplay"));
            }
        } catch (Throwable ignored) {
        }
    }
}
