package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kits saved from a player's inventory, given whole (armor and offhand included).
 */
public final class KitService {
    private final SmpCore plugin;

    public KitService(SmpCore plugin) {
        this.plugin = plugin;
    }

    private YamlStore store() {
        return plugin.data().store("kits");
    }

    public List<String> names() {
        ConfigurationSection s = store().yaml().getConfigurationSection("kits");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public boolean exists(String name) {
        return store().yaml().isSet("kits." + name.toLowerCase(Locale.ROOT));
    }

    public void save(String name, Player from) {
        store().set("kits." + name.toLowerCase(Locale.ROOT) + ".items", Items.encode(from.getInventory().getContents()));
        store().set("kits." + name.toLowerCase(Locale.ROOT) + ".created-by", from.getName());
        store().save();
    }

    public boolean delete(String name) {
        if (!exists(name)) return false;
        store().set("kits." + name.toLowerCase(Locale.ROOT), null);
        store().save();
        return true;
    }

    public ItemStack[] items(String name) {
        return Items.decode(store().yaml().getString("kits." + name.toLowerCase(Locale.ROOT) + ".items"), 41);
    }

    /** Gives a kit. Items go in their saved slots when free, otherwise into the inventory or on the ground. */
    public boolean give(Player p, String name, boolean silent) {
        if (!exists(name)) {
            if (!silent) Msg.error(p, "Kit " + Msg.escape(name) + " doesn't exist.");
            return false;
        }
        ItemStack[] items = items(name);
        PlayerInventory inv = p.getInventory();
        List<ItemStack> overflow = new ArrayList<>();
        for (int i = 0; i < items.length && i < inv.getSize(); i++) {
            ItemStack it = items[i];
            if (it == null) continue;
            ItemStack current = inv.getItem(i);
            if (current == null || current.getType().isAir()) {
                inv.setItem(i, it.clone());
            } else {
                overflow.add(it.clone());
            }
        }
        for (ItemStack left : inv.addItem(overflow.toArray(new ItemStack[0])).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        Msg.ok(p, "You received kit <white>" + Msg.escape(name) + "</white>.");
        return true;
    }

    /** Replace the inventory with the kit exactly. */
    public void load(Player p, String name) {
        p.getInventory().setContents(items(name));
    }
}
