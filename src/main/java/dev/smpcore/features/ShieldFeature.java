package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.settings.Settings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Shield delay fix: vanilla shields need 5 ticks after raising before they block.
 * This rewrites the blocks_attacks component on held shields to block instantly.
 */
public final class ShieldFeature {

    public ShieldFeature(SmpCore plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private void tick() {
        boolean fix = Settings.SHIELD_DELAY_FIX.on();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            ItemStack main = inv.getItemInMainHand();
            if (patch(main, fix)) inv.setItemInMainHand(main);
            ItemStack off = inv.getItemInOffHand();
            if (patch(off, fix)) inv.setItemInOffHand(off);
        }
    }

    private static boolean patch(ItemStack item, boolean fix) {
        if (item == null || item.getType() != Material.SHIELD) return false;
        if (!fix) {
            if (item.isDataOverridden(DataComponentTypes.BLOCKS_ATTACKS)) {
                item.resetData(DataComponentTypes.BLOCKS_ATTACKS);
                return true;
            }
            return false;
        }
        BlocksAttacks current = item.getData(DataComponentTypes.BLOCKS_ATTACKS);
        if (current == null || current.blockDelaySeconds() == 0f) return false;
        BlocksAttacks patched = BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0f)
                .disableCooldownScale(current.disableCooldownScale())
                .damageReductions(current.damageReductions())
                .itemDamage(current.itemDamage())
                .bypassedBy(current.bypassedBy())
                .blockSound(current.blockSound())
                .disableSound(current.disableSound())
                .build();
        item.setData(DataComponentTypes.BLOCKS_ATTACKS, patched);
        return true;
    }
}
