package dev.smpcore.installer;

import dev.smpcore.SmpCore;
import dev.smpcore.data.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Installs plugins from Modrinth into /plugins, resolving required dependencies,
 * and keeps a record so they can be updated or removed later.
 */
public final class PluginInstaller {
    private final SmpCore plugin;
    private final ModrinthClient client;

    public record Installed(String projectId, String title, String versionId, String versionNumber, String file, ModrinthClient.Kind kind) {
    }

    public record InstallResult(List<String> installed, List<String> skipped, List<String> failed) {
    }

    public PluginInstaller(SmpCore plugin) {
        this.plugin = plugin;
        this.client = new ModrinthClient(plugin.getPluginMeta().getVersion());
    }

    public ModrinthClient client() {
        return client;
    }

    public static String gameVersion() {
        return Bukkit.getMinecraftVersion();
    }

    private YamlStore store() {
        return plugin.data().store("installer");
    }

    public List<Installed> installed() {
        List<Installed> out = new ArrayList<>();
        ConfigurationSection s = store().yaml().getConfigurationSection("installed");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ModrinthClient.Kind kind = "DATAPACK".equals(s.getString(id + ".kind")) ? ModrinthClient.Kind.DATAPACK : ModrinthClient.Kind.PLUGIN;
            out.add(new Installed(id, s.getString(id + ".title", id), s.getString(id + ".version-id", ""),
                    s.getString(id + ".version", ""), s.getString(id + ".file", ""), kind));
        }
        return out;
    }

    public Installed installed(String projectId) {
        for (Installed i : installed()) {
            if (i.projectId().equals(projectId)) return i;
        }
        return null;
    }

    private File pluginsFolder() {
        return plugin.getDataFolder().getParentFile();
    }

    /** Datapacks go into the main world's datapacks folder (applies to every dimension). */
    public static File datapacksFolder() {
        return new File(Bukkit.getWorlds().get(0).getWorldFolder(), "datapacks");
    }

    private File folder(ModrinthClient.Kind kind) {
        return kind == ModrinthClient.Kind.DATAPACK ? datapacksFolder() : pluginsFolder();
    }

    /**
     * Installs a specific version (or the newest compatible one when versionId is null)
     * plus every required dependency that isn't already present.
     */
    public CompletableFuture<InstallResult> install(String projectId, String title, String versionId, Consumer<String> progress) {
        return install(ModrinthClient.Kind.PLUGIN, projectId, title, versionId, progress);
    }

    public CompletableFuture<InstallResult> install(ModrinthClient.Kind kind, String projectId, String title, String versionId,
                                                    Consumer<String> progress) {
        InstallResult result = new InstallResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Set<String> visited = new LinkedHashSet<>();
        File folder = folder(kind);
        return installRecursive(kind, folder, projectId, title, versionId, progress, result, visited, 0).thenApply(v -> result);
    }

    private CompletableFuture<Void> installRecursive(ModrinthClient.Kind kind, File folder, String projectId, String title, String versionId,
                                                     Consumer<String> progress, InstallResult result, Set<String> visited, int depth) {
        if (!visited.add(projectId) || depth > 6) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<ModrinthClient.Version> versionFuture = versionId != null && !versionId.isEmpty()
                ? client.version(versionId)
                : client.versions(kind, projectId, gameVersion()).thenCompose(list -> list.isEmpty()
                        ? client.versions(kind, projectId, null).thenApply(all -> all.isEmpty() ? null : all.get(0))
                        : CompletableFuture.completedFuture(pickBest(list)));
        return versionFuture.thenCompose(version -> {
            String name = title == null || title.isEmpty() ? projectId : title;
            if (version == null) {
                result.failed().add(name + " (no server version found)");
                return CompletableFuture.completedFuture(null);
            }
            ModrinthClient.FileInfo file = version.primaryFile();
            String ext = kind == ModrinthClient.Kind.DATAPACK ? ".zip" : ".jar";
            if (file == null || !file.filename().endsWith(ext)) {
                result.failed().add(name + " (no " + ext + " file)");
                return CompletableFuture.completedFuture(null);
            }
            Installed existing = installed(projectId);
            File target = new File(folder, file.filename());
            boolean alreadyThere = target.exists() || (existing != null && existing.versionId().equals(version.id()));
            CompletableFuture<Void> self;
            if (alreadyThere && depth > 0) {
                result.skipped().add(name + " (already installed)");
                self = CompletableFuture.completedFuture(null);
            } else {
                progress.accept("Downloading " + name + " " + version.number() + "...");
                self = client.download(file, target.toPath()).thenAccept(path -> {
                    if (existing != null && !existing.file().equals(file.filename())) {
                        new File(folder, existing.file()).delete();
                    }
                    record(kind, projectId, name, version, file.filename());
                    result.installed().add(name + " " + version.number());
                }).exceptionally(ex -> {
                    result.failed().add(name + " (" + rootMessage(ex) + ")");
                    return null;
                });
            }
            List<CompletableFuture<Void>> deps = new ArrayList<>();
            for (ModrinthClient.Dependency dep : version.dependencies()) {
                if (!"required".equals(dep.type()) || dep.projectId().isEmpty()) continue;
                deps.add(self.thenCompose(v -> client.project(dep.projectId())
                        .thenCompose(p -> {
                            if (isLoadedPluginNamed(p.title()) || isLoadedPluginNamed(p.slug())) {
                                result.skipped().add(p.title() + " (already loaded)");
                                return CompletableFuture.<Void>completedFuture(null);
                            }
                            progress.accept("Installing dependency " + p.title() + "...");
                            return installRecursive(kind, folder, dep.projectId(), p.title(), dep.versionId(), progress, result, visited, depth + 1);
                        })
                        .exceptionally(ex -> {
                            result.failed().add("dependency " + dep.projectId() + " (" + rootMessage(ex) + ")");
                            return null;
                        })));
            }
            return CompletableFuture.allOf(self, CompletableFuture.allOf(deps.toArray(new CompletableFuture[0])));
        });
    }

    /** Prefers the newest release, then beta, then alpha. Lists come newest first from Modrinth. */
    public static ModrinthClient.Version pickBest(List<ModrinthClient.Version> versions) {
        for (String type : List.of("release", "beta", "alpha")) {
            for (ModrinthClient.Version v : versions) {
                if (type.equals(v.type())) return v;
            }
        }
        return versions.isEmpty() ? null : versions.get(0);
    }

    private static boolean isLoadedPluginNamed(String name) {
        if (name == null) return false;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase(name.replace(" ", "")) || p.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void record(ModrinthClient.Kind kind, String projectId, String title, ModrinthClient.Version version, String file) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String path = "installed." + projectId;
            store().set(path + ".kind", kind.name());
            store().set(path + ".title", title);
            store().set(path + ".version-id", version.id());
            store().set(path + ".version", version.number());
            store().set(path + ".file", file);
            store().set(path + ".installed-at", System.currentTimeMillis());
            store().save();
        });
    }

    /** Disables the plugin if it's running and deletes its jar. */
    public boolean uninstall(String projectId) {
        Installed i = installed(projectId);
        if (i == null) return false;
        File jar = new File(folder(i.kind()), i.file());
        for (Plugin p : i.kind() == ModrinthClient.Kind.DATAPACK ? new Plugin[0] : Bukkit.getPluginManager().getPlugins()) {
            if (p == plugin) continue;
            try {
                File source = new File(p.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                if (source.getName().equals(jar.getName())) {
                    Bukkit.getPluginManager().disablePlugin(p);
                }
            } catch (Exception ignored) {
                // unknown code source
            }
        }
        boolean deleted = !jar.exists() || jar.delete();
        if (!deleted) jar.deleteOnExit();
        store().set("installed." + projectId, null);
        store().save();
        return true;
    }

    /** Experimental: load a freshly installed plugin without a restart. Returns an error message or null. */
    public String loadNow(String fileName) {
        File jar = new File(pluginsFolder(), fileName);
        try {
            Plugin loaded = Bukkit.getPluginManager().loadPlugin(jar);
            if (loaded == null) return "The plugin couldn't be loaded.";
            loaded.onLoad();
            Bukkit.getPluginManager().enablePlugin(loaded);
            Bukkit.getOnlinePlayers().forEach(org.bukkit.entity.Player::updateCommands);
            return loaded.isEnabled() ? null : "The plugin loaded but failed to enable. Restart the server.";
        } catch (Throwable t) {
            return "Needs a restart: " + rootMessage(t);
        }
    }

    public static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        String m = c.getMessage();
        return m == null ? c.getClass().getSimpleName() : m;
    }

    public Path pluginsPath() {
        return pluginsFolder().toPath();
    }
}
