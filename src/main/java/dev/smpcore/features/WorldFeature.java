package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * World mechanics: game rules, render distance, fire, falling blocks,
 * tree feller, veinminer, sitting, block decay, void protection and more.
 */
public final class WorldFeature implements Listener {
    private final SmpCore plugin;
    private final NamespacedKey seatKey;
    private final Set<UUID> chopping = new HashSet<>();

    public WorldFeature(SmpCore plugin) {
        this.plugin = plugin;
        this.seatKey = new NamespacedKey(plugin, "seat");

        Settings.ONE_PLAYER_SLEEP.onChange(v -> applyGameRules());
        Settings.LOCATOR_BAR.onChange(v -> applyGameRules());
        Settings.VIEW_DISTANCE.onChange(v -> applyDistances());
        Settings.SIMULATION_DISTANCE.onChange(v -> applyDistances());
        Settings.TICK_RATE.onChange(v -> Bukkit.getServerTickManager().setTickRate(v.floatValue()));
        Settings.ANTI_XRAY.requiresRestart();
        Settings.ANTI_XRAY.onChange(v -> writePaperWorldDefaults());
        Settings.ANTI_SEED_CRACKING.onChange(v -> {
            writePaperWorldDefaults();
            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        });

        Bukkit.getScheduler().runTaskTimer(plugin, this::antiStasis, 20L, 20L);
        // Clean up seats left over from a crash.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                    if (d.getPersistentDataContainer().has(seatKey) && d.getPassengers().isEmpty()) d.remove();
                }
            }
        });
    }

    // ------------------------------------------------------------------ world settings

    private void applyGameRules() {
        for (World w : Bukkit.getWorlds()) {
            applyGameRules(w);
        }
    }

    private void applyGameRules(World w) {
        w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, Settings.ONE_PLAYER_SLEEP.on() ? 0 : 100);
        w.setGameRule(GameRule.LOCATOR_BAR, Settings.LOCATOR_BAR.on());
    }

    private void applyDistances() {
        int view = Settings.VIEW_DISTANCE.asInt();
        int sim = Settings.SIMULATION_DISTANCE.asInt();
        for (World w : Bukkit.getWorlds()) {
            if (view >= 2) w.setViewDistance(view);
            if (sim >= 2) w.setSimulationDistance(sim);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        applyGameRules(e.getWorld());
        int view = Settings.VIEW_DISTANCE.asInt();
        if (view >= 2) e.getWorld().setViewDistance(view);
    }

    /** Anti-xray and feature seeds live in Paper's own world config; they apply after a restart. */
    private void writePaperWorldDefaults() {
        File file = new File(Bukkit.getWorldContainer(), "config/paper-world-defaults.yml");
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String mode = Settings.ANTI_XRAY.get();
            boolean xray = !mode.equals("off");
            boolean changed = false;
            if (yaml.getBoolean("anticheat.anti-xray.enabled") != xray) {
                yaml.set("anticheat.anti-xray.enabled", xray);
                changed = true;
            }
            if (xray && yaml.getInt("anticheat.anti-xray.engine-mode") != Integer.parseInt(mode)) {
                yaml.set("anticheat.anti-xray.engine-mode", Integer.parseInt(mode));
                changed = true;
            }
            boolean seeds = Settings.ANTI_SEED_CRACKING.on();
            if (yaml.getBoolean("feature-seeds.generate-random-seeds-for-all") != seeds) {
                yaml.set("feature-seeds.generate-random-seeds-for-all", seeds);
                changed = true;
            }
            if (changed) {
                yaml.save(file);
                plugin.getLogger().info("Updated paper-world-defaults.yml (anti-xray=" + mode + ", random feature seeds=" + seeds
                        + "). Restart the server to apply.");
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not update paper-world-defaults.yml", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSeed(PlayerCommandPreprocessEvent e) {
        if (!Settings.ANTI_SEED_CRACKING.on() || e.getPlayer().hasPermission("smpcore.bypass")) return;
        String cmd = e.getMessage().toLowerCase();
        if (cmd.equals("/seed") || cmd.startsWith("/seed ") || cmd.startsWith("/minecraft:seed")) {
            e.setCancelled(true);
            Msg.error(e.getPlayer(), "The seed is secret on this server.");
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent e) {
        if (Settings.ANTI_SEED_CRACKING.on() && !e.getPlayer().hasPermission("smpcore.bypass")) {
            e.getCommands().remove("seed");
            e.getCommands().remove("minecraft:seed");
        }
    }

    // ------------------------------------------------------------------ fire & falling blocks

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (!Settings.FIRE_SPREAD.on() && e.getNewState().getType() == Material.FIRE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!Settings.FIRE_SPREAD.on()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!Settings.FIRE_SPREAD.on() && (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent e) {
        if (Settings.NO_FALLING_BLOCKS.on() && e.getEntity() instanceof FallingBlock && e.getTo() == Material.AIR) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ tree feller & veinminer

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Material type = block.getType();

        if (Tag.LEAVES.isTagged(type) && p.getInventory().getItemInMainHand().getType() != Material.SHEARS) {
            maybeDropApple(block);
        }
        if (!p.isSneaking() || chopping.contains(p.getUniqueId())) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        String toolName = tool.getType().name();
        boolean tree = Settings.TREE_FELLER.on() && Tag.LOGS.isTagged(type) && toolName.endsWith("_AXE");
        boolean vein = Settings.VEINMINER.on() && isOre(type) && toolName.endsWith("_PICKAXE");
        if (!tree && !vein) return;

        List<Block> blocks = collect(block, tree);
        if (blocks.isEmpty()) return;
        chopping.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                for (Block b : blocks) {
                    ItemStack t = p.getInventory().getItemInMainHand();
                    if (t.getType().isAir() || !p.isOnline()) break;
                    p.breakBlock(b);
                }
            } finally {
                chopping.remove(p.getUniqueId());
            }
        });
    }

    public boolean isChopping(Player p) {
        return chopping.contains(p.getUniqueId());
    }

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private List<Block> collect(Block origin, boolean tree) {
        int max = Settings.VEIN_MAX.asInt();
        Material type = origin.getType();
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && out.size() < max) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (seen.contains(n)) continue;
                        boolean match = tree ? Tag.LOGS.isTagged(n.getType()) : sameOre(type, n.getType());
                        if (match) {
                            seen.add(n);
                            queue.add(n);
                            out.add(n);
                            if (out.size() >= max) return out;
                        }
                    }
                }
            }
        }
        return out;
    }

    private static boolean sameOre(Material a, Material b) {
        if (a == b) return true;
        String na = a.name().replace("DEEPSLATE_", "");
        String nb = b.name().replace("DEEPSLATE_", "");
        return na.equals(nb);
    }

    // ------------------------------------------------------------------ apples, decay, armor stands

    private void maybeDropApple(Block block) {
        double chance = Settings.APPLE_CHANCE.get();
        if (chance > 0 && ThreadLocalRandom.current().nextDouble(100) < chance) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.APPLE));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        maybeDropApple(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getPlayer().hasPermission("smpcore.bypass")) return;
        Block block = e.getBlockPlaced();
        Material type = block.getType();
        int seconds = Items.limitFor(Settings.BLOCK_DECAY.get(), type);
        if (seconds > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (block.getType() == type) {
                    block.setType(Material.AIR);
                }
            }, seconds * 20L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(EntityPlaceEvent e) {
        if (Settings.ARMOR_STAND_ARMS.on() && e.getEntity() instanceof ArmorStand stand) {
            stand.setArms(true);
        }
    }

    // ------------------------------------------------------------------ happy ghast speed

    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent e) {
        if (e.getMount() instanceof HappyGhast ghast) {
            AttributeInstance speed = ghast.getAttribute(Attribute.FLYING_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getDefaultValue() * Settings.HAPPY_GHAST_SPEED.get());
            }
        }
    }

    // ------------------------------------------------------------------ items

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        if (Settings.GLOWING_ITEMS.contains(item.getItemStack().getType().getKey().getKey())) {
            item.setGlowing(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewingStartEvent e) {
        if (Settings.INSTANT_BREWING.on()) {
            e.setBrewingTime(1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent e) {
        BookMeta meta = e.getNewBookMeta();
        int maxPages = Settings.BOOK_MAX_PAGES.asInt();
        int maxChars = Settings.BOOK_MAX_CHARS.asInt();
        List<Component> pages = new ArrayList<>(meta.pages());
        boolean trimmed = false;
        if (pages.size() > maxPages) {
            pages = new ArrayList<>(pages.subList(0, maxPages));
            trimmed = true;
        }
        for (int i = 0; i < pages.size(); i++) {
            String text = Msg.plain(pages.get(i));
            if (text.length() > maxChars) {
                pages.set(i, Component.text(text.substring(0, maxChars)));
                trimmed = true;
            }
        }
        if (trimmed) {
            meta.pages(pages);
            e.setNewBookMeta(meta);
            Msg.send(e.getPlayer(), "<yellow>Your book was trimmed to " + maxPages + " pages / " + maxChars + " characters per page.");
        }
    }

    // ------------------------------------------------------------------ protection: void, death drops, stasis

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent e) {
        if (!Settings.VOID_PROTECTION.on() || e.getCause() != EntityDamageEvent.DamageCause.VOID || !(e.getEntity() instanceof Player p)) {
            return;
        }
        if (p.getLocation().getY() > p.getWorld().getMinHeight() - 10) return;
        e.setCancelled(true);
        Location safe = findLand(p.getLocation());
        p.setFallDistance(0);
        p.setVelocity(p.getVelocity().zero());
        p.teleportAsync(safe);
        Msg.actionBar(p, "<aqua>Void protection saved you!");
    }

    private Location findLand(Location from) {
        World w = from.getWorld();
        int bx = from.getBlockX();
        int bz = from.getBlockZ();
        for (int r = 0; r <= 96; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = bx + dx;
                    int z = bz + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block top = w.getHighestBlockAt(x, z);
                    if (!top.getType().isAir() && top.getType().isSolid() && top.getY() > w.getMinHeight()) {
                        return top.getLocation().add(0.5, 1, 0.5);
                    }
                }
            }
        }
        return w.getSpawnLocation();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathDrops(PlayerDeathEvent e) {
        if (!Settings.IMMORTAL_DEATH_DROPS.on() || e.getKeepInventory()) return;
        List<ItemStack> drops = new ArrayList<>(e.getDrops());
        e.getDrops().clear();
        Location loc = e.getPlayer().getLocation();
        if (loc.getY() < loc.getWorld().getMinHeight()) {
            loc = findLand(loc);
        }
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType().isAir()) continue;
            Item item = loc.getWorld().dropItemNaturally(loc, stack);
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setPersistent(true);
        }
    }

    private void antiStasis() {
        if (!Settings.ANTI_STASIS.on()) return;
        int maxTicks = Settings.ANTI_STASIS_SECONDS.asInt() * 20;
        for (World w : Bukkit.getWorlds()) {
            for (EnderPearl pearl : w.getEntitiesByClass(EnderPearl.class)) {
                if (pearl.getTicksLived() > maxTicks) {
                    pearl.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ sit

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSitClick(PlayerInteractEvent e) {
        if (!Settings.SIT.on() || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null || p.isSneaking() || !p.getInventory().getItemInMainHand().getType().isAir() || p.isInsideVehicle()) return;
        if (e.getBlockFace() == BlockFace.DOWN || !b.getRelative(BlockFace.UP).isPassable()) return;
        boolean seat = false;
        if (b.getBlockData() instanceof Stairs stairs && stairs.getHalf() == Bisected.Half.BOTTOM) seat = true;
        if (b.getBlockData() instanceof Slab slab && slab.getType() == Slab.Type.BOTTOM) seat = true;
        if (!seat || p.getLocation().distanceSquared(b.getLocation().add(0.5, 0.5, 0.5)) > 9) return;
        e.setCancelled(true);
        sit(p, b.getLocation().add(0.5, 0.5, 0.5));
    }

    public void sit(Player p, Location at) {
        Location loc = at.clone();
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(0);
        ItemDisplay seat = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.getPersistentDataContainer().set(seatKey, PersistentDataType.BOOLEAN, true);
            d.setPersistent(false);
        });
        seat.addPassenger(p);
        Msg.actionBar(p, "<gray>Sneak to stand up");
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        Entity vehicle = e.getDismounted();
        if (vehicle instanceof ItemDisplay d && d.getPersistentDataContainer().has(seatKey)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                d.remove();
                if (e.getEntity() instanceof Player p && p.isOnline()) {
                    p.teleport(p.getLocation().add(0, 0.6, 0));
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Entity v = e.getPlayer().getVehicle();
        if (v instanceof ItemDisplay d && d.getPersistentDataContainer().has(seatKey)) {
            d.removePassenger(e.getPlayer());
            d.remove();
        }
    }

    @EventHandler
    public void onLoaded(PlayerClientLoadedWorldEvent e) {
        // keep render distance in sync for players arriving after a change
        int view = Settings.VIEW_DISTANCE.asInt();
        if (view >= 2 && e.getPlayer().getWorld().getViewDistance() != view) {
            e.getPlayer().getWorld().setViewDistance(view);
        }
    }
}
