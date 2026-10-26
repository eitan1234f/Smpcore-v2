package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers which accounts connect from which IPs, links alts together,
 * and stops banned players from coming back on another account.
 */
public final class AltDetector implements Listener {
    private final SmpCore plugin;

    public AltDetector(SmpCore plugin) {
        this.plugin = plugin;
    }

    private YamlStore store() {
        return plugin.data().store("alts");
    }

    private static String ipKey(String ip) {
        return ip.replace('.', '_').replace(':', '-');
    }

    private synchronized void record(UUID uuid, String name, String ip) {
        YamlStore s = store();
        String key = ipKey(ip);
        List<String> accounts = new ArrayList<>(s.yaml().getStringList("ips." + key));
        if (!accounts.contains(uuid.toString())) {
            accounts.add(uuid.toString());
            s.set("ips." + key, accounts);
        }
        List<String> ips = new ArrayList<>(s.yaml().getStringList("players." + uuid + ".ips"));
        if (!ips.contains(key)) {
            ips.add(key);
            s.set("players." + uuid + ".ips", ips);
        }
        s.set("players." + uuid + ".name", name);
    }

    /** Every account that shared an IP with this one (excluding itself). */
    public synchronized Set<UUID> alts(UUID uuid) {
        YamlStore s = store();
        Set<UUID> out = new LinkedHashSet<>();
        for (String ip : s.yaml().getStringList("players." + uuid + ".ips")) {
            for (String id : s.yaml().getStringList("ips." + ip)) {
                if (!id.equals(uuid.toString())) out.add(UUID.fromString(id));
            }
        }
        return out;
    }

    /** Alts including the IP that is connecting right now (not recorded yet). */
    private synchronized Set<UUID> altsOf(UUID uuid, String currentIp) {
        Set<UUID> out = alts(uuid);
        for (String id : store().yaml().getStringList("ips." + ipKey(currentIp))) {
            if (!id.equals(uuid.toString())) out.add(UUID.fromString(id));
        }
        return out;
    }

    public String name(UUID uuid) {
        String n = store().yaml().getString("players." + uuid + ".name");
        if (n != null) return n;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
    }

    public int ipCount(UUID uuid) {
        return store().yaml().getStringList("players." + uuid + ".ips").size();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        String ip = e.getAddress().getHostAddress();
        UUID uuid = e.getUniqueId();
        String name = e.getName();
        // write on the main thread so the autosave never sees a half-written file
        Bukkit.getScheduler().runTask(plugin, () -> record(uuid, name, ip));
        if (!Settings.AC_BLOCK_BANNED_ALTS.on()) return;
        OfflinePlayer self = Bukkit.getOfflinePlayer(e.getUniqueId());
        if (self.isOp()) return;
        for (UUID alt : altsOf(e.getUniqueId(), ip)) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(alt);
            if (op.isBanned()) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        Msg.mm("<red><b>Ban evasion detected</b></red><newline><gray>This connection is linked to a banned account."));
                String msg = "<red>" + Msg.escape(e.getName()) + " was blocked: linked to banned account " + Msg.escape(name(alt));
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(Msg.prefixed(msg), "smpcore.staff"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!Settings.AC_ALT_ALERTS.on()) return;
        Player p = e.getPlayer();
        Set<UUID> alts = alts(p.getUniqueId());
        if (alts.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (UUID id : alts) names.add(name(id));
        Bukkit.broadcast(Msg.prefixed("<#f97316>⛊ <white>" + Msg.escape(p.getName()) + "</white> <gray>may be an alt of <yellow>"
                + Msg.escape(String.join(", ", names)) + "</yellow> <dark_gray>(/alts " + p.getName() + ")"), "smpcore.staff");
    }
}
