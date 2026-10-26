package dev.smpcore.features;

import dev.smpcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FreezeService implements Listener {
    private final Set<UUID> frozen = new HashSet<>();

    public boolean isFrozen(Player p) {
        return frozen.contains(p.getUniqueId());
    }

    public boolean toggle(Player p) {
        if (!frozen.remove(p.getUniqueId())) {
            frozen.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!isFrozen(e.getPlayer()) || !e.hasChangedPosition()) return;
        Location from = e.getFrom();
        Location to = e.getTo().clone();
        to.setX(from.getX());
        to.setZ(from.getZ());
        if (to.getY() > from.getY()) to.setY(from.getY());
        e.setTo(to);
        Msg.actionBar(e.getPlayer(), "<aqua>❄ You are frozen by staff ❄");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isFrozen(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isFrozen(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (isFrozen(e.getPlayer()) && !e.getPlayer().hasPermission("smpcore.staff")) {
            String m = e.getMessage().toLowerCase();
            if (!m.startsWith("/msg") && !m.startsWith("/r ") && !m.startsWith("/reply") && !m.startsWith("/tell")) {
                e.setCancelled(true);
                Msg.error(e.getPlayer(), "You can't use commands while frozen.");
            }
        }
    }
}
