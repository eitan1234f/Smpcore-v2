package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

public final class VanishService implements Listener {
    private final SmpCore plugin;
    private final NamespacedKey key;

    public VanishService(SmpCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "vanished");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isVanished(p)) {
                    Msg.actionBar(p, "<gradient:#94a3b8:#e2e8f0>You are vanished</gradient>");
                }
            }
        }, 40L, 40L);
    }

    public boolean isVanished(Player p) {
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BOOLEAN, false);
    }

    public void setVanished(Player p, boolean vanished) {
        p.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, vanished);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == p) continue;
            apply(p, other);
        }
        p.setSleepingIgnored(vanished);
        p.setCollidable(!vanished);
        p.setSilent(vanished);
    }

    private void apply(Player target, Player viewer) {
        if (isVanished(target) && !viewer.hasPermission("smpcore.staff")) {
            viewer.hidePlayer(plugin, target);
        } else {
            viewer.showPlayer(plugin, target);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == joined) continue;
            apply(other, joined);
            apply(joined, other);
        }
        if (isVanished(joined)) {
            e.joinMessage(null);
            Msg.send(joined, "<gray>You joined silently because you are vanished.");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        if (isVanished(e.getPlayer())) {
            e.quitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && isVanished(p)) {
            e.setCancelled(true);
        }
    }
}
