package dev.smpcore.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class Items {
    private Items() {
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mm(name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String line : lore) {
                l.add(Msg.mm(line));
            }
            meta.lore(l);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static Material material(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace(' ', '_');
        return Material.matchMaterial(n);
    }

    public static Enchantment enchantment(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    public static PotionEffectType effect(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        return key == null ? null : Registry.MOB_EFFECT.get(key);
    }

    public static String encode(ItemStack[] items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) {
            parts.add(item == null || item.getType().isAir() ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        return String.join(";", parts);
    }

    public static ItemStack[] decode(String data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.isEmpty()) {
            return out;
        }
        String[] parts = data.split(";", -1);
        for (int i = 0; i < Math.min(size, parts.length); i++) {
            if (!parts[i].isEmpty()) {
                try {
                    out[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[i]));
                } catch (RuntimeException ignored) {
                    out[i] = null;
                }
            }
        }
        return out;
    }

    public static boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    public static boolean isSpear(Material m) {
        return m.name().endsWith("_SPEAR");
    }

    /** Parses "item:amount" entries. */
    public static int limitFor(List<String> entries, Material material) {
        for (String e : entries) {
            String[] p = e.split(":");
            if (p.length < 2) {
                continue;
            }
            String last = p[p.length - 1];
            String mat = e.substring(0, e.length() - last.length() - 1);
            Material m = material(mat);
            if (m == material) {
                try {
                    return Integer.parseInt(last.trim());
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
