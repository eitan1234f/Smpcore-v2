package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.DataManager;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Join/quit handling, spawn, nicknames, death records for /rollback and
 * inventory snapshots so /invsee and /endersee work on offline players.
 */
public final class PlayerFeature implements Listener {
    public static final int MAX_DEATHS = 15;
    private final SmpCore plugin;

    public PlayerFeature(SmpCore plugin) {
        this.plugin = plugin;
    }

    public Location spawn() {
        return DataManager.readLocation(plugin.data().store("server").yaml(), "spawn");
    }

    public void setSpawn(Location loc) {
        YamlStore s = plugin.data().store("server");
        DataManager.writeLocation(s.yaml(), "spawn", loc);
        s.markDirty();
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        YamlStore data = plugin.data().player(p.getUniqueId());
        data.set("name", p.getName());
        data.set("last-seen", System.currentTimeMillis());

        String nick = data.yaml().getString("nick");
        if (nick != null && !nick.isEmpty()) {
            Component c = Msg.mm(nick);
            p.displayName(c);
            p.playerListName(c);
        }

        // Apply edits made with /invsee or /endersee while the player was offline.
        if (data.yaml().getBoolean("offline-edit.inventory")) {
            p.getInventory().setContents(Items.decode(data.yaml().getString("snapshot.inventory"), p.getInventory().getSize()));
            data.set("offline-edit.inventory", false);
        }
        if (data.yaml().getBoolean("offline-edit.enderchest")) {
            p.getEnderChest().setContents(Items.decode(data.yaml().getString("snapshot.enderchest"), p.getEnderChest().getSize()));
            data.set("offline-edit.enderchest", false);
        }

        if (!p.hasPlayedBefore()) {
            Location spawn = spawn();
            if (Settings.SPAWN_ON_JOIN.on() && spawn != null) {
                p.teleportAsync(spawn);
            }
            String kit = Settings.FIRST_JOIN_KIT.get().trim();
            if (!kit.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.features().kits().give(p, kit, true), 10L);
            }
            if (Settings.JOIN_MESSAGES.on()) {
                Bukkit.broadcast(Msg.mm("<gradient:#a855f7:#ec4899>✦ Welcome <white>" + Msg.escape(p.getName())
                        + "</white> to the server! ✦</gradient>"));
            }
        }
        if (Settings.JOIN_MESSAGES.on() && e.joinMessage() != null) {
            e.joinMessage(Msg.mm("<dark_gray>[<green>+</green>]</dark_gray> <gray>" + Msg.escape(p.getName())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (Settings.JOIN_MESSAGES.on() && e.quitMessage() != null) {
            e.quitMessage(Msg.mm("<dark_gray>[<red>-</red>]</dark_gray> <gray>" + Msg.escape(p.getName())));
        }
        snapshot(p);
        plugin.data().unloadPlayer(p.getUniqueId());
    }

    public void snapshot(Player p) {
        YamlStore data = plugin.data().player(p.getUniqueId());
        data.set("snapshot.inventory", Items.encode(p.getInventory().getContents()));
        data.set("snapshot.enderchest", Items.encode(p.getEnderChest().getContents()));
        data.set("last-seen", System.currentTimeMillis());
        DataManager.writeLocation(data.yaml(), "logout-location", p.getLocation());
        data.save();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (Settings.RESPAWN_AT_SPAWN.on() && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            Location spawn = spawn();
            if (spawn != null && e.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) {
                e.setRespawnLocation(spawn);
            }
        }
    }

    // ------------------------------------------------------------------ deaths for rollback

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        YamlStore data = plugin.data().player(p.getUniqueId());
        List<Map<?, ?>> deaths = new ArrayList<>(data.yaml().getMapList("deaths"));
        Location loc = p.getLocation();
        Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("time", System.currentTimeMillis());
        record.put("reason", e.deathMessage() == null ? "Died" : Msg.plain(e.deathMessage()));
        record.put("world", loc.getWorld().getName());
        record.put("x", loc.getBlockX());
        record.put("y", loc.getBlockY());
        record.put("z", loc.getBlockZ());
        record.put("level", p.getLevel());
        record.put("inventory", Items.encode(p.getInventory().getContents()));
        deaths.add(0, record);
        while (deaths.size() > MAX_DEATHS) {
            deaths.remove(deaths.size() - 1);
        }
        data.set("deaths", deaths);
    }

    public List<Map<?, ?>> deaths(UUID uuid) {
        return plugin.data().player(uuid).yaml().getMapList("deaths");
    }

    public static String formatTime(long millis) {
        return new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss").format(new Date(millis));
    }

    public static ConfigurationSection section(YamlStore store, String path) {
        ConfigurationSection s = store.yaml().getConfigurationSection(path);
        return s == null ? store.yaml().createSection(path) : s;
    }
}
