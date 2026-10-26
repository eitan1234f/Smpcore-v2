package dev.smpcore.commands;

import dev.smpcore.SmpCore;
import dev.smpcore.settings.BoolSetting;
import dev.smpcore.settings.Category;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Base for every SmpCore command. Each command gets an on/off switch in the
 * Commands category so it can be turned off to let another plugin handle it.
 */
public abstract class SmpCommand extends Command implements PluginIdentifiableCommand {
    protected final SmpCore plugin;
    private final BoolSetting toggle;

    protected SmpCommand(SmpCore plugin, String name, Material icon, String description, String usage,
                         String permission, String... aliases) {
        super(name, description, usage, Arrays.asList(aliases));
        this.plugin = plugin;
        if (permission != null) {
            setPermission(permission);
        }
        this.toggle = Settings.bool("commands." + name, Category.COMMANDS, icon, "/" + name, description, true);
    }

    public BoolSetting toggle() {
        return toggle;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public final boolean execute(CommandSender sender, String label, String[] args) {
        if (!toggle.on()) {
            Msg.error(sender, "This command is disabled.");
            return true;
        }
        if (getPermission() != null && !sender.hasPermission(getPermission())) {
            Msg.error(sender, "You don't have permission to do that.");
            return true;
        }
        try {
            run(sender, label, args);
        } catch (RuntimeException e) {
            Msg.error(sender, "Something went wrong running that command.");
            plugin.getLogger().log(Level.WARNING, "Error in /" + getName(), e);
        }
        return true;
    }

    protected abstract void run(CommandSender sender, String label, String[] args);

    protected List<String> complete(CommandSender sender, String[] args) {
        return null;
    }

    @Override
    public final List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!toggle.on() || (getPermission() != null && !sender.hasPermission(getPermission()))) {
            return List.of();
        }
        List<String> options = complete(sender, args);
        if (options == null) {
            return players(args.length == 0 ? "" : args[args.length - 1]);
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }

    protected static List<String> players(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(p.getName());
            }
        }
        return out;
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        Msg.error(sender, "Only players can use this command.");
        return null;
    }

    protected Player online(CommandSender sender, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            p = Bukkit.getPlayer(name);
        }
        if (p == null) {
            Msg.error(sender, "Player " + Msg.escape(name) + " is not online.");
        }
        return p;
    }

    @SuppressWarnings("deprecation")
    protected OfflinePlayer offline(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null || (!op.hasPlayedBefore() && !op.isOnline())) {
            op = Bukkit.getOfflinePlayer(name);
            if (!op.hasPlayedBefore()) {
                Msg.error(sender, "Player " + Msg.escape(name) + " has never joined.");
                return null;
            }
        }
        return op;
    }

    protected void usage(CommandSender sender) {
        Msg.send(sender, "<gray>Usage: <white>" + Msg.escape(getUsage().replace("<command>", getName())));
    }

    protected static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, Math.min(from, args.length), args.length));
    }
}
