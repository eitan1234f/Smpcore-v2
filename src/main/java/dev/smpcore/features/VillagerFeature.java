package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Villager trades and trial chamber vaults.
 */
public final class VillagerFeature implements Listener {
    private final SmpCore plugin;

    public VillagerFeature(SmpCore plugin) {
        this.plugin = plugin;
    }

    private boolean disabledTrade(MerchantRecipe recipe) {
        if (Settings.DISABLED_TRADES.get().isEmpty()) return false;
        ItemStack result = recipe.getResult();
        if (Settings.DISABLED_TRADES.contains(result.getType().getKey().getKey())) return true;
        if (result.getItemMeta() instanceof EnchantmentStorageMeta book) {
            for (Enchantment e : book.getStoredEnchants().keySet()) {
                if (Settings.DISABLED_TRADES.contains(e.getKey().getKey())) return true;
            }
        }
        for (Enchantment e : result.getEnchantments().keySet()) {
            if (Settings.DISABLED_TRADES.contains(e.getKey().getKey())) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAcquire(VillagerAcquireTradeEvent e) {
        if (disabledTrade(e.getRecipe())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent e) {
        if (Settings.INFINITE_RESTOCK.on()) {
            e.setIncreaseTradeUses(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager villager) || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();

        if (Settings.TRADE_CYCLE.on() && p.isSneaking() && p.getInventory().getItemInMainHand().getType().isAir()) {
            if (villager.getVillagerExperience() == 0 && villager.getProfession() != Villager.Profession.NONE
                    && villager.getProfession() != Villager.Profession.NITWIT) {
                e.setCancelled(true);
                villager.resetOffers();
                villager.getRecipes();
                p.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_WORK_LIBRARIAN, 1f, 1.2f);
                Msg.actionBar(p, "<green>⟳ Trades rerolled");
                return;
            } else if (villager.getVillagerExperience() > 0) {
                Msg.actionBar(p, "<gray>This villager has already been traded with");
            }
        }

        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        boolean changed = false;
        if (!Settings.DISABLED_TRADES.get().isEmpty()) {
            changed = recipes.removeIf(this::disabledTrade);
        }
        if (Settings.INFINITE_RESTOCK.on()) {
            for (MerchantRecipe r : recipes) {
                if (r.getUses() > 0) {
                    r.setUses(0);
                    changed = true;
                }
            }
        }
        if (changed) {
            villager.setRecipes(recipes);
        }
    }

    // ------------------------------------------------------------------ vaults

    private static String vaultKey(Block b) {
        return b.getWorld().getName() + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVault(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.VAULT) {
            return;
        }
        Player p = e.getPlayer();
        if (Settings.DISABLE_VAULTS.on() && !p.hasPermission("smpcore.bypass")) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Vaults are disabled");
            return;
        }
        int minutes = Settings.VAULT_REOPEN_MINUTES.asInt();
        if (minutes <= 0 || !(e.getClickedBlock().getState() instanceof Vault vault)) return;
        if (!vault.hasRewardedPlayer(p.getUniqueId())) return;
        YamlStore store = plugin.data().store("vaults");
        long opened = store.yaml().getLong(vaultKey(e.getClickedBlock()) + "." + p.getUniqueId(), 0L);
        long wait = minutes * 60_000L - (System.currentTimeMillis() - opened);
        if (wait <= 0) {
            vault.removeRewardedPlayer(p.getUniqueId());
            vault.update();
        } else {
            Msg.actionBar(p, "<yellow>You can open this vault again in " + Msg.duration(wait));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVaultLoot(BlockDispenseLootEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.VAULT) return;
        if (e.getPlayer() != null) {
            plugin.data().store("vaults").set(vaultKey(b) + "." + e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
        double chance = Settings.HEAVY_CORE_CHANCE.get();
        if (chance < 0) return;
        boolean ominous = b.getBlockData() instanceof org.bukkit.block.data.type.Vault v && v.isOminous();
        List<ItemStack> loot = new ArrayList<>(e.getDispensedLoot());
        if (chance == 0) {
            loot.removeIf(i -> i != null && i.getType() == Material.HEAVY_CORE);
        } else if (ominous && loot.stream().noneMatch(i -> i != null && i.getType() == Material.HEAVY_CORE)
                && ThreadLocalRandom.current().nextDouble(100) < chance) {
            loot.add(new ItemStack(Material.HEAVY_CORE));
            if (e.getPlayer() != null) {
                Bukkit.broadcast(Msg.prefixed("<gold>" + Msg.escape(e.getPlayer().getName()) + " found a Heavy Core!"));
            }
        }
        e.setDispensedLoot(loot);
    }
}
