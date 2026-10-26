package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight heuristic anti-cheat. It is tuned to avoid false positives on normal
 * SMP play; it won't catch everything a dedicated anti-cheat like Grim does.
 */
public final class AntiCheat implements Listener {
    private final SmpCore plugin;
    private final Map<UUID, Data> data = new HashMap<>();
    private final Set<UUID> alertsOff = new HashSet<>();

    static final class Data {
        final Map<String, Double> vl = new HashMap<>();
        Location lastGround;
        int airTicks;
        double lastY;
        long exemptUntil;
        int speedBuffer;
        int flyBuffer;
        final Deque<Long> clicks = new ArrayDeque<>();
        final Deque<Long> places = new ArrayDeque<>();
        final Deque<Long> breaks = new ArrayDeque<>();
        final Deque<Long> hitTimes = new ArrayDeque<>();
        final Deque<UUID> hitTargets = new ArrayDeque<>();
        long lastAlert;
        long lastDig;
    }

    public AntiCheat(SmpCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::decay, 100L, 100L);
    }

    private Data data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new Data());
    }

    public Map<String, Double> violations(Player p) {
        return data(p).vl;
    }

    public void reset(Player p) {
        data.remove(p.getUniqueId());
    }

    public boolean toggleAlerts(Player p) {
        if (!alertsOff.remove(p.getUniqueId())) {
            alertsOff.add(p.getUniqueId());
            return false;
        }
        return true;
    }

    private void decay() {
        for (Data d : data.values()) {
            d.vl.replaceAll((k, v) -> Math.max(0, v - 1));
            d.vl.values().removeIf(v -> v <= 0);
        }
    }

    private boolean exempt(Player p) {
        if (!Settings.AC_ENABLED.on() || p.hasPermission("smpcore.bypass")) return true;
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || System.currentTimeMillis() < data(p).exemptUntil;
    }

    private void exemptFor(Player p, long millis) {
        Data d = data(p);
        d.exemptUntil = Math.max(d.exemptUntil, System.currentTimeMillis() + millis);
    }

    /** Adds violation points; returns true if the cheat should be blocked (setback/kick mode). */
    private boolean flag(Player p, String check, double amount, String detail) {
        Data d = data(p);
        double vl = d.vl.merge(check, amount, Double::sum);
        long now = System.currentTimeMillis();
        if (now - d.lastAlert > 1500) {
            d.lastAlert = now;
            String msg = "<#f97316>⛊ <white>" + Msg.escape(p.getName()) + "</white> <gray>failed <white>" + check + "</white> <dark_gray>("
                    + Msg.escape(detail) + ")</dark_gray> <red>VL " + (int) vl;
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("smpcore.staff") && !alertsOff.contains(staff.getUniqueId())) {
                    staff.sendMessage(Msg.prefixed(msg));
                }
            }
            plugin.getLogger().info("[AC] " + p.getName() + " failed " + check + " (" + detail + ") VL " + (int) vl);
        }
        String action = Settings.AC_ACTION.get();
        if (action.equals("kick") && vl >= Settings.AC_KICK_VL.get()) {
            d.vl.clear();
            p.kick(Msg.mm("<red>Kicked by the anti-cheat</red><newline><gray>Unfair advantage detected (" + check + ")"));
            Bukkit.broadcast(Msg.prefixed("<red>" + Msg.escape(p.getName()) + " was kicked by the anti-cheat for " + check + "."), "smpcore.staff");
            return true;
        }
        return !action.equals("alert");
    }

    // ------------------------------------------------------------------ exemptions

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        exemptFor(e.getPlayer(), 5000);
        data(e.getPlayer()).lastGround = e.getPlayer().getLocation();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        data.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        exemptFor(e.getPlayer(), 1500);
        Data d = data(e.getPlayer());
        d.lastGround = e.getTo();
        d.airTicks = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Vector v = e.getVelocity();
        exemptFor(e.getPlayer(), 1000 + (long) (v.length() * 1500));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) exemptFor(p, 1200);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        exemptFor(e.getPlayer(), 3000);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent e) {
        if (e.getEntity() instanceof Player p) exemptFor(p, 1500);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDig(org.bukkit.event.block.BlockDamageEvent e) {
        data(e.getPlayer()).lastDig = System.currentTimeMillis();
    }

    /** Standing on a happy ghast, boat, shulker or minecart counts as ground. */
    private static boolean onEntity(Player p) {
        for (Entity en : p.getNearbyEntities(1.5, 2.5, 1.5)) {
            if (en instanceof org.bukkit.entity.HappyGhast || en instanceof org.bukkit.entity.Boat || en instanceof org.bukkit.entity.Shulker
                    || en instanceof org.bukkit.entity.Minecart) {
                if (en.getLocation().getY() <= p.getLocation().getY() + 0.1) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ movement

    private static boolean nearGround(Location loc) {
        BoundingBox box = BoundingBox.of(loc.clone().add(-0.31, -0.6, -0.31), loc.clone().add(0.31, 0.1, 0.31));
        for (int x = (int) Math.floor(box.getMinX()); x <= Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= Math.floor(box.getMaxZ()); z++) {
                    Block b = loc.getWorld().getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (!b.isPassable() || b.isLiquid() || m == Material.LADDER || m == Material.VINE || m == Material.SCAFFOLDING
                            || m == Material.COBWEB || m == Material.POWDER_SNOW || m == Material.BUBBLE_COLUMN || Tag.CLIMBABLE.isTagged(m)
                            || m == Material.SWEET_BERRY_BUSH || m == Material.HONEY_BLOCK || m == Material.SLIME_BLOCK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean onIceOrSlime(Location loc) {
        Material m = loc.clone().add(0, -0.6, 0).getBlock().getType();
        return Tag.ICE.isTagged(m) || m == Material.SLIME_BLOCK || m == Material.SOUL_SAND || m == Material.SOUL_SOIL;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!e.hasChangedPosition() || exempt(p)) return;
        if (p.isFlying() || p.getAllowFlight() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || p.isSwimming()
                || p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            Data d = data(p);
            d.airTicks = 0;
            d.lastGround = e.getTo();
            return;
        }
        Data d = data(p);
        Location from = e.getFrom();
        Location to = e.getTo();
        boolean ground = nearGround(to);
        if (!ground && onEntity(p)) {
            d.airTicks = 0;
            d.speedBuffer = 0;
            d.lastGround = to.clone();
            return;
        }
        if (ground && to.clone().add(0, -0.6, 0).getBlock().getType() == Material.SLIME_BLOCK) {
            exemptFor(p, 3000);
        }
        boolean inLiquid = to.getBlock().isLiquid() || from.getBlock().isLiquid();

        // speed
        if (Settings.AC_SPEED.on() && !inLiquid) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double limit = 0.72;
            PotionEffect speed = p.getPotionEffect(PotionEffectType.SPEED);
            if (speed != null) limit *= 1 + 0.2 * (speed.getAmplifier() + 1);
            AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attr != null && attr.getValue() > 0.1001) limit *= attr.getValue() / 0.1;
            if (onIceOrSlime(to) || onIceOrSlime(from)) limit *= 1.8;
            if (horizontal > limit) {
                if (++d.speedBuffer >= 3) {
                    d.speedBuffer = 0;
                    if (flag(p, "Speed", 1, String.format("%.2f > %.2f b/t", horizontal, limit)) && d.lastGround != null) {
                        e.setTo(d.lastGround.clone().setDirection(to.getDirection()));
                        return;
                    }
                }
            } else if (d.speedBuffer > 0 && horizontal < limit * 0.8) {
                d.speedBuffer--;
            }
        }

        // fly
        if (ground) {
            d.airTicks = 0;
            d.flyBuffer = Math.max(0, d.flyBuffer - 1);
            d.lastGround = to.clone();
        } else {
            d.airTicks++;
            double dy = to.getY() - from.getY();
            int jumpBoost = p.hasPotionEffect(PotionEffectType.JUMP_BOOST) ? p.getPotionEffect(PotionEffectType.JUMP_BOOST).getAmplifier() + 1 : 0;
            int maxRise = 12 + jumpBoost * 3;
            if (Settings.AC_FLY.on() && d.airTicks > maxRise && dy >= -0.01) {
                if (++d.flyBuffer >= 4) {
                    d.flyBuffer = 0;
                    if (flag(p, "Fly", 2, "air " + d.airTicks + "t, dy " + String.format("%.2f", dy)) && d.lastGround != null) {
                        e.setTo(d.lastGround.clone().setDirection(to.getDirection()));
                        d.airTicks = 0;
                    }
                }
            }
        }
        d.lastY = to.getY();
    }

    // ------------------------------------------------------------------ combat

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (exempt(p) || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        Entity target = e.getEntity();
        Location eye = p.getEyeLocation();
        BoundingBox box = target.getBoundingBox().clone().expand(0.1);

        if (Settings.AC_REACH.on() && !dev.smpcore.util.Items.isSpear(p.getInventory().getItemInMainHand().getType())) {
            double cx = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
            double cy = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
            double cz = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
            double dist = eye.toVector().distance(new Vector(cx, cy, cz));
            AttributeInstance range = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            double max = (range == null ? 3.0 : range.getValue()) + 0.45 + (target.getVelocity().length() * 2)
                    + Math.min(1.0, p.getPing() / 500.0);
            if (dist > max) {
                if (flag(p, "Reach", 1.5, String.format("%.2f > %.2f", dist, max))) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        if (Settings.AC_KILLAURA.on() && target instanceof LivingEntity) {
            Vector toTarget = box.getCenter().subtract(eye.toVector());
            double angle = Math.toDegrees(eye.getDirection().angle(toTarget));
            double size = Math.toDegrees(Math.atan2(Math.max(box.getWidthX(), box.getHeight()) / 2, Math.max(0.5, toTarget.length())));
            if (angle - size > 75) {
                if (flag(p, "KillAura", 2, String.format("angle %.0f°", angle))) {
                    e.setCancelled(true);
                    return;
                }
            }
            Data d = data(p);
            long now = System.currentTimeMillis();
            d.hitTimes.addLast(now);
            d.hitTargets.addLast(target.getUniqueId());
            while (!d.hitTimes.isEmpty() && now - d.hitTimes.peekFirst() > 150) {
                d.hitTimes.pollFirst();
                d.hitTargets.pollFirst();
            }
            if (new HashSet<>(d.hitTargets).size() >= 3) {
                if (flag(p, "KillAura", 3, "multi-aura " + new HashSet<>(d.hitTargets).size() + " targets")) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent e) {
        Player p = e.getPlayer();
        if (!Settings.AC_AUTOCLICKER.on() || e.getHand() != EquipmentSlot.HAND || exempt(p)) return;
        Data d = data(p);
        long now = System.currentTimeMillis();
        // holding attack on a block sends a swing every tick while mining - not clicks
        if (now - d.lastDig < 1000 || p.getTargetBlockExact(5) != null) return;
        d.clicks.addLast(now);
        while (!d.clicks.isEmpty() && now - d.clicks.peekFirst() > 1000) d.clicks.pollFirst();
        int cps = d.clicks.size();
        if (cps > Settings.AC_MAX_CPS.asInt()) {
            flag(p, "AutoClicker", 0.5, cps + " cps");
        }
    }

    // ------------------------------------------------------------------ blocks

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!Settings.AC_FASTPLACE.on() || exempt(p)) return;
        Data d = data(p);
        long now = System.currentTimeMillis();
        d.places.addLast(now);
        while (!d.places.isEmpty() && now - d.places.peekFirst() > 1000) d.places.pollFirst();
        if (d.places.size() > 16) {
            if (flag(p, "FastPlace", 1, d.places.size() + " blocks/s")) e.setCancelled(true);
        }
        // scaffold: placing under yourself while looking away from the block
        Block placed = e.getBlockPlaced();
        if (placed.getY() < p.getLocation().getY() && !p.isSneaking() && p.getLocation().getPitch() < 20
                && placed.getLocation().add(0.5, 0.5, 0.5).distanceSquared(p.getLocation()) < 4 && p.getVelocity().setY(0).lengthSquared() > 0.01) {
            if (flag(p, "Scaffold", 1, "pitch " + (int) p.getLocation().getPitch())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!Settings.AC_NUKER.on() || exempt(p) || plugin.features().world().isChopping(p)) return;
        if (e.getBlock().getType().getHardness() == 0f) return;
        Data d = data(p);
        long now = System.currentTimeMillis();
        d.breaks.addLast(now);
        while (!d.breaks.isEmpty() && now - d.breaks.peekFirst() > 1000) d.breaks.pollFirst();
        if (d.breaks.size() > 22) {
            if (flag(p, "Nuker", 1, d.breaks.size() + " blocks/s")) e.setCancelled(true);
        }
    }
}
