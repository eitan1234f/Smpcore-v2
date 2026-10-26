package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.ui.ChestGui;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Custom crafting, smelting, brewing, stonecutting and anvil recipes made with an in-game editor.
 */
public final class CustomRecipes implements Listener {
    public static final List<String> TYPES = List.of("shaped", "shapeless", "furnace", "blasting", "smoking", "campfire",
            "stonecutting", "brewing", "anvil");
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int INPUT = 20, SECOND = 22, RESULT = 24, SAVE = 49, CANCEL = 45;

    private final SmpCore plugin;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final List<NamespacedKey> brewing = new ArrayList<>();

    public CustomRecipes(SmpCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTask(plugin, this::registerAll);
    }

    private YamlStore store() {
        return plugin.data().store("recipes");
    }

    public List<String> ids() {
        ConfigurationSection s = store().yaml().getConfigurationSection("recipes");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    private static String enc(ItemStack i) {
        return i == null || i.getType().isAir() ? "" : Base64.getEncoder().encodeToString(i.serializeAsBytes());
    }

    private static ItemStack dec(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Items with custom names/lore/etc must match exactly; plain items match by type. */
    private static RecipeChoice choice(ItemStack i) {
        ItemStack one = i.asOne();
        return one.hasItemMeta() && !one.isSimilar(new ItemStack(one.getType()))
                ? new RecipeChoice.ExactChoice(one) : new RecipeChoice.MaterialChoice(one.getType());
    }

    // ------------------------------------------------------------------ registration

    public void registerAll() {
        for (NamespacedKey k : registered) Bukkit.removeRecipe(k, false);
        registered.clear();
        for (NamespacedKey k : brewing) Bukkit.getPotionBrewer().removePotionMix(k);
        brewing.clear();
        ConfigurationSection s = store().yaml().getConfigurationSection("recipes");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                try {
                    register(id, s.getConfigurationSection(id));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Custom recipe " + id + " is invalid: " + e.getMessage());
                }
            }
        }
        Bukkit.updateRecipes();
    }

    private void register(String id, ConfigurationSection r) {
        String type = r.getString("type", "shaped");
        ItemStack result = dec(r.getString("result"));
        if (result == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "custom_" + id);
        List<String> ing = r.getStringList("ingredients");
        switch (type) {
            case "shaped" -> {
                char[] letters = "ABCDEFGHI".toCharArray();
                String[] rows = new String[3];
                for (int row = 0; row < 3; row++) {
                    StringBuilder b = new StringBuilder();
                    for (int col = 0; col < 3; col++) {
                        int idx = row * 3 + col;
                        b.append(idx < ing.size() && dec(ing.get(idx)) != null ? letters[idx] : ' ');
                    }
                    rows[row] = b.toString();
                }
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(trim(rows));
                for (int i = 0; i < Math.min(9, ing.size()); i++) {
                    ItemStack it = dec(ing.get(i));
                    if (it != null) recipe.setIngredient(letters[i], choice(it));
                }
                add(recipe, key);
            }
            case "shapeless" -> {
                ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                for (String s : ing) {
                    ItemStack it = dec(s);
                    if (it != null) recipe.addIngredient(choice(it));
                }
                add(recipe, key);
            }
            case "furnace", "blasting", "smoking", "campfire" -> {
                ItemStack input = dec(ing.isEmpty() ? null : ing.get(0));
                if (input == null) return;
                float xp = (float) r.getDouble("xp", 0.5);
                int time = r.getInt("time", type.equals("furnace") || type.equals("campfire") ? 200 : 100);
                switch (type) {
                    case "blasting" -> add(new BlastingRecipe(key, result, choice(input), xp, time), key);
                    case "smoking" -> add(new SmokingRecipe(key, result, choice(input), xp, time), key);
                    case "campfire" -> add(new CampfireRecipe(key, result, choice(input), xp, time * 3), key);
                    default -> add(new FurnaceRecipe(key, result, choice(input), xp, time), key);
                }
            }
            case "stonecutting" -> {
                ItemStack input = dec(ing.isEmpty() ? null : ing.get(0));
                if (input != null) add(new StonecuttingRecipe(key, result, choice(input)), key);
            }
            case "brewing" -> {
                ItemStack input = dec(ing.isEmpty() ? null : ing.get(0));
                ItemStack ingredient = dec(ing.size() < 2 ? null : ing.get(1));
                if (input == null || ingredient == null) return;
                Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, result, choice(input), choice(ingredient)));
                brewing.add(key);
            }
            default -> {
                // anvil recipes are handled in onAnvil
            }
        }
    }

    private void add(org.bukkit.inventory.Recipe recipe, NamespacedKey key) {
        if (Bukkit.addRecipe(recipe, false)) registered.add(key);
    }

    private static String[] trim(String[] rows) {
        List<String> out = new ArrayList<>();
        for (String r : rows) if (!r.isBlank()) out.add(r);
        if (out.isEmpty()) out.add("A");
        int minCol = 3, maxCol = -1;
        for (String r : out) {
            for (int c = 0; c < 3; c++) {
                if (r.charAt(c) != ' ') {
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        List<String> cut = new ArrayList<>();
        for (String r : out) cut.add(r.substring(minCol, maxCol + 1));
        return cut.toArray(new String[0]);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack left = e.getInventory().getFirstItem();
        ItemStack right = e.getInventory().getSecondItem();
        if (left == null || right == null) return;
        ConfigurationSection s = store().yaml().getConfigurationSection("recipes");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r == null || !"anvil".equals(r.getString("type"))) continue;
            List<String> ing = r.getStringList("ingredients");
            ItemStack a = dec(ing.isEmpty() ? null : ing.get(0));
            ItemStack b = dec(ing.size() < 2 ? null : ing.get(1));
            if (a == null || b == null) continue;
            if (matches(a, left) && matches(b, right) && left.getAmount() >= a.getAmount() && right.getAmount() >= b.getAmount()) {
                e.setResult(dec(r.getString("result")));
                e.getView().setRepairCost(Math.max(1, r.getInt("cost", 5)));
                return;
            }
        }
    }

    private static boolean matches(ItemStack want, ItemStack have) {
        return want.hasItemMeta() && !want.asOne().isSimilar(new ItemStack(want.getType()))
                ? want.isSimilar(have) : want.getType() == have.getType();
    }

    // ------------------------------------------------------------------ editor

    public void openEditor(Player p, String id, String type) {
        String t = type.toLowerCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            Msg.error(p, "Type must be one of: " + String.join(", ", TYPES));
            return;
        }
        boolean grid = t.equals("shaped") || t.equals("shapeless");
        boolean two = t.equals("brewing") || t.equals("anvil");
        ChestGui gui = new ChestGui(6, "<dark_gray>Recipe: " + Msg.escape(id) + " (" + t + ")", true);
        ItemStack pane = Items.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        java.util.Set<Integer> open = new java.util.HashSet<>();
        if (grid) {
            for (int g : GRID) open.add(g);
        } else {
            open.add(INPUT);
            if (two) open.add(SECOND);
        }
        open.add(RESULT);
        for (int i = 0; i < 54; i++) {
            if (!open.contains(i)) gui.button(i, pane, x -> { });
        }
        gui.button(RESULT - 1, Items.icon(Material.ARROW, "<white>→ Result", "<gray>Put the result on the right"), x -> { });
        if (!grid) {
            gui.button(INPUT - 9, Items.icon(Material.HOPPER, "<white>Input", "<gray>" + inputHint(t, true)), x -> { });
            if (two) gui.button(SECOND - 9, Items.icon(Material.HOPPER, "<white>Second input", "<gray>" + inputHint(t, false)), x -> { });
        }
        // load existing values
        ConfigurationSection existing = store().yaml().getConfigurationSection("recipes." + id);
        if (existing != null) {
            List<String> ing = existing.getStringList("ingredients");
            int[] slots = grid ? GRID : two ? new int[]{INPUT, SECOND} : new int[]{INPUT};
            for (int i = 0; i < Math.min(slots.length, ing.size()); i++) gui.set(slots[i], dec(ing.get(i)));
            gui.set(RESULT, dec(existing.getString("result")));
        }
        boolean[] saved = {false};
        gui.button(SAVE, Items.icon(Material.LIME_CONCRETE, "<green><b>Save Recipe", "<gray>Registers it right away"), x -> {
            Inventory inv = gui.getInventory();
            ItemStack result = inv.getItem(RESULT);
            if (result == null || result.getType().isAir()) {
                Msg.error(x, "Put a result item in the result slot.");
                return;
            }
            List<String> ing = new ArrayList<>();
            int[] slots = grid ? GRID : two ? new int[]{INPUT, SECOND} : new int[]{INPUT};
            boolean any = false;
            for (int slot : slots) {
                ItemStack it = inv.getItem(slot);
                ing.add(enc(it));
                if (it != null && !it.getType().isAir()) any = true;
            }
            if (!any) {
                Msg.error(x, "Add at least one ingredient.");
                return;
            }
            String path = "recipes." + id;
            store().set(path + ".type", t);
            store().set(path + ".result", enc(result));
            store().set(path + ".ingredients", ing);
            store().set(path + ".created-by", x.getName());
            store().save();
            saved[0] = true;
            registerAll();
            x.closeInventory();
            Msg.ok(x, "Recipe <white>" + Msg.escape(id) + "</white> saved and active. See it in /recipes.");
        });
        gui.button(CANCEL, Items.icon(Material.BARRIER, "<red>Cancel"), HumanClose::close);
        gui.onClose(inv -> {
            // the recipe keeps its own copies - hand the template items back
            Msg.actionBar(p, saved[0] ? "<green>Recipe saved" : "<gray>Editor closed");
            for (int slot : open) {
                ItemStack it = inv.getItem(slot);
                if (it != null && !it.getType().isAir()) {
                    p.getInventory().addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                }
            }
        });
        gui.open(p);
        Msg.send(p, "<gray>Place items in the open slots. Items with custom names must match exactly; plain items match by type.");
    }

    private static String inputHint(String type, boolean first) {
        return switch (type) {
            case "brewing" -> first ? "The potion that goes in the bottle slots" : "The ingredient at the top";
            case "anvil" -> first ? "Left anvil slot" : "Right anvil slot";
            case "stonecutting" -> "The block to cut";
            default -> "The item to cook";
        };
    }

    public boolean delete(String id) {
        if (!store().yaml().isSet("recipes." + id)) return false;
        store().set("recipes." + id, null);
        store().save();
        registerAll();
        return true;
    }

    public String type(String id) {
        return store().yaml().getString("recipes." + id + ".type", "?");
    }

    private static final class HumanClose {
        static void close(Player p) {
            p.closeInventory();
        }
    }
}
