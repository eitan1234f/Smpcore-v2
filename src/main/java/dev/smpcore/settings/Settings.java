package dev.smpcore.settings;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.smpcore.settings.Category.*;

/**
 * Registry of every SmpCore setting. Each feature reads its values from here.
 */
public final class Settings {
    private static final Map<String, Setting<?>> REGISTRY = new LinkedHashMap<>();
    private static FileConfiguration config;

    private Settings() {
    }

    public static FileConfiguration config() {
        return config;
    }

    public static void bind(FileConfiguration cfg) {
        config = cfg;
        for (Setting<?> s : REGISTRY.values()) {
            if (!cfg.isSet(s.path())) {
                cfg.set(s.path(), s instanceof NumberSetting n && n.integral() ? (Object) (int) Math.round(n.def()) : s.def());
            }
        }
    }

    public static <S extends Setting<?>> S register(S setting) {
        REGISTRY.put(setting.path(), setting);
        if (config != null && !config.isSet(setting.path())) {
            config.set(setting.path(), setting.def());
        }
        return setting;
    }

    public static List<Setting<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    public static List<Setting<?>> in(Category category) {
        return REGISTRY.values().stream().filter(s -> s.category() == category).toList();
    }

    public static Setting<?> byPath(String path) {
        return REGISTRY.get(path);
    }

    public static List<Setting<?>> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String[] words = keyword.trim().split("\\s+");
        return REGISTRY.values().stream().filter(s -> {
            for (String w : words) {
                if (!s.matches(w)) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    // ------------------------------------------------------------------ factories

    public static BoolSetting bool(String path, Category c, Material icon, String name, String desc, boolean def) {
        return register(new BoolSetting(path, c, icon, name, desc, def));
    }

    public static NumberSetting num(String path, Category c, Material icon, String name, String desc,
                                    double def, double min, double max, double step, String unit) {
        return register(new NumberSetting(path, c, icon, name, desc, def, min, max, step, unit));
    }

    public static ChoiceSetting choice(String path, Category c, Material icon, String name, String desc,
                                       String def, String... options) {
        return register(new ChoiceSetting(path, c, icon, name, desc, def, List.of(options)));
    }

    public static TextSetting text(String path, Category c, Material icon, String name, String desc, String def, int max) {
        return register(new TextSetting(path, c, icon, name, desc, def, max));
    }

    public static ListSetting list(String path, Category c, Material icon, String name, String desc, String... def) {
        return register(new ListSetting(path, c, icon, name, desc, List.of(def)));
    }

    // ================================================================== GENERAL
    public static final TextSetting PREFIX = text("general.prefix", GENERAL, Material.NAME_TAG, "Message Prefix",
            "MiniMessage prefix put in front of SmpCore messages",
            "<gradient:#a855f7:#ec4899><b>SMP</b></gradient> <dark_gray>»</dark_gray> ", 128);
    public static final BoolSetting PLAYERS_VIEW_SETTINGS = bool("general.players-can-view-settings", GENERAL, Material.SPYGLASS,
            "Players Can View /settings", "Lets normal players open /settings read-only so they know what is allowed", true);
    public static final TextSetting DISCORD_LINK = text("general.discord-link", GENERAL, Material.LIGHT_BLUE_DYE, "Discord Link",
            "Link sent by /discord", "https://discord.gg/your-server", 200);
    public static final TextSetting STORE_LINK = text("general.store-link", GENERAL, Material.GOLD_INGOT, "Store Link",
            "Link sent by /store", "https://store.example.com", 200);
    public static final TextSetting FIRST_JOIN_KIT = text("general.first-join-kit", GENERAL, Material.CHEST, "First Join Kit",
            "Kit given to new players on their first join (empty = none)", "", 32);
    public static final BoolSetting JOIN_MESSAGES = bool("general.custom-join-messages", GENERAL, Material.OAK_SIGN,
            "Custom Join/Quit Messages", "Replace vanilla join and quit messages with styled ones", false);
    public static final BoolSetting INSTALLER_ENABLED = bool("general.plugin-installer", GENERAL, Material.HOPPER,
            "Plugin Installer", "Allow admins to browse and install any Modrinth plugin with /smpcore plugins", true);
    public static final BoolSetting INSTALLER_COMPATIBLE_ONLY = bool("general.installer-compatible-only", GENERAL, Material.HOPPER_MINECART,
            "Installer: Compatible Only", "Only show plugins that have a release for this server's Minecraft version", true);

    // ================================================================== PROTECTION
    public static final BoolSetting NAKED_PROTECTION = bool("protection.naked", PROTECTION, Material.LEATHER_CHESTPLATE,
            "Naked Protection", "Players wearing no armor can't be damaged by players who wear armor", false);
    public static final BoolSetting AFK_PROTECTION = bool("protection.afk.enabled", PROTECTION, Material.CLOCK,
            "AFK Protection", "AFK players can't be hurt by other players", false);
    public static final NumberSetting AFK_MINUTES = num("protection.afk.minutes", PROTECTION, Material.CLOCK,
            "AFK After", "Minutes without moving before a player counts as AFK", 5, 1, 60, 1, "min");
    public static final BoolSetting VOID_PROTECTION = bool("protection.void", PROTECTION, Material.END_PORTAL_FRAME,
            "Void Protection", "Teleports players to the closest land when they fall into the void", false);
    public static final BoolSetting IMMORTAL_DEATH_DROPS = bool("protection.immortal-death-drops", PROTECTION, Material.NETHERITE_SCRAP,
            "Immortal Death Drops", "Items dropped on death can't burn, explode or despawn", false);
    public static final BoolSetting TOTEM_EFFECTS = bool("protection.totem-keeps-effects", PROTECTION, Material.TOTEM_OF_UNDYING,
            "Totem Keeps Effects", "Keep your potion effects after popping a totem", false);
    public static final BoolSetting ANTI_STASIS = bool("protection.anti-stasis", PROTECTION, Material.ENDER_PEARL,
            "Anti Stasis", "Removes ender pearls that have been flying/stuck too long (stasis chambers)", false);
    public static final NumberSetting ANTI_STASIS_SECONDS = num("protection.anti-stasis-seconds", PROTECTION, Material.ENDER_PEARL,
            "Stasis Pearl Lifetime", "Seconds a thrown pearl may exist before being removed", 20, 5, 600, 5, "s");
    public static final NumberSetting DEATH_BAN_MINUTES = num("protection.death-ban-minutes", PROTECTION, Material.SKELETON_SKULL,
            "Death Ban", "Ban players for this many minutes when they die (0 = off)", 0, 0, 1440, 5, "min");

    // ================================================================== COMBAT
    public static final BoolSetting PVP = bool("combat.pvp", COMBAT, Material.IRON_SWORD,
            "PvP", "Global PvP switch", true);
    public static final BoolSetting COMBAT_TAG = bool("combat.tag.enabled", COMBAT, Material.REDSTONE,
            "Combat Tag", "Players hit by other players are tagged in combat", true);
    public static final NumberSetting COMBAT_TAG_SECONDS = num("combat.tag.seconds", COMBAT, Material.REDSTONE,
            "Combat Tag Time", "How long a combat tag lasts", 15, 5, 120, 1, "s");
    public static final BoolSetting COMBAT_LOG_KILL = bool("combat.tag.kill-on-logout", COMBAT, Material.SKELETON_SKULL,
            "Kill Combat Loggers", "Players who log out while tagged are killed", true);
    public static final BoolSetting COMBAT_NO_ELYTRA = bool("combat.tag.block-elytra", COMBAT, Material.ELYTRA,
            "No Elytra In Combat", "Tagged players can't glide", false);
    public static final BoolSetting COMBAT_NO_PEARLS = bool("combat.tag.block-pearls", COMBAT, Material.ENDER_PEARL,
            "No Pearls In Combat", "Tagged players can't throw ender pearls", false);
    public static final BoolSetting COMBAT_NO_COMMANDS = bool("combat.tag.block-commands", COMBAT, Material.COMMAND_BLOCK,
            "No Commands In Combat", "Tagged players can't use teleport/home commands", true);
    public static final BoolSetting COMBAT_NO_ENDERCHEST = bool("combat.tag.block-enderchest", COMBAT, Material.ENDER_CHEST,
            "No Ender Chest In Combat", "Tagged players can't open ender chests", false);
    public static final BoolSetting COMBAT_NO_RIPTIDE = bool("combat.tag.block-riptide", COMBAT, Material.TRIDENT,
            "No Riptide In Combat", "Tagged players can't launch with riptide tridents", false);
    public static final NumberSetting DAMAGE_MODIFIER = num("combat.damage-modifier", COMBAT, Material.REDSTONE_BLOCK,
            "Damage Modifier", "Change all player-vs-player damage by this percent", 0, -90, 200, 5, "%");
    public static final BoolSetting ATTRIBUTE_SWAP_FIX = bool("combat.attribute-swap-fix", COMBAT, Material.MACE,
            "Attribute Swap Fix", "Blocks hits that happen right after swapping hotbar slots", false);
    public static final BoolSetting BAN_BREACH_SWAP = bool("combat.ban-breach-swap", COMBAT, Material.MACE,
            "Ban Breach Swapping", "Blocks Breach hits made right after swapping to the breach weapon", false);
    public static final BoolSetting MACE_STUNS_SHIELD = bool("combat.mace-stuns-shield", COMBAT, Material.MACE,
            "Mace Stuns Shield", "Hitting a blocking player with a mace disables their shield", false);
    public static final BoolSetting SHIELD_DELAY_FIX = bool("combat.shield.delay-fix", COMBAT, Material.SHIELD,
            "Shield Delay Fix", "Removes the 5 tick delay before a raised shield blocks", false);
    public static final BoolSetting SHIELD_STUNS = bool("combat.shield.axe-stuns", COMBAT, Material.IRON_AXE,
            "Axes Stun Shields", "Turn off to stop axes from disabling shields", true);
    public static final BoolSetting INVIS_HIDE_KILLER = bool("combat.invis.hide-killer", COMBAT, Material.FERMENTED_SPIDER_EYE,
            "Invis: Hide Killer Name", "Hide an invisible killer's name in death messages", false);
    public static final BoolSetting INVIS_HIDE_VICTIM = bool("combat.invis.hide-victim", COMBAT, Material.FERMENTED_SPIDER_EYE,
            "Invis: Hide Victim Name", "Hide an invisible victim's name in death messages", false);
    public static final TextSetting KILL_SOUND = text("combat.kill-sound", COMBAT, Material.NOTE_BLOCK,
            "Custom Kill Sound", "Sound played to a killer (e.g. entity.player.levelup), empty = off", "", 64);
    public static final BoolSetting LIGHTNING_ON_KILL = bool("combat.lightning-on-kill", COMBAT, Material.LIGHTNING_ROD,
            "Lightning On Death", "Strike harmless lightning where a player dies", false);

    // ================================================================== COOLDOWNS
    public static final NumberSetting CD_PEARL = num("cooldowns.ender-pearl", COOLDOWNS, Material.ENDER_PEARL,
            "Ender Pearl", "Cooldown after throwing a pearl", 0, 0, 120, 1, "s");
    public static final NumberSetting CD_WIND_CHARGE = num("cooldowns.wind-charge", COOLDOWNS, Material.WIND_CHARGE,
            "Wind Charge", "Cooldown after throwing a wind charge", 0, 0, 120, 1, "s");
    public static final NumberSetting CD_GAPPLE = num("cooldowns.golden-apple", COOLDOWNS, Material.GOLDEN_APPLE,
            "Golden Apple", "Cooldown after eating a golden apple", 0, 0, 300, 1, "s");
    public static final NumberSetting CD_EGAPPLE = num("cooldowns.enchanted-golden-apple", COOLDOWNS, Material.ENCHANTED_GOLDEN_APPLE,
            "Enchanted Golden Apple", "Cooldown after eating a notch apple", 0, 0, 600, 5, "s");
    public static final NumberSetting CD_CHORUS = num("cooldowns.chorus-fruit", COOLDOWNS, Material.CHORUS_FRUIT,
            "Chorus Fruit", "Cooldown after eating chorus fruit", 0, 0, 120, 1, "s");
    public static final NumberSetting CD_TRIDENT = num("cooldowns.riptide", COOLDOWNS, Material.TRIDENT,
            "Riptide Trident", "Cooldown after launching with riptide", 0, 0, 120, 1, "s");
    public static final NumberSetting CD_MACE = num("cooldowns.mace", COOLDOWNS, Material.MACE,
            "Mace Smash", "Cooldown after landing a mace smash attack", 0, 0, 120, 1, "s");
    public static final NumberSetting CD_FIREWORK = num("cooldowns.elytra-firework", COOLDOWNS, Material.FIREWORK_ROCKET,
            "Elytra Firework", "Cooldown after boosting with a firework", 0, 0, 60, 1, "s");
    public static final NumberSetting CD_TOTEM = num("cooldowns.totem", COOLDOWNS, Material.TOTEM_OF_UNDYING,
            "Totem Of Undying", "Seconds after popping before another totem works", 0, 0, 300, 5, "s");
    public static final NumberSetting CD_SHIELD = num("cooldowns.shield", COOLDOWNS, Material.SHIELD,
            "Shield Disable", "Extra seconds a shield stays disabled after an axe hit", 0, 0, 30, 1, "s");
    public static final NumberSetting CD_SPEAR = num("cooldowns.spear", COOLDOWNS, Material.IRON_SPEAR,
            "Spear Lunge", "Cooldown after damaging with a spear", 0, 0, 60, 1, "s");

    // ================================================================== DAMAGE LIMITERS
    public static final NumberSetting DL_MACE = num("damage-limiters.mace", DAMAGE_LIMITERS, Material.MACE,
            "Mace", "Maximum damage of one mace hit (hearts x2)", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_CRYSTAL = num("damage-limiters.end-crystal", DAMAGE_LIMITERS, Material.END_CRYSTAL,
            "End Crystal", "Maximum damage from an end crystal explosion", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_ANCHOR = num("damage-limiters.respawn-anchor", DAMAGE_LIMITERS, Material.RESPAWN_ANCHOR,
            "Respawn Anchor", "Maximum damage from an anchor explosion", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_BED = num("damage-limiters.bed", DAMAGE_LIMITERS, Material.RED_BED,
            "Bed Explosion", "Maximum damage from a bed explosion", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_TNT = num("damage-limiters.tnt", DAMAGE_LIMITERS, Material.TNT,
            "TNT", "Maximum damage from TNT", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_TNT_MINECART = num("damage-limiters.tnt-minecart", DAMAGE_LIMITERS, Material.TNT_MINECART,
            "TNT Minecart", "Maximum damage from a TNT minecart", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_ARROW = num("damage-limiters.arrow", DAMAGE_LIMITERS, Material.ARROW,
            "Arrows", "Maximum damage of one arrow", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_TRIDENT = num("damage-limiters.trident", DAMAGE_LIMITERS, Material.TRIDENT,
            "Trident", "Maximum damage of a trident hit or throw", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_SPEAR = num("damage-limiters.spear", DAMAGE_LIMITERS, Material.DIAMOND_SPEAR,
            "Spear", "Maximum damage of a spear hit", 0, 0, 100, 1, "HP");
    public static final NumberSetting DL_ANY_PVP = num("damage-limiters.any-player-hit", DAMAGE_LIMITERS, Material.NETHERITE_SWORD,
            "Any Player Hit", "Maximum damage of any single player-vs-player hit", 0, 0, 100, 1, "HP");

    // ================================================================== BANS
    public static final BoolSetting BAN_MACE = bool("bans.mace", BANS, Material.MACE,
            "Ban The Mace", "Mace can't be crafted or used", false);
    public static final BoolSetting MACE_ONE_CRAFT = bool("bans.mace-one-time-craft", BANS, Material.HEAVY_CORE,
            "Mace: One Time Craft", "Only one mace can ever be crafted on the server", false);
    public static final BoolSetting BAN_CRYSTALS = bool("bans.explosives.end-crystal", BANS, Material.END_CRYSTAL,
            "Ban Crystal PvP", "End crystals can't damage players (they still work for the dragon)", false);
    public static final BoolSetting BAN_ANCHORS = bool("bans.explosives.respawn-anchor", BANS, Material.RESPAWN_ANCHOR,
            "Ban Anchor Explosions", "Respawn anchors can't explode outside the nether", false);
    public static final BoolSetting BAN_BEDS = bool("bans.explosives.bed", BANS, Material.RED_BED,
            "Ban Bed Explosions", "Beds can't explode in the nether or end", false);
    public static final BoolSetting BAN_TNT_MINECART = bool("bans.explosives.tnt-minecart", BANS, Material.TNT_MINECART,
            "Ban TNT Minecarts", "TNT minecarts can't be placed", false);
    public static final BoolSetting BAN_TNT = bool("bans.explosives.tnt", BANS, Material.TNT,
            "Ban TNT", "TNT can't be placed", false);
    public static final NumberSetting BAN_LAVA_RADIUS = num("bans.lava-near-players", BANS, Material.LAVA_BUCKET,
            "Ban Lava Near Players", "Players can't place lava within this many blocks of another player (0 = off)", 0, 0, 16, 1, "blocks");
    public static final BoolSetting BAN_TIPPED_ARROWS = bool("bans.tipped-arrows", BANS, Material.TIPPED_ARROW,
            "Ban Tipped Arrows", "Tipped arrows can't be crafted or shot", false);
    public static final BoolSetting BAN_NETHER = bool("bans.dimensions.nether", BANS, Material.NETHERRACK,
            "Ban The Nether", "Nobody can enter the nether", false);
    public static final BoolSetting BAN_END = bool("bans.dimensions.end", BANS, Material.END_STONE,
            "Ban The End", "Nobody can enter the end", false);
    public static final ListSetting BANNED_NETHERITE = list("bans.netherite", BANS, Material.NETHERITE_INGOT,
            "Ban Netherite Pieces", "Netherite items that can't be made (e.g. netherite_helmet, netherite_sword)");
    public static final ListSetting BANNED_ITEMS = list("bans.items", BANS, Material.BARRIER,
            "Item Ban", "Items that can't be crafted, picked up or used");
    public static final ListSetting BANNED_ENCHANTS = list("bans.enchants", BANS, Material.ENCHANTED_BOOK,
            "Enchant Ban", "Banned enchants. Use name (mending) to ban or name:level (sharpness:3) to cap");
    public static final ListSetting BANNED_POTIONS = list("bans.potions", BANS, Material.POTION,
            "Potion Ban", "Banned potion effects (e.g. strength, invisibility, turtle_master effects: resistance)");
    public static final BoolSetting PURGE_BANNED = bool("bans.purge-from-inventories", BANS, Material.LAVA_BUCKET,
            "Purge Banned Items", "Remove banned items and over-level enchants from inventories automatically", true);
    public static final ListSetting CRAFT_LIMITS = list("bans.craft-limits", BANS, Material.CRAFTER,
            "Craft Limit", "Server-wide craft limits as item:amount (e.g. mace:3)");
    public static final ListSetting ITEM_LIMITS = list("bans.inventory-limits", BANS, Material.BUNDLE,
            "Item Limit", "Max amount a player can carry as item:amount (e.g. totem_of_undying:2)");
    public static final ListSetting MOB_DISABLER = list("bans.disabled-mobs", BANS, Material.SPAWNER,
            "Mob Disabler", "Entity types that never spawn naturally (e.g. phantom, wither)");
    public static final BoolSetting BLOCK_PLACING = bool("bans.block-placing", BANS, Material.GRASS_BLOCK,
            "Block Placing", "Turn off to stop non-admins from placing blocks", true);
    public static final BoolSetting BLOCK_BREAKING = bool("bans.block-breaking", BANS, Material.IRON_PICKAXE,
            "Block Breaking", "Turn off to stop non-admins from breaking blocks", true);
    public static final BoolSetting BAN_ELYTRA = bool("bans.elytra", BANS, Material.ELYTRA,
            "Ban Elytra", "Nobody can glide with elytra", false);

    // ================================================================== WORLD
    public static final BoolSetting ONE_PLAYER_SLEEP = bool("world.one-player-sleep", WORLD, Material.RED_BED,
            "One Player Sleep", "Only one player needs to sleep to skip the night", true);
    public static final BoolSetting LOCATOR_BAR = bool("world.locator-bar", WORLD, Material.COMPASS,
            "Locator Bar", "Show the player locator bar", true);
    public static final BoolSetting FIRE_SPREAD = bool("world.fire-spread", WORLD, Material.FLINT_AND_STEEL,
            "Fire Spread", "Turn off to stop fire from spreading and burning blocks", true);
    public static final BoolSetting NO_FALLING_BLOCKS = bool("world.no-falling-blocks", WORLD, Material.SAND,
            "No Falling Blocks", "Stops sand, gravel and concrete powder from falling", false);
    public static final NumberSetting VIEW_DISTANCE = num("world.render-distance", WORLD, Material.SPYGLASS,
            "Render Distance", "Server view distance in chunks (0 = server default)", 0, 0, 32, 1, "chunks");
    public static final NumberSetting SIMULATION_DISTANCE = num("world.simulation-distance", WORLD, Material.REPEATER,
            "Simulation Distance", "Server simulation distance in chunks (0 = server default)", 0, 0, 32, 1, "chunks");
    public static final ChoiceSetting ANTI_XRAY = choice("world.anti-xray", WORLD, Material.DIAMOND_ORE,
            "Anti-Xray", "Paper's anti-xray engine mode: off, 1 (hide ores), 2 (fake ores), 3 (fake ores, less lag)",
            "off", "off", "1", "2", "3");
    public static final BoolSetting ANTI_SEED_CRACKING = bool("world.anti-seed-cracking", WORLD, Material.WHEAT_SEEDS,
            "Anti Seed Cracking", "Randomizes feature seeds (Paper) and hides /seed from players", false);
    public static final BoolSetting TREE_FELLER = bool("world.tree-feller", WORLD, Material.DIAMOND_AXE,
            "Tree Feller", "Sneak + break a log with an axe to chop the whole tree", false);
    public static final BoolSetting VEINMINER = bool("world.veinminer", WORLD, Material.DIAMOND_PICKAXE,
            "Veinminer", "Sneak + break an ore with a pickaxe to mine the whole vein", false);
    public static final NumberSetting VEIN_MAX = num("world.veinminer-max-blocks", WORLD, Material.DIAMOND_PICKAXE,
            "Tree/Vein Max Blocks", "Maximum blocks broken at once", 64, 8, 256, 8, "blocks");
    public static final BoolSetting SIT = bool("world.sit", WORLD, Material.OAK_STAIRS,
            "Sit", "Right-click stairs or slabs with an empty hand to sit", true);
    public static final NumberSetting APPLE_CHANCE = num("world.apple-drop-chance", WORLD, Material.APPLE,
            "Apple Drops", "Extra chance (percent) for any leaves to drop an apple", 0, 0, 100, 0.5, "%");
    public static final ListSetting BLOCK_DECAY = list("world.block-decay", WORLD, Material.COBWEB,
            "Block Decay", "Player-placed blocks that vanish after some seconds as block:seconds (e.g. cobweb:10)");
    public static final BoolSetting ARMOR_STAND_ARMS = bool("world.armor-stand-arms", WORLD, Material.ARMOR_STAND,
            "Armor Stand Arms", "Placed armor stands always have arms", true);
    public static final NumberSetting HAPPY_GHAST_SPEED = num("world.happy-ghast-speed", WORLD, Material.GHAST_TEAR,
            "Happy Ghast Speed", "Flying speed multiplier for happy ghasts", 1, 0.5, 5, 0.25, "x");
    public static final ListSetting GLOWING_ITEMS = list("world.glowing-items", WORLD, Material.GLOWSTONE_DUST,
            "Glowing Dropped Items", "Dropped items of these types glow so they are easy to find",
            "mace", "heavy_core", "elytra", "totem_of_undying", "netherite_ingot", "dragon_egg");
    public static final BoolSetting INSTANT_BREWING = bool("world.instant-brewing", WORLD, Material.BREWING_STAND,
            "Instant Brewing", "Brewing stands finish instantly", false);
    public static final NumberSetting BOOK_MAX_PAGES = num("world.book-max-pages", WORLD, Material.WRITABLE_BOOK,
            "Book Size Limit: Pages", "Max pages in a book (stops chunk-ban and dupe books)", 50, 1, 100, 1, "pages");
    public static final NumberSetting BOOK_MAX_CHARS = num("world.book-max-chars", WORLD, Material.WRITABLE_BOOK,
            "Book Size Limit: Characters", "Max characters on one page", 256, 32, 1024, 32, "chars");
    public static final NumberSetting TICK_RATE = num("world.tick-rate", WORLD, Material.CLOCK,
            "Server Tick Rate", "Ticks per second (20 = normal)", 20, 1, 100, 1, "tps");

    // ================================================================== VILLAGERS & VAULTS
    public static final BoolSetting INFINITE_RESTOCK = bool("villagers.infinite-restock", VILLAGERS, Material.EMERALD,
            "Infinite Restock", "Villager trades never run out", false);
    public static final BoolSetting TRADE_CYCLE = bool("villagers.trade-cycle", VILLAGERS, Material.LECTERN,
            "Trade Cycle", "Sneak + right-click an untraded villager with an empty hand to reroll its trades", true);
    public static final ListSetting DISABLED_TRADES = list("villagers.disabled-trades", VILLAGERS, Material.BOOK,
            "Disable Villager Trades", "Trades removed from villagers: item names or enchant names (e.g. mending)");
    public static final BoolSetting DISABLE_VAULTS = bool("villagers.disable-vaults", VILLAGERS, Material.VAULT,
            "Disable Vaults", "Trial chamber vaults can't be opened", false);
    public static final NumberSetting VAULT_REOPEN_MINUTES = num("villagers.reopenable-vaults-minutes", VILLAGERS, Material.TRIAL_KEY,
            "Reopenable Vaults", "Minutes before a player can open the same vault again (0 = vanilla, never)", 0, 0, 1440, 10, "min");
    public static final NumberSetting HEAVY_CORE_CHANCE = num("villagers.heavy-core-chance", VILLAGERS, Material.HEAVY_CORE,
            "Heavy Core Drop", "Ominous vault heavy core handling: -1 vanilla, 0 never drop, otherwise extra % chance", -1, -1, 100, 1, "%");

    // ================================================================== ITEMS
    public static final NumberSetting POTION_STACK = num("items.potion-stack-size", ITEMS, Material.POTION,
            "Stackable Potions", "Max stack size for potions (1 = vanilla)", 1, 1, 64, 1, "");
    public static final BoolSetting EASY_RECIPES = bool("items.easy-recipes", ITEMS, Material.CRAFTING_TABLE,
            "Easy Recipes", "Adds handy recipes: name tags, saddles, string from wool, chainmail, bundles", false);
    public static final ListSetting REMOVED_RECIPES = list("items.removed-recipes", ITEMS, Material.BARRIER,
            "Vanilla Recipe Remover", "Vanilla recipe keys to remove (e.g. minecraft:end_crystal)");
    public static final BoolSetting UNBREAKABLE_ANVILS = bool("items.unbreakable-anvils", ITEMS, Material.ANVIL,
            "Unbreakable Anvils", "Anvils never get damaged", false);
    public static final BoolSetting ANVIL_FIX = bool("items.too-expensive-fix", ITEMS, Material.DAMAGED_ANVIL,
            "\"Too Expensive\" Fix", "Anvil repairs are never too expensive", true);
    public static final ListSetting RITUAL_ITEMS = list("items.ritual-items", ITEMS, Material.BEACON,
            "Rituals", "Crafting these items starts a public ritual instead of giving the item instantly", "mace");
    public static final NumberSetting RITUAL_SECONDS = num("items.ritual-seconds", ITEMS, Material.BEACON,
            "Ritual Duration", "How long a ritual lasts before the item drops", 300, 10, 3600, 10, "s");
    public static final BoolSetting RITUAL_BROADCAST_COORDS = bool("items.ritual-broadcast-coords", ITEMS, Material.COMPASS,
            "Ritual: Show Coordinates", "Broadcast the coordinates of a ritual to everyone", true);

    // ================================================================== CHAT
    public static final BoolSetting NO_CHAT_REPORTS = bool("chat.no-chat-reports", CHAT, Material.WRITABLE_BOOK,
            "No Chat Reports", "Sends chat as unsigned system messages so it can't be reported to Mojang", true);
    public static final BoolSetting CHAT_FILTER = bool("chat.filter.enabled", CHAT, Material.PAPER,
            "Chat Filter", "Censor words from the blocked words list", false);
    public static final ListSetting CHAT_FILTER_WORDS = list("chat.filter.words", CHAT, Material.PAPER,
            "Blocked Words", "Words replaced with stars in chat");
    public static final BoolSetting SPAM_LIMITER = bool("chat.spam.enabled", CHAT, Material.SLIME_BALL,
            "Spam Limiter", "Limit how fast players can chat and block repeated messages", true);
    public static final NumberSetting SPAM_DELAY = num("chat.spam.delay", CHAT, Material.SLIME_BALL,
            "Chat Delay", "Minimum time between messages", 1, 0, 10, 0.5, "s");
    public static final BoolSetting ANTI_MINIMAP = bool("chat.anti-minimap", CHAT, Material.FILLED_MAP,
            "Anti Minimap", "Tells Xaero, VoxelMap and JourneyMap to disable radar/minimap", false);
    public static final ChoiceSetting CHAT_FORMAT = choice("chat.format", CHAT, Material.NAME_TAG,
            "Chat Format", "How chat messages look", "modern", "vanilla", "modern", "arrow");

    // ================================================================== SECURITY
    public static final BoolSetting MAINTENANCE = bool("security.maintenance", SECURITY, Material.BARRIER,
            "Maintenance", "Only admins and whitelisted players can join", false);
    public static final TextSetting MAINTENANCE_MESSAGE = text("security.maintenance-message", SECURITY, Material.OAK_SIGN,
            "Maintenance Message", "Kick message shown during maintenance",
            "<red>The server is under maintenance.</red><newline><gray>Check back soon!</gray>", 256);
    public static final BoolSetting ANTI_VPN = bool("security.anti-vpn", SECURITY, Material.IRON_DOOR,
            "Anti VPN", "Block players joining through VPNs and proxies (uses ip-api.com)", false);
    public static final NumberSetting ANTI_ALT = num("security.max-accounts-per-ip", SECURITY, Material.PLAYER_HEAD,
            "Anti Alt", "Max accounts online from one IP (0 = unlimited)", 0, 0, 10, 1, "accounts");
    public static final BoolSetting MOD_DETECTOR = bool("security.mod-detector.enabled", SECURITY, Material.BLAZE_ROD,
            "Mod Detector", "Alerts staff about players' client brand and mod channels", true);
    public static final ListSetting BLOCKED_MODS = list("security.mod-detector.blocked", SECURITY, Material.BLAZE_POWDER,
            "Blocked Mods", "Players are kicked if their brand or channels contain any of these",
            "wurst", "meteor", "aristois", "liquidbounce", "impact", "future", "rusherhack", "freecam");
    public static final BoolSetting MAINTENANCE_MOTD = bool("security.maintenance-motd", SECURITY, Material.NAME_TAG,
            "Maintenance MOTD", "Show 'maintenance' in the server list while maintenance is on", true);

    // ================================================================== TELEPORT
    public static final NumberSetting TP_WARMUP = num("teleport.warmup", TELEPORT, Material.CLOCK,
            "Teleport Warmup", "Seconds standing still before teleporting (0 = instant)", 3, 0, 15, 1, "s");
    public static final NumberSetting TPA_TIMEOUT = num("teleport.tpa-timeout", TELEPORT, Material.ENDER_EYE,
            "TPA Timeout", "Seconds before a teleport request expires", 60, 10, 300, 10, "s");
    public static final NumberSetting RTP_RADIUS = num("teleport.rtp-radius", TELEPORT, Material.COMPASS,
            "RTP Radius", "Max distance from spawn for /rtp", 5000, 500, 50000, 500, "blocks");
    public static final NumberSetting RTP_MIN_RADIUS = num("teleport.rtp-min-radius", TELEPORT, Material.COMPASS,
            "RTP Min Radius", "Min distance from spawn for /rtp", 500, 0, 10000, 100, "blocks");
    public static final NumberSetting RTP_COOLDOWN = num("teleport.rtp-cooldown", TELEPORT, Material.RECOVERY_COMPASS,
            "RTP Cooldown", "Seconds between /rtp uses", 300, 0, 3600, 30, "s");
    public static final NumberSetting MAX_HOMES = num("teleport.max-homes", TELEPORT, Material.RED_BED,
            "Max Homes", "How many homes each player can set", 3, 1, 50, 1, "homes");
    public static final BoolSetting BACK_ON_DEATH = bool("teleport.back-on-death", TELEPORT, Material.RECOVERY_COMPASS,
            "/back To Death", "/back can return players to where they died", true);
    public static final BoolSetting SPAWN_ON_JOIN = bool("teleport.spawn-on-first-join", TELEPORT, Material.BEACON,
            "Spawn On First Join", "New players are sent to /spawn", true);
    public static final BoolSetting RESPAWN_AT_SPAWN = bool("teleport.respawn-at-spawn", TELEPORT, Material.RESPAWN_ANCHOR,
            "Respawn At /spawn", "Players without a bed respawn at /spawn", true);
}
