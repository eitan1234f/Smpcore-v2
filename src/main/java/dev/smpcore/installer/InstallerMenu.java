package dev.smpcore.installer;

import dev.smpcore.SmpCore;
import dev.smpcore.settings.Settings;
import dev.smpcore.ui.Dialogs;
import dev.smpcore.ui.SettingsMenu;
import dev.smpcore.util.Msg;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Native dialog front-end for browsing and installing any Modrinth plugin.
 */
public final class InstallerMenu {
    private static final int PAGE_SIZE = 10;
    private static final String PERMISSION = "smpcore.admin";

    private static final Map<String, String> SORTS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        SORTS.put("relevance", "Relevance");
        SORTS.put("downloads", "Most downloaded");
        SORTS.put("follows", "Most followed");
        SORTS.put("newest", "Newest");
        SORTS.put("updated", "Recently updated");

        CATEGORIES.put("any", "Any category");
        for (String c : List.of("adventure", "economy", "equipment", "game-mechanics", "library", "magic", "management", "minigame",
                "mobs", "optimization", "social", "storage", "technology", "transportation", "utility", "worldgen", "cursed",
                "decoration", "food")) {
            CATEGORIES.put(c, Msg.pretty(c.replace('-', '_')));
        }
    }

    /** Remembers each player's last search so paging keeps the filters. */
    private record Query(String text, String sort, String category, boolean compatibleOnly) {
    }

    private static final Map<java.util.UUID, Query> LAST = new java.util.HashMap<>();

    private InstallerMenu() {
    }

    private static PluginInstaller installer() {
        return SmpCore.get().installer();
    }

    private static boolean allowed(Player p) {
        if (!p.hasPermission(PERMISSION)) {
            Msg.error(p, "Only admins can use the plugin installer.");
            return false;
        }
        if (!Settings.INSTALLER_ENABLED.on()) {
            Msg.error(p, "The plugin installer is disabled in /settings.");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ home

    public static void openHome(Player player) {
        if (!allowed(player)) return;
        Query last = LAST.getOrDefault(player.getUniqueId(), new Query("", "relevance", "any", Settings.INSTALLER_COMPATIBLE_ONLY.on()));
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("query", Msg.mm("<gray>Search Modrinth plugins")).width(Dialogs.WIDE).maxLength(100).initial(last.text()).build());
        inputs.add(option("sort", "Sort by", SORTS, last.sort()));
        inputs.add(option("category", "Category", CATEGORIES, last.category()));
        inputs.add(DialogInput.bool("compatible", Msg.mm("<white>Only plugins for " + PluginInstaller.gameVersion())).initial(last.compatibleOnly()).build());

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Dialogs.button("<green><b>🔍 Search</b>", "<gray>Search every plugin on Modrinth", Dialogs.NORMAL, (p, view) -> {
            Query q = new Query(Objects.requireNonNullElse(view.getText("query"), "").trim(),
                    Objects.requireNonNullElse(view.getText("sort"), "relevance"),
                    Objects.requireNonNullElse(view.getText("category"), "any"),
                    Boolean.TRUE.equals(view.getBoolean("compatible")));
            LAST.put(p.getUniqueId(), q);
            runSearch(p, q, 0);
        }));
        buttons.add(Dialogs.button("<gold>🔥 Top Plugins", "<gray>Most downloaded server plugins", Dialogs.NORMAL, p -> {
            Query q = new Query("", "downloads", "any", Settings.INSTALLER_COMPATIBLE_ONLY.on());
            LAST.put(p.getUniqueId(), q);
            runSearch(p, q, 0);
        }));
        buttons.add(Dialogs.button("<aqua>🆕 New & Updated", "<gray>Recently updated plugins", Dialogs.NORMAL, p -> {
            Query q = new Query("", "updated", "any", Settings.INSTALLER_COMPATIBLE_ONLY.on());
            LAST.put(p.getUniqueId(), q);
            runSearch(p, q, 0);
        }));
        buttons.add(Dialogs.button("<light_purple>⭐ SMP Essentials", "<gray>Voice chat, ViaVersion, ViaBackwards, Chunky...", Dialogs.NORMAL,
                InstallerMenu::openEssentials));
        buttons.add(Dialogs.button("<#a5b4fc>📦 Installed", "<gray>Plugins installed with SMP Core: update or remove", Dialogs.NORMAL,
                InstallerMenu::openInstalled));
        buttons.add(Dialogs.button("<gray>« Settings", null, Dialogs.NORMAL, SettingsMenu::openHome));

        List<DialogBody> body = List.of(
                Dialogs.item(new ItemStack(Material.HOPPER), "<#67e8f9><b>PLUGIN INSTALLER</b><newline><gray>Browse and install <white>any</white> plugin on Modrinth"),
                Dialogs.text("<gray>Server: <white>" + PluginInstaller.gameVersion() + "</white> • Downloads are checksum-verified and saved to /plugins."));
        Dialogs.show(player, Dialogs.menu("<#67e8f9><b>Plugin Installer</b>", body, inputs, buttons, null, 2));
    }

    private static SingleOptionDialogInput option(String key, String label, Map<String, String> options, String selected) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : options.entrySet()) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(e.getKey(), Msg.mm(e.getValue()), e.getKey().equals(selected)));
        }
        return DialogInput.singleOption(key, Msg.mm("<gray>" + label), entries).width(Dialogs.WIDE).build();
    }

    public static void openSearch(Player player, String text, int page) {
        if (!allowed(player)) return;
        Query q = new Query(text, "relevance", "any", Settings.INSTALLER_COMPATIBLE_ONLY.on());
        LAST.put(player.getUniqueId(), q);
        runSearch(player, q, page);
    }

    private static void loading(Player p, String what) {
        Msg.actionBar(p, "<#67e8f9>⏳ " + what + "...");
    }

    private static void failed(Player p, Throwable ex) {
        Dialogs.sync(() -> Dialogs.message(p, "<red><b>Modrinth Error", "<gray>Couldn't reach Modrinth:<newline><white>"
                + Msg.escape(PluginInstaller.rootMessage(ex)), () -> openHome(p)));
    }

    // ------------------------------------------------------------------ search results

    private static void runSearch(Player player, Query q, int page) {
        loading(player, "Searching Modrinth");
        String category = "any".equals(q.category()) ? null : q.category();
        String version = q.compatibleOnly() ? PluginInstaller.gameVersion() : null;
        installer().client().search(q.text(), q.sort(), category, version, page * PAGE_SIZE, PAGE_SIZE)
                .thenAccept(result -> Dialogs.sync(() -> showResults(player, q, page, result)))
                .exceptionally(ex -> {
                    failed(player, ex);
                    return null;
                });
    }

    private static void showResults(Player player, Query q, int page, ModrinthClient.SearchResult result) {
        int pages = Math.max(1, (result.total() + PAGE_SIZE - 1) / PAGE_SIZE);
        List<ActionButton> buttons = new ArrayList<>();
        for (ModrinthClient.Hit hit : result.hits()) {
            boolean installed = installer().installed(hit.id()) != null;
            String label = (installed ? "<green>✔ " : "<white>") + Msg.escape(trim(hit.title(), 26)) + " <gray>⬇" + compact(hit.downloads());
            String tooltip = "<white><b>" + Msg.escape(hit.title()) + "</b></white> <gray>by " + Msg.escape(hit.author()) + "<newline><gray>"
                    + Msg.escape(hit.description()) + "<newline><dark_gray>" + Msg.escape(String.join(", ", hit.categories()));
            buttons.add(Dialogs.button(label, tooltip, Dialogs.NORMAL, p -> openProject(p, hit.id(), () -> runSearch(p, q, page))));
        }
        if (page > 0) {
            buttons.add(Dialogs.button("<yellow>◀ Previous", null, Dialogs.NORMAL, p -> runSearch(p, q, page - 1)));
        }
        if (page < pages - 1) {
            buttons.add(Dialogs.button("<yellow>Next ▶", null, Dialogs.NORMAL, p -> runSearch(p, q, page + 1)));
        }
        String header = result.total() == 0
                ? "<gray>No plugins found. Try another search" + (q.compatibleOnly() ? " or turn off <white>Only plugins for " + PluginInstaller.gameVersion() + "</white>." : ".")
                : "<gray>" + String.format("%,d", result.total()) + " plugins • page <white>" + (page + 1) + "</white>/" + pages
                + (q.text().isEmpty() ? "" : " • \"<white>" + Msg.escape(q.text()) + "</white>\"") + " • " + SORTS.getOrDefault(q.sort(), q.sort());
        Dialogs.show(player, Dialogs.menu("<#67e8f9><b>Modrinth Plugins</b>", List.of(Dialogs.text(header)), List.of(), buttons,
                Dialogs.button("<gray>« Search", null, Dialogs.NORMAL, InstallerMenu::openHome), 2));
    }

    // ------------------------------------------------------------------ project page

    public static void openProject(Player player, String projectId, Runnable back) {
        loading(player, "Loading plugin");
        installer().client().project(projectId)
                .thenCombine(installer().client().versions(projectId, PluginInstaller.gameVersion()), (project, compatible) -> {
                    Dialogs.sync(() -> showProject(player, project, compatible, back));
                    return null;
                })
                .exceptionally(ex -> {
                    failed(player, ex);
                    return null;
                });
    }

    private static void showProject(Player player, ModrinthClient.Project project, List<ModrinthClient.Version> compatible, Runnable back) {
        ModrinthClient.Version best = PluginInstaller.pickBest(compatible);
        PluginInstaller.Installed installed = installer().installed(project.id());
        String status;
        if (installed != null) {
            boolean update = best != null && !best.id().equals(installed.versionId());
            status = "<green>Installed: " + Msg.escape(installed.versionNumber()) + (update ? " <yellow>(update available: " + Msg.escape(best.number()) + ")" : " <gray>(up to date)");
        } else if (best == null) {
            status = "<red>No version for " + PluginInstaller.gameVersion() + " - you can still pick an older one.";
        } else {
            status = "<gray>Latest for " + PluginInstaller.gameVersion() + ": <white>" + Msg.escape(best.number()) + "</white> <dark_gray>(" + best.type() + ")";
        }
        String info = "<white><b>" + Msg.escape(project.title()) + "</b></white><newline>"
                + "<gray>" + Msg.escape(project.description()) + "<newline><newline>"
                + "<gray>⬇ <white>" + String.format("%,d", project.downloads()) + "</white> downloads • ♥ <white>"
                + String.format("%,d", project.follows()) + "</white> followers<newline>"
                + "<gray>Loaders: <white>" + Msg.escape(String.join(", ", project.loaders())) + "</white><newline>"
                + "<gray>Categories: <white>" + Msg.escape(String.join(", ", project.categories())) + "</white>"
                + (project.license().isEmpty() ? "" : "<newline><gray>License: <white>" + Msg.escape(project.license()));
        List<DialogBody> body = List.of(Dialogs.item(new ItemStack(Material.ENCHANTED_BOOK), info), Dialogs.text(status));

        List<ActionButton> buttons = new ArrayList<>();
        if (best != null) {
            String verb = installed == null ? "⬇ Install" : best.id().equals(installed.versionId()) ? "⟳ Reinstall" : "⬆ Update";
            buttons.add(Dialogs.button("<green><b>" + verb + "</b> " + Msg.escape(trim(best.number(), 16)),
                    "<gray>Downloads the plugin and its required dependencies", Dialogs.NORMAL,
                    p -> confirmInstall(p, project, best, () -> showProject(p, project, compatible, back))));
        }
        buttons.add(Dialogs.button("<aqua>☰ All Versions", "<gray>Pick a specific version", Dialogs.NORMAL,
                p -> openVersions(p, project, () -> showProject(p, project, compatible, back))));
        if (installed != null) {
            buttons.add(Dialogs.button("<red>🗑 Uninstall", "<gray>Disable and delete this plugin", Dialogs.NORMAL,
                    p -> confirmUninstall(p, installed, () -> showProject(p, project, compatible, back))));
        }
        buttons.add(Dialogs.link("<#93c5fd>🌐 Modrinth Page", "<gray>Open in your browser", Dialogs.NORMAL,
                "https://modrinth.com/plugin/" + (project.slug().isEmpty() ? project.id() : project.slug())));
        Dialogs.show(player, Dialogs.menu("<#67e8f9><b>" + Msg.escape(trim(project.title(), 32)) + "</b>", body, List.of(), buttons,
                Dialogs.button("<gray>« Back", null, Dialogs.NORMAL, p -> back.run()), 2));
    }

    private static void openVersions(Player player, ModrinthClient.Project project, Runnable back) {
        loading(player, "Loading versions");
        installer().client().versions(project.id(), null).thenAccept(all -> Dialogs.sync(() -> {
            List<ActionButton> buttons = new ArrayList<>();
            String mc = PluginInstaller.gameVersion();
            for (ModrinthClient.Version v : all.subList(0, Math.min(20, all.size()))) {
                boolean fits = v.gameVersions().contains(mc);
                String color = fits ? "<green>" : "<gray>";
                String type = "release".equals(v.type()) ? "" : " <yellow>" + v.type();
                List<String> gv = v.gameVersions();
                String range = gv.isEmpty() ? "?" : gv.size() == 1 ? gv.get(0) : gv.get(0) + " – " + gv.get(gv.size() - 1);
                buttons.add(Dialogs.button(color + Msg.escape(trim(v.number(), 20)) + type,
                        "<white>" + Msg.escape(v.name()) + "<newline><gray>MC " + Msg.escape(range) + "<newline><gray>Loaders: "
                                + Msg.escape(String.join(", ", v.loaders())) + (fits ? "" : "<newline><red>Not marked for " + mc),
                        Dialogs.NORMAL, p -> confirmInstall(p, project, v, () -> openVersions(p, project, back))));
            }
            if (buttons.isEmpty()) {
                Dialogs.message(player, "<red>No versions", "<gray>This project has no server plugin files.", back);
                return;
            }
            Dialogs.show(player, Dialogs.menu("<#67e8f9><b>Versions</b> <gray>" + Msg.escape(trim(project.title(), 24)),
                    List.of(Dialogs.text("<green>Green</green> <gray>= built for " + mc + ". Newest first.")), List.of(), buttons,
                    Dialogs.button("<gray>« Back", null, Dialogs.NORMAL, p -> back.run()), 2));
        })).exceptionally(ex -> {
            failed(player, ex);
            return null;
        });
    }

    // ------------------------------------------------------------------ install / uninstall

    private static void confirmInstall(Player player, ModrinthClient.Project project, ModrinthClient.Version version, Runnable back) {
        long required = version.dependencies().stream().filter(d -> "required".equals(d.type())).count();
        ModrinthClient.FileInfo file = version.primaryFile();
        String text = "<gray>Install <white>" + Msg.escape(project.title()) + " " + Msg.escape(version.number()) + "</white>?<newline>"
                + "<gray>File: <white>" + Msg.escape(file == null ? "?" : file.filename()) + "</white>"
                + (file == null ? "" : " <dark_gray>(" + (file.size() / 1024) + " KB)") + "<newline>"
                + (required > 0 ? "<yellow>" + required + " required dependenc" + (required == 1 ? "y" : "ies") + " will be installed too.<newline>" : "")
                + (version.gameVersions().contains(PluginInstaller.gameVersion()) ? "" : "<red>⚠ This version isn't marked for " + PluginInstaller.gameVersion() + ".<newline>")
                + "<dark_gray>Only install plugins you trust.";
        Dialogs.show(player, Dialogs.confirm("<green><b>Install Plugin?", text,
                Dialogs.button("<green><b>Install", null, Dialogs.SMALL, p -> doInstall(p, project, version, back)),
                Dialogs.button("<gray>Cancel", null, Dialogs.SMALL, p -> back.run())));
    }

    private static void doInstall(Player player, ModrinthClient.Project project, ModrinthClient.Version version, Runnable back) {
        Msg.send(player, "<#67e8f9>Installing " + Msg.escape(project.title()) + "...");
        SmpCore.get().getLogger().info(player.getName() + " is installing " + project.title() + " " + version.number() + " from Modrinth");
        installer().install(project.id(), project.title(), version.id(), step -> Dialogs.sync(() -> Msg.actionBar(player, "<#67e8f9>⏳ " + Msg.escape(step))))
                .thenAccept(result -> Dialogs.sync(() -> {
                    StringBuilder b = new StringBuilder();
                    for (String s : result.installed()) b.append("<green>✔ ").append(Msg.escape(s)).append("<newline>");
                    for (String s : result.skipped()) b.append("<gray>• ").append(Msg.escape(s)).append("<newline>");
                    for (String s : result.failed()) b.append("<red>✖ ").append(Msg.escape(s)).append("<newline>");
                    b.append("<newline><yellow>Restart the server to start new plugins.</yellow>");
                    List<ActionButton> buttons = new ArrayList<>();
                    PluginInstaller.Installed rec = installer().installed(project.id());
                    if (rec != null && result.failed().isEmpty()) {
                        buttons.add(Dialogs.button("<gold>⚡ Load Now (experimental)", "<gray>Try to start it without a restart.<newline>"
                                + "<red>Some plugins only work after a restart.", Dialogs.NORMAL, p -> {
                            String err = installer().loadNow(rec.file());
                            if (err == null) Msg.ok(p, project.title() + " is now running!"); else Msg.error(p, err);
                        }));
                    }
                    buttons.add(Dialogs.button("<gray>« Back", null, Dialogs.NORMAL, p -> back.run()));
                    Dialogs.show(player, Dialogs.menu(result.failed().isEmpty() ? "<green><b>Installed!" : "<yellow><b>Install finished with errors",
                            List.of(Dialogs.text(b.toString())), List.of(), buttons, null, 2));
                }))
                .exceptionally(ex -> {
                    failed(player, ex);
                    return null;
                });
    }

    private static void confirmUninstall(Player player, PluginInstaller.Installed installed, Runnable back) {
        Dialogs.show(player, Dialogs.confirm("<red><b>Uninstall?", "<gray>Disable and delete <white>" + Msg.escape(installed.title())
                        + "</white> (" + Msg.escape(installed.file()) + ")?<newline><dark_gray>Its config folder is kept.",
                Dialogs.button("<red><b>Uninstall", null, Dialogs.SMALL, p -> {
                    installer().uninstall(installed.projectId());
                    Msg.ok(p, installed.title() + " was removed.");
                    openInstalled(p);
                }),
                Dialogs.button("<gray>Cancel", null, Dialogs.SMALL, p -> back.run())));
    }

    // ------------------------------------------------------------------ installed & essentials

    public static void openInstalled(Player player) {
        if (!allowed(player)) return;
        List<PluginInstaller.Installed> list = installer().installed();
        List<ActionButton> buttons = new ArrayList<>();
        for (PluginInstaller.Installed i : list) {
            buttons.add(Dialogs.button("<white>" + Msg.escape(trim(i.title(), 24)) + " <gray>" + Msg.escape(trim(i.versionNumber(), 12)),
                    "<gray>" + Msg.escape(i.file()) + "<newline><dark_gray>Click for update / uninstall", Dialogs.NORMAL,
                    p -> openProject(p, i.projectId(), () -> openInstalled(p))));
        }
        if (!list.isEmpty()) {
            buttons.add(Dialogs.button("<yellow>⬆ Check For Updates", "<gray>Compare every installed plugin with Modrinth", Dialogs.NORMAL,
                    InstallerMenu::checkUpdates));
        }
        Dialogs.show(player, Dialogs.menu("<#a5b4fc><b>Installed Plugins</b>",
                List.of(Dialogs.text(list.isEmpty() ? "<gray>Nothing installed through SMP Core yet." : "<gray>" + list.size() + " plugins installed through SMP Core.")),
                List.of(), buttons, Dialogs.button("<gray>« Back", null, Dialogs.NORMAL, InstallerMenu::openHome), 2));
    }

    private static void checkUpdates(Player player) {
        loading(player, "Checking for updates");
        List<PluginInstaller.Installed> list = installer().installed();
        List<java.util.concurrent.CompletableFuture<String>> checks = new ArrayList<>();
        for (PluginInstaller.Installed i : list) {
            checks.add(installer().client().versions(i.projectId(), PluginInstaller.gameVersion()).thenApply(vs -> {
                ModrinthClient.Version best = PluginInstaller.pickBest(vs);
                if (best == null || best.id().equals(i.versionId())) return null;
                return i.projectId() + "|" + i.title() + "|" + i.versionNumber() + "|" + best.number();
            }).exceptionally(ex -> null));
        }
        java.util.concurrent.CompletableFuture.allOf(checks.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> Dialogs.sync(() -> {
            List<ActionButton> buttons = new ArrayList<>();
            for (var f : checks) {
                String r = f.join();
                if (r == null) continue;
                String[] parts = r.split("\\|", 4);
                buttons.add(Dialogs.button("<yellow>⬆ " + Msg.escape(trim(parts[1], 20)), "<gray>" + Msg.escape(parts[2]) + " → <green>" + Msg.escape(parts[3]),
                        Dialogs.NORMAL, p -> openProject(p, parts[0], () -> openInstalled(p))));
            }
            Dialogs.show(player, Dialogs.menu("<yellow><b>Updates</b>", List.of(Dialogs.text(buttons.isEmpty()
                            ? "<green>Everything is up to date!" : "<gray>" + buttons.size() + " update(s) available. Click one to update.")),
                    List.of(), buttons, Dialogs.button("<gray>« Back", null, Dialogs.NORMAL, InstallerMenu::openInstalled), 2));
        }));
    }

    private static void openEssentials(Player player) {
        Map<String, String> essentials = new LinkedHashMap<>();
        essentials.put("simple-voice-chat", "Simple Voice Chat|Proximity voice chat");
        essentials.put("viaversion", "ViaVersion|Let newer clients join");
        essentials.put("viabackwards", "ViaBackwards|Let older clients join");
        essentials.put("chunky", "Chunky|Pre-generate your world");
        essentials.put("luckperms", "LuckPerms|Permissions");
        essentials.put("spark", "spark|Performance profiler");
        essentials.put("bluemap", "BlueMap|3D web map");
        essentials.put("grimac", "GrimAC|Anticheat");
        essentials.put("tab-was-taken", "TAB|Tab list & nametags");
        essentials.put("discordsrv", "DiscordSRV|Discord chat bridge");
        List<ActionButton> buttons = new ArrayList<>();
        for (Map.Entry<String, String> e : essentials.entrySet()) {
            String[] parts = e.getValue().split("\\|");
            buttons.add(Dialogs.button("<white>" + parts[0], "<gray>" + parts[1], Dialogs.NORMAL,
                    p -> openProject(p, e.getKey(), () -> openEssentials(p))));
        }
        Dialogs.show(player, Dialogs.menu("<light_purple><b>⭐ SMP Essentials</b>",
                List.of(Dialogs.text("<gray>Popular plugins most SMPs use. Everything else is one search away.")),
                List.of(), buttons, Dialogs.button("<gray>« Back", null, Dialogs.NORMAL, InstallerMenu::openHome), 2));
    }

    // ------------------------------------------------------------------ helpers

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
