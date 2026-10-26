package dev.smpcore.settings;

import org.bukkit.Material;

import java.util.List;

public final class ChoiceSetting extends Setting<String> {
    private final List<String> options;

    public ChoiceSetting(String path, Category category, Material icon, String name, String description,
                         String def, List<String> options) {
        super(path, category, icon, name, description, def);
        this.options = List.copyOf(options);
    }

    public List<String> options() { return options; }

    @Override
    public String get() {
        String v = config().getString(path(), def());
        return options.contains(v) ? v : def();
    }

    @Override
    public String display() {
        return "<yellow>" + get() + "</yellow>";
    }
}
