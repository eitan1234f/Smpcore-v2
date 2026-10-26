package dev.smpcore.features;

import dev.smpcore.settings.Settings;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkTracker implements Listener {
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();

    public boolean isAfk(Player player) {
        long last = lastActive.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        lastActive.putIfAbsent(player.getUniqueId(), last);
        return System.currentTimeMillis() - last > Settings.AFK_MINUTES.get() * 60_000L;
    }

    private void touch(Player p) {
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.hasChangedOrientation()) {
            touch(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastActive.remove(e.getPlayer().getUniqueId());
    }
}
