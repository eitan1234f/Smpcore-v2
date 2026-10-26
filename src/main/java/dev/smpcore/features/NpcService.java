package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.util.Msg;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * NPCs built on the vanilla Mannequin entity (1.21.9+) - real player skins, no packets needed.
 */
public final class NpcService implements Listener {
    private final SmpCore plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    public NpcService(SmpCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "npc");
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 40L, 5L);
    }

    private YamlStore store() {
        return plugin.data().store("npcs");
    }

    public List<String> names() {
        ConfigurationSection s = store().yaml().getConfigurationSection("npcs");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public boolean exists(String name) {
        return store().yaml().isSet("npcs." + id(name));
    }

    private static String id(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public Mannequin entity(String name) {
        String uuid = store().yaml().getString("npcs." + id(name) + ".uuid");
        if (uuid == null) return null;
        Entity e = Bukkit.getEntity(UUID.fromString(uuid));
        return e instanceof Mannequin m ? m : null;
    }

    public Mannequin create(String name, Location loc, String skin) {
        Mannequin m = loc.getWorld().spawn(loc, Mannequin.class, mq -> {
            mq.getPersistentDataContainer().set(key, PersistentDataType.STRING, id(name));
            mq.customName(Msg.mm(name.replace('_', ' ')));
            mq.setCustomNameVisible(true);
            mq.setImmovable(true);
            mq.setInvulnerable(true);
            mq.setPersistent(true);
            mq.setRemoveWhenFarAway(false);
            mq.setDescription(null);
            if (skin != null && !skin.isEmpty()) {
                mq.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
            }
        });
        String path = "npcs." + id(name);
        store().set(path + ".display", name);
        store().set(path + ".uuid", m.getUniqueId().toString());
        store().set(path + ".skin", skin == null ? "" : skin);
        store().set(path + ".look-at-players", true);
        store().set(path + ".actions", new ArrayList<String>());
        store().save();
        return m;
    }

    public void setSkin(String name, String skin) {
        Mannequin m = entity(name);
        if (m != null) m.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
        store().set("npcs." + id(name) + ".skin", skin);
    }

    public void setDescription(String name, String text) {
        Mannequin m = entity(name);
        if (m != null) m.setDescription(text == null || text.isEmpty() ? null : Msg.mm(text));
        store().set("npcs." + id(name) + ".description", text);
    }

    public List<String> actions(String name) {
        return new ArrayList<>(store().yaml().getStringList("npcs." + id(name) + ".actions"));
    }

    public void setActions(String name, List<String> actions) {
        store().set("npcs." + id(name) + ".actions", actions);
        store().save();
    }

    public boolean remove(String name) {
        if (!exists(name)) return false;
        Mannequin m = entity(name);
        if (m != null) m.remove();
        store().set("npcs." + id(name), null);
        store().save();
        return true;
    }

    public void teleport(String name, Location loc) {
        Mannequin m = entity(name);
        if (m != null) m.teleport(loc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        run(e.getPlayer(), id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        String id = e.getEntity().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) run(p, id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(key)) e.setCancelled(true);
    }

    private void run(Player p, String id) {
        long now = System.currentTimeMillis();
        if (now - clickCooldown.getOrDefault(p.getUniqueId(), 0L) < 500) return;
        clickCooldown.put(p.getUniqueId(), now);
        for (String action : store().yaml().getStringList("npcs." + id + ".actions")) {
            int colon = action.indexOf(':');
            if (colon < 0) continue;
            String type = action.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = action.substring(colon + 1).trim().replace("%player%", p.getName());
            switch (type) {
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.startsWith("/") ? value.substring(1) : value);
                case "player" -> p.performCommand(value.startsWith("/") ? value.substring(1) : value);
                case "msg", "message" -> p.sendMessage(Msg.mm(value));
                case "sound" -> p.playSound(p.getLocation(), value, 1f, 1f);
                default -> {
                }
            }
        }
    }

    private void lookAtPlayers() {
        ConfigurationSection s = store().yaml().getConfigurationSection("npcs");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            if (!s.getBoolean(id + ".look-at-players", true)) continue;
            Mannequin m = entity(id);
            if (m == null || !m.isValid()) continue;
            World w = m.getWorld();
            Player nearest = null;
            double best = 64;
            for (Player p : w.getPlayers()) {
                double d = p.getLocation().distanceSquared(m.getLocation());
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest == null) continue;
            Vector dir = nearest.getEyeLocation().toVector().subtract(m.getEyeLocation().toVector());
            Location look = m.getLocation().setDirection(dir);
            m.setRotation(look.getYaw(), look.getPitch());
        }
    }
}
