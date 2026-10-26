package dev.smpcore.settings;

import org.bukkit.Material;

/**
 * Top-level groups shown on the /settings home screen.
 */
public enum Category {
    GENERAL("General", Material.COMPARATOR, "Server links, prefix and menu access"),
    PROTECTION("Protection", Material.TOTEM_OF_UNDYING, "Keep players safe from griefing and spawn-killing"),
    COMBAT("Combat Rules", Material.NETHERITE_SWORD, "Combat tag, PvP, mace and shield tweaks"),
    COOLDOWNS("Cooldowns", Material.CLOCK, "Item cooldowns in seconds (0 = vanilla)"),
    DAMAGE_LIMITERS("Damage Limiters", Material.ANVIL, "Maximum damage per hit (0 = no limit)"),
    BANS("Bans & Limits", Material.BARRIER, "Ban items, enchants, potions, dimensions and more"),
    WORLD("World", Material.GRASS_BLOCK, "World mechanics, sleep, render distance, anti-xray"),
    VILLAGERS("Villagers & Vaults", Material.EMERALD, "Restocks, trade cycling, vaults"),
    ITEMS("Items & Crafting", Material.CRAFTING_TABLE, "Stackable potions, recipes, rituals, craft limits"),
    CHAT("Chat", Material.WRITABLE_BOOK, "No chat reports, filter, spam limiter"),
    SECURITY("Security", Material.IRON_BARS, "Maintenance, anti-VPN, anti-alt, mod detector"),
    TELEPORT("Teleport & Homes", Material.ENDER_PEARL, "RTP, TPA, homes, back, spawn"),
    COMMANDS("Commands", Material.COMMAND_BLOCK, "Turn any SmpCore command on or off");

    private final String displayName;
    private final Material icon;
    private final String description;

    Category(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String description() { return description; }
}
