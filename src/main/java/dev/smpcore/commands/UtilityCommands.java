package dev.smpcore.commands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.features.RitualFeature;
import dev.smpcore.installer.InstallerMenu;
import dev.smpcore.settings.Settings;
import dev.smpcore.ui.ChestGui;
import dev.smpcore.ui.Dialogs;
import dev.smpcore.ui.SettingsMenu;
import dev.smpcore.util.Msg;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-facing utility commands and admin tools (kits, npcs, arenas, start, rituals).
 */
public final class UtilityCommands {
    private UtilityCommands() {
    }

    public static void register(SmpCore plugin, CommandManager m) {
        Map<UUID, UUID> replies = new HashMap<>();
        m.add(new SettingsCmd(plugin));
        m.add(new MsgCmd(plugin, replies));
        m.add(new Reply(plugin, replies));
        m.add(new Link(plugin, true));
        m.add(new Link(plugin, false));
        m.add(new Sit(plugin));
        m.add(new Nick(plugin));
        m.add(new Skin(plugin));
        m.add(new Recipes(plugin));
        m.add(new Ritual(plugin));
        m.add(new Kit(plugin));
        m.add(new Start(plugin));
        m.add(new Npc(plugin));
        m.add(new ArenaRepair(plugin));
        m.add(new Admin(plugin));
        m.add(new CustomRecipe(plugin));
    }

    // ------------------------------------------------------------------ /settings

    static final class SettingsCmd extends SmpCommand {
        SettingsCmd(SmpCore plugin) {
            super(plugin, "settings", Material.COMPARATOR, "Open the SMP Core settings menu", "/<command> [search]", null, "smpsettings");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (args.length > 0) {
                String keyword = join(args, 0);
                var results = Settings.search(keyword);
                if (results.isEmpty()) {
                    Msg.error(p, "No settings matched " + Msg.escape(keyword) + ".");
                    return;
                }
                SettingsMenu.openPage(p, "<#fde68a>🔍 " + Msg.escape(keyword), results.size() + " results", results, 0, SettingsMenu::openHome);
                return;
            }
            SettingsMenu.openHome(p);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------ messaging

    static void whisper(SmpCore plugin, Map<UUID, UUID> replies, Player from, Player to, String message) {
        if (plugin.features().chat().isMuted(from.getUniqueId())) {
            Msg.error(from, "You are muted.");
            return;
        }
        from.sendMessage(Msg.mm("<dark_gray>[<light_purple>me</light_purple> → <white>" + Msg.escape(to.getName()) + "</white>]</dark_gray> <gray>"
                + Msg.escape(message)));
        to.sendMessage(Msg.mm("<dark_gray>[<white>" + Msg.escape(from.getName()) + "</white> → <light_purple>me</light_purple>]</dark_gray> <gray>"
                + Msg.escape(message)).clickEvent(ClickEvent.suggestCommand("/r ")));
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
        replies.put(to.getUniqueId(), from.getUniqueId());
        replies.put(from.getUniqueId(), to.getUniqueId());
    }

    static final class MsgCmd extends SmpCommand {
        private final Map<UUID, UUID> replies;

        MsgCmd(SmpCore plugin, Map<UUID, UUID> replies) {
            super(plugin, "msg", Material.PAPER, "Send a private message", "/<command> <player> <message>", null, "tell", "w", "whisper", "m", "pm");
            this.replies = replies;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (args.length < 2) {
                usage(sender);
                return;
            }
            Player to = online(sender, args[0]);
            if (to == null) return;
            whisper(plugin, replies, p, to, join(args, 1));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? null : List.of();
        }
    }

    static final class Reply extends SmpCommand {
        private final Map<UUID, UUID> replies;

        Reply(SmpCore plugin, Map<UUID, UUID> replies) {
            super(plugin, "reply", Material.WRITABLE_BOOK, "Reply to your last private message", "/<command> <message>", null, "r");
            this.replies = replies;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (args.length < 1) {
                usage(sender);
                return;
            }
            UUID last = replies.get(p.getUniqueId());
            Player to = last == null ? null : Bukkit.getPlayer(last);
            if (to == null) {
                Msg.error(p, "Nobody to reply to.");
                return;
            }
            whisper(plugin, replies, p, to, join(args, 0));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return List.of();
        }
    }

    static final class Link extends SmpCommand {
        private final boolean discord;

        Link(SmpCore plugin, boolean discord) {
            super(plugin, discord ? "discord" : "store", discord ? Material.LIGHT_BLUE_DYE : Material.GOLD_INGOT,
                    discord ? "Get the Discord link" : "Get the store link", "/<command>", null, discord ? new String[]{"dc"} : new String[]{"shop", "buy"});
            this.discord = discord;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            String url = discord ? Settings.DISCORD_LINK.get() : Settings.STORE_LINK.get();
            if (sender instanceof Player p) {
                Dialogs.show(p, Dialogs.notice(discord ? "<#93c5fd><b>Join our Discord</b>" : "<gold><b>Server Store</b>",
                        List.of(Dialogs.text("<gray>" + Msg.escape(url))),
                        Dialogs.link(discord ? "<#93c5fd>Open Discord" : "<gold>Open Store", "<gray>" + Msg.escape(url), Dialogs.NORMAL, url)));
            }
            sender.sendMessage(Msg.prefixed("<gray>" + (discord ? "Discord" : "Store") + ": <aqua><u>" + Msg.escape(url))
                    .clickEvent(ClickEvent.openUrl(url)));
        }
    }

    static final class Sit extends SmpCommand {
        Sit(SmpCore plugin) {
            super(plugin, "sit", Material.OAK_STAIRS, "Sit down where you are", "/<command>", null);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (!p.isOnGround() || p.isInsideVehicle()) {
                Msg.error(p, "You need to stand on the ground.");
                return;
            }
            plugin.features().world().sit(p, p.getLocation().add(0, -0.05, 0));
        }
    }

    // ------------------------------------------------------------------ nick / skin

    static final class Nick extends SmpCommand {
        Nick(SmpCore plugin) {
            super(plugin, "nick", Material.NAME_TAG, "Set a nickname (MiniMessage colors allowed)", "/<command> <name|off> [player]", ADMIN_PERM);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Player t = args.length > 1 ? online(sender, args[1]) : requirePlayer(sender);
            if (t == null) return;
            YamlStore data = plugin.data().player(t.getUniqueId());
            if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset")) {
                data.set("nick", null);
                t.displayName(null);
                t.playerListName(null);
                Msg.ok(sender, "Nickname removed.");
                return;
            }
            String nick = args[0];
            Component c = Msg.mm(nick);
            if (Msg.plain(c).length() > 24) {
                Msg.error(sender, "Nicknames can be at most 24 characters.");
                return;
            }
            data.set("nick", nick);
            t.displayName(c);
            t.playerListName(c);
            Msg.ok(sender, "Nickname set to <reset>" + nick);
        }
    }

    static final String ADMIN_PERM = "smpcore.admin";

    static final class Skin extends SmpCommand {
        Skin(SmpCore plugin) {
            super(plugin, "skin", Material.PLAYER_HEAD, "Wear another player's skin", "/<command> <player|reset> [target]", ADMIN_PERM);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Player t = args.length > 1 ? online(sender, args[1]) : requirePlayer(sender);
            if (t == null) return;
            String skinOf = args[0].equalsIgnoreCase("reset") ? t.getName() : args[0];
            Msg.send(sender, "<gray>Fetching skin of " + Msg.escape(skinOf) + "...");
            PlayerProfile source = Bukkit.createProfile(skinOf);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = source.complete(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ProfileProperty textures = source.getProperties().stream().filter(pp -> pp.getName().equals("textures")).findFirst().orElse(null);
                    if (!ok || textures == null || !t.isOnline()) {
                        Msg.error(sender, "Couldn't find that skin.");
                        return;
                    }
                    PlayerProfile profile = t.getPlayerProfile();
                    profile.removeProperty("textures");
                    profile.setProperty(textures);
                    t.setPlayerProfile(profile);
                    Msg.ok(sender, "Skin updated.");
                });
            });
        }
    }

    // ------------------------------------------------------------------ recipes / rituals

    static final class Recipes extends SmpCommand {
        Recipes(SmpCore plugin) {
            super(plugin, "recipes", Material.KNOWLEDGE_BOOK, "Show custom recipes added by SMP Core", "/<command>", null);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            List<DialogBody> body = new ArrayList<>();
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe r = it.next();
                if (!(r instanceof Keyed k) || !k.getKey().getNamespace().equals(plugin.getName().toLowerCase())) continue;
                body.add(Dialogs.item(r.getResult(), "<white><b>" + Msg.pretty(r.getResult().getType().name()) + "</b> x" + r.getResult().getAmount()
                        + "<newline><gray>" + describe(r)));
            }
            var custom = plugin.features().recipes();
            List<String> special = custom.ids().stream().filter(id -> custom.type(id).equals("brewing") || custom.type(id).equals("anvil"))
                    .map(id -> id + " (" + custom.type(id) + ")").toList();
            if (!special.isEmpty()) {
                body.add(Dialogs.text("<light_purple>Brewing & anvil recipes:</light_purple> <gray>" + Msg.escape(String.join(", ", special))));
            }
            if (!Settings.REMOVED_RECIPES.get().isEmpty()) {
                body.add(Dialogs.text("<red>Removed vanilla recipes:</red> <gray>" + Msg.escape(Settings.REMOVED_RECIPES.joined())));
            }
            if (body.isEmpty()) {
                body.add(Dialogs.text("<gray>This server has no custom recipes."));
            }
            Dialogs.show(p, Dialogs.notice("<gold><b>Custom Recipes", body, Dialogs.close("<gray>Close")));
        }

        private static String describe(Recipe r) {
            if (r instanceof ShapedRecipe s) {
                StringBuilder b = new StringBuilder();
                for (String row : s.getShape()) {
                    b.append("<white>[");
                    for (char c : row.toCharArray()) {
                        var choice = s.getChoiceMap().get(c);
                        b.append(choice == null ? " · " : " " + shortName(choice.getItemStack()) + " ");
                    }
                    b.append("]</white><newline>");
                }
                return b.toString();
            }
            if (r instanceof ShapelessRecipe s) {
                List<String> parts = new ArrayList<>();
                s.getChoiceList().forEach(c -> parts.add(shortName(c.getItemStack())));
                return "Shapeless: " + String.join(" + ", parts);
            }
            if (r instanceof org.bukkit.inventory.CookingRecipe<?> c) {
                String station = r instanceof org.bukkit.inventory.BlastingRecipe ? "Blast furnace"
                        : r instanceof org.bukkit.inventory.SmokingRecipe ? "Smoker"
                        : r instanceof org.bukkit.inventory.CampfireRecipe ? "Campfire" : "Furnace";
                return station + ": " + shortName(c.getInputChoice().getItemStack()) + " (" + c.getCookingTime() / 20 + "s)";
            }
            if (r instanceof org.bukkit.inventory.StonecuttingRecipe s) {
                return "Stonecutter: " + shortName(s.getInputChoice().getItemStack());
            }
            return "";
        }

        private static String shortName(ItemStack i) {
            return i == null ? "?" : Msg.pretty(i.getType().name());
        }
    }

    static final class Ritual extends SmpCommand {
        Ritual(SmpCore plugin) {
            super(plugin, "ritual", Material.BEACON, "See active rituals (admins: /ritual start with held item)", "/<command> [start]", null, "rituals");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            if (args.length > 0 && args[0].equalsIgnoreCase("start") && p.hasPermission(ADMIN_PERM)) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    Msg.error(p, "Hold the item the ritual should create.");
                    return;
                }
                plugin.features().rituals().start(p.getName(), hand.clone(), p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
                p.getInventory().setItemInMainHand(null);
                return;
            }
            List<DialogBody> body = new ArrayList<>();
            for (RitualFeature.Ritual r : plugin.features().rituals().active()) {
                Location l = r.location();
                String where = Settings.RITUAL_BROADCAST_COORDS.on() || p.hasPermission(ADMIN_PERM)
                        ? l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() : "hidden";
                body.add(Dialogs.item(r.item(), "<gold><b>" + Msg.pretty(r.item().getType().name()) + "</b></gold> <gray>by " + Msg.escape(r.owner())
                        + "<newline><gray>Location: <white>" + where + "<newline><gray>Time left: <white>" + Msg.duration(r.remaining())));
            }
            if (body.isEmpty()) body.add(Dialogs.text("<gray>No rituals are running right now.<newline>Craft a ritual item ("
                    + Msg.escape(Settings.RITUAL_ITEMS.joined()) + ") to start one."));
            Dialogs.show(p, Dialogs.notice("<light_purple><b>✦ Active Rituals", body, Dialogs.close("<gray>Close")));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender.hasPermission(ADMIN_PERM) ? List.of("start") : List.of();
        }
    }

    // ------------------------------------------------------------------ kits

    static final class Kit extends SmpCommand {
        Kit(SmpCore plugin) {
            super(plugin, "kit", Material.BUNDLE, "Kits: save/load/delete/give/view/list",
                    "/<command> <save|load|delete|give|view|list|resetplayers> [name] [player]", null, "kits");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var kits = plugin.features().kits();
            boolean admin = sender.hasPermission(ADMIN_PERM);
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                Msg.send(sender, "<gray>Kits: <white>" + (kits.names().isEmpty() ? "none" : String.join(", ", kits.names())));
                return;
            }
            if (!admin) {
                Msg.error(sender, "Only admins can manage kits.");
                return;
            }
            String sub = args[0].toLowerCase();
            String name = args.length > 1 ? args[1] : null;
            switch (sub) {
                case "save" -> {
                    Player p = requirePlayer(sender);
                    if (p == null || name == null) { usage(sender); return; }
                    kits.save(name, p);
                    Msg.ok(p, "Kit <white>" + Msg.escape(name) + "</white> saved from your inventory.");
                }
                case "load" -> {
                    Player p = requirePlayer(sender);
                    if (p == null || name == null) { usage(sender); return; }
                    if (!kits.exists(name)) { Msg.error(p, "Kit doesn't exist."); return; }
                    kits.load(p, name);
                    Msg.ok(p, "Loaded kit " + Msg.escape(name) + ".");
                }
                case "delete" -> {
                    if (name == null) { usage(sender); return; }
                    if (kits.delete(name)) Msg.ok(sender, "Deleted kit."); else Msg.error(sender, "Kit doesn't exist.");
                }
                case "give" -> {
                    if (name == null || args.length < 3) { usage(sender); return; }
                    if (args[2].equals("*") || args[2].equalsIgnoreCase("@a")) {
                        Bukkit.getOnlinePlayers().forEach(t -> kits.give(t, name, false));
                    } else {
                        Player t = online(sender, args[2]);
                        if (t != null) kits.give(t, name, false);
                    }
                }
                case "resetplayers" -> {
                    if (name == null) { usage(sender); return; }
                    for (Player t : Bukkit.getOnlinePlayers()) {
                        t.getInventory().clear();
                        kits.give(t, name, false);
                    }
                    Msg.ok(sender, "Reset every online player to kit " + Msg.escape(name) + ".");
                }
                case "view" -> {
                    Player p = requirePlayer(sender);
                    if (p == null || name == null || !kits.exists(name)) { Msg.error(sender, "Kit doesn't exist."); return; }
                    ItemStack[] items = kits.items(name);
                    ChestGui gui = new ChestGui(5, "<dark_gray>Kit: " + Msg.escape(name), false);
                    for (int i = 0; i < 36; i++) gui.set(i < 9 ? i + 27 : i - 9, items[i]);
                    for (int i = 36; i < 41; i++) gui.set(i, items[i]);
                    gui.open(p);
                }
                default -> usage(sender);
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("list", "save", "load", "delete", "give", "view", "resetplayers");
            if (args.length == 2) return plugin.features().kits().names();
            if (args.length == 3) return null;
            return List.of();
        }
    }

    // ------------------------------------------------------------------ /start

    static final class Start extends SmpCommand {
        Start(SmpCore plugin) {
            super(plugin, "start", Material.FIREWORK_ROCKET, "Start the SMP with a countdown and optional PvP grace period",
                    "/<command> [countdown seconds] [grace minutes] [kit]", ADMIN_PERM);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            int countdown = 10;
            int grace = 0;
            try {
                if (args.length > 0) countdown = Math.max(1, Integer.parseInt(args[0]));
                if (args.length > 1) grace = Math.max(0, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                usage(sender);
                return;
            }
            String kit = args.length > 2 ? args[2] : Settings.FIRST_JOIN_KIT.get();
            int[] left = {countdown};
            int graceMinutes = grace;
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (left[0] > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(Msg.mm("<gradient:#a855f7:#ec4899><b>" + left[0]), Msg.mm("<gray>The SMP is starting..."),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, left[0] <= 3 ? 2f : 1f);
                    }
                    left[0]--;
                    return;
                }
                task.cancel();
                for (World w : Bukkit.getWorlds()) {
                    if (w.getEnvironment() == World.Environment.NORMAL) {
                        w.setTime(0);
                        w.setStorm(false);
                    }
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    p.setHealth(max == null ? 20 : max.getValue());
                    p.setFoodLevel(20);
                    p.setSaturation(20);
                    p.showTitle(Title.title(Msg.mm("<gradient:#a855f7:#ec4899><b>GO!"), Msg.mm("<white>The SMP has begun. Good luck!")));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    if (kit != null && !kit.isBlank() && plugin.features().kits().exists(kit)) {
                        plugin.features().kits().give(p, kit, true);
                    }
                }
                Bukkit.broadcast(Msg.prefixed("<green><b>The SMP has started!</b>"));
                if (graceMinutes > 0) {
                    Settings.PVP.set(false);
                    plugin.saveSettings();
                    Bukkit.broadcast(Msg.prefixed("<yellow>PvP is off for a " + graceMinutes + " minute grace period."));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Settings.PVP.set(true);
                        plugin.saveSettings();
                        Bukkit.broadcast(Msg.prefixed("<red><b>⚔ The grace period is over - PvP is enabled!"));
                        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f));
                    }, graceMinutes * 60L * 20L);
                }
            }, 0L, 20L);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("10", "30", "60");
            if (args.length == 2) return List.of("0", "10", "30", "60");
            if (args.length == 3) return plugin.features().kits().names();
            return List.of();
        }
    }

    // ------------------------------------------------------------------ /npc

    static final class Npc extends SmpCommand {
        Npc(SmpCore plugin) {
            super(plugin, "npc", Material.ARMOR_STAND, "Create NPCs with skins and click actions",
                    "/<command> <create|skin|tp|movehere|description|action|remove|list> ...", ADMIN_PERM, "npcs");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var npcs = plugin.features().npcs();
            if (args.length == 0) {
                help(sender);
                return;
            }
            String sub = args[0].toLowerCase();
            if (sub.equals("list")) {
                Msg.send(sender, "<gray>NPCs: <white>" + (npcs.names().isEmpty() ? "none" : String.join(", ", npcs.names())));
                return;
            }
            if (args.length < 2) {
                help(sender);
                return;
            }
            String name = args[1];
            if (!sub.equals("create") && !npcs.exists(name)) {
                Msg.error(sender, "NPC " + Msg.escape(name) + " doesn't exist.");
                return;
            }
            switch (sub) {
                case "create" -> {
                    Player p = requirePlayer(sender);
                    if (p == null) return;
                    if (npcs.exists(name)) { Msg.error(p, "That NPC already exists."); return; }
                    Mannequin m = npcs.create(name, p.getLocation(), args.length > 2 ? args[2] : null);
                    Msg.ok(p, "Created NPC <white>" + Msg.escape(name) + "</white>. Add actions with /npc action " + name + " ...");
                    m.getWorld().playSound(m.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                }
                case "skin" -> {
                    if (args.length < 3) { help(sender); return; }
                    npcs.setSkin(name, args[2]);
                    Msg.ok(sender, "Skin updated.");
                }
                case "tp" -> {
                    Player p = requirePlayer(sender);
                    Mannequin m = npcs.entity(name);
                    if (p != null && m != null) p.teleportAsync(m.getLocation()); else Msg.error(sender, "NPC isn't loaded.");
                }
                case "movehere" -> {
                    Player p = requirePlayer(sender);
                    if (p != null) npcs.teleport(name, p.getLocation());
                }
                case "description" -> {
                    npcs.setDescription(name, args.length > 2 ? join(args, 2) : null);
                    Msg.ok(sender, "Description updated.");
                }
                case "action" -> {
                    if (args.length < 3) { help(sender); return; }
                    List<String> actions = npcs.actions(name);
                    String type = args[2].toLowerCase();
                    switch (type) {
                        case "clear" -> {
                            npcs.setActions(name, new ArrayList<>());
                            Msg.ok(sender, "Actions cleared.");
                        }
                        case "remove" -> {
                            try {
                                actions.remove(Integer.parseInt(args[3]) - 1);
                                npcs.setActions(name, actions);
                                Msg.ok(sender, "Action removed.");
                            } catch (RuntimeException e) {
                                Msg.error(sender, "Use /npc action " + name + " remove <number>");
                            }
                        }
                        case "list" -> {
                            for (int i = 0; i < actions.size(); i++) {
                                Msg.send(sender, "<gray>" + (i + 1) + ". <white>" + Msg.escape(actions.get(i)));
                            }
                            if (actions.isEmpty()) Msg.send(sender, "<gray>No actions.");
                        }
                        case "console", "player", "msg", "sound" -> {
                            if (args.length < 4) { help(sender); return; }
                            actions.add(type + ": " + join(args, 3));
                            npcs.setActions(name, actions);
                            Msg.ok(sender, "Added action #" + actions.size() + ". Actions run in order.");
                        }
                        default -> help(sender);
                    }
                }
                case "remove", "delete" -> {
                    npcs.remove(name);
                    Msg.ok(sender, "NPC removed.");
                }
                default -> help(sender);
            }
        }

        private void help(CommandSender s) {
            Msg.send(s, "<gradient:#a855f7:#ec4899><b>NPCs</b></gradient>");
            for (String line : List.of(
                    "/npc create <name> [skin]", "/npc skin <name> <player>", "/npc tp <name>", "/npc movehere <name>",
                    "/npc description <name> <text>", "/npc action <name> <console|player|msg|sound> <value>",
                    "/npc action <name> remove <number>", "/npc action <name> clear|list", "/npc remove <name>", "/npc list")) {
                s.sendMessage(Msg.mm("<gray> " + Msg.escape(line)));
            }
            s.sendMessage(Msg.mm("<dark_gray> Use %player% in actions for the clicking player."));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("create", "skin", "tp", "movehere", "description", "action", "remove", "list");
            if (args.length == 2 && !args[0].equalsIgnoreCase("create")) return plugin.features().npcs().names();
            if (args.length == 3 && args[0].equalsIgnoreCase("action")) return List.of("console", "player", "msg", "sound", "remove", "clear", "list");
            if (args.length == 3 && (args[0].equalsIgnoreCase("skin") || args[0].equalsIgnoreCase("create"))) return null;
            return List.of();
        }
    }

    // ------------------------------------------------------------------ /arenarepair

    static final class ArenaRepair extends SmpCommand {
        ArenaRepair(SmpCore plugin) {
            super(plugin, "arenarepair", Material.STRUCTURE_BLOCK, "Save arenas and repair them (no FAWE required)",
                    "/<command> <pos1|pos2|create|repair|auto|remove|list> [name] [interval]", ADMIN_PERM, "arena");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var arenas = plugin.features().arenas();
            if (args.length == 0) {
                help(sender);
                return;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "pos1", "pos2" -> {
                    Player p = requirePlayer(sender);
                    if (p == null) return;
                    Block target = p.getTargetBlockExact(8);
                    Location loc = target != null ? target.getLocation() : p.getLocation().getBlock().getLocation();
                    arenas.selection(p.getUniqueId())[sub.equals("pos1") ? 0 : 1] = loc;
                    Msg.ok(p, "Corner " + sub.charAt(3) + " set to " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ".");
                }
                case "create" -> {
                    Player p = requirePlayer(sender);
                    if (p == null || args.length < 2) { help(sender); return; }
                    Location[] sel = arenas.selection(p.getUniqueId());
                    try {
                        arenas.create(p, args[1], sel[0], sel[1]);
                    } catch (java.io.IOException e) {
                        Msg.error(p, "Could not save arena: " + e.getMessage());
                    }
                }
                case "repair" -> {
                    if (args.length < 2) { help(sender); return; }
                    arenas.repair(sender, args[1]);
                }
                case "auto" -> {
                    if (args.length < 3) { help(sender); return; }
                    long ms = args[2].equalsIgnoreCase("off") ? 0 : Msg.parseDuration(args[2]);
                    if (!arenas.exists(args[1])) { Msg.error(sender, "Arena doesn't exist."); return; }
                    arenas.setAuto(args[1], (int) Math.max(0, ms / 1000));
                    Msg.ok(sender, ms <= 0 ? "Auto repair off." : "Auto repair every " + Msg.duration(ms) + ".");
                }
                case "remove" -> {
                    if (args.length < 2) { help(sender); return; }
                    if (arenas.remove(args[1])) Msg.ok(sender, "Arena removed."); else Msg.error(sender, "Arena doesn't exist.");
                }
                case "list" -> Msg.send(sender, "<gray>Arenas: <white>" + (arenas.names().isEmpty() ? "none" : String.join(", ", arenas.names())));
                default -> help(sender);
            }
        }

        private void help(CommandSender s) {
            Msg.send(s, "<gradient:#a855f7:#ec4899><b>Arena Repair</b></gradient>");
            for (String line : List.of("/arenarepair pos1 | pos2  (look at a block)", "/arenarepair create <name>", "/arenarepair repair <name>",
                    "/arenarepair auto <name> <30s|5m|2h|off>", "/arenarepair remove <name>", "/arenarepair list")) {
                s.sendMessage(Msg.mm("<gray> " + Msg.escape(line)));
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("pos1", "pos2", "create", "repair", "auto", "remove", "list");
            if (args.length == 2 && !args[0].equalsIgnoreCase("create")) return plugin.features().arenas().names();
            if (args.length == 3 && args[0].equalsIgnoreCase("auto")) return List.of("30s", "5m", "2h", "off");
            return List.of();
        }
    }

    // ------------------------------------------------------------------ /customrecipe

    static final class CustomRecipe extends SmpCommand {
        CustomRecipe(SmpCore plugin) {
            super(plugin, "customrecipe", Material.CRAFTING_TABLE, "Create custom crafting, smelting, brewing, stonecutter and anvil recipes",
                    "/<command> <create|edit|delete|list> [id] [type]", ADMIN_PERM, "crecipe");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            var recipes = plugin.features().recipes();
            String sub = args.length > 0 ? args[0].toLowerCase() : "list";
            switch (sub) {
                case "create", "edit" -> {
                    Player p = requirePlayer(sender);
                    if (p == null) return;
                    if (args.length < 2) { usage(sender); return; }
                    String id = args[1].toLowerCase().replaceAll("[^a-z0-9_]", "");
                    String type = args.length > 2 ? args[2] : sub.equals("edit") ? recipes.type(id) : "shaped";
                    recipes.openEditor(p, id, type);
                }
                case "delete", "remove" -> {
                    if (args.length < 2) { usage(sender); return; }
                    if (recipes.delete(args[1].toLowerCase())) Msg.ok(sender, "Recipe deleted."); else Msg.error(sender, "No such recipe.");
                }
                default -> {
                    List<String> ids = recipes.ids();
                    Msg.send(sender, "<gray>Custom recipes: <white>" + (ids.isEmpty() ? "none" : String.join(", ",
                            ids.stream().map(i -> i + " (" + recipes.type(i) + ")").toList())));
                    sender.sendMessage(Msg.mm("<dark_gray> Types: " + String.join(", ", dev.smpcore.features.CustomRecipes.TYPES)));
                }
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("create", "edit", "delete", "list");
            if (args.length == 2 && !args[0].equalsIgnoreCase("create")) return plugin.features().recipes().ids();
            if (args.length == 3 && args[0].equalsIgnoreCase("create")) return dev.smpcore.features.CustomRecipes.TYPES;
            return List.of();
        }
    }

    // ------------------------------------------------------------------ /smpcore

    static final class Admin extends SmpCommand {
        Admin(SmpCore plugin) {
            super(plugin, "smpcore", Material.NETHER_STAR, "SMP Core admin: reload, plugins, version", "/<command> <reload|plugins|version>", ADMIN_PERM, "smp");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            String sub = args.length > 0 ? args[0].toLowerCase() : "help";
            switch (sub) {
                case "reload" -> {
                    plugin.reloadSettings();
                    Msg.ok(sender, "Reloaded config.yml.");
                }
                case "plugins", "installer" -> {
                    Player p = requirePlayer(sender);
                    if (p == null) return;
                    if (args.length > 1) {
                        InstallerMenu.openSearch(p, join(args, 1), 0);
                    } else {
                        InstallerMenu.openHome(p);
                    }
                }
                case "version" -> Msg.send(sender, "<gray>SMP Core <white>v" + plugin.getPluginMeta().getVersion() + "</white> • "
                        + Settings.all().size() + " settings • " + plugin.commands().commands().size() + " commands");
                default -> {
                    Msg.send(sender, "<gradient:#a855f7:#ec4899><b>SMP Core</b></gradient> <gray>v" + plugin.getPluginMeta().getVersion());
                    sender.sendMessage(Msg.mm("<gray> /settings <dark_gray>- open the settings menu"));
                    sender.sendMessage(Msg.mm("<gray> /smpcore plugins [search] <dark_gray>- browse & install Modrinth plugins"));
                    sender.sendMessage(Msg.mm("<gray> /smpcore reload <dark_gray>- reload config.yml"));
                }
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? List.of("reload", "plugins", "version") : List.of();
        }
    }
}
