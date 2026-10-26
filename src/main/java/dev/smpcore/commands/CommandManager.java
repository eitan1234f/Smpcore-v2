package dev.smpcore.commands;

import dev.smpcore.SmpCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers commands into the server command map at runtime so they can be
 * switched on and off from /settings without a restart.
 */
public final class CommandManager {
    private final SmpCore plugin;
    private final List<SmpCommand> commands = new ArrayList<>();

    public CommandManager(SmpCore plugin) {
        this.plugin = plugin;
    }

    public void add(SmpCommand command) {
        commands.add(command);
        command.toggle().onChange(v -> Bukkit.getScheduler().runTask(plugin, this::sync));
    }

    public List<SmpCommand> commands() {
        return commands;
    }

    public void sync() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        for (SmpCommand cmd : commands) {
            boolean want = cmd.toggle().on();
            if (want && !cmd.isRegistered()) {
                map.register(plugin.getName().toLowerCase(), cmd);
            } else if (!want && cmd.isRegistered()) {
                cmd.unregister(map);
                List<String> labels = new ArrayList<>(cmd.getAliases());
                labels.add(cmd.getName());
                for (String label : labels) {
                    known.remove(label, cmd);
                    known.remove(plugin.getName().toLowerCase() + ":" + label, cmd);
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateCommands();
        }
    }

    public void unregisterAll() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        for (SmpCommand cmd : commands) {
            cmd.unregister(map);
            known.values().removeIf(c -> c == cmd);
        }
    }
}
