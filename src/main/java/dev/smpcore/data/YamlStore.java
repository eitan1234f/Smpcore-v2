package dev.smpcore.data;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A YAML file that is saved lazily: mark it dirty and the autosave task writes it.
 */
public final class YamlStore {
    private final File file;
    private final Logger logger;
    private YamlConfiguration yaml;
    private boolean dirty;

    public YamlStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public void load() {
        yaml = YamlConfiguration.loadConfiguration(file);
        dirty = false;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public void set(String path, Object value) {
        yaml.set(path, value);
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public File file() {
        return file;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file.getName(), e);
        }
    }
}
