package dev.smpcore.settings;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A list of strings, edited in menus as a comma separated text box.
 */
public final class ListSetting extends Setting<List<String>> {
    public ListSetting(String path, Category category, Material icon, String name, String description, List<String> def) {
        super(path, category, icon, name, description, List.copyOf(def));
    }

    @Override
    public List<String> get() {
        if (!config().isList(path())) {
            return def();
        }
        return config().getStringList(path());
    }

    /** Case-insensitive contains, ignoring "minecraft:" prefixes. */
    public boolean contains(String value) {
        String v = normalize(value);
        for (String s : get()) {
            if (normalize(s).equals(v)) {
                return true;
            }
        }
        return false;
    }

    public void add(String value) {
        if (contains(value)) {
            return;
        }
        List<String> copy = new ArrayList<>(get());
        copy.add(value);
        set(copy);
    }

    public boolean remove(String value) {
        List<String> copy = new ArrayList<>(get());
        boolean removed = copy.removeIf(s -> normalize(s).equals(normalize(value)));
        if (removed) {
            set(copy);
        }
        return removed;
    }

    public static String normalize(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("minecraft:") ? v.substring(10) : v;
    }

    public String joined() {
        return String.join(", ", get());
    }

    public static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("[,\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    @Override
    public String display() {
        List<String> v = get();
        return v.isEmpty() ? "<gray><i>none</i></gray>" : "<white>" + String.join(", ", v).replace("<", "\\<") + "</white>";
    }

    @Override
    public boolean matches(String keyword) {
        return super.matches(keyword) || get().stream().anyMatch(s -> s.toLowerCase().contains(keyword.toLowerCase()));
    }
}
