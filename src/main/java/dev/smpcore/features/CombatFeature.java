package dev.smpcore.features;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import dev.smpcore.SmpCore;
import dev.smpcore.settings.NumberSetting;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combat tag, PvP rules, damage limiters, item cooldowns and death handling.
 */
public final class CombatFeature implements Listener {
    private static final Set<String> COMBAT_BLOCKED_COMMANDS = Set.of(
            "spawn", "home", "homes", "tpa", "tpahere", "tpaccept", "tpyes", "rtp", "wild", "back", "warp", "kit", "ec", "enderchest", "sit");

    private final SmpCore plugin;
    private final AfkTracker afk;
    private final Map<UUID, Long> tagged = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> lastSwap = new HashMap<>();
    private final Map<UUID, Long> lastTotem = new HashMap<>();

    public CombatFeature(SmpCore plugin, AfkTracker afk) {
        this.plugin = plugin;
        this.afk = afk;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTags, 10L, 10L);
    }

    // ------------------------------------------------------------------ combat tag

    public boolean isTagged(Player p) {
        Long until = tagged.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public void tag(Player p) {
        if (!Settings.COMBAT_TAG.on() || p.hasPermission("smpcore.bypass") && p.getGameMode().name().equals("CREATIVE")) {
            return;
        }
        boolean was = isTagged(p);
        tagged.put(p.getUniqueId(), System.currentTimeMillis() + Settings.COMBAT_TAG_SECONDS.asInt() * 1000L);
        if (!was) {
            Msg.send(p, "<red>⚔ You are in combat! <gray>Don't log out.");
        }
    }

    public void untag(Player p) {
        tagged.remove(p.getUniqueId());
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    private void tickTags() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = tagged.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            long left = e.getValue() - now;
            BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> {
                BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
                p.showBossBar(b);
                return b;
            });
            if (left <= 0) {
                it.remove();
                p.hideBossBar(bar);
                bars.remove(p.getUniqueId());
                Msg.actionBar(p, "<green>✔ You are no longer in combat");
                continue;
            }
            float progress = (float) Math.max(0, Math.min(1, left / (Settings.COMBAT_TAG_SECONDS.get() * 1000.0)));
            bar.progress(progress);
            bar.name(Msg.mm("<red><b>⚔ COMBAT</b></red> <gray>" + String.format("%.1f", left / 1000.0) + "s"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean disconnected = e.getReason() == PlayerQuitEvent.QuitReason.DISCONNECTED
                || e.getReason() == PlayerQuitEvent.QuitReason.TIMED_OUT;
        if (disconnected && isTagged(p) && Settings.COMBAT_LOG_KILL.on() && !p.hasPermission("smpcore.bypass")) {
            p.setHealth(0);
            Bukkit.broadcast(Msg.prefixed("<red>" + Msg.escape(p.getName()) + " logged out during combat and died!"));
        }
        untag(p);
        lastSwap.remove(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.isGliding() && e.getEntity() instanceof Player p && !p.hasPermission("smpcore.bypass")) {
            if (Settings.BAN_ELYTRA.on() || (isTagged(p) && Settings.COMBAT_NO_ELYTRA.on())) {
                e.setCancelled(true);
                Msg.actionBar(p, Settings.BAN_ELYTRA.on() ? "<red>Elytra are disabled" : "<red>You can't glide in combat");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChest(InventoryOpenEvent e) {
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST && e.getPlayer() instanceof Player p
                && isTagged(p) && Settings.COMBAT_NO_ENDERCHEST.on() && !p.hasPermission("smpcore.bypass")) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>You can't open your ender chest in combat");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isTagged(p) || !Settings.COMBAT_NO_COMMANDS.on() || p.hasPermission("smpcore.bypass")) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        if (COMBAT_BLOCKED_COMMANDS.contains(label)) {
            e.setCancelled(true);
            Msg.error(p, "You can't use /" + label + " while in combat.");
        }
    }

    // ------------------------------------------------------------------ damage rules

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerItemHeldEvent e) {
        lastSwap.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        lastSwap.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    private static void cap(EntityDamageByEntityEvent e, NumberSetting limit) {
        double max = limit.get();
        if (max > 0 && e.getDamage() > max) {
            e.setDamage(max);
        }
    }

    private static boolean hasArmor(Player p) {
        for (ItemStack i : p.getInventory().getArmorContents()) {
            if (i != null && !i.getType().isAir() && Items.isArmor(i.getType())) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Player attacker = attacker(damager);

        // explosion sources
        if (damager instanceof EnderCrystal) {
            if (e.getEntity() instanceof Player && Settings.BAN_CRYSTALS.on()) {
                e.setCancelled(true);
                return;
            }
            cap(e, Settings.DL_CRYSTAL);
        } else if (damager instanceof ExplosiveMinecart) {
            cap(e, Settings.DL_TNT_MINECART);
        } else if (damager instanceof TNTPrimed) {
            cap(e, Settings.DL_TNT);
        } else if (damager instanceof Trident) {
            cap(e, Settings.DL_TRIDENT);
        } else if (damager instanceof AbstractArrow) {
            cap(e, Settings.DL_ARROW);
        }

        if (attacker != null && damager == attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            Material type = weapon.getType();
            int swapTick = lastSwap.getOrDefault(attacker.getUniqueId(), -100);
            int sinceSwap = Bukkit.getCurrentTick() - swapTick;
            if (Settings.ATTRIBUTE_SWAP_FIX.on() && sinceSwap <= 1) {
                e.setCancelled(true);
                return;
            }
            if (Settings.BAN_BREACH_SWAP.on() && sinceSwap <= 3 && weapon.containsEnchantment(Enchantment.BREACH)) {
                e.setCancelled(true);
                Msg.actionBar(attacker, "<red>Breach swapping is not allowed");
                return;
            }
            if (type == Material.MACE) {
                if (Settings.BAN_MACE.on() && !attacker.hasPermission("smpcore.bypass")) {
                    e.setCancelled(true);
                    Msg.actionBar(attacker, "<red>The mace is banned");
                    return;
                }
                cap(e, Settings.DL_MACE);
                if (attacker.getFallDistance() > 1.5f && Settings.CD_MACE.asInt() > 0) {
                    attacker.setCooldown(Material.MACE, Settings.CD_MACE.asInt() * 20);
                }
                if (Settings.MACE_STUNS_SHIELD.on() && e.getEntity() instanceof Player victim && victim.isBlocking()) {
                    victim.setCooldown(Material.SHIELD, 100);
                    victim.clearActiveItem();
                    victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BREAK, 1f, 1f);
                }
            } else if (Items.isSpear(type)) {
                cap(e, Settings.DL_SPEAR);
                int cd = Settings.CD_SPEAR.asInt();
                if (cd > 0) {
                    for (Material m : Material.values()) {
                        if (Items.isSpear(m) && m.isItem()) attacker.setCooldown(m, cd * 20);
                    }
                }
            } else if (type == Material.TRIDENT) {
                cap(e, Settings.DL_TRIDENT);
            }
        }

        if (attacker != null && e.getEntity() instanceof Player victim && victim != attacker) {
            if (!Settings.PVP.on()) {
                e.setCancelled(true);
                Msg.actionBar(attacker, "<red>PvP is disabled");
                return;
            }
            if (Settings.NAKED_PROTECTION.on() && !hasArmor(victim) && hasArmor(attacker)) {
                e.setCancelled(true);
                Msg.actionBar(attacker, "<yellow>That player has no armor - they are protected");
                return;
            }
            if (Settings.AFK_PROTECTION.on() && afk.isAfk(victim)) {
                e.setCancelled(true);
                Msg.actionBar(attacker, "<yellow>That player is AFK and protected");
                return;
            }
            double mod = Settings.DAMAGE_MODIFIER.get();
            if (mod != 0) {
                e.setDamage(Math.max(0, e.getDamage() * (1 + mod / 100.0)));
            }
            cap(e, Settings.DL_ANY_PVP);
            tag(attacker);
            tag(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(EntityDamageByBlockEvent e) {
        if (e.getDamagerBlockState() == null) {
            return;
        }
        Material m = e.getDamagerBlockState().getType();
        NumberSetting limit = null;
        if (m == Material.RESPAWN_ANCHOR) {
            limit = Settings.DL_ANCHOR;
        } else if (Tag.BEDS.isTagged(m)) {
            limit = Settings.DL_BED;
        }
        if (limit != null && limit.get() > 0 && e.getDamage() > limit.get()) {
            e.setDamage(limit.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent e) {
        if (!Settings.SHIELD_STUNS.on()) {
            e.setCancelled(true);
            return;
        }
        int extra = Settings.CD_SHIELD.asInt();
        if (extra > 0) {
            e.setCooldown(e.getCooldown() + extra * 20);
        }
    }

    // ------------------------------------------------------------------ cooldowns

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        if (e.getEntity() instanceof EnderPearl) {
            if (isTagged(p) && Settings.COMBAT_NO_PEARLS.on() && !p.hasPermission("smpcore.bypass")) {
                e.setCancelled(true);
                Msg.actionBar(p, "<red>You can't pearl in combat");
                return;
            }
            int cd = Settings.CD_PEARL.asInt();
            if (cd > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.ENDER_PEARL, cd * 20));
            }
        } else if (e.getEntity() instanceof WindCharge) {
            int cd = Settings.CD_WIND_CHARGE.asInt();
            if (cd > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.WIND_CHARGE, cd * 20));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        int cd = switch (m) {
            case GOLDEN_APPLE -> Settings.CD_GAPPLE.asInt();
            case ENCHANTED_GOLDEN_APPLE -> Settings.CD_EGAPPLE.asInt();
            case CHORUS_FRUIT -> Settings.CD_CHORUS.asInt();
            default -> 0;
        };
        if (cd > 0) {
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(m, cd * 20));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        Player p = e.getPlayer();
        if (isTagged(p) && Settings.COMBAT_NO_RIPTIDE.on() && !p.hasPermission("smpcore.bypass")) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>You can't riptide in combat");
            return;
        }
        int cd = Settings.CD_TRIDENT.asInt();
        if (cd > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.TRIDENT, cd * 20));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent e) {
        int cd = Settings.CD_FIREWORK.asInt();
        if (cd > 0) {
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.FIREWORK_ROCKET, cd * 20));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        int cd = Settings.CD_TOTEM.asInt();
        if (cd > 0) {
            long last = lastTotem.getOrDefault(p.getUniqueId(), 0L);
            if (System.currentTimeMillis() - last < cd * 1000L) {
                e.setCancelled(true);
                Msg.send(p, "<red>Your totem was on cooldown!");
                return;
            }
            lastTotem.put(p.getUniqueId(), System.currentTimeMillis());
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.TOTEM_OF_UNDYING, cd * 20));
        }
        if (Settings.TOTEM_EFFECTS.on()) {
            Collection<PotionEffect> before = new ArrayList<>(p.getActivePotionEffects());
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (PotionEffect effect : before) {
                    PotionEffect current = p.getPotionEffect(effect.getType());
                    if (current == null || current.getAmplifier() < effect.getAmplifier()) {
                        p.addPotionEffect(effect);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ deaths

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getPlayer();
        Player killer = victim.getKiller();
        untag(victim);

        Component message = e.deathMessage();
        if (message != null) {
            if (Settings.INVIS_HIDE_VICTIM.on() && victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                message = message.replaceText(TextReplacementConfig.builder().matchLiteral(victim.getName()).replacement("???").build());
            }
            if (killer != null && Settings.INVIS_HIDE_KILLER.on() && killer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                message = message.replaceText(TextReplacementConfig.builder().matchLiteral(killer.getName()).replacement("???").build());
            }
            e.deathMessage(message);
        }

        if (killer != null && killer != victim) {
            String sound = Settings.KILL_SOUND.get().trim();
            if (!sound.isEmpty()) {
                try {
                    killer.playSound(killer.getLocation(), sound, 1f, 1f);
                } catch (RuntimeException ignored) {
                    // invalid sound key
                }
            }
        }
        if (Settings.LIGHTNING_ON_KILL.on()) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }

        int banMinutes = Settings.DEATH_BAN_MINUTES.asInt();
        if (banMinutes > 0 && !victim.hasPermission("smpcore.bypass")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    victim.ban("You died! Come back in " + Msg.duration(banMinutes * 60_000L) + ".",
                            Duration.ofMinutes(banMinutes), "SmpCore Death Ban", true);
                }
            }, 2L);
        }
    }

    public List<Player> taggedPlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID id : tagged.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && isTagged(p)) out.add(p);
        }
        return out;
    }
}
