package dev.smpcore.data;

import dev.smpcore.SmpCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every data file SmpCore writes (homes, deaths, kits, npcs, punishments...).
 */
public final class DataManager {
    private final SmpCore plugin;
    private final File dataFolder;
    private final Map<String, YamlStore> stores = new HashMap<>();
    private final Map<UUID, YamlStore> players = new HashMap<>();

    public DataManager(SmpCore plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        dataFolder.mkdirs();
    }

    /** A shared store such as "server", "kits" or "npcs". */
    public YamlStore store(String name) {
        return stores.computeIfAbsent(name, n -> new YamlStore(new File(dataFolder, n + ".yml"), plugin.getLogger()));
    }

    public YamlStore player(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> new YamlStore(new File(dataFolder, "players/" + id + ".yml"), plugin.getLogger()));
    }

    public boolean hasPlayerFile(UUID uuid) {
        return players.containsKey(uuid) || new File(dataFolder, "players/" + uuid + ".yml").exists();
    }

    public void unloadPlayer(UUID uuid) {
        YamlStore s = players.remove(uuid);
        if (s != null) {
            s.saveIfDirty();
        }
    }

    public File folder() {
        return dataFolder;
    }

    public void saveAll() {
        stores.values().forEach(YamlStore::saveIfDirty);
        players.values().forEach(YamlStore::saveIfDirty);
    }

    // ------------------------------------------------------------------ location helpers

    public static void writeLocation(ConfigurationSection section, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            section.set(path, null);
            return;
        }
        section.set(path + ".world", loc.getWorld().getName());
        section.set(path + ".x", loc.getX());
        section.set(path + ".y", loc.getY());
        section.set(path + ".z", loc.getZ());
        section.set(path + ".yaw", (double) loc.getYaw());
        section.set(path + ".pitch", (double) loc.getPitch());
    }

    public static Location readLocation(ConfigurationSection section, String path) {
        if (section == null || !section.isConfigurationSection(path)) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString(path + ".world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble(path + ".x"), section.getDouble(path + ".y"), section.getDouble(path + ".z"),
                (float) section.getDouble(path + ".yaw"), (float) section.getDouble(path + ".pitch"));
    }
}
