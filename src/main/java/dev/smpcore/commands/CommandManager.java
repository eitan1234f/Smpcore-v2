package dev.smpcore.commands;

import dev.smpcore.SmpCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers commands into the server command map at runtime so they can be
 * switched on and off from /settings without a restart. Enabled commands take
 * over their label (e.g. /ban, /msg); disabled ones hand it back to whatever
 * command owned it before (vanilla or another plugin).
 */
public final class CommandManager {
    private final SmpCore plugin;
    private final List<SmpCommand> commands = new ArrayList<>();
    private final Map<String, Command> displaced = new HashMap<>();

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

    private String prefix() {
        return plugin.getName().toLowerCase();
    }

    private List<String> labels(SmpCommand cmd) {
        List<String> labels = new ArrayList<>();
        labels.add(cmd.getName());
        labels.addAll(cmd.getAliases());
        return labels;
    }

    public void sync() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        for (SmpCommand cmd : commands) {
            boolean want = cmd.toggle().on();
            if (want) {
                for (String label : labels(cmd)) {
                    Command existing = known.get(label);
                    if (existing != cmd) {
                        if (existing != null && !displaced.containsKey(label)) {
                            displaced.put(label, existing);
                        }
                        known.put(label, cmd);
                    }
                    known.put(prefix() + ":" + label, cmd);
                }
                if (!cmd.isRegistered()) {
                    cmd.register(map);
                }
            } else {
                for (String label : labels(cmd)) {
                    known.remove(prefix() + ":" + label, cmd);
                    if (known.get(label) == cmd) {
                        Command previous = displaced.remove(label);
                        if (previous != null) {
                            known.put(label, previous);
                        } else {
                            known.remove(label);
                        }
                    }
                }
                if (cmd.isRegistered()) {
                    cmd.unregister(map);
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
            for (String label : labels(cmd)) {
                known.remove(prefix() + ":" + label, cmd);
                if (known.get(label) == cmd) {
                    Command previous = displaced.remove(label);
                    if (previous != null) known.put(label, previous); else known.remove(label);
                }
            }
            cmd.unregister(map);
        }
    }
}
