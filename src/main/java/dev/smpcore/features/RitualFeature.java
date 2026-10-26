package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.DataManager;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rituals: crafting a powerful item starts a public countdown at the crafting spot.
 * Everyone sees a boss bar with the location; the item drops when the ritual finishes.
 */
public final class RitualFeature implements Listener {
    private final SmpCore plugin;
    private final Map<String, Ritual> rituals = new LinkedHashMap<>();

    public static final class Ritual {
        final String id;
        final String owner;
        final ItemStack item;
        final Location location;
        final long endsAt;
        final long duration;
        BossBar bar;
        ItemDisplay display;

        Ritual(String id, String owner, ItemStack item, Location location, long endsAt, long duration) {
            this.id = id;
            this.owner = owner;
            this.item = item;
            this.location = location;
            this.endsAt = endsAt;
            this.duration = duration;
        }

        public String owner() { return owner; }
        public ItemStack item() { return item; }
        public Location location() { return location; }
        public long remaining() { return Math.max(0, endsAt - System.currentTimeMillis()); }
    }

    public RitualFeature(SmpCore plugin) {
        this.plugin = plugin;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public Collection<Ritual> active() {
        return rituals.values();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getRecipe().getResult();
        if (!Settings.RITUAL_ITEMS.contains(result.getType().getKey().getKey())) return;
        e.setCancelled(true);
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            if (it == null || it.getType().isAir()) continue;
            if (it.getAmount() > 1) {
                it.setAmount(it.getAmount() - 1);
            } else {
                Material remainder = it.getType().getCraftingRemainingItem();
                matrix[i] = remainder == null ? null : new ItemStack(remainder);
            }
        }
        inv.setMatrix(matrix);
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
        start(p.getName(), result.clone(), p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
    }

    public void start(String owner, ItemStack item, Location loc) {
        long duration = Settings.RITUAL_SECONDS.asInt() * 1000L;
        Ritual r = new Ritual(UUID.randomUUID().toString().substring(0, 8), owner, item, loc, System.currentTimeMillis() + duration, duration);
        rituals.put(r.id, r);
        spawnVisuals(r);
        save();
        String coords = Settings.RITUAL_BROADCAST_COORDS.on()
                ? " at <white>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</white> in " + loc.getWorld().getName()
                : "";
        Bukkit.broadcast(Msg.prefixed("<light_purple>✦ <white>" + Msg.escape(owner) + "</white> started a ritual for <gold>"
                + Msg.pretty(item.getType().name()) + "</gold>" + coords + "! <gray>(" + Msg.duration(duration) + ")"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.7f);
        }
    }

    private void spawnVisuals(Ritual r) {
        r.bar = BossBar.bossBar(Msg.mm(""), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(r.bar);
        }
        if (r.location.isChunkLoaded()) {
            r.display = r.location.getWorld().spawn(r.location.clone().add(0, 1.6, 0), ItemDisplay.class, d -> {
                d.setItemStack(r.item);
                d.setBillboard(Display.Billboard.FIXED);
                d.setPersistent(false);
                d.setGlowing(true);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.2f), new AxisAngle4f()));
                d.setInterpolationDuration(10);
            });
        }
    }

    private void tick() {
        Iterator<Ritual> it = rituals.values().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Ritual r = it.next();
            long left = r.remaining();
            if (r.display == null || !r.display.isValid()) {
                if (r.location.isChunkLoaded()) {
                    if (r.display != null) r.display.remove();
                    r.display = r.location.getWorld().spawn(r.location.clone().add(0, 1.6, 0), ItemDisplay.class, d -> {
                        d.setItemStack(r.item);
                        d.setPersistent(false);
                        d.setGlowing(true);
                    });
                }
            } else {
                float angle = (float) ((System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2);
                r.display.setInterpolationDelay(0);
                r.display.setTransformation(new Transformation(new Vector3f(0, (float) Math.sin(angle) * 0.15f, 0),
                        new AxisAngle4f(angle, 0, 1, 0), new Vector3f(1.2f), new AxisAngle4f()));
            }
            if (r.location.isChunkLoaded()) {
                r.location.getWorld().spawnParticle(Particle.END_ROD, r.location.clone().add(0, 1.6, 0), 6, 0.3, 0.3, 0.3, 0.02);
                r.location.getWorld().spawnParticle(Particle.ENCHANT, r.location.clone().add(0, 0.5, 0), 20, 1.5, 0.2, 1.5, 0.5);
            }
            String where = Settings.RITUAL_BROADCAST_COORDS.on()
                    ? " <gray>@ " + r.location.getBlockX() + " " + r.location.getBlockY() + " " + r.location.getBlockZ() : "";
            r.bar.name(Msg.mm("<light_purple>✦ Ritual: <gold>" + Msg.pretty(r.item.getType().name()) + "</gold>" + where
                    + " <white>" + Msg.duration(left)));
            r.bar.progress((float) Math.max(0, Math.min(1, left / (double) Math.max(1, r.duration))));
            if (left <= 0) {
                finish(r);
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    private void finish(Ritual r) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(r.bar);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        if (r.display != null) r.display.remove();
        Location drop = r.location.clone().add(0, 1, 0);
        drop.getWorld().strikeLightningEffect(r.location);
        drop.getWorld().dropItem(drop, r.item, i -> {
            i.setGlowing(true);
            i.setInvulnerable(true);
            i.setUnlimitedLifetime(true);
        });
        Bukkit.broadcast(Msg.prefixed("<light_purple>✦ The ritual is complete! <gold>" + Msg.pretty(r.item.getType().name())
                + "</gold> has appeared."));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        for (Ritual r : rituals.values()) {
            if (r.bar != null) e.getPlayer().showBossBar(r.bar);
        }
    }

    // ------------------------------------------------------------------ persistence

    private void save() {
        YamlStore store = plugin.data().store("rituals");
        store.set("rituals", null);
        for (Ritual r : rituals.values()) {
            String path = "rituals." + r.id;
            store.set(path + ".owner", r.owner);
            store.set(path + ".item", Base64.getEncoder().encodeToString(r.item.serializeAsBytes()));
            store.set(path + ".ends", r.endsAt);
            store.set(path + ".duration", r.duration);
            DataManager.writeLocation(store.yaml(), path + ".loc", r.location);
        }
        store.markDirty();
    }

    private void load() {
        YamlStore store = plugin.data().store("rituals");
        ConfigurationSection sec = store.yaml().getConfigurationSection("rituals");
        if (sec == null) return;
        List<Ritual> loaded = new ArrayList<>();
        for (String id : sec.getKeys(false)) {
            try {
                Location loc = DataManager.readLocation(sec, id + ".loc");
                if (loc == null) continue;
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(sec.getString(id + ".item", "")));
                loaded.add(new Ritual(id, sec.getString(id + ".owner", "?"), item, loc, sec.getLong(id + ".ends"), sec.getLong(id + ".duration", 1)));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping broken ritual " + id);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Ritual r : loaded) {
                rituals.put(r.id, r);
                spawnVisuals(r);
            }
        });
    }

    public void shutdown() {
        for (Ritual r : rituals.values()) {
            if (r.display != null) r.display.remove();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (r.bar != null) p.hideBossBar(r.bar);
            }
        }
    }
}
