package dev.smpcore.inspector;

import dev.smpcore.SmpCore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CoreProtect-style logging: who placed/broke what, container transactions,
 * an inspect mode, lookups and rollback/restore.
 */
public final class Inspector implements Listener {
    private static final int PER_PAGE = 8;
    private static final int APPLY_PER_TICK = 2000;

    private final SmpCore plugin;
    private final InspectorDb db;
    private final Set<UUID> inspecting = new HashSet<>();
    private final Map<UUID, Snapshot> openContainers = new HashMap<>();
    private final Map<String, LastLookup> lastLookups = new HashMap<>();

    private record Snapshot(Location location, Map<String, Integer> counts, Map<String, ItemStack> samples) {
    }

    private record LastLookup(String title, List<InspectorDb.Entry> entries) {
    }

    public Inspector(SmpCore plugin) {
        this.plugin = plugin;
        this.db = new InspectorDb(plugin.getLogger());
        if (db.open(new File(plugin.getDataFolder(), "data/inspector.db"))) {
            long cutoff = System.currentTimeMillis() - Settings.INSPECTOR_RETENTION.asInt() * 86_400_000L;
            db.purge(cutoff).thenAccept(n -> {
                if (n > 0) plugin.getLogger().info("Inspector purged " + n + " old log entries.");
            });
        }
        Bukkit.getScheduler().runTaskTimer(plugin, db::flush, 40L, 40L);
    }

    public InspectorDb db() {
        return db;
    }

    public void shutdown() {
        db.close();
    }

    public boolean toggleInspect(Player p) {
        if (!inspecting.remove(p.getUniqueId())) {
            inspecting.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    private boolean logging() {
        return Settings.INSPECTOR_ENABLED.on() && db.isOpen();
    }

    // ------------------------------------------------------------------ logging

    private void logBlock(String user, Block b, int action, BlockData oldData, BlockData newData) {
        db.queue(new InspectorDb.Entry(0, System.currentTimeMillis(), user, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0, action,
                oldData == null ? "minecraft:air" : oldData.getAsString(), newData == null ? "minecraft:air" : newData.getAsString(),
                null, 0, null, false));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!logging()) return;
        logBlock(e.getPlayer().getName(), e.getBlock(), InspectorDb.BREAK, e.getBlock().getBlockData(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!logging()) return;
        logBlock(e.getPlayer().getName(), e.getBlockPlaced(), InspectorDb.PLACE, e.getBlockReplacedState().getBlockData(), e.getBlockPlaced().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!logging()) return;
        Material liquid = e.getBucket() == Material.LAVA_BUCKET ? Material.LAVA : e.getBucket() == Material.WATER_BUCKET ? Material.WATER : null;
        if (liquid == null) return;
        logBlock(e.getPlayer().getName(), e.getBlock(), InspectorDb.BUCKET, e.getBlock().getBlockData(), liquid.createBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!logging()) return;
        logBlock(e.getPlayer().getName(), e.getBlock(), InspectorDb.BUCKET, e.getBlock().getBlockData(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!logging() || !Settings.INSPECTOR_EXPLOSIONS.on()) return;
        Entity entity = e.getEntity();
        String user = "#" + entity.getType().getKey().getKey();
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            user = p.getName();
        }
        for (Block b : e.blockList()) {
            logBlock(user, b, InspectorDb.EXPLODE, b.getBlockData(), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!logging() || !Settings.INSPECTOR_EXPLOSIONS.on()) return;
        String user = "#" + e.getExplodedBlockState().getType().getKey().getKey();
        for (Block b : e.blockList()) {
            logBlock(user, b, InspectorDb.EXPLODE, b.getBlockData(), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!logging() || !Settings.INSPECTOR_EXPLOSIONS.on()) return;
        logBlock("#fire", e.getBlock(), InspectorDb.BURN, e.getBlock().getBlockData(), null);
    }

    // ------------------------------------------------------------------ containers

    private static Location containerLocation(InventoryHolder holder) {
        if (holder instanceof DoubleChest dc && dc.getLeftSide() instanceof Container left) {
            return left.getLocation();
        }
        if (holder instanceof BlockState state) {
            return state.getLocation();
        }
        return null;
    }

    private static String key(ItemStack item) {
        ItemStack one = item.asOne();
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    private static Snapshot snapshot(Location loc, Inventory inv) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ItemStack> samples = new HashMap<>();
        for (ItemStack i : inv.getContents()) {
            if (i == null || i.getType().isAir()) continue;
            String k = key(i);
            counts.merge(k, i.getAmount(), Integer::sum);
            samples.putIfAbsent(k, i.asOne());
        }
        return new Snapshot(loc, counts, samples);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!logging() || !Settings.INSPECTOR_CONTAINERS.on() || !(e.getPlayer() instanceof Player p)) return;
        Location loc = containerLocation(e.getInventory().getHolder(false));
        if (loc == null) return;
        openContainers.put(p.getUniqueId(), snapshot(loc, e.getInventory()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        Snapshot before = openContainers.remove(e.getPlayer().getUniqueId());
        if (before == null || !logging()) return;
        Snapshot after = snapshot(before.location(), e.getInventory());
        Set<String> keys = new HashSet<>(before.counts().keySet());
        keys.addAll(after.counts().keySet());
        long now = System.currentTimeMillis();
        Location l = before.location();
        for (String k : keys) {
            int diff = after.counts().getOrDefault(k, 0) - before.counts().getOrDefault(k, 0);
            if (diff == 0) continue;
            ItemStack sample = after.samples().containsKey(k) ? after.samples().get(k) : before.samples().get(k);
            db.queue(new InspectorDb.Entry(0, now, e.getPlayer().getName(), l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                    1, diff > 0 ? InspectorDb.ADDED : InspectorDb.REMOVED, null, null, sample.getType().getKey().toString(), Math.abs(diff),
                    sample.serializeAsBytes(), false));
        }
    }

    // ------------------------------------------------------------------ inspect mode

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInspect(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!inspecting.contains(p.getUniqueId()) || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        e.setCancelled(true);
        Block b = e.getClickedBlock();
        InspectorDb.Query q = new InspectorDb.Query();
        q.world = b.getWorld().getName();
        q.limit = 200;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b.getState() instanceof Container) {
            Location l = containerLocation(((Container) b.getState()).getInventory().getHolder(false));
            if (l == null) l = b.getLocation();
            q.x = l.getBlockX();
            q.y = l.getBlockY();
            q.z = l.getBlockZ();
            q.kind = 1;
            runLookup(p, q, "Container " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        } else {
            Block target = e.getAction() == Action.RIGHT_CLICK_BLOCK ? b.getRelative(e.getBlockFace()) : b;
            q.x = target.getX();
            q.y = target.getY();
            q.z = target.getZ();
            q.kind = 0;
            runLookup(p, q, "Block " + target.getX() + " " + target.getY() + " " + target.getZ());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInspectBreak(BlockBreakEvent e) {
        if (inspecting.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ lookup output

    public void runLookup(CommandSender sender, InspectorDb.Query q, String title) {
        if (!db.isOpen()) {
            Msg.error(sender, "The inspector database isn't available (see console).");
            return;
        }
        db.lookup(q).thenAccept(entries -> Bukkit.getScheduler().runTask(plugin, () -> {
            String key = sender instanceof Player p ? p.getUniqueId().toString() : "console";
            lastLookups.put(key, new LastLookup(title, entries));
            showPage(sender, 1);
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> Msg.error(sender, "Lookup failed: " + ex.getMessage()));
            return null;
        });
    }

    public void showPage(CommandSender sender, int page) {
        String key = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        LastLookup last = lastLookups.get(key);
        if (last == null) {
            Msg.error(sender, "Run a lookup first.");
            return;
        }
        List<InspectorDb.Entry> entries = last.entries();
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, pages));
        sender.sendMessage(Msg.mm("<dark_gray>----- <#38bdf8>🔎 Inspector</#38bdf8> <gray>" + Msg.escape(last.title()) + "</gray> -----"));
        if (entries.isEmpty()) {
            sender.sendMessage(Msg.mm("<gray>No data found."));
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = (page - 1) * PER_PAGE; i < Math.min(entries.size(), page * PER_PAGE); i++) {
            sender.sendMessage(format(entries.get(i), now));
        }
        Component nav = Msg.mm("<gray>Page <white>" + page + "</white>/" + pages + " ");
        if (page > 1) {
            nav = nav.append(Msg.mm("<aqua>[◀ Prev]").clickEvent(ClickEvent.runCommand("/inspector page " + (page - 1))));
        }
        if (page < pages) {
            nav = nav.append(Msg.mm(" <aqua>[Next ▶]").clickEvent(ClickEvent.runCommand("/inspector page " + (page + 1))));
        }
        sender.sendMessage(nav);
    }

    private static String shortData(String data) {
        if (data == null) return "?";
        String s = data.startsWith("minecraft:") ? data.substring(10) : data;
        int bracket = s.indexOf('[');
        return bracket >= 0 ? s.substring(0, bracket) : s;
    }

    private static Component format(InspectorDb.Entry e, long now) {
        String ago = Msg.duration(now - e.time()) + " ago";
        String user = Msg.escape(e.user());
        String what;
        if (e.kind() == 1) {
            String mat = shortData(e.material());
            what = (e.action() == InspectorDb.ADDED ? "<green>+ added" : "<red>- removed") + " <white>" + e.amount() + "x " + Msg.escape(mat);
        } else {
            String oldB = shortData(e.oldData());
            String newB = shortData(e.newData());
            what = switch (e.action()) {
                case InspectorDb.PLACE -> "<green>placed <white>" + Msg.escape(newB);
                case InspectorDb.EXPLODE -> "<gold>exploded <white>" + Msg.escape(oldB);
                case InspectorDb.BURN -> "<gold>burned <white>" + Msg.escape(oldB);
                case InspectorDb.BUCKET -> newB.equals("air") ? "<aqua>emptied <white>" + Msg.escape(oldB) : "<aqua>poured <white>" + Msg.escape(newB);
                default -> "<red>broke <white>" + Msg.escape(oldB);
            };
        }
        String line = "<gray>" + ago + " <dark_gray>-</dark_gray> <#38bdf8>" + user + "</#38bdf8> " + what;
        if (e.rolledBack()) line = "<st>" + line + "</st> <dark_gray>(rolled back)";
        String loc = e.world() + " " + e.x() + " " + e.y() + " " + e.z();
        return Msg.mm(line).hoverEvent(HoverEvent.showText(Msg.mm("<gray>" + loc + "<newline><dark_gray>Click to teleport")))
                .clickEvent(ClickEvent.runCommand("/inspector tp " + e.world() + " " + e.x() + " " + e.y() + " " + e.z()));
    }

    // ------------------------------------------------------------------ rollback / restore

    public void rollback(CommandSender sender, InspectorDb.Query q, boolean restore) {
        if (!db.isOpen()) {
            Msg.error(sender, "The inspector database isn't available.");
            return;
        }
        q.rolled = restore;
        q.ascending = restore;
        q.limit = 500_000;
        Msg.send(sender, "<gray>" + (restore ? "Restoring" : "Rolling back") + "... searching logs.");
        db.lookup(q).thenAccept(entries -> Bukkit.getScheduler().runTask(plugin, () -> apply(sender, entries, restore)))
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> Msg.error(sender, "Rollback failed: " + ex.getMessage()));
                    return null;
                });
    }

    private void apply(CommandSender sender, List<InspectorDb.Entry> entries, boolean restore) {
        if (entries.isEmpty()) {
            Msg.send(sender, "<gray>Nothing to " + (restore ? "restore" : "roll back") + ".");
            return;
        }
        long started = System.currentTimeMillis();
        int[] index = {0};
        int[] blocks = {0};
        int[] items = {0};
        List<Long> done = new ArrayList<>();
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(entries.size(), index[0] + APPLY_PER_TICK);
            for (int i = index[0]; i < end; i++) {
                InspectorDb.Entry e = entries.get(i);
                World w = Bukkit.getWorld(e.world());
                if (w == null) continue;
                Block b = w.getBlockAt(e.x(), e.y(), e.z());
                if (e.kind() == 0) {
                    String data = restore ? e.newData() : e.oldData();
                    try {
                        b.setBlockData(Bukkit.createBlockData(data), false);
                        blocks[0]++;
                        done.add(e.id());
                    } catch (IllegalArgumentException ignored) {
                        // unknown block data from another version
                    }
                } else if (b.getState() instanceof Container c && e.item() != null) {
                    ItemStack item = ItemStack.deserializeBytes(e.item());
                    item.setAmount(e.amount());
                    boolean putBack = (e.action() == InspectorDb.REMOVED) != restore;
                    if (putBack) {
                        c.getInventory().addItem(item);
                    } else {
                        c.getInventory().removeItem(item);
                    }
                    items[0]++;
                    done.add(e.id());
                }
            }
            index[0] = end;
            if (index[0] >= entries.size()) {
                task.cancel();
                db.markRolled(done, !restore);
                Msg.ok(sender, (restore ? "Restore" : "Rollback") + " complete: <white>" + blocks[0] + "</white> blocks, <white>" + items[0]
                        + "</white> item changes in " + Msg.duration(System.currentTimeMillis() - started) + ".");
            }
        }, 1L, 1L);
    }
}
