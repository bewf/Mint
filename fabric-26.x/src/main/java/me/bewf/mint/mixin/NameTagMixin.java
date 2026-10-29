package me.bewf.mint.mixin;

import java.lang.reflect.Field;

import me.bewf.mint.extras.Sparkle;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
    private static Field mint$nameTagField;
    private static boolean mint$lookedUp;

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
    private void mint$polish(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        try {
            if (!(entity instanceof AbstractClientPlayer player)) return;

            if (!mint$lookedUp) {
                mint$lookedUp = true;
                try {
                    mint$nameTagField = EntityRenderState.class.getDeclaredField("nameTag");
                    mint$nameTagField.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            if (mint$nameTagField == null) return;

            Object current = mint$nameTagField.get(state);
            if (!(current instanceof Component tag)) return;

            Component replaced = Sparkle.replaceNames(tag, true, false);
            if (replaced != tag) {
                mint$nameTagField.set(state, replaced);
            } else if (Sparkle.wants(player.getUUID())) {
                mint$nameTagField.set(state, Sparkle.name(player.getUUID(), player.getName().getString()));
            }
        } catch (Throwable ignored) {
        }
    }
}
