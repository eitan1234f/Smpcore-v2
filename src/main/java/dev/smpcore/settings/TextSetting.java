package dev.smpcore.settings;

import org.bukkit.Material;

public final class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String path, Category category, Material icon, String name, String description,
                       String def, int maxLength) {
        super(path, category, icon, name, description, def);
        this.maxLength = maxLength;
    }

    public int maxLength() { return maxLength; }

    @Override
    public String get() {
        return config().getString(path(), def());
    }

    @Override
    public String display() {
        String v = get();
        return v.isEmpty() ? "<gray><i>empty</i></gray>" : "<white>" + v.replace("<", "\\<") + "</white>";
    }
}
