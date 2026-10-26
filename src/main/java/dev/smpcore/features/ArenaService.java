package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Snapshot an arena and restore it later, a few thousand blocks per tick.
 * Works without WorldEdit/FAWE.
 */
public final class ArenaService {
    public static final int MAX_VOLUME = 4_000_000;
    private static final int BLOCKS_PER_TICK = 8000;

    private final SmpCore plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<String, BukkitTask> running = new HashMap<>();
    private final Map<String, Long> lastAuto = new HashMap<>();

    public ArenaService(SmpCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoRepair, 20L * 10, 20L * 10);
    }

    private YamlStore store() {
        return plugin.data().store("arenas");
    }

    private File file(String name) {
        return new File(plugin.data().folder(), "arenas/" + name.toLowerCase(Locale.ROOT) + ".arena");
    }

    public Location[] selection(UUID player) {
        return selections.computeIfAbsent(player, k -> new Location[2]);
    }

    public List<String> names() {
        ConfigurationSection s = store().yaml().getConfigurationSection("arenas");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public boolean exists(String name) {
        return store().yaml().isSet("arenas." + name.toLowerCase(Locale.ROOT));
    }

    public void create(CommandSender sender, String name, Location a, Location b) throws IOException {
        if (a == null || b == null || a.getWorld() != b.getWorld()) {
            Msg.error(sender, "Set both corners in the same world first (/arenarepair pos1 and pos2).");
            return;
        }
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_VOLUME) {
            Msg.error(sender, "That area is too big (" + volume + " blocks, max " + MAX_VOLUME + ").");
            return;
        }
        World w = a.getWorld();
        Map<String, Integer> palette = new HashMap<>();
        List<String> paletteList = new ArrayList<>();
        int[] data = new int[(int) volume];
        int i = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String s = w.getBlockAt(x, y, z).getBlockData().getAsString();
                    Integer idx = palette.get(s);
                    if (idx == null) {
                        idx = paletteList.size();
                        palette.put(s, idx);
                        paletteList.add(s);
                    }
                    data[i++] = idx;
                }
            }
        }
        File f = file(name);
        f.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
            out.writeInt(paletteList.size());
            for (String s : paletteList) out.writeUTF(s);
            out.writeInt(data.length);
            for (int v : data) out.writeInt(v);
        }
        String path = "arenas." + name.toLowerCase(Locale.ROOT);
        store().set(path + ".world", w.getName());
        store().set(path + ".min", List.of(minX, minY, minZ));
        store().set(path + ".max", List.of(maxX, maxY, maxZ));
        store().set(path + ".auto-seconds", store().yaml().getInt(path + ".auto-seconds", 0));
        store().save();
        Msg.ok(sender, "Saved arena <white>" + Msg.escape(name) + "</white> (" + volume + " blocks, " + paletteList.size() + " block types).");
    }

    public void repair(CommandSender sender, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!exists(key)) {
            if (sender != null) Msg.error(sender, "Arena " + Msg.escape(name) + " doesn't exist.");
            return;
        }
        if (running.containsKey(key)) {
            if (sender != null) Msg.error(sender, "That arena is already being repaired.");
            return;
        }
        String path = "arenas." + key;
        World w = Bukkit.getWorld(store().yaml().getString(path + ".world", ""));
        List<Integer> min = store().yaml().getIntegerList(path + ".min");
        List<Integer> max = store().yaml().getIntegerList(path + ".max");
        if (w == null || min.size() != 3 || max.size() != 3) {
            if (sender != null) Msg.error(sender, "Arena data is broken (world missing?).");
            return;
        }
        BlockData[] palette;
        int[] data;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file(key))))) {
            int n = in.readInt();
            palette = new BlockData[n];
            for (int i = 0; i < n; i++) palette[i] = Bukkit.createBlockData(in.readUTF());
            data = new int[in.readInt()];
            for (int i = 0; i < data.length; i++) data[i] = in.readInt();
        } catch (IOException | IllegalArgumentException e) {
            if (sender != null) Msg.error(sender, "Could not read arena file: " + e.getMessage());
            return;
        }
        int minX = min.get(0), minY = min.get(1), minZ = min.get(2);
        int sy = max.get(1) - minY + 1, sz = max.get(2) - minZ + 1;
        int[] cursor = {0};
        int[] changed = {0};
        long started = System.currentTimeMillis();
        running.put(key, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int end = Math.min(data.length, cursor[0] + BLOCKS_PER_TICK);
            for (int i = cursor[0]; i < end; i++) {
                int x = minX + i / (sy * sz);
                int rem = i % (sy * sz);
                int y = minY + rem / sz;
                int z = minZ + rem % sz;
                Block b = w.getBlockAt(x, y, z);
                BlockData want = palette[data[i]];
                if (!b.getBlockData().equals(want)) {
                    b.setBlockData(want, false);
                    changed[0]++;
                }
            }
            cursor[0] = end;
            if (cursor[0] >= data.length) {
                BukkitTask t = running.remove(key);
                if (t != null) t.cancel();
                if (sender != null) {
                    Msg.ok(sender, "Arena <white>" + Msg.escape(name) + "</white> repaired: " + changed[0] + " blocks in "
                            + Msg.duration(System.currentTimeMillis() - started) + ".");
                }
            }
        }, 1L, 1L));
    }

    public boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!exists(key)) return false;
        store().set("arenas." + key, null);
        store().save();
        file(key).delete();
        return true;
    }

    public void setAuto(String name, int seconds) {
        store().set("arenas." + name.toLowerCase(Locale.ROOT) + ".auto-seconds", seconds);
        store().save();
    }

    private void autoRepair() {
        ConfigurationSection s = store().yaml().getConfigurationSection("arenas");
        if (s == null) return;
        long now = System.currentTimeMillis();
        for (String key : s.getKeys(false)) {
            int seconds = s.getInt(key + ".auto-seconds", 0);
            if (seconds <= 0) continue;
            long last = lastAuto.computeIfAbsent(key, k -> now);
            if (now - last >= seconds * 1000L) {
                lastAuto.put(key, now);
                repair(null, key);
            }
        }
    }
}
