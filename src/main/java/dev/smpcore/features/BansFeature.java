package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.ListSetting;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item, enchant, potion, dimension and explosive bans plus craft/carry limits.
 */
public final class BansFeature implements Listener {
    private final SmpCore plugin;

    public BansFeature(SmpCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeAll, 100L, 100L);
    }

    // ------------------------------------------------------------------ checks

    public boolean isBannedItem(Material m) {
        if (m == null || m.isAir()) return false;
        if (m == Material.MACE && Settings.BAN_MACE.on()) return true;
        if (m == Material.TIPPED_ARROW && Settings.BAN_TIPPED_ARROWS.on()) return true;
        String key = m.getKey().getKey();
        return Settings.BANNED_ITEMS.contains(key) || Settings.BANNED_NETHERITE.contains(key);
    }

    /** Max allowed level of an enchantment, -1 when unrestricted, 0 when banned. */
    public int maxEnchantLevel(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        for (String entry : Settings.BANNED_ENCHANTS.get()) {
            String n = ListSetting.normalize(entry);
            String name = n;
            int level = 0;
            int colon = n.lastIndexOf(':');
            if (colon > 0) {
                name = n.substring(0, colon);
                try {
                    level = Integer.parseInt(n.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    level = 0;
                }
            }
            if (name.equals(key)) {
                return level;
            }
        }
        return -1;
    }

    public boolean isBannedEffect(PotionEffectType type) {
        return Settings.BANNED_POTIONS.contains(type.getKey().getKey());
    }

    /** Strips banned/over-level enchants. Returns true if the item changed. */
    public boolean cleanEnchants(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta() || Settings.BANNED_ENCHANTS.get().isEmpty()) {
            return false;
        }
        boolean changed = false;
        ItemMeta meta = item.getItemMeta();
        for (Map.Entry<Enchantment, Integer> e : new HashMap<>(meta.getEnchants()).entrySet()) {
            int max = maxEnchantLevel(e.getKey());
            if (max >= 0 && e.getValue() > max) {
                meta.removeEnchant(e.getKey());
                if (max > 0) meta.addEnchant(e.getKey(), max, true);
                changed = true;
            }
        }
        if (meta instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> e : new HashMap<>(book.getStoredEnchants()).entrySet()) {
                int max = maxEnchantLevel(e.getKey());
                if (max >= 0 && e.getValue() > max) {
                    book.removeStoredEnchant(e.getKey());
                    if (max > 0) book.addStoredEnchant(e.getKey(), max, true);
                    changed = true;
                }
            }
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("smpcore.bypass");
    }

    // ------------------------------------------------------------------ crafting

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (result == null) return;
        if (isBannedItem(result.getType())) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getRecipe().getResult();
        Material type = result.getType();
        if (isBannedItem(type) && !bypass(p)) {
            e.setCancelled(true);
            Msg.error(p, Msg.pretty(type.name()) + " is banned on this server.");
            return;
        }
        int limit = Items.limitFor(Settings.CRAFT_LIMITS.get(), type);
        if (type == Material.MACE && Settings.MACE_ONE_CRAFT.on()) {
            limit = limit < 0 ? 1 : Math.min(limit, 1);
        }
        if (limit >= 0) {
            YamlStore store = plugin.data().store("server");
            String path = "crafted." + type.getKey().getKey();
            int crafted = store.yaml().getInt(path, 0);
            if (crafted >= limit) {
                e.setCancelled(true);
                Msg.error(p, "The craft limit for " + Msg.pretty(type.name()) + " has been reached (" + limit + ").");
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
                Msg.error(p, "Limited items must be crafted one at a time (no shift-click).");
                return;
            }
            store.set(path, crafted + result.getAmount());
            Bukkit.broadcast(Msg.prefixed("<gold>" + Msg.escape(p.getName()) + " crafted " + Msg.pretty(type.name())
                    + " <gray>(" + (crafted + 1) + "/" + limit + ")"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmith(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result != null && isBannedItem(result.getType())) {
            e.setResult(null);
        }
    }

    // ------------------------------------------------------------------ enchants

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        var offers = e.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] == null) continue;
            int max = maxEnchantLevel(offers[i].getEnchantment());
            if (max == 0) {
                offers[i] = null;
            } else if (max > 0 && offers[i].getEnchantmentLevel() > max) {
                offers[i].setEnchantmentLevel(max);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        e.getEnchantsToAdd().entrySet().removeIf(en -> maxEnchantLevel(en.getKey()) == 0);
        e.getEnchantsToAdd().replaceAll((ench, lvl) -> {
            int max = maxEnchantLevel(ench);
            return max > 0 ? Math.min(lvl, max) : lvl;
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result == null) return;
        if (isBannedItem(result.getType())) {
            e.setResult(null);
            return;
        }
        ItemStack copy = result.clone();
        if (cleanEnchants(copy)) {
            e.setResult(copy);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent e) {
        List<ItemStack> loot = e.getLoot();
        loot.removeIf(i -> i != null && isBannedItem(i.getType()));
        for (ItemStack i : loot) cleanEnchants(i);
    }

    // ------------------------------------------------------------------ items

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || bypass(p)) return;
        Item item = e.getItem();
        ItemStack stack = item.getItemStack();
        if (isBannedItem(stack.getType())) {
            e.setCancelled(true);
            return;
        }
        int limit = Items.limitFor(Settings.ITEM_LIMITS.get(), stack.getType());
        if (limit >= 0) {
            int have = count(p.getInventory(), stack.getType());
            if (have >= limit) {
                e.setCancelled(true);
                Msg.actionBar(p, "<red>You can only carry " + limit + " " + Msg.pretty(stack.getType().name()));
            } else if (have + stack.getAmount() > limit) {
                e.setCancelled(true);
                int take = limit - have;
                ItemStack give = stack.clone();
                give.setAmount(take);
                p.getInventory().addItem(give);
                stack.setAmount(stack.getAmount() - take);
                item.setItemStack(stack);
            }
        }
        if (cleanEnchants(stack)) {
            item.setItemStack(stack);
        }
    }

    private static int count(PlayerInventory inv, Material m) {
        int c = 0;
        for (ItemStack i : inv.getContents()) {
            if (i != null && i.getType() == m) c += i.getAmount();
        }
        return c;
    }

    private void purgeAll() {
        boolean purge = Settings.PURGE_BANNED.on();
        List<String> limits = Settings.ITEM_LIMITS.get();
        if (!purge && limits.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (bypass(p)) continue;
            PlayerInventory inv = p.getInventory();
            ItemStack[] contents = inv.getContents();
            Map<Material, Integer> seen = new HashMap<>();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (it == null || it.getType().isAir()) continue;
                if (purge) {
                    if (isBannedItem(it.getType())) {
                        contents[i] = null;
                        changed = true;
                        Msg.send(p, "<red>Removed banned item: " + Msg.pretty(it.getType().name()));
                        continue;
                    }
                    if (cleanEnchants(it)) changed = true;
                }
                int limit = Items.limitFor(limits, it.getType());
                if (limit >= 0) {
                    int have = seen.getOrDefault(it.getType(), 0);
                    int allowed = Math.max(0, limit - have);
                    if (it.getAmount() > allowed) {
                        ItemStack drop = it.clone();
                        drop.setAmount(it.getAmount() - allowed);
                        p.getWorld().dropItemNaturally(p.getLocation(), drop);
                        if (allowed == 0) contents[i] = null; else it.setAmount(allowed);
                        changed = true;
                        Msg.actionBar(p, "<red>You can only carry " + limit + " " + Msg.pretty(it.getType().name()));
                    }
                    seen.put(it.getType(), have + Math.min(allowed, it.getAmount()));
                }
            }
            if (changed) inv.setContents(contents);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        if (isBannedItem(e.getItem().getType())) {
            e.setCancelled(true);
            return;
        }
        if (e.getItem().getItemMeta() instanceof PotionMeta meta && hasBannedEffect(meta)) {
            e.setCancelled(true);
            Msg.error(p, "That potion is banned.");
        }
    }

    private boolean hasBannedEffect(PotionMeta meta) {
        if (Settings.BANNED_POTIONS.get().isEmpty()) return false;
        if (meta.getBasePotionType() != null) {
            for (PotionEffect effect : meta.getBasePotionType().getPotionEffects()) {
                if (isBannedEffect(effect.getType())) return true;
            }
        }
        for (PotionEffect effect : meta.getCustomEffects()) {
            if (isBannedEffect(effect.getType())) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (e.getNewEffect() == null || !(e.getEntity() instanceof Player)) return;
        switch (e.getCause()) {
            case POTION_DRINK, POTION_SPLASH, AREA_EFFECT_CLOUD, ARROW -> {
                if (isBannedEffect(e.getNewEffect().getType())) {
                    e.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || bypass(p)) return;
        if (e.getProjectile() instanceof Arrow arrow && Settings.BAN_TIPPED_ARROWS.on()
                && (arrow.hasCustomEffects() || arrow.getBasePotionType() != null)) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Tipped arrows are banned");
        } else if (e.getProjectile() instanceof AbstractArrow && e.getConsumable() != null && isBannedItem(e.getConsumable().getType())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ explosives & blocks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        Material m = e.getBlockPlaced().getType();
        if (!Settings.BLOCK_PLACING.on()) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Block placing is disabled");
            return;
        }
        if ((m == Material.TNT && Settings.BAN_TNT.on()) || isBannedItem(e.getItemInHand().getType())) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>" + Msg.pretty(m.name()) + " is banned");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!Settings.BLOCK_BREAKING.on() && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "<red>Block breaking is disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        ItemStack hand = e.getItem();
        if (hand != null && isBannedItem(hand.getType()) && e.getAction() != Action.PHYSICAL) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>" + Msg.pretty(hand.getType().name()) + " is banned");
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        World.Environment env = b.getWorld().getEnvironment();
        if (Settings.BAN_BEDS.on() && Tag.BEDS.isTagged(b.getType()) && env != World.Environment.NORMAL) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Bed explosions are disabled");
        } else if (Settings.BAN_ANCHORS.on() && b.getType() == Material.RESPAWN_ANCHOR && env != World.Environment.NETHER
                && b.getBlockData() instanceof RespawnAnchor anchor && anchor.getCharges() > 0
                && (hand == null || hand.getType() != Material.GLOWSTONE || anchor.getCharges() >= anchor.getMaximumCharges())) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Anchor explosions are disabled");
        } else if (Settings.BAN_TNT_MINECART.on() && hand != null && hand.getType() == Material.TNT_MINECART) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>TNT minecarts are banned");
        } else if (Settings.BAN_CRYSTALS.on() && hand != null && hand.getType() == Material.END_CRYSTAL
                && env != World.Environment.THE_END) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>End crystals are banned outside the end");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLava(PlayerBucketEmptyEvent e) {
        int radius = Settings.BAN_LAVA_RADIUS.asInt();
        if (radius <= 0 || e.getBucket() != Material.LAVA_BUCKET || bypass(e.getPlayer())) return;
        Location loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (Player other : loc.getNearbyPlayers(radius)) {
            if (other != e.getPlayer()) {
                e.setCancelled(true);
                Msg.actionBar(e.getPlayer(), "<red>You can't place lava this close to other players");
                return;
            }
        }
    }

    // ------------------------------------------------------------------ dimensions & mobs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (bypass(e.getPlayer()) || e.getTo() == null || e.getTo().getWorld() == null) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        if ((env == World.Environment.NETHER && Settings.BAN_NETHER.on()) || (env == World.Environment.THE_END && Settings.BAN_END.on())) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "<red>The " + (env == World.Environment.NETHER ? "nether" : "end") + " is closed");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        if ((env == World.Environment.NETHER && Settings.BAN_NETHER.on()) || (env == World.Environment.THE_END && Settings.BAN_END.on())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        switch (e.getSpawnReason()) {
            case NATURAL, CHUNK_GEN, SPAWNER, PATROL, TRAP, REINFORCEMENTS, JOCKEY, MOUNT, VILLAGE_DEFENSE, VILLAGE_INVASION, RAID, BUILD_WITHER, TRIAL_SPAWNER -> {
                if (Settings.MOB_DISABLER.contains(e.getEntityType().getKey().getKey())) {
                    e.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }
}
