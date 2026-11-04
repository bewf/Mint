package me.bewf.mint.util;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/** Client side {@code /mint settings}. The "click here" in the one time tip runs it, and it never reaches the server. */
public final class MintCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "mint";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mint settings";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        SettingsLink.open();
    }
}
