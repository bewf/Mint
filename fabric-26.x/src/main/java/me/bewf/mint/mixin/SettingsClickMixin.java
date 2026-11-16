package me.bewf.mint.mixin;

import me.bewf.mint.util.TeamChestSync;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SettingsClickMixin {
    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void mint$settingsClick(String command, Screen screen, CallbackInfo ci) {
        if (TeamChestSync.handleSettingsCommand(command)) {
            ci.cancel();
        }
    }
}
