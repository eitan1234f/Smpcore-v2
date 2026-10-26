package dev.smpcore.commands;

import dev.smpcore.SmpCore;
import dev.smpcore.features.AltDetector;
import dev.smpcore.inspector.InspectorDb;
import dev.smpcore.settings.Settings;
import dev.smpcore.ui.Dialogs;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import io.papermc.paper.registry.data.dialog.ActionButton;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Inspector, anti-cheat, alt detector, mod check and command ban commands.
 */
public final class ProtectionCommands {
    private ProtectionCommands() {
    }

    static final String STAFF = "smpcore.staff";
    static final String ADMIN = "smpcore.admin";

    public static void register(SmpCore plugin, CommandManager m) {
        m.add(new Inspect(plugin));
        m.add(new InspectorCmd(plugin));
        m.add(new AntiCheatCmd(plugin));
        m.add(new Alts(plugin));
        m.add(new ModCheck(plugin));
        m.add(new BanCommand(plugin));
    }

    // ------------------------------------------------------------------ inspector

    static final class Inspect extends SmpCommand {
        Inspect(SmpCore plugin) {
            super(plugin, "inspect", Material.SPYGLASS, "Toggle inspect mode: click blocks to see who changed them", "/<command>", STAFF, "ins");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            boolean on = plugin.features().inspector().toggleInspect(p);
            Msg.send(p, on ? "<#38bdf8>🔎 Inspector enabled.</#38bdf8> <gray>Left-click a block for its history, right-click a container for transactions."
                    : "<gray>Inspector disabled.");
        }
    }

    /** Parses CoreProtect-style parameters: u:user t:2h r:10 a:block|container|break|place|explode b:stone,dirt */
    static InspectorDb.Query parse(CommandSender sender, String[] args, int from, boolean requireTime) {
        InspectorDb.Query q = new InspectorDb.Query();
        boolean global = false;
        Integer radius = null;
        for (int i = from; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            int colon = a.indexOf(':');
            if (colon < 0) {
                Msg.error(sender, "Unknown parameter " + Msg.escape(a) + ". Use u: t: r: a: b:");
                return null;
            }
            String k = a.substring(0, colon);
            String v = a.substring(colon + 1);
            switch (k) {
                case "u", "user", "p" -> q.user = args[i].substring(colon + 1);
                case "t", "time" -> {
                    long ms = Msg.parseDuration(v);
                    if (ms <= 0) {
                        Msg.error(sender, "Bad time " + Msg.escape(v) + " (e.g. t:30m, t:2h, t:3d).");
                        return null;
                    }
                    q.since = System.currentTimeMillis() - ms;
                }
                case "r", "radius" -> {
                    if (v.equals("#global") || v.equals("global")) {
                        global = true;
                    } else if (v.startsWith("#")) {
                        q.world = v.substring(1);
                        global = true;
                    } else {
                        try {
                            radius = Math.min(500, Integer.parseInt(v));
                        } catch (NumberFormatException e) {
                            Msg.error(sender, "Bad radius " + Msg.escape(v) + ".");
                            return null;
                        }
                    }
                }
                case "a", "action" -> {
                    switch (v) {
                        case "block" -> q.kind = 0;
                        case "container", "item", "items" -> q.kind = 1;
                        case "break", "-block" -> { q.kind = 0; q.actions = List.of(InspectorDb.BREAK); }
                        case "place", "+block" -> { q.kind = 0; q.actions = List.of(InspectorDb.PLACE); }
                        case "explode", "explosion" -> { q.kind = 0; q.actions = List.of(InspectorDb.EXPLODE); }
                        case "burn", "fire" -> { q.kind = 0; q.actions = List.of(InspectorDb.BURN); }
                        case "+container", "add" -> { q.kind = 1; q.actions = List.of(InspectorDb.ADDED); }
                        case "-container", "remove", "take" -> { q.kind = 1; q.actions = List.of(InspectorDb.REMOVED); }
                        default -> {
                            Msg.error(sender, "Unknown action " + Msg.escape(v) + ".");
                            return null;
                        }
                    }
                }
                case "b", "block", "i", "item" -> {
                    List<String> mats = new ArrayList<>();
                    for (String part : v.split(",")) {
                        Material m = Items.material(part);
                        if (m != null) mats.add(m.getKey().toString());
                    }
                    q.materials = mats;
                }
                default -> {
                    Msg.error(sender, "Unknown parameter " + Msg.escape(k) + ":");
                    return null;
                }
            }
        }
        if (!global) {
            if (sender instanceof Player p) {
                Location l = p.getLocation();
                q.world = l.getWorld().getName();
                q.cx = l.getBlockX();
                q.cy = l.getBlockY();
                q.cz = l.getBlockZ();
                q.radius = radius == null ? 10 : radius;
            } else if (radius != null) {
                Msg.error(sender, "Console must use r:#global or r:#world.");
                return null;
            }
        }
        if (requireTime && q.since == null) {
            Msg.error(sender, "Add a time, e.g. t:1h, so you don't undo the whole map by accident.");
            return null;
        }
        return q;
    }

    static final class InspectorCmd extends SmpCommand {
        InspectorCmd(SmpCore plugin) {
            super(plugin, "inspector", Material.SPYGLASS, "Block logging: lookup, rollback, restore (like CoreProtect)",
                    "/<command> <lookup|rollback|restore|near|page|purge> [u:user] [t:time] [r:radius] [a:action] [b:blocks]", STAFF, "insp", "blocklog");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var inspector = plugin.features().inspector();
            if (args.length == 0) {
                help(sender);
                return;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "i", "inspect" -> {
                    Player p = requirePlayer(sender);
                    if (p != null) p.performCommand("inspect");
                }
                case "l", "lookup" -> {
                    InspectorDb.Query q = parse(sender, args, 1, false);
                    if (q != null) inspector.runLookup(sender, q, "Lookup");
                }
                case "near" -> {
                    InspectorDb.Query q = parse(sender, new String[]{"near", "r:5"}, 1, false);
                    if (q != null) inspector.runLookup(sender, q, "Nearby (5 blocks)");
                }
                case "rb", "rollback", "restore", "rs" -> {
                    if (!sender.hasPermission(ADMIN)) {
                        Msg.error(sender, "Only admins can roll back.");
                        return;
                    }
                    InspectorDb.Query q = parse(sender, args, 1, true);
                    if (q != null) inspector.rollback(sender, q, sub.startsWith("res") || sub.equals("rs"));
                }
                case "page" -> {
                    int page = 1;
                    try {
                        page = Integer.parseInt(args.length > 1 ? args[1] : "1");
                    } catch (NumberFormatException ignored) {
                        // default page
                    }
                    inspector.showPage(sender, page);
                }
                case "tp" -> {
                    Player p = requirePlayer(sender);
                    if (p == null || args.length < 5) return;
                    World w = Bukkit.getWorld(args[1]);
                    try {
                        if (w != null) p.teleportAsync(new Location(w, Integer.parseInt(args[2]) + 0.5, Integer.parseInt(args[3]) + 1,
                                Integer.parseInt(args[4]) + 0.5));
                    } catch (NumberFormatException ignored) {
                        // bad coordinates
                    }
                }
                case "purge" -> {
                    if (!sender.hasPermission(ADMIN) || args.length < 2) {
                        Msg.error(sender, "Use /inspector purge t:<time> (admins only). Deletes logs older than that.");
                        return;
                    }
                    long ms = Msg.parseDuration(args[1].replace("t:", ""));
                    if (ms <= 0) {
                        Msg.error(sender, "Bad time.");
                        return;
                    }
                    inspector.db().purge(System.currentTimeMillis() - ms).thenAccept(n ->
                            Bukkit.getScheduler().runTask(plugin, () -> Msg.ok(sender, "Purged " + n + " entries.")));
                }
                default -> help(sender);
            }
        }

        private void help(CommandSender s) {
            Msg.send(s, "<#38bdf8><b>🔎 Inspector</b></#38bdf8>");
            for (String line : List.of(
                    "/inspect <dark_gray>- toggle inspect mode (click blocks)",
                    "/inspector lookup u:Steve t:2h r:20 a:break b:diamond_ore",
                    "/inspector rollback u:Steve t:1h r:30 <dark_gray>- undo griefing",
                    "/inspector restore u:Steve t:1h r:30 <dark_gray>- undo a rollback",
                    "/inspector near <dark_gray>- changes within 5 blocks",
                    "<dark_gray>r:#global for everywhere, r:#world_nether for one world",
                    "<dark_gray>a: block, container, break, place, explode, burn, +container, -container")) {
                s.sendMessage(Msg.mm("<gray> " + line));
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("lookup", "rollback", "restore", "near", "inspect", "page", "purge");
            String last = args[args.length - 1];
            if (last.startsWith("u:")) {
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) out.add("u:" + p.getName());
                return out;
            }
            return List.of("u:", "t:1h", "t:1d", "r:10", "r:#global", "a:block", "a:container", "a:break", "a:place", "b:");
        }
    }

    // ------------------------------------------------------------------ anti-cheat

    static final class AntiCheatCmd extends SmpCommand {
        AntiCheatCmd(SmpCore plugin) {
            super(plugin, "anticheat", Material.SHIELD, "Anti-cheat alerts and violation levels", "/<command> <alerts|vl|reset> [player]", STAFF, "ac");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var ac = plugin.features().antiCheat();
            String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
            switch (sub) {
                case "alerts" -> {
                    Player p = requirePlayer(sender);
                    if (p == null) return;
                    boolean on = ac.toggleAlerts(p);
                    Msg.send(p, "<gray>Anti-cheat alerts " + (on ? "<green>enabled" : "<red>disabled") + "<gray>.");
                }
                case "vl", "violations" -> {
                    if (args.length < 2) {
                        boolean any = false;
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            Map<String, Double> v = ac.violations(p);
                            if (v.isEmpty()) continue;
                            any = true;
                            sender.sendMessage(Msg.mm("<white>" + Msg.escape(p.getName()) + "<gray>: " + format(v)));
                        }
                        if (!any) Msg.send(sender, "<green>No one has violations right now.");
                        return;
                    }
                    Player t = online(sender, args[1]);
                    if (t == null) return;
                    Map<String, Double> v = ac.violations(t);
                    Msg.send(sender, "<white>" + Msg.escape(t.getName()) + "<gray>: " + (v.isEmpty() ? "<green>clean" : format(v)));
                }
                case "reset" -> {
                    if (args.length < 2) { usage(sender); return; }
                    Player t = online(sender, args[1]);
                    if (t == null) return;
                    ac.reset(t);
                    Msg.ok(sender, "Reset violations for " + t.getName() + ".");
                }
                default -> {
                    Msg.send(sender, "<#f97316><b>⛊ Anti-Cheat</b></#f97316> <gray>mode: <white>" + Settings.AC_ACTION.get()
                            + "</white> • " + (Settings.AC_ENABLED.on() ? "<green>enabled" : "<red>disabled"));
                    sender.sendMessage(Msg.mm("<gray> /ac alerts <dark_gray>- toggle alerts"));
                    sender.sendMessage(Msg.mm("<gray> /ac vl [player] <dark_gray>- show violation levels"));
                    sender.sendMessage(Msg.mm("<gray> /ac reset <player> <dark_gray>- clear violations"));
                    sender.sendMessage(Msg.mm("<gray> /alts <player> • /modcheck <player>"));
                    sender.sendMessage(Msg.mm("<dark_gray> Configure checks in /settings → Anti-Cheat"));
                }
            }
        }

        private static String format(Map<String, Double> v) {
            List<String> parts = new ArrayList<>();
            v.forEach((k, val) -> parts.add("<yellow>" + k + "</yellow> <red>" + (int) Math.round(val)));
            return String.join("<gray>, ", parts);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("alerts", "vl", "reset");
            return args.length == 2 ? null : List.of();
        }
    }

    static final class Alts extends SmpCommand {
        Alts(SmpCore plugin) {
            super(plugin, "alts", Material.PLAYER_HEAD, "Show accounts that share an IP with a player", "/<command> <player>", STAFF, "dupeip");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            OfflinePlayer target = offline(sender, args[0]);
            if (target == null) return;
            AltDetector alts = plugin.features().alts();
            List<UUID> linked = new ArrayList<>(alts.alts(target.getUniqueId()));
            StringBuilder b = new StringBuilder();
            for (UUID id : linked) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                String status = op.isBanned() ? "<red>BANNED" : op.isOnline() ? "<green>online" : "<gray>offline";
                b.append("<white>").append(Msg.escape(alts.name(id))).append("</white> ").append(status).append("<newline>");
            }
            String text = linked.isEmpty() ? "<green>No linked accounts found."
                    : "<gray>Accounts sharing an IP with <white>" + Msg.escape(String.valueOf(target.getName())) + "</white>:<newline>" + b;
            text += "<newline><dark_gray>Known IPs: " + alts.ipCount(target.getUniqueId());
            if (sender instanceof Player p) {
                List<ActionButton> buttons = new ArrayList<>();
                for (UUID id : linked) {
                    String n = alts.name(id);
                    buttons.add(Dialogs.button("<white>" + Msg.escape(n), "<gray>Show this account's alts", Dialogs.NORMAL, x -> x.performCommand("alts " + n)));
                }
                Dialogs.show(p, Dialogs.menu("<#f97316><b>⛊ Alt Detector</b>", List.of(Dialogs.text(text)), List.of(), buttons,
                        Dialogs.close("<gray>Close"), 2));
            } else {
                Msg.send(sender, text);
            }
        }
    }

    static final class ModCheck extends SmpCommand {
        ModCheck(SmpCore plugin) {
            super(plugin, "modcheck", Material.BLAZE_ROD, "See a player's client brand and mod channels", "/<command> <player>", STAFF, "clientcheck");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Player t = online(sender, args[0]);
            if (t == null) return;
            String brand = t.getClientBrandName();
            List<String> channels = new ArrayList<>(t.getListeningPluginChannels());
            List<String> flagged = new ArrayList<>();
            String all = ((brand == null ? "" : brand) + " " + String.join(" ", channels)).toLowerCase(Locale.ROOT);
            for (String blocked : Settings.BLOCKED_MODS.get()) {
                if (!blocked.isBlank() && all.contains(blocked.toLowerCase(Locale.ROOT))) flagged.add(blocked);
            }
            String text = "<gray>Player: <white>" + Msg.escape(t.getName()) + "</white><newline>"
                    + "<gray>Client brand: <white>" + Msg.escape(brand == null ? "unknown" : brand) + "</white><newline>"
                    + "<gray>Protocol version: <white>" + t.getProtocolVersion() + "</white><newline>"
                    + "<gray>Mod channels (" + channels.size() + "): <white>" + Msg.escape(channels.isEmpty() ? "none" : String.join(", ", channels)) + "</white><newline>"
                    + (flagged.isEmpty() ? "<green>No blocked mods detected." : "<red>Blocked mods: " + Msg.escape(String.join(", ", flagged)));
            if (sender instanceof Player p) {
                Dialogs.message(p, "<#f97316><b>Mod Check</b>", text, null);
            } else {
                Msg.send(sender, text);
            }
        }
    }

    static final class BanCommand extends SmpCommand {
        BanCommand(SmpCore plugin) {
            super(plugin, "bancommand", Material.COMMAND_BLOCK, "Block a command for players", "/<command> <command> | remove <command> | list", ADMIN,
                    "blockcommand", "cmdban");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var list = Settings.BLOCKED_COMMANDS;
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                Msg.send(sender, "<gray>Banned commands: " + list.display());
                return;
            }
            if (args[0].equalsIgnoreCase("remove") && args.length > 1) {
                String c = args[1].replaceFirst("^/", "");
                if (list.remove(c)) {
                    plugin.saveSettings();
                    Msg.ok(sender, "/" + c + " is allowed again.");
                } else {
                    Msg.error(sender, "/" + c + " isn't banned.");
                }
                return;
            }
            String c = args[0].replaceFirst("^/", "").toLowerCase(Locale.ROOT);
            list.add(c);
            plugin.saveSettings();
            Msg.ok(sender, "/" + c + " is now blocked for players.");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                List<String> out = new ArrayList<>(List.of("list", "remove"));
                out.addAll(Bukkit.getCommandMap().getKnownCommands().keySet().stream().filter(k -> !k.contains(":")).limit(200).toList());
                return out;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return Settings.BLOCKED_COMMANDS.get();
            return List.of();
        }
    }
}
