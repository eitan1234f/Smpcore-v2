package dev.smpcore.commands;

import dev.smpcore.SmpCore;
import dev.smpcore.data.DataManager;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.ui.Dialogs;
import dev.smpcore.util.Msg;
import io.papermc.paper.registry.data.dialog.ActionButton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /tpa, /tpahere, /tpaccept, /tpdeny, /rtp, /back, /home, /sethome, /delhome, /homes, /spawn, /setspawn, /worldtp
 */
public final class TeleportCommands {
    private TeleportCommands() {
    }

    public static void register(SmpCore plugin, CommandManager m) {
        Requests requests = new Requests();
        m.add(new Tpa(plugin, requests, false));
        m.add(new Tpa(plugin, requests, true));
        m.add(new TpAccept(plugin, requests));
        m.add(new TpDeny(plugin, requests));
        m.add(new Rtp(plugin));
        m.add(new Back(plugin));
        m.add(new Home(plugin));
        m.add(new SetHome(plugin));
        m.add(new DelHome(plugin));
        m.add(new Homes(plugin));
        m.add(new Spawn(plugin));
        m.add(new SetSpawn(plugin));
        m.add(new WorldTp(plugin));
    }

    // ------------------------------------------------------------------ TPA

    record Request(UUID from, boolean here, long expires) {
    }

    static final class Requests {
        /** target -> (requester -> request) */
        final Map<UUID, Map<UUID, Request>> byTarget = new HashMap<>();

        void add(Player from, Player to, boolean here) {
            long expires = System.currentTimeMillis() + Settings.TPA_TIMEOUT.asInt() * 1000L;
            byTarget.computeIfAbsent(to.getUniqueId(), k -> new HashMap<>()).put(from.getUniqueId(), new Request(from.getUniqueId(), here, expires));
        }

        Request take(Player target, String fromName) {
            Map<UUID, Request> map = byTarget.get(target.getUniqueId());
            if (map == null) return null;
            map.values().removeIf(r -> r.expires < System.currentTimeMillis() || Bukkit.getPlayer(r.from) == null);
            Request found = null;
            if (fromName == null) {
                long newest = 0;
                for (Request r : map.values()) {
                    if (r.expires > newest) {
                        newest = r.expires;
                        found = r;
                    }
                }
            } else {
                Player from = Bukkit.getPlayerExact(fromName);
                if (from != null) found = map.get(from.getUniqueId());
            }
            if (found != null) map.remove(found.from);
            return found;
        }

        List<String> names(Player target) {
            Map<UUID, Request> map = byTarget.get(target.getUniqueId());
            List<String> out = new ArrayList<>();
            if (map != null) {
                for (UUID id : map.keySet()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) out.add(p.getName());
                }
            }
            return out;
        }
    }

    static final class Tpa extends SmpCommand {
        private final Requests requests;
        private final boolean here;

        Tpa(SmpCore plugin, Requests requests, boolean here) {
            super(plugin, here ? "tpahere" : "tpa", here ? Material.ENDER_EYE : Material.ENDER_PEARL,
                    here ? "Ask a player to teleport to you" : "Ask to teleport to a player",
                    "/<command> <player>", null, here ? new String[]{"tphere"} : new String[]{"tpask"});
            this.requests = requests;
            this.here = here;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) return;
            if (target == p) {
                Msg.error(p, "You can't teleport to yourself.");
                return;
            }
            if (plugin.features().vanish().isVanished(target) && !p.hasPermission("smpcore.staff")) {
                Msg.error(p, "Player " + Msg.escape(args[0]) + " is not online.");
                return;
            }
            requests.add(p, target, here);
            Msg.ok(p, "Request sent to <white>" + Msg.escape(target.getName()) + "</white>.");
            String what = here ? " wants you to teleport to them." : " wants to teleport to you.";
            Component accept = Msg.mm("<green><b>[✔ ACCEPT]</b>")
                    .clickEvent(ClickEvent.runCommand("/tpaccept " + p.getName()))
                    .hoverEvent(HoverEvent.showText(Msg.mm("<green>Click to accept")));
            Component deny = Msg.mm("<red><b>[✖ DENY]</b>")
                    .clickEvent(ClickEvent.runCommand("/tpdeny " + p.getName()))
                    .hoverEvent(HoverEvent.showText(Msg.mm("<red>Click to deny")));
            target.sendMessage(Msg.prefixed("<white>" + Msg.escape(p.getName()) + "</white><gray>" + what));
            target.sendMessage(Component.text("   ").append(accept).append(Component.text("  ")).append(deny));
            target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        }
    }

    static final class TpAccept extends SmpCommand {
        private final Requests requests;

        TpAccept(SmpCore plugin, Requests requests) {
            super(plugin, "tpaccept", Material.LIME_DYE, "Accept a teleport request", "/<command> [player]", null, "tpyes");
            this.requests = requests;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            Request r = requests.take(p, args.length > 0 ? args[0] : null);
            if (r == null) {
                Msg.error(p, "You have no pending teleport requests.");
                return;
            }
            Player from = Bukkit.getPlayer(r.from());
            if (from == null) {
                Msg.error(p, "That player went offline.");
                return;
            }
            Msg.ok(p, "Accepted <white>" + Msg.escape(from.getName()) + "</white>'s request.");
            Msg.ok(from, "<white>" + Msg.escape(p.getName()) + "</white> accepted your request.");
            if (r.here()) {
                plugin.features().teleport().teleport(p, from.getLocation(), from.getName());
            } else {
                plugin.features().teleport().teleport(from, p.getLocation(), p.getName());
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player p && args.length == 1 ? requests.names(p) : List.of();
        }
    }

    static final class TpDeny extends SmpCommand {
        private final Requests requests;

        TpDeny(SmpCore plugin, Requests requests) {
            super(plugin, "tpdeny", Material.RED_DYE, "Deny a teleport request", "/<command> [player]", null, "tpno");
            this.requests = requests;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            Request r = requests.take(p, args.length > 0 ? args[0] : null);
            if (r == null) {
                Msg.error(p, "You have no pending teleport requests.");
                return;
            }
            Msg.send(p, "<gray>Request denied.");
            Player from = Bukkit.getPlayer(r.from());
            if (from != null) Msg.error(from, p.getName() + " denied your teleport request.");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player p && args.length == 1 ? requests.names(p) : List.of();
        }
    }

    // ------------------------------------------------------------------ RTP

    static final class Rtp extends SmpCommand {
        private final Map<UUID, Long> cooldowns = new HashMap<>();

        Rtp(SmpCore plugin) {
            super(plugin, "rtp", Material.COMPASS, "Teleport to a random safe location", "/<command> [world]", null, "wild", "randomtp");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            long cd = Settings.RTP_COOLDOWN.asInt() * 1000L;
            long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
            if (!p.hasPermission("smpcore.bypass") && System.currentTimeMillis() - last < cd) {
                Msg.error(p, "You can use /rtp again in " + Msg.duration(cd - (System.currentTimeMillis() - last)) + ".");
                return;
            }
            World world = p.getWorld();
            if (args.length > 0 && p.hasPermission("smpcore.admin")) {
                World w = Bukkit.getWorld(args[0]);
                if (w != null) world = w;
            }
            if (world.getEnvironment() == World.Environment.THE_END) {
                Msg.error(p, "You can't /rtp in the end.");
                return;
            }
            Msg.send(p, "<gray>Searching for a safe location...");
            World w = world;
            find(w, 12).thenAccept(loc -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (loc == null) {
                    Msg.error(p, "Couldn't find a safe spot, try again.");
                    return;
                }
                cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
                plugin.features().teleport().teleport(p, loc, loc.getBlockX() + ", " + loc.getBlockZ());
            }));
        }

        private CompletableFuture<Location> find(World world, int attempts) {
            if (attempts <= 0) return CompletableFuture.completedFuture(null);
            WorldBorder border = world.getWorldBorder();
            Location center = world.getSpawnLocation();
            double max = Math.min(Settings.RTP_RADIUS.get(), border.getSize() / 2 - 16);
            double min = Math.min(Settings.RTP_MIN_RADIUS.get(), max - 1);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double angle = r.nextDouble(Math.PI * 2);
            double dist = min + r.nextDouble(Math.max(1, max - min));
            int x = (int) (center.getX() + Math.cos(angle) * dist);
            int z = (int) (center.getZ() + Math.sin(angle) * dist);
            return world.getChunkAtAsync(x >> 4, z >> 4).thenCompose(chunk -> {
                Location loc = safe(world, x, z);
                return loc != null ? CompletableFuture.completedFuture(loc) : find(world, attempts - 1);
            });
        }

        private static Location safe(World world, int x, int z) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                for (int y = 32; y < 110; y++) {
                    Block feet = world.getBlockAt(x, y, z);
                    Block below = feet.getRelative(0, -1, 0);
                    if (below.getType().isSolid() && below.getType() != Material.BEDROCK && feet.isPassable()
                            && feet.getRelative(0, 1, 0).isPassable() && !below.isLiquid() && below.getType() != Material.MAGMA_BLOCK) {
                        return feet.getLocation().add(0.5, 0, 0.5);
                    }
                }
                return null;
            }
            Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material t = top.getType();
            if (top.isLiquid() || t == Material.MAGMA_BLOCK || t == Material.CACTUS || t == Material.POWDER_SNOW
                    || t == Material.CAMPFIRE || t.name().contains("LAVA")) {
                return null;
            }
            return top.getLocation().add(0.5, 1, 0.5);
        }
    }

    // ------------------------------------------------------------------ back

    static final class Back extends SmpCommand {
        Back(SmpCore plugin) {
            super(plugin, "back", Material.RECOVERY_COMPASS, "Return to your previous location", "/<command>", null);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            Location back = plugin.features().teleport().getBack(p);
            if (back == null) {
                Msg.error(p, "You have nowhere to go back to.");
                return;
            }
            plugin.features().teleport().teleport(p, back, "your last location");
        }
    }

    // ------------------------------------------------------------------ homes

    static ConfigurationSection homes(SmpCore plugin, UUID uuid) {
        YamlStore s = plugin.data().player(uuid);
        ConfigurationSection sec = s.yaml().getConfigurationSection("homes");
        return sec == null ? s.yaml().createSection("homes") : sec;
    }

    static List<String> homeNames(SmpCore plugin, UUID uuid) {
        return new ArrayList<>(homes(plugin, uuid).getKeys(false));
    }

    static final class Home extends SmpCommand {
        Home(SmpCore plugin) {
            super(plugin, "home", Material.RED_BED, "Teleport to one of your homes", "/<command> [name]", null, "h");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            List<String> names = homeNames(plugin, p.getUniqueId());
            if (names.isEmpty()) {
                Msg.error(p, "You don't have any homes. Set one with /sethome.");
                return;
            }
            String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : names.contains("home") ? "home" : names.size() == 1 ? names.get(0) : null;
            if (name == null) {
                Homes.open(plugin, p);
                return;
            }
            Location loc = DataManager.readLocation(homes(plugin, p.getUniqueId()), name);
            if (loc == null) {
                Msg.error(p, "Home " + Msg.escape(name) + " doesn't exist (or its world is gone).");
                return;
            }
            plugin.features().teleport().teleport(p, loc, "home " + name);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player p && args.length == 1 ? homeNames(plugin, p.getUniqueId()) : List.of();
        }
    }

    static final class SetHome extends SmpCommand {
        SetHome(SmpCore plugin) {
            super(plugin, "sethome", Material.WHITE_BED, "Set a home where you stand", "/<command> [name]", null);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "") : "home";
            if (name.isEmpty()) name = "home";
            ConfigurationSection homes = homes(plugin, p.getUniqueId());
            int max = Settings.MAX_HOMES.asInt();
            if (!homes.isSet(name) && homes.getKeys(false).size() >= max && !p.hasPermission("smpcore.bypass")) {
                Msg.error(p, "You can only have " + max + " home" + (max == 1 ? "" : "s") + ". Delete one with /delhome.");
                return;
            }
            DataManager.writeLocation(homes, name, p.getLocation());
            plugin.data().player(p.getUniqueId()).markDirty();
            Msg.ok(p, "Home <white>" + name + "</white> set.");
        }
    }

    static final class DelHome extends SmpCommand {
        DelHome(SmpCore plugin) {
            super(plugin, "delhome", Material.BARRIER, "Delete one of your homes", "/<command> <name>", null, "removehome");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (args.length < 1) {
                usage(sender);
                return;
            }
            ConfigurationSection homes = homes(plugin, p.getUniqueId());
            String name = args[0].toLowerCase(Locale.ROOT);
            if (!homes.isSet(name)) {
                Msg.error(p, "Home " + Msg.escape(name) + " doesn't exist.");
                return;
            }
            homes.set(name, null);
            plugin.data().player(p.getUniqueId()).markDirty();
            Msg.ok(p, "Home <white>" + Msg.escape(name) + "</white> deleted.");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player p && args.length == 1 ? homeNames(plugin, p.getUniqueId()) : List.of();
        }
    }

    static final class Homes extends SmpCommand {
        Homes(SmpCore plugin) {
            super(plugin, "homes", Material.CYAN_BED, "Open your homes menu", "/<command>", null);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p != null) open(plugin, p);
        }

        static void open(SmpCore plugin, Player p) {
            ConfigurationSection homes = homes(plugin, p.getUniqueId());
            List<ActionButton> buttons = new ArrayList<>();
            for (String name : homes.getKeys(false)) {
                Location loc = DataManager.readLocation(homes, name);
                String where = loc == null ? "<red>world missing" : "<gray>" + loc.getWorld().getName() + " " + loc.getBlockX() + ", "
                        + loc.getBlockY() + ", " + loc.getBlockZ();
                buttons.add(Dialogs.button("<#fca5a5>⌂ " + name, where + "<newline><dark_gray>Click to teleport", Dialogs.NORMAL, pl -> {
                    if (loc != null) plugin.features().teleport().teleport(pl, loc, "home " + name);
                }));
                buttons.add(Dialogs.button("<red>🗑 Delete", "<gray>Delete home " + name, Dialogs.NORMAL, pl ->
                        Dialogs.show(pl, Dialogs.confirm("<red>Delete home?", "<gray>Delete <white>" + name + "</white>? This can't be undone.",
                                Dialogs.button("<red>Delete", null, Dialogs.SMALL, x -> {
                                    homes(plugin, x.getUniqueId()).set(name, null);
                                    plugin.data().player(x.getUniqueId()).markDirty();
                                    Msg.ok(x, "Home deleted.");
                                    open(plugin, x);
                                }),
                                Dialogs.button("<gray>Cancel", null, Dialogs.SMALL, x -> open(plugin, x))))));
            }
            if (buttons.isEmpty()) {
                Dialogs.message(p, "<#fca5a5><b>Your Homes</b>", "<gray>You don't have any homes yet.<newline>Use <white>/sethome [name]</white> to set one.", null);
                return;
            }
            Dialogs.show(p, Dialogs.menu("<#fca5a5><b>Your Homes</b> <dark_gray>(" + homes.getKeys(false).size() + "/" + Settings.MAX_HOMES.asInt() + ")",
                    List.of(Dialogs.text("<gray>Pick a home to teleport to.")), List.of(), buttons, Dialogs.close("<gray>Close"), 2));
        }
    }

    // ------------------------------------------------------------------ spawn & worlds

    static final class Spawn extends SmpCommand {
        Spawn(SmpCore plugin) {
            super(plugin, "spawn", Material.BEACON, "Teleport to spawn", "/<command> [player]", null);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Location spawn = plugin.features().players().spawn();
            if (spawn == null) spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            if (args.length > 0 && sender.hasPermission("smpcore.admin")) {
                Player t = online(sender, args[0]);
                if (t == null) return;
                t.teleportAsync(spawn);
                Msg.ok(sender, "Sent " + Msg.escape(t.getName()) + " to spawn.");
                return;
            }
            Player p = requirePlayer(sender);
            if (p != null) plugin.features().teleport().teleport(p, spawn, "spawn");
        }
    }

    static final class SetSpawn extends SmpCommand {
        SetSpawn(SmpCore plugin) {
            super(plugin, "setspawn", Material.RESPAWN_ANCHOR, "Set the server spawn", "/<command>", "smpcore.admin");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            plugin.features().players().setSpawn(p.getLocation());
            p.getWorld().setSpawnLocation(p.getLocation());
            Msg.ok(p, "Spawn set to your location.");
        }
    }

    static final class WorldTp extends SmpCommand {
        WorldTp(SmpCore plugin) {
            super(plugin, "worldtp", Material.END_PORTAL_FRAME, "Teleport to another world", "/<command> <world> [player]", "smpcore.admin", "wtp");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            World w = Bukkit.getWorld(args[0]);
            if (w == null) {
                Msg.error(sender, "World " + Msg.escape(args[0]) + " doesn't exist.");
                return;
            }
            Player target = args.length > 1 ? online(sender, args[1]) : requirePlayer(sender);
            if (target == null) return;
            target.teleportAsync(w.getSpawnLocation());
            Msg.ok(sender, "Teleported " + Msg.escape(target.getName()) + " to " + w.getName() + ".");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return Bukkit.getWorlds().stream().map(World::getName).toList();
            return null;
        }
    }
}
