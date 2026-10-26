package dev.smpcore.features;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.settings.Settings;
import dev.smpcore.util.Msg;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Chat formatting, no-chat-reports, filter, spam limiter, mutes and anti-minimap.
 */
public final class ChatFeature implements Listener {
    private final SmpCore plugin;
    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    public ChatFeature(SmpCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ mutes

    public long muteExpiry(UUID uuid) {
        YamlStore store = plugin.data().store("punishments");
        return store.yaml().getLong("mutes." + uuid + ".until", 0L);
    }

    public boolean isMuted(UUID uuid) {
        long until = muteExpiry(uuid);
        return until == -1 || until > System.currentTimeMillis();
    }

    public void mute(UUID uuid, long durationMillis, String reason, String by) {
        YamlStore store = plugin.data().store("punishments");
        store.set("mutes." + uuid + ".until", durationMillis < 0 ? -1 : System.currentTimeMillis() + durationMillis);
        store.set("mutes." + uuid + ".reason", reason);
        store.set("mutes." + uuid + ".by", by);
    }

    public boolean unmute(UUID uuid) {
        YamlStore store = plugin.data().store("punishments");
        boolean was = store.yaml().isSet("mutes." + uuid);
        store.set("mutes." + uuid, null);
        return was;
    }

    // ------------------------------------------------------------------ chat

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatEarly(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (isMuted(p.getUniqueId())) {
            e.setCancelled(true);
            long until = muteExpiry(p.getUniqueId());
            Msg.error(p, "You are muted" + (until == -1 ? "." : " for " + Msg.duration(until - System.currentTimeMillis()) + "."));
            return;
        }
        String plain = Msg.plain(e.message());
        if (Settings.SPAM_LIMITER.on() && !p.hasPermission("smpcore.bypass")) {
            long now = System.currentTimeMillis();
            long last = lastMessageAt.getOrDefault(p.getUniqueId(), 0L);
            long delay = (long) (Settings.SPAM_DELAY.get() * 1000);
            if (now - last < delay) {
                e.setCancelled(true);
                Msg.actionBar(p, "<red>Slow down! You can chat again in " + String.format("%.1f", (delay - (now - last)) / 1000.0) + "s");
                return;
            }
            String prev = lastMessage.get(p.getUniqueId());
            if (prev != null && prev.equalsIgnoreCase(plain) && now - last < 30_000) {
                e.setCancelled(true);
                Msg.actionBar(p, "<red>Don't repeat the same message");
                return;
            }
            lastMessageAt.put(p.getUniqueId(), now);
            lastMessage.put(p.getUniqueId(), plain);
        }
        if (Settings.CHAT_FILTER.on() && !Settings.CHAT_FILTER_WORDS.get().isEmpty()) {
            Component msg = e.message();
            for (String word : Settings.CHAT_FILTER_WORDS.get()) {
                String w = word.trim();
                if (w.isEmpty()) continue;
                msg = msg.replaceText(TextReplacementConfig.builder()
                        .match(Pattern.compile("(?i)" + Pattern.quote(w)))
                        .replacement(m -> Component.text("*".repeat(m.content().length())))
                        .build());
            }
            e.message(msg);
        }
    }

    private ChatRenderer renderer() {
        String format = Settings.CHAT_FORMAT.get();
        return switch (format) {
            case "modern" -> (source, name, message, viewer) -> Msg.mm("<name> <dark_gray>»</dark_gray> <white><message>",
                    Placeholder.component("name", name), Placeholder.component("message", message));
            case "arrow" -> (source, name, message, viewer) -> Msg.mm("<gray><name></gray> <dark_purple>➜</dark_purple> <message>",
                    Placeholder.component("name", name), Placeholder.component("message", message));
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        ChatRenderer custom = renderer();
        if (custom != null) {
            e.renderer(custom);
        }
        if (!Settings.NO_CHAT_REPORTS.on()) return;
        // Re-send as unsigned system messages: nothing left for the client to report.
        e.setCancelled(true);
        ChatRenderer r = e.renderer();
        Player source = e.getPlayer();
        Component name = source.displayName();
        Component message = e.message();
        for (Audience viewer : e.viewers()) {
            viewer.sendMessage(r.render(source, name, message, viewer));
        }
    }

    // ------------------------------------------------------------------ anti minimap

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!Settings.ANTI_MINIMAP.on() || e.getPlayer().hasPermission("smpcore.bypass")) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Xaero's: fair-play + no minimap, VoxelMap: no radar / caves. Rendered invisibly as plain text codes.
            p.sendMessage(Component.text("§f§a§i§r§x§a§e§r§o§n§o§m§i§n§i§m§a§p"));
            p.sendMessage(Component.text("§3 §6 §3 §6 §3 §6 §e§3 §6 §3 §6 §3 §6 §d"));
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastMessage.remove(e.getPlayer().getUniqueId());
        lastMessageAt.remove(e.getPlayer().getUniqueId());
    }
}
