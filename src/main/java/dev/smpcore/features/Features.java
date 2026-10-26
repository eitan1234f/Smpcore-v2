package dev.smpcore.features;

import dev.smpcore.SmpCore;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * Creates and wires every feature module.
 */
public final class Features {
    private final SmpCore plugin;
    private AfkTracker afk;
    private CombatFeature combat;
    private TeleportService teleport;
    private VanishService vanish;
    private FreezeService freeze;
    private ChatFeature chat;
    private PlayerFeature players;
    private KitService kits;
    private NpcService npcs;
    private ArenaService arenas;
    private RitualFeature rituals;
    private BansFeature bans;
    private WorldFeature world;
    private ItemsFeature items;

    public Features(SmpCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        afk = register(new AfkTracker());
        combat = register(new CombatFeature(plugin, afk));
        teleport = register(new TeleportService(plugin));
        vanish = register(new VanishService(plugin));
        freeze = register(new FreezeService());
        chat = register(new ChatFeature(plugin));
        players = register(new PlayerFeature(plugin));
        bans = register(new BansFeature(plugin));
        world = register(new WorldFeature(plugin));
        register(new VillagerFeature(plugin));
        items = register(new ItemsFeature(plugin));
        rituals = register(new RitualFeature(plugin));
        register(new SecurityFeature(plugin));
        npcs = register(new NpcService(plugin));
        new ShieldFeature(plugin);
        kits = new KitService(plugin);
        arenas = new ArenaService(plugin);
    }

    private <T extends Listener> T register(T listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return listener;
    }

    public void disable() {
        if (rituals != null) rituals.shutdown();
        if (players != null) {
            plugin.getServer().getOnlinePlayers().forEach(players::snapshot);
        }
        HandlerList.unregisterAll(plugin);
    }

    public AfkTracker afk() { return afk; }
    public CombatFeature combat() { return combat; }
    public TeleportService teleport() { return teleport; }
    public VanishService vanish() { return vanish; }
    public FreezeService freeze() { return freeze; }
    public ChatFeature chat() { return chat; }
    public PlayerFeature players() { return players; }
    public KitService kits() { return kits; }
    public NpcService npcs() { return npcs; }
    public ArenaService arenas() { return arenas; }
    public RitualFeature rituals() { return rituals; }
    public BansFeature bans() { return bans; }
    public WorldFeature world() { return world; }
    public ItemsFeature items() { return items; }
}
