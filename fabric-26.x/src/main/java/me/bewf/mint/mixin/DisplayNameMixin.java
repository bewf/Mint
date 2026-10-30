package me.bewf.mint.mixin;

import me.bewf.mint.extras.Sparkle;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class DisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void mint$polish(CallbackInfoReturnable<Component> cir) {
        try {
            if ((Object) this instanceof AbstractClientPlayer player
                    && Sparkle.wants(player.getUUID())) {
                cir.setReturnValue(Sparkle.smartName(player.getUUID(), player.getName().getString(), "getDisplayName"));
            }
        } catch (Throwable ignored) {
        }
    }
}
