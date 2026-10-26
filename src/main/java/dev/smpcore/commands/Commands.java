package dev.smpcore.commands;

import dev.smpcore.SmpCore;

/**
 * Registers every SmpCore command.
 */
public final class Commands {
    private Commands() {
    }

    public static void registerAll(SmpCore plugin, CommandManager manager) {
        UtilityCommands.register(plugin, manager);
        TeleportCommands.register(plugin, manager);
        StaffCommands.register(plugin, manager);
        ProtectionCommands.register(plugin, manager);
    }
}
