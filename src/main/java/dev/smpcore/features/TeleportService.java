package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.DataManager;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teleports with a warmup that cancels on movement, plus /back tracking.
 */
public final class TeleportService implements Listener {
    private final SmpCore plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public TeleportService(SmpCore plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, Location target, String what) {
        if (plugin.features().combat().isTagged(player) && Settings.COMBAT_NO_COMMANDS.on() && !player.hasPermission("smpcore.bypass")) {
            Msg.error(player, "You can't teleport while in combat!");
            return;
        }
        cancel(player);
        int warmup = Settings.TP_WARMUP.asInt();
        if (warmup <= 0 || player.hasPermission("smpcore.bypass")) {
            go(player, target, what);
            return;
        }
        Location start = player.getLocation().clone();
        int[] left = {warmup};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(player);
                return;
            }
            Location now = player.getLocation();
            if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.5) {
                cancel(player);
                Msg.actionBar(player, "<red>✖ Teleport cancelled - you moved");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }
            if (left[0] <= 0) {
                cancel(player);
                go(player, target, what);
                return;
            }
            Msg.actionBar(player, "<gradient:#a855f7:#ec4899>Teleporting to " + what + " in <white>" + left[0] + "s</white>... don't move</gradient>");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.2f + (warmup - left[0]) * 0.1f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.2);
            left[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void go(Player player, Location target, String what) {
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND, TeleportFlag.EntityState.RETAIN_PASSENGERS).thenAccept(ok -> {
            if (ok) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                Msg.actionBar(player, "<green>✔ Teleported to " + what);
            }
        });
    }

    public void cancel(Player player) {
        BukkitTask t = pending.remove(player.getUniqueId());
        if (t != null) {
            t.cancel();
        }
    }

    public void setBack(Player player, Location loc) {
        YamlStore store = plugin.data().player(player.getUniqueId());
        DataManager.writeLocation(store.yaml(), "back", loc);
        store.markDirty();
    }

    public Location getBack(Player player) {
        return DataManager.readLocation(plugin.data().player(player.getUniqueId()).yaml(), "back");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        switch (event.getCause()) {
            case COMMAND, PLUGIN, SPECTATE -> {
                if (event.getFrom().getWorld() != event.getTo().getWorld() || event.getFrom().distanceSquared(event.getTo()) > 25) {
                    setBack(event.getPlayer(), event.getFrom());
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (Settings.BACK_ON_DEATH.on()) {
            setBack(event.getPlayer(), event.getPlayer().getLocation());
        }
    }
}
