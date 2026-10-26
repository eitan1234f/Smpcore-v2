package dev.smpcore.features;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import dev.smpcore.SmpCore;
import dev.smpcore.settings.Settings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.List;

/**
 * Stackable potions, easy recipes, recipe remover and anvil tweaks.
 */
public final class ItemsFeature implements Listener {
    private final SmpCore plugin;
    private final List<NamespacedKey> easyKeys = new ArrayList<>();

    public ItemsFeature(SmpCore plugin) {
        this.plugin = plugin;
        Settings.EASY_RECIPES.onChange(v -> applyEasyRecipes());
        Settings.REMOVED_RECIPES.onChange(v -> applyRemovedRecipes());
        Bukkit.getScheduler().runTaskTimer(plugin, this::stackPotionsOnline, 100L, 100L);
    }

    // ------------------------------------------------------------------ stackable potions

    private static boolean isPotion(Material m) {
        return m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION;
    }

    /** Applies the configured stack size to a potion. Returns true if it changed. */
    public static boolean applyStack(ItemStack item) {
        if (item == null || !isPotion(item.getType())) return false;
        int size = Settings.POTION_STACK.asInt();
        Integer current = item.getData(DataComponentTypes.MAX_STACK_SIZE);
        if (size <= 1) {
            if (item.isDataOverridden(DataComponentTypes.MAX_STACK_SIZE)) {
                item.resetData(DataComponentTypes.MAX_STACK_SIZE);
                return true;
            }
            return false;
        }
        if (current != null && current == size) return false;
        item.setData(DataComponentTypes.MAX_STACK_SIZE, size);
        return true;
    }

    private void stackPotionsOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            boolean changed = false;
            for (ItemStack i : contents) {
                if (applyStack(i)) changed = true;
            }
            if (changed) p.getInventory().setContents(contents);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        ItemStack stack = item.getItemStack();
        if (applyStack(stack)) {
            item.setItemStack(stack);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBrew(BrewEvent e) {
        for (ItemStack i : e.getResults()) {
            applyStack(i);
        }
    }

    // ------------------------------------------------------------------ anvils

    @EventHandler(ignoreCancelled = true)
    public void onAnvilDamage(AnvilDamagedEvent e) {
        if (Settings.UNBREAKABLE_ANVILS.on()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!Settings.ANVIL_FIX.on()) return;
        AnvilView view = e.getView();
        view.setMaximumRepairCost(Integer.MAX_VALUE);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (view.getRepairCost() >= 40) {
                view.setRepairCost(39);
            }
        });
        if (view.getRepairCost() >= 40) {
            view.setRepairCost(39);
        }
    }

    // ------------------------------------------------------------------ recipes

    public void applyEasyRecipes() {
        for (NamespacedKey key : easyKeys) {
            Bukkit.removeRecipe(key, false);
        }
        easyKeys.clear();
        if (Settings.EASY_RECIPES.on()) {
            add(shapeless("easy_string", new ItemStack(Material.STRING, 4), new RecipeChoice.MaterialChoice(Tag.WOOL)));
            add(shaped("easy_name_tag", new ItemStack(Material.NAME_TAG), new String[]{"  S", " P ", "P  "},
                    'S', Material.STRING, 'P', Material.PAPER));
            add(shaped("easy_saddle", new ItemStack(Material.SADDLE), new String[]{"LLL", "LIL", "I I"},
                    'L', Material.LEATHER, 'I', Material.IRON_INGOT));
            add(shaped("easy_chain_helmet", new ItemStack(Material.CHAINMAIL_HELMET), new String[]{"NNN", "N N"}, 'N', Material.IRON_NUGGET));
            add(shaped("easy_chain_chestplate", new ItemStack(Material.CHAINMAIL_CHESTPLATE), new String[]{"N N", "NNN", "NNN"}, 'N', Material.IRON_NUGGET));
            add(shaped("easy_chain_leggings", new ItemStack(Material.CHAINMAIL_LEGGINGS), new String[]{"NNN", "N N", "N N"}, 'N', Material.IRON_NUGGET));
            add(shaped("easy_chain_boots", new ItemStack(Material.CHAINMAIL_BOOTS), new String[]{"N N", "N N"}, 'N', Material.IRON_NUGGET));
            add(shapeless("easy_leather", new ItemStack(Material.LEATHER), new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH),
                    new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH), new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH),
                    new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH)));
            add(shapeless("easy_clay_balls", new ItemStack(Material.CLAY_BALL, 4), new RecipeChoice.MaterialChoice(Material.CLAY)));
            add(shapeless("easy_quartz", new ItemStack(Material.QUARTZ, 4), new RecipeChoice.MaterialChoice(Material.QUARTZ_BLOCK)));
        }
        Bukkit.updateRecipes();
    }

    private void add(Recipe recipe) {
        if (Bukkit.addRecipe(recipe, false) && recipe instanceof org.bukkit.Keyed k) {
            easyKeys.add(k.getKey());
        }
    }

    private ShapelessRecipe shapeless(String id, ItemStack result, RecipeChoice... choices) {
        ShapelessRecipe r = new ShapelessRecipe(new NamespacedKey(plugin, id), result);
        for (RecipeChoice c : choices) r.addIngredient(c);
        return r;
    }

    private ShapedRecipe shaped(String id, ItemStack result, String[] shape, Object... ingredients) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, id), result);
        r.shape(shape);
        for (int i = 0; i < ingredients.length; i += 2) {
            r.setIngredient((Character) ingredients[i], (Material) ingredients[i + 1]);
        }
        return r;
    }

    public void applyRemovedRecipes() {
        boolean any = false;
        for (String key : Settings.REMOVED_RECIPES.get()) {
            NamespacedKey k = NamespacedKey.fromString(key.trim().toLowerCase());
            if (k != null && Bukkit.getRecipe(k) != null) {
                Bukkit.removeRecipe(k, false);
                any = true;
            }
        }
        if (any) {
            Bukkit.updateRecipes();
        }
    }
}
