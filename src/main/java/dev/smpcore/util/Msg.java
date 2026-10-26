package dev.smpcore.util;

import dev.smpcore.settings.Settings;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage helpers. Every player-facing string in SmpCore goes through here.
 */
public final class Msg {
    public static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {
    }

    public static Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component prefixed(String text, TagResolver... resolvers) {
        return mm(Settings.PREFIX.get() + text, resolvers);
    }

    public static void send(Audience to, String text, TagResolver... resolvers) {
        to.sendMessage(prefixed(text, resolvers));
    }

    public static void error(Audience to, String text) {
        send(to, "<red>" + text);
    }

    public static void ok(Audience to, String text) {
        send(to, "<green>" + text);
    }

    public static void actionBar(Audience to, String text, TagResolver... resolvers) {
        to.sendActionBar(mm(text, resolvers));
    }

    public static String escape(String raw) {
        return MM.escapeTags(raw);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String pretty(String enumName) {
        String s = enumName.toLowerCase().replace("minecraft:", "").replace('_', ' ');
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char ch : s.toCharArray()) {
            b.append(up ? Character.toUpperCase(ch) : ch);
            up = ch == ' ';
        }
        return b.toString();
    }

    public static String duration(long millis) {
        long s = Math.max(0, millis / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        if (sec > 0 || b.isEmpty()) b.append(sec).append("s");
        return b.toString().trim();
    }

    /** Parses durations like 30s, 10m, 2h, 7d, 1w or "perm". Returns -1 for permanent, 0 when invalid. */
    public static long parseDuration(String in) {
        String s = in.toLowerCase().trim();
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever")) {
            return -1;
        }
        long total = 0;
        long num = 0;
        boolean any = false;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                num = num * 10 + (c - '0');
                any = true;
                continue;
            }
            long mult = switch (c) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                case 'y' -> 31_536_000_000L;
                default -> 0L;
            };
            if (mult == 0) {
                return 0;
            }
            total += num * mult;
            num = 0;
        }
        if (num > 0) {
            total += num * 60_000L;
        }
        return any ? total : 0;
    }
}
