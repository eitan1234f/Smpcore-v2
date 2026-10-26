package dev.smpcore.commands;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import dev.smpcore.features.PlayerFeature;
import dev.smpcore.settings.ListSetting;
import dev.smpcore.settings.Settings;
import dev.smpcore.ui.ChestGui;
import dev.smpcore.ui.Dialogs;
import dev.smpcore.util.Items;
import dev.smpcore.util.Msg;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moderation and admin commands.
 */
public final class StaffCommands {
    private StaffCommands() {
    }

    public static void register(SmpCore plugin, CommandManager m) {
        m.add(new Vanish(plugin));
        m.add(new InvSee(plugin, false));
        m.add(new InvSee(plugin, true));
        m.add(new Freeze(plugin));
        m.add(new Heal(plugin, false));
        m.add(new Heal(plugin, true));
        m.add(new Fly(plugin));
        m.add(new Broadcast(plugin));
        m.add(new Ban(plugin));
        m.add(new Unban(plugin));
        m.add(new Mute(plugin));
        m.add(new Unmute(plugin));
        m.add(new BanItem(plugin));
        m.add(new ItemLimit(plugin));
        m.add(new Unbreakable(plugin));
        m.add(new Enchant(plugin));
        m.add(new TickRate(plugin));
        m.add(new Rollback(plugin));
    }

    static final String STAFF = "smpcore.staff";
    static final String ADMIN = "smpcore.admin";

    // ------------------------------------------------------------------ vanish / freeze / heal / fly

    static final class Vanish extends SmpCommand {
        Vanish(SmpCore plugin) {
            super(plugin, "vanish", Material.GLASS_BOTTLE, "Become invisible to players", "/<command> [player]", STAFF, "v");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player target = args.length > 0 ? online(sender, args[0]) : requirePlayer(sender);
            if (target == null) return;
            boolean now = !plugin.features().vanish().isVanished(target);
            plugin.features().vanish().setVanished(target, now);
            Msg.send(target, now ? "<gray>You are now <white>vanished</white>." : "<gray>You are now <white>visible</white>.");
            if (target != sender) Msg.ok(sender, target.getName() + " is now " + (now ? "vanished" : "visible") + ".");
        }
    }

    static final class Freeze extends SmpCommand {
        Freeze(SmpCore plugin) {
            super(plugin, "freeze", Material.BLUE_ICE, "Freeze or unfreeze a player", "/<command> <player>", STAFF);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Player t = online(sender, args[0]);
            if (t == null) return;
            boolean frozen = plugin.features().freeze().toggle(t);
            if (frozen) {
                t.showTitle(Title.title(Msg.mm("<aqua><b>FROZEN"), Msg.mm("<gray>Don't log out - a staff member wants to talk")));
                t.playSound(t.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
            } else {
                t.showTitle(Title.title(Msg.mm("<green>Unfrozen"), Msg.mm("")));
            }
            Msg.ok(sender, t.getName() + " is now " + (frozen ? "frozen" : "unfrozen") + ".");
        }
    }

    static final class Heal extends SmpCommand {
        private final boolean feedOnly;

        Heal(SmpCore plugin, boolean feedOnly) {
            super(plugin, feedOnly ? "feed" : "heal", feedOnly ? Material.COOKED_BEEF : Material.GLISTERING_MELON_SLICE,
                    feedOnly ? "Fill a player's hunger" : "Heal a player fully", "/<command> [player]", STAFF);
            this.feedOnly = feedOnly;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player t = args.length > 0 ? online(sender, args[0]) : requirePlayer(sender);
            if (t == null) return;
            t.setFoodLevel(20);
            t.setSaturation(20f);
            if (!feedOnly) {
                var max = t.getAttribute(Attribute.MAX_HEALTH);
                t.setHealth(max == null ? 20 : max.getValue());
                t.setFireTicks(0);
                t.getActivePotionEffects().stream()
                        .filter(e -> !e.getType().getEffectCategory().equals(org.bukkit.potion.PotionEffectTypeCategory.BENEFICIAL))
                        .forEach(e -> t.removePotionEffect(e.getType()));
            }
            Msg.ok(t, feedOnly ? "You have been fed." : "You have been healed.");
            if (t != sender) Msg.ok(sender, (feedOnly ? "Fed " : "Healed ") + t.getName() + ".");
        }
    }

    static final class Fly extends SmpCommand {
        Fly(SmpCore plugin) {
            super(plugin, "fly", Material.FEATHER, "Toggle flight", "/<command> [player]", STAFF);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player t = args.length > 0 ? online(sender, args[0]) : requirePlayer(sender);
            if (t == null) return;
            boolean fly = !t.getAllowFlight();
            t.setAllowFlight(fly);
            if (!fly) t.setFlying(false);
            Msg.ok(t, "Flight " + (fly ? "enabled" : "disabled") + ".");
            if (t != sender) Msg.ok(sender, "Flight " + (fly ? "enabled" : "disabled") + " for " + t.getName() + ".");
        }
    }

    static final class Broadcast extends SmpCommand {
        Broadcast(SmpCore plugin) {
            super(plugin, "broadcast", Material.BELL, "Announce a message to everyone", "/<command> <message>", STAFF, "bc", "announce");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            String message = join(args, 0);
            Bukkit.broadcast(Msg.mm("<newline><gradient:#a855f7:#ec4899><b>📣 ANNOUNCEMENT</b></gradient><newline><white>" + message + "<newline>"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(Title.title(Msg.mm("<gradient:#a855f7:#ec4899><b>ANNOUNCEMENT"), Msg.mm("<white>" + message),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
            }
        }
    }

    // ------------------------------------------------------------------ invsee / endersee

    static final class InvSee extends SmpCommand {
        private final boolean ender;

        InvSee(SmpCore plugin, boolean ender) {
            super(plugin, ender ? "endersee" : "invsee", ender ? Material.ENDER_CHEST : Material.CHEST,
                    ender ? "View and edit any player's ender chest (even offline)" : "View and edit any player's inventory (even offline)",
                    "/<command> <player>", STAFF, ender ? new String[]{"ecsee"} : new String[0]);
            this.ender = ender;
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player viewer = requirePlayer(sender);
            if (viewer == null) return;
            if (args.length < 1) {
                if (ender) {
                    viewer.openInventory(viewer.getEnderChest());
                } else {
                    usage(sender);
                }
                return;
            }
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                viewer.openInventory(ender ? online.getEnderChest() : online.getInventory());
                return;
            }
            OfflinePlayer off = offline(sender, args[0]);
            if (off == null) return;
            UUID id = off.getUniqueId();
            if (!plugin.data().hasPlayerFile(id)) {
                Msg.error(sender, "No saved data for that player yet (they need to log out once with SmpCore installed).");
                return;
            }
            YamlStore data = plugin.data().player(id);
            String key = ender ? "enderchest" : "inventory";
            int size = ender ? 27 : 41;
            ItemStack[] items = Items.decode(data.yaml().getString("snapshot." + key), size);
            ChestGui gui = new ChestGui(ender ? 3 : 5, "<dark_gray>" + (ender ? "Ender Chest" : "Inventory") + ": " + Msg.escape(String.valueOf(off.getName()))
                    + " <red>(offline)", true);
            for (int i = 0; i < items.length; i++) {
                gui.set(i, items[i]);
            }
            gui.onClose(inv -> {
                if (Bukkit.getPlayer(id) != null) {
                    Msg.error(viewer, "That player came online - offline edits were not saved.");
                    return;
                }
                ItemStack[] edited = new ItemStack[size];
                for (int i = 0; i < size; i++) edited[i] = inv.getItem(i);
                data.set("snapshot." + key, Items.encode(edited));
                data.set("offline-edit." + key, true);
                data.save();
                Msg.ok(viewer, "Offline edits saved - they apply when " + off.getName() + " joins.");
            });
            gui.open(viewer);
        }
    }

    // ------------------------------------------------------------------ bans & mutes

    static final class Ban extends SmpCommand {
        Ban(SmpCore plugin) {
            super(plugin, "ban", Material.BARRIER, "Ban a player, optionally for a time (e.g. /ban Steve 7d griefing)",
                    "/<command> <player> [duration|perm] [reason]", STAFF, "tempban");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            OfflinePlayer target = offline(sender, args[0]);
            if (target == null) return;
            long duration = -1;
            int reasonStart = 1;
            if (args.length > 1) {
                long d = Msg.parseDuration(args[1]);
                if (d != 0) {
                    duration = d;
                    reasonStart = 2;
                }
            }
            String reason = args.length > reasonStart ? join(args, reasonStart) : "You have been banned.";
            Duration dur = duration < 0 ? null : Duration.ofMillis(duration);
            String kick = "<red><b>You are banned</b></red><newline><newline><white>" + Msg.escape(reason) + "</white><newline><gray>"
                    + (dur == null ? "This ban is permanent." : "Expires in " + Msg.duration(duration));
            target.ban(reason, dur, sender.getName());
            if (target.getPlayer() != null) {
                target.getPlayer().kick(Msg.mm(kick));
            }
            Bukkit.broadcast(Msg.prefixed("<red>" + Msg.escape(String.valueOf(target.getName())) + " was banned"
                    + (dur == null ? "" : " for " + Msg.duration(duration)) + ". <gray>(" + Msg.escape(reason) + ")"), STAFF);
            Msg.ok(sender, "Banned " + target.getName() + (dur == null ? " permanently." : " for " + Msg.duration(duration) + "."));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 2) return List.of("1h", "1d", "7d", "30d", "perm");
            return args.length == 1 ? null : List.of();
        }
    }

    static final class Unban extends SmpCommand {
        Unban(SmpCore plugin) {
            super(plugin, "unban", Material.LIME_DYE, "Unban a player", "/<command> <player>", STAFF, "pardon");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.isBanned()) {
                Msg.error(sender, args[0] + " isn't banned.");
                return;
            }
            Bukkit.getBanList(BanListType.PROFILE).pardon(target.getPlayerProfile());
            Msg.ok(sender, "Unbanned " + Msg.escape(args[0]) + ".");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            List<String> out = new ArrayList<>();
            for (var e : Bukkit.getBanList(BanListType.PROFILE).getEntries()) {
                if (e.getBanTarget() instanceof com.destroystokyo.paper.profile.PlayerProfile p && p.getName() != null) out.add(p.getName());
            }
            return out;
        }
    }

    static final class Mute extends SmpCommand {
        Mute(SmpCore plugin) {
            super(plugin, "mute", Material.NOTE_BLOCK, "Mute a player's chat", "/<command> <player> [duration|perm] [reason]", STAFF, "tempmute");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            OfflinePlayer target = offline(sender, args[0]);
            if (target == null) return;
            long duration = -1;
            int reasonStart = 1;
            if (args.length > 1) {
                long d = Msg.parseDuration(args[1]);
                if (d != 0) {
                    duration = d;
                    reasonStart = 2;
                }
            }
            String reason = args.length > reasonStart ? join(args, reasonStart) : "Muted by staff";
            plugin.features().chat().mute(target.getUniqueId(), duration, reason, sender.getName());
            String time = duration < 0 ? "permanently" : "for " + Msg.duration(duration);
            if (target.getPlayer() != null) {
                Msg.error(target.getPlayer(), "You have been muted " + time + ". Reason: " + reason);
            }
            Msg.ok(sender, "Muted " + target.getName() + " " + time + ".");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 2) return List.of("10m", "1h", "1d", "perm");
            return args.length == 1 ? null : List.of();
        }
    }

    static final class Unmute extends SmpCommand {
        Unmute(SmpCore plugin) {
            super(plugin, "unmute", Material.JUKEBOX, "Unmute a player", "/<command> <player>", STAFF);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            OfflinePlayer target = offline(sender, args[0]);
            if (target == null) return;
            if (plugin.features().chat().unmute(target.getUniqueId())) {
                Msg.ok(sender, "Unmuted " + target.getName() + ".");
                if (target.getPlayer() != null) Msg.ok(target.getPlayer(), "You can chat again.");
            } else {
                Msg.error(sender, target.getName() + " isn't muted.");
            }
        }
    }

    // ------------------------------------------------------------------ item rules

    static final class BanItem extends SmpCommand {
        BanItem(SmpCore plugin) {
            super(plugin, "banitem", Material.STRUCTURE_VOID, "Ban the item you hold (or a named item)",
                    "/<command> [item] | remove <item> | list", ADMIN);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            ListSetting list = Settings.BANNED_ITEMS;
            if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
                Msg.send(sender, "<gray>Banned items: " + list.display());
                return;
            }
            if (args.length > 1 && args[0].equalsIgnoreCase("remove")) {
                Material m = Items.material(args[1]);
                if (m != null && list.remove(m.getKey().getKey())) {
                    plugin.saveSettings();
                    Msg.ok(sender, Msg.pretty(m.name()) + " is no longer banned.");
                } else {
                    Msg.error(sender, "That item isn't banned.");
                }
                return;
            }
            Material m;
            if (args.length > 0) {
                m = Items.material(args[0]);
            } else if (sender instanceof Player p) {
                m = p.getInventory().getItemInMainHand().getType();
            } else {
                usage(sender);
                return;
            }
            if (m == null || m.isAir()) {
                Msg.error(sender, "Hold an item or name one.");
                return;
            }
            list.add(m.getKey().getKey());
            plugin.saveSettings();
            Msg.ok(sender, Msg.pretty(m.name()) + " is now banned.");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("list", "remove");
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return Settings.BANNED_ITEMS.get();
            return List.of();
        }
    }

    static final class ItemLimit extends SmpCommand {
        ItemLimit(SmpCore plugin) {
            super(plugin, "itemlimit", Material.BUNDLE, "Limit how many of an item a player can carry",
                    "/<command> <amount> (held item) | <item> <amount> | remove <item> | list", ADMIN);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            ListSetting list = Settings.ITEM_LIMITS;
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                Msg.send(sender, "<gray>Item limits: " + list.display());
                return;
            }
            if (args[0].equalsIgnoreCase("remove") && args.length > 1) {
                Material m = Items.material(args[1]);
                List<String> copy = new ArrayList<>(list.get());
                boolean removed = m != null && copy.removeIf(s -> Items.material(s.substring(0, Math.max(0, s.lastIndexOf(':')))) == m);
                list.set(copy);
                plugin.saveSettings();
                if (removed) Msg.ok(sender, "Removed the limit."); else Msg.error(sender, "No limit for that item.");
                return;
            }
            Material m;
            String amountArg;
            if (args.length >= 2) {
                m = Items.material(args[0]);
                amountArg = args[1];
            } else if (sender instanceof Player p) {
                m = p.getInventory().getItemInMainHand().getType();
                amountArg = args[0];
            } else {
                usage(sender);
                return;
            }
            if (m == null || m.isAir()) {
                Msg.error(sender, "Hold an item or name one.");
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(amountArg);
            } catch (NumberFormatException e) {
                Msg.error(sender, "Amount must be a number.");
                return;
            }
            List<String> copy = new ArrayList<>(list.get());
            copy.removeIf(s -> s.contains(":") && Items.material(s.substring(0, s.lastIndexOf(':'))) == m);
            copy.add(m.getKey().getKey() + ":" + amount);
            list.set(copy);
            plugin.saveSettings();
            Msg.ok(sender, "Players can now carry at most " + amount + " " + Msg.pretty(m.name()) + ".");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("list", "remove", "1", "2", "4", "16");
            return List.of();
        }
    }

    static final class Unbreakable extends SmpCommand {
        Unbreakable(SmpCore plugin) {
            super(plugin, "unbreakable", Material.NETHERITE_CHESTPLATE, "Toggle unbreakable on the held item", "/<command>", ADMIN);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                Msg.error(p, "Hold an item first.");
                return;
            }
            ItemMeta meta = item.getItemMeta();
            meta.setUnbreakable(!meta.isUnbreakable());
            item.setItemMeta(meta);
            Msg.ok(p, "Item is " + (meta.isUnbreakable() ? "now unbreakable." : "breakable again."));
        }
    }

    static final class Enchant extends SmpCommand {
        Enchant(SmpCore plugin) {
            super(plugin, "enchant", Material.ENCHANTING_TABLE, "Enchant the held item with any level (better /enchant)",
                    "/<command> <enchantment> [level] [player]", ADMIN);
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Enchantment ench = Items.enchantment(args[0]);
            if (ench == null) {
                Msg.error(sender, "Unknown enchantment " + Msg.escape(args[0]) + ".");
                return;
            }
            int level = 1;
            if (args.length > 1) {
                try {
                    level = Math.max(0, Math.min(255, Integer.parseInt(args[1])));
                } catch (NumberFormatException e) {
                    Msg.error(sender, "Level must be a number.");
                    return;
                }
            }
            Player t = args.length > 2 ? online(sender, args[2]) : requirePlayer(sender);
            if (t == null) return;
            ItemStack item = t.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                Msg.error(sender, "That player isn't holding anything.");
                return;
            }
            if (level == 0) {
                item.removeEnchantment(ench);
                Msg.ok(sender, "Removed " + ench.getKey().getKey() + ".");
            } else {
                item.addUnsafeEnchantment(ench, level);
                Msg.ok(sender, "Applied " + Msg.pretty(ench.getKey().getKey()) + " " + level + ".");
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                List<String> out = new ArrayList<>();
                Registry.ENCHANTMENT.forEach(e -> out.add(e.getKey().getKey()));
                return out;
            }
            if (args.length == 2) return List.of("1", "5", "10", "255");
            return args.length == 3 ? null : List.of();
        }
    }

    static final class TickRate extends SmpCommand {
        TickRate(SmpCore plugin) {
            super(plugin, "setticksrate", Material.CLOCK, "Change the server tick rate (no PacketEvents needed)",
                    "/<command> <tps|reset>", ADMIN, "tickrate");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                Msg.send(sender, "<gray>Tick rate: <white>" + Bukkit.getServerTickManager().getTickRate() + "</white> tps");
                return;
            }
            float rate;
            if (args[0].equalsIgnoreCase("reset")) {
                rate = 20f;
            } else {
                try {
                    rate = Float.parseFloat(args[0]);
                } catch (NumberFormatException e) {
                    Msg.error(sender, "Rate must be a number.");
                    return;
                }
            }
            rate = Math.max(1f, Math.min(10000f, rate));
            Settings.TICK_RATE.set((double) Math.min(100, Math.round(rate)));
            Bukkit.getServerTickManager().setTickRate(rate);
            plugin.saveSettings();
            Msg.ok(sender, "Tick rate set to " + rate + " tps.");
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return List.of("reset", "10", "20", "40", "60");
        }
    }

    // ------------------------------------------------------------------ rollback

    static final class Rollback extends SmpCommand {
        Rollback(SmpCore plugin) {
            super(plugin, "rollback", Material.RECOVERY_COMPASS, "View a player's recent deaths and restore inventories",
                    "/<command> <player>", STAFF, "deaths", "restoreinv");
        }

        @Override
        protected void run(CommandSender sender, String label, String[] args) {
            Player viewer = requirePlayer(sender);
            if (viewer == null) return;
            if (args.length < 1) {
                usage(sender);
                return;
            }
            OfflinePlayer target = offline(sender, args[0]);
            if (target == null) return;
            openList(viewer, target);
        }

        private void openList(Player viewer, OfflinePlayer target) {
            List<Map<?, ?>> deaths = plugin.features().players().deaths(target.getUniqueId());
            if (deaths.isEmpty()) {
                Dialogs.message(viewer, "<#67e8f9><b>Rollback</b>", "<gray>" + Msg.escape(String.valueOf(target.getName())) + " has no recorded deaths.", null);
                return;
            }
            List<ActionButton> buttons = new ArrayList<>();
            for (int i = 0; i < deaths.size(); i++) {
                Map<?, ?> d = deaths.get(i);
                int index = i;
                long time = ((Number) d.get("time")).longValue();
                String tooltip = "<gray>" + Msg.escape(String.valueOf(d.get("reason"))) + "<newline><dark_gray>" + d.get("world") + " "
                        + d.get("x") + ", " + d.get("y") + ", " + d.get("z");
                buttons.add(Dialogs.button("<red>☠</red> <white>Death #" + (i + 1) + "</white> <gray>" + Msg.duration(System.currentTimeMillis() - time) + " ago",
                        tooltip, Dialogs.WIDE, p -> openDeath(p, target, index)));
            }
            Dialogs.show(viewer, Dialogs.menu("<#67e8f9><b>Rollback</b> <gray>" + Msg.escape(String.valueOf(target.getName())),
                    List.of(Dialogs.text("<gray>Last " + deaths.size() + " deaths. Pick one to inspect or restore.")),
                    List.of(), buttons, Dialogs.close("<gray>Close"), 1));
        }

        private void openDeath(Player viewer, OfflinePlayer target, int index) {
            List<Map<?, ?>> deaths = plugin.features().players().deaths(target.getUniqueId());
            if (index >= deaths.size()) return;
            Map<?, ?> d = deaths.get(index);
            ItemStack[] items = Items.decode(String.valueOf(d.get("inventory")), 41);
            int count = 0;
            for (ItemStack i : items) if (i != null) count++;
            String info = "<red><b>DEATH #" + (index + 1) + "</b></red><newline>"
                    + "<gray>Day: <white>" + PlayerFeature.formatTime(((Number) d.get("time")).longValue()) + "</white><newline>"
                    + "<gray>Reason: <white>" + Msg.escape(String.valueOf(d.get("reason"))) + "</white><newline>"
                    + "<gray>World: <white>" + d.get("world") + "</white><newline>"
                    + "<gray>Coords: <white>" + d.get("x") + ", " + d.get("y") + ", " + d.get("z") + "</white><newline>"
                    + "<gray>XP level: <white>" + d.get("level") + "</white> • Item stacks: <white>" + count;
            List<DialogBody> body = List.of(Dialogs.item(new ItemStack(Material.SKELETON_SKULL), info));
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(Dialogs.button("<aqua>👁 View Items", "<gray>Preview the inventory", Dialogs.NORMAL, p -> preview(p, target, index, items)));
            buttons.add(Dialogs.button("<green>⟲ Restore (replace)", "<gray>Replace their inventory with this one", Dialogs.NORMAL,
                    p -> restore(p, target, items, true)));
            buttons.add(Dialogs.button("<yellow>+ Give items (add)", "<gray>Add the items to their inventory", Dialogs.NORMAL,
                    p -> restore(p, target, items, false)));
            buttons.add(Dialogs.button("<light_purple>➶ Teleport there", "<gray>Go to the death location", Dialogs.NORMAL, p -> {
                World w = Bukkit.getWorld(String.valueOf(d.get("world")));
                if (w != null) p.teleportAsync(new Location(w, ((Number) d.get("x")).doubleValue() + 0.5,
                        ((Number) d.get("y")).doubleValue(), ((Number) d.get("z")).doubleValue() + 0.5));
            }));
            Dialogs.show(viewer, Dialogs.menu("<#67e8f9><b>Rollback</b> <gray>" + Msg.escape(String.valueOf(target.getName())), body, List.of(), buttons,
                    Dialogs.button("<gray>« Back", null, Dialogs.SMALL, p -> openList(p, target)), 2));
        }

        private void preview(Player viewer, OfflinePlayer target, int index, ItemStack[] items) {
            ChestGui gui = new ChestGui(6, "<dark_gray>Death #" + (index + 1) + " - " + Msg.escape(String.valueOf(target.getName())), false);
            for (int i = 0; i < 36; i++) gui.set(i < 9 ? i + 27 : i - 9, items[i]);
            for (int i = 36; i < 41; i++) gui.set(i + 9, items[i]);
            ItemStack pane = Items.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 36; i < 45; i++) gui.set(i, pane);
            gui.button(51, Items.icon(Material.LIME_CONCRETE, "<green><b>Restore (replace)", "<gray>Replace their inventory"), p -> {
                p.closeInventory();
                restore(p, target, items, true);
            });
            gui.button(52, Items.icon(Material.YELLOW_CONCRETE, "<yellow><b>Give items (add)", "<gray>Add to their inventory"), p -> {
                p.closeInventory();
                restore(p, target, items, false);
            });
            gui.button(53, Items.icon(Material.ARROW, "<gray>Back"), p -> {
                p.closeInventory();
                openDeath(p, target, index);
            });
            gui.open(viewer);
        }

        private void restore(Player viewer, OfflinePlayer target, ItemStack[] items, boolean replace) {
            Player online = target.getPlayer();
            if (online == null) {
                YamlStore data = plugin.data().player(target.getUniqueId());
                ItemStack[] base = replace ? items : merge(Items.decode(data.yaml().getString("snapshot.inventory"), 41), items);
                data.set("snapshot.inventory", Items.encode(base));
                data.set("offline-edit.inventory", true);
                data.save();
                Msg.ok(viewer, "Restored - applies when " + target.getName() + " joins.");
                return;
            }
            if (replace) {
                online.getInventory().setContents(items);
            } else {
                for (ItemStack i : items) {
                    if (i == null) continue;
                    online.getInventory().addItem(i.clone()).values().forEach(left -> online.getWorld().dropItemNaturally(online.getLocation(), left));
                }
            }
            Msg.ok(viewer, "Inventory restored for " + online.getName() + ".");
            Msg.ok(online, "Your inventory was restored by staff.");
        }

        private static ItemStack[] merge(ItemStack[] base, ItemStack[] add) {
            List<ItemStack> extra = new ArrayList<>();
            for (int i = 0; i < add.length; i++) {
                if (add[i] == null) continue;
                if (base[i] == null) base[i] = add[i]; else extra.add(add[i]);
            }
            for (ItemStack e : extra) {
                for (int i = 0; i < 36; i++) {
                    if (base[i] == null) {
                        base[i] = e;
                        break;
                    }
                }
            }
            return base;
        }
    }
}
