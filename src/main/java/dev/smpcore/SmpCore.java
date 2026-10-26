package dev.smpcore;

import dev.smpcore.commands.CommandManager;
import dev.smpcore.commands.Commands;
import dev.smpcore.data.DataManager;
import dev.smpcore.features.Features;
import dev.smpcore.installer.PluginInstaller;
import dev.smpcore.settings.Setting;
import dev.smpcore.settings.Settings;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SmpCore - everything an SMP needs, configured from native in-game dialogs.
 */
public final class SmpCore extends JavaPlugin {
    private static SmpCore instance;

    private DataManager data;
    private CommandManager commands;
    private Features features;
    private PluginInstaller installer;

    public static SmpCore get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        Settings.bind(getConfig());

        data = new DataManager(this);
        installer = new PluginInstaller(this);
        features = new Features(this);
        features.enable();

        commands = new CommandManager(this);
        Commands.registerAll(this, commands);
        Settings.bind(getConfig());
        saveSettings();
        commands.sync();

        // Re-apply settings that push values into the world (game rules, view distance...).
        getServer().getScheduler().runTask(this, () -> Settings.all().forEach(Setting::fireChanged));
        getServer().getScheduler().runTaskTimer(this, () -> data.saveAll(), 20L * 60, 20L * 60);

        getLogger().info("SmpCore enabled with " + Settings.all().size() + " settings and "
                + commands.commands().size() + " commands. Use /settings in-game.");
    }

    @Override
    public void onDisable() {
        if (features != null) {
            features.disable();
        }
        if (commands != null) {
            commands.unregisterAll();
        }
        if (data != null) {
            data.saveAll();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        Settings.bind(getConfig());
        Settings.all().forEach(Setting::fireChanged);
        commands.sync();
    }

    public void saveSettings() {
        getConfig().options().setHeader(java.util.List.of(
                "SmpCore configuration",
                "Everything here can be changed in-game with /settings - changes apply instantly."));
        saveConfig();
    }

    public DataManager data() {
        return data;
    }

    public Features features() {
        return features;
    }

    public CommandManager commands() {
        return commands;
    }

    public PluginInstaller installer() {
        return installer;
    }
}
