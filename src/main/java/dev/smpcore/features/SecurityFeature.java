package dev.smpcore.features;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.smpcore.SmpCore;
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
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintenance mode, anti-VPN, anti-alt and the mod detector.
 */
public final class SecurityFeature implements Listener {
    private final SmpCore plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, Boolean> vpnCache = new ConcurrentHashMap<>();

    public SecurityFeature(SmpCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        OfflinePlayer player = Bukkit.getOfflinePlayer(e.getUniqueId());
        boolean trusted = player.isOp() || player.isWhitelisted();

        if (Settings.MAINTENANCE.on() && !trusted) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Msg.mm(Settings.MAINTENANCE_MESSAGE.get()));
            return;
        }

        String ip = e.getAddress().getHostAddress();
        int maxAlts = Settings.ANTI_ALT.asInt();
        if (maxAlts > 0 && !trusted) {
            long same = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getAddress() != null && p.getAddress().getAddress().getHostAddress().equals(ip))
                    .filter(p -> !p.getUniqueId().equals(e.getUniqueId()))
                    .count();
            if (same >= maxAlts) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Msg.mm("<red>Too many accounts are already online from your connection.</red>"));
                return;
            }
        }

        if (Settings.ANTI_VPN.on() && !trusted && !isLocal(e.getAddress())) {
            if (isVpn(ip)) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Msg.mm("<red>VPNs and proxies are not allowed on this server.</red>"));
                staff("<yellow>" + Msg.escape(e.getName()) + " was blocked for using a VPN/proxy.");
            }
        }
    }

    private static boolean isLocal(InetAddress a) {
        return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress();
    }

    private boolean isVpn(String ip) {
        Boolean cached = vpnCache.get(ip);
        if (cached != null) return cached;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://ip-api.com/json/" + ip + "?fields=status,proxy,hosting"))
                    .timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            boolean vpn = "success".equals(json.get("status").getAsString())
                    && (json.get("proxy").getAsBoolean() || json.get("hosting").getAsBoolean());
            vpnCache.put(ip, vpn);
            return vpn;
        } catch (Exception ex) {
            plugin.getLogger().fine("Anti-VPN lookup failed for " + ip + ": " + ex.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        if (Settings.MAINTENANCE.on() && Settings.MAINTENANCE_MOTD.on()) {
            e.motd(Msg.mm("<red><b>⚠ MAINTENANCE</b></red><newline><gray>We'll be back soon!"));
            e.setVersion("Maintenance");
            e.setProtocolVersion(-1);
        }
    }

    // ------------------------------------------------------------------ mod detector

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!Settings.MOD_DETECTOR.on()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            String brand = p.getClientBrandName();
            String channels = String.join(", ", p.getListeningPluginChannels());
            if (brand != null && !brand.equalsIgnoreCase("vanilla")) {
                staff("<gray>" + Msg.escape(p.getName()) + " joined with client <white>" + Msg.escape(brand) + "</white>"
                        + (channels.isEmpty() ? "" : " <dark_gray>[" + Msg.escape(channels) + "]"));
            }
            check(p, brand == null ? "" : brand);
            for (String ch : p.getListeningPluginChannels()) {
                check(p, ch);
            }
        }, 60L);
    }

    @EventHandler
    public void onChannel(PlayerRegisterChannelEvent e) {
        if (Settings.MOD_DETECTOR.on()) {
            check(e.getPlayer(), e.getChannel());
        }
    }

    private void check(Player p, String text) {
        if (p.hasPermission("smpcore.bypass") || !p.isOnline()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String blocked : Settings.BLOCKED_MODS.get()) {
            String b = blocked.trim().toLowerCase(Locale.ROOT);
            if (!b.isEmpty() && lower.contains(b)) {
                staff("<red>" + Msg.escape(p.getName()) + " was kicked for a blocked mod: <white>" + Msg.escape(text));
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.kick(Msg.mm("<red>Blocked client modification detected:</red><newline><white>" + Msg.escape(blocked))));
                return;
            }
        }
    }

    private void staff(String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("smpcore.staff")) {
                    Msg.send(p, message);
                }
            }
            plugin.getLogger().info(Msg.plain(Msg.mm(message)));
        });
    }
}
