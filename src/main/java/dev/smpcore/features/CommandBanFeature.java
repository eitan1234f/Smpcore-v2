package dev.smpcore.features;

import dev.smpcore.settings.ListSetting;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;

/**
 * Command ban: blocks a list of commands for non-admins and hides them from tab-complete.
 */
public final class CommandBanFeature implements Listener {

    public CommandBanFeature() {
        Settings.BLOCKED_COMMANDS.onChange(v -> Bukkit.getOnlinePlayers().forEach(Player::updateCommands));
    }

    public static boolean isBlocked(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (Settings.BLOCKED_COMMANDS.contains(l)) return true;
        int colon = l.indexOf(':');
        return colon >= 0 && Settings.BLOCKED_COMMANDS.contains(l.substring(colon + 1));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getPlayer().hasPermission("smpcore.bypass")) return;
        String label = e.getMessage().substring(1).split(" ", 2)[0];
        if (isBlocked(label)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Msg.mm(Settings.BLOCKED_COMMAND_MESSAGE.get()));
        }
    }

    @EventHandler
    public void onTab(PlayerCommandSendEvent e) {
        if (e.getPlayer().hasPermission("smpcore.bypass") || Settings.BLOCKED_COMMANDS.get().isEmpty()) return;
        e.getCommands().removeIf(CommandBanFeature::isBlocked);
    }

    public static ListSetting list() {
        return Settings.BLOCKED_COMMANDS;
    }
}
