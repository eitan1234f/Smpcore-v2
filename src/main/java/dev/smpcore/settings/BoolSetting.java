package dev.smpcore.settings;

import org.bukkit.Material;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String path, Category category, Material icon, String name, String description, boolean def) {
        super(path, category, icon, name, description, def);
    }

    @Override
    public Boolean get() {
        return config().getBoolean(path(), def());
    }

    public boolean on() {
        return get();
    }

    @Override
    public String display() {
        return get() ? "<green>Enabled</green>" : "<red>Disabled</red>";
    }
}
