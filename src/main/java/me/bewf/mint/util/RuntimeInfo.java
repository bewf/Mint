package me.bewf.mint.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public final class RuntimeInfo {

    private RuntimeInfo() {
    }

    public static final String PREFIX_AQUA = ChatFormatting.AQUA.toString();
    public static final String GRAY = ChatFormatting.GRAY.toString();
    public static final String DARK_GRAY = ChatFormatting.DARK_GRAY.toString();
    public static final String GOLD = ChatFormatting.GOLD.toString();
    public static final String GREEN = ChatFormatting.GREEN.toString();
    public static final String RED = ChatFormatting.RED.toString();

    public static String currentMinecraftVersion() {
        Optional<net.fabricmc.loader.api.ModContainer> minecraft = FabricLoader.getInstance().getModContainer("minecraft");
        if (minecraft.isPresent()) {
            return minecraft.get().getMetadata().getVersion().getFriendlyString();
        }

        return "unknown";
    }

    public static void sendChatMessage(String legacyFormattedText) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        Component msg = Component.literal(legacyFormattedText);

        if (ReflectUtil.tryCall(client.player, "displayClientMessage", msg, false)) return;
        if (ReflectUtil.tryCall(client.player, "sendSystemMessage", msg)) return;

        Object gui = client.gui;
        Object chat = ReflectUtil.tryGetNoArg(gui, "getChat");
        if (chat == null) chat = ReflectUtil.tryGetNoArg(gui, "chat");

        if (chat != null) {
            if (ReflectUtil.tryCall(chat, "addMessage", msg)) return;
            if (ReflectUtil.tryCall(chat, "addMessage", msg, null, null, null)) return;
            if (ReflectUtil.tryCall(chat, "addMessage", msg, null, null, null, false)) return;
        }

        System.out.println(legacyFormattedText);
    }
}
