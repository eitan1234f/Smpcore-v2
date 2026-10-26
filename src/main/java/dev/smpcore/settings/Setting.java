package dev.smpcore.settings;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A single configurable value. Values live in config.yml under {@link #path()}
 * and are read live, so changes from the menu apply instantly.
 */
public abstract class Setting<T> {
    private final String path;
    private final Category category;
    private final Material icon;
    private final String name;
    private final String description;
    private final T def;
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private boolean restartRequired;

    protected Setting(String path, Category category, Material icon, String name, String description, T def) {
        this.path = path;
        this.category = category;
        this.icon = icon;
        this.name = name;
        this.description = description;
        this.def = def;
    }

    public String path() { return path; }
    public Category category() { return category; }
    public Material icon() { return icon; }
    public String name() { return name; }
    public String description() { return description; }
    public T def() { return def; }
    public boolean restartRequired() { return restartRequired; }

    public Setting<T> requiresRestart() {
        this.restartRequired = true;
        return this;
    }

    public Setting<T> onChange(Consumer<T> listener) {
        listeners.add(listener);
        return this;
    }

    protected FileConfiguration config() {
        return Settings.config();
    }

    public abstract T get();

    public void set(T value) {
        T old = get();
        config().set(path, toYaml(value));
        if (!old.equals(value)) {
            fireChanged();
        }
    }

    public void fireChanged() {
        T now = get();
        for (Consumer<T> l : listeners) {
            l.accept(now);
        }
    }

    protected Object toYaml(T value) {
        return value;
    }

    /** Human readable value for read-only views. */
    public abstract String display();

    /** Matches search keywords against name, description and path. */
    public boolean matches(String keyword) {
        String k = keyword.toLowerCase();
        return name.toLowerCase().contains(k) || description.toLowerCase().contains(k) || path.toLowerCase().contains(k)
                || category.displayName().toLowerCase().contains(k);
    }
}
