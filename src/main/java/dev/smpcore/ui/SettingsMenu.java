package dev.smpcore.ui;

import dev.smpcore.SmpCore;
import dev.smpcore.installer.InstallerMenu;
import dev.smpcore.settings.BoolSetting;
import dev.smpcore.settings.Category;
import dev.smpcore.settings.ChoiceSetting;
import dev.smpcore.settings.ListSetting;
import dev.smpcore.settings.NumberSetting;
import dev.smpcore.settings.Setting;
import dev.smpcore.settings.Settings;
import dev.smpcore.settings.TextSetting;
import dev.smpcore.util.Msg;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The /settings experience, built entirely from native Minecraft dialogs:
 * real toggles, sliders, dropdowns and text boxes instead of a chest full of items.
 */
public final class SettingsMenu {
    public static final String EDIT_PERMISSION = "smpcore.settings.edit";
    private static final int PAGE_SIZE = 10;

    private static final Map<Category, String> STYLE = new EnumMap<>(Category.class);

    static {
        STYLE.put(Category.GENERAL, "<#c4b5fd>⚙ ");
        STYLE.put(Category.PROTECTION, "<#86efac>⛨ ");
        STYLE.put(Category.COMBAT, "<#f87171>⚔ ");
        STYLE.put(Category.COOLDOWNS, "<#93c5fd>⌚ ");
        STYLE.put(Category.DAMAGE_LIMITERS, "<#fca5a5>❤ ");
        STYLE.put(Category.BANS, "<#fb7185>⛔ ");
        STYLE.put(Category.WORLD, "<#6ee7b7>☀ ");
        STYLE.put(Category.VILLAGERS, "<#34d399>◆ ");
        STYLE.put(Category.ITEMS, "<#fcd34d>⚒ ");
        STYLE.put(Category.CHAT, "<#f0abfc>✎ ");
        STYLE.put(Category.SECURITY, "<#fdba74>⚠ ");
        STYLE.put(Category.ANTICHEAT, "<#f97316>⛊ ");
        STYLE.put(Category.INSPECTOR, "<#38bdf8>🔎 ");
        STYLE.put(Category.TELEPORT, "<#a5b4fc>➶ ");
        STYLE.put(Category.COMMANDS, "<#e2e8f0>⌘ ");
    }

    private SettingsMenu() {
    }

    public static boolean canEdit(Player p) {
        return p.hasPermission(EDIT_PERMISSION);
    }

    // ------------------------------------------------------------------ home

    public static void openHome(Player player) {
        if (!canEdit(player) && !Settings.PLAYERS_VIEW_SETTINGS.on()) {
            Msg.error(player, "Viewing the server settings is disabled.");
            return;
        }
        int total = 0;
        int enabled = 0;
        for (Setting<?> s : Settings.all()) {
            if (s instanceof BoolSetting b) {
                total++;
                if (b.on()) enabled++;
            }
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(Dialogs.item(new ItemStack(org.bukkit.Material.NETHER_STAR),
                "<gradient:#a855f7:#ec4899><b>SMP CORE</b></gradient> <gray>v" + SmpCore.get().getPluginMeta().getVersion()));
        body.add(Dialogs.text("<gray>" + Settings.all().size() + " settings • <green>" + enabled + "</green>/<white>" + total
                + "</white> toggles on" + (canEdit(player) ? "" : "<newline><yellow>Read-only: you can see what is allowed on this server.")));

        List<ActionButton> buttons = new ArrayList<>();
        for (Category c : Category.values()) {
            List<Setting<?>> list = Settings.in(c);
            if (list.isEmpty()) continue;
            buttons.add(Dialogs.button(STYLE.get(c) + c.displayName(),
                    "<gray>" + c.description() + "<newline><dark_gray>" + list.size() + " settings",
                    Dialogs.NORMAL, p -> openPage(p, STYLE.get(c) + c.displayName(), c.description(), list, 0, SettingsMenu::openHome)));
        }
        buttons.add(Dialogs.button("<#fde68a>🔍 Search Settings", "<gray>Find any setting by keyword", Dialogs.NORMAL, SettingsMenu::openSearch));
        if (canEdit(player)) {
            if (Settings.INSTALLER_ENABLED.on()) {
                buttons.add(Dialogs.button("<#67e8f9>⬇ Plugin Installer", "<gray>Browse and install any plugin from Modrinth",
                        Dialogs.NORMAL, InstallerMenu::openHome));
            }
            buttons.add(Dialogs.button("<#d8b4fe>⟳ Reload Config", "<gray>Reload config.yml from disk", Dialogs.NORMAL, p -> {
                SmpCore.get().reloadSettings();
                Msg.ok(p, "Configuration reloaded.");
                openHome(p);
            }));
        }
        Dialogs.show(player, Dialogs.menu("<gradient:#a855f7:#ec4899><b>SMP CORE SETTINGS</b></gradient>", body, List.of(), buttons,
                Dialogs.close("<gray>Close"), 2));
    }

    // ------------------------------------------------------------------ search

    public static void openSearch(Player player) {
        TextDialogInput input = DialogInput.text("keyword", Msg.mm("<gray>Keyword")).width(Dialogs.WIDE).maxLength(64).initial("").build();
        List<ActionButton> buttons = List.of(
                Dialogs.button("<green>🔍 Search", null, Dialogs.NORMAL, (p, view) -> {
                    String keyword = Objects.requireNonNullElse(view.getText("keyword"), "").trim();
                    List<Setting<?>> results = Settings.search(keyword);
                    if (results.isEmpty()) {
                        Dialogs.message(p, "<yellow>No Results", "<gray>Nothing matched <white>" + Msg.escape(keyword) + "</white>.", () -> openSearch(p));
                        return;
                    }
                    openPage(p, "<#fde68a>🔍 " + Msg.escape(keyword), results.size() + " results", results, 0, SettingsMenu::openSearch);
                }),
                Dialogs.button("<yellow>« Back", null, Dialogs.NORMAL, SettingsMenu::openHome));
        Dialogs.show(player, Dialogs.menu("<#fde68a><b>Search Settings</b>", List.of(Dialogs.text("<gray>Search names, descriptions and values. Try <white>mace</white>, <white>pearl</white> or <white>villager</white>.")),
                List.of(input), buttons, null, 2));
    }

    // ------------------------------------------------------------------ settings page

    public static void openPage(Player player, String title, String subtitle, List<Setting<?>> settings, int page,
                                java.util.function.Consumer<Player> back) {
        int pages = Math.max(1, (settings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        List<Setting<?>> slice = settings.subList(current * PAGE_SIZE, Math.min(settings.size(), (current + 1) * PAGE_SIZE));
        boolean edit = canEdit(player);
        String pageTitle = title + (pages > 1 ? " <dark_gray>(" + (current + 1) + "/" + pages + ")" : "");

        List<DialogBody> body = new ArrayList<>();
        body.add(Dialogs.text("<gray>" + subtitle));
        List<DialogInput> inputs = new ArrayList<>();

        if (edit) {
            for (int i = 0; i < slice.size(); i++) {
                inputs.add(input("s" + i, slice.get(i)));
            }
        } else {
            StringBuilder b = new StringBuilder();
            for (Setting<?> s : slice) {
                b.append("<white>").append(s.name()).append("</white><dark_gray>: </dark_gray>").append(s.display()).append("<newline>");
            }
            body.add(Dialogs.text(b.toString()));
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (edit) {
            buttons.add(Dialogs.button("<green><b>✔ Save</b>", "<gray>Apply the changes on this page", Dialogs.SMALL,
                    (p, view) -> {
                        int changed = save(p, slice, view);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                        Msg.actionBar(p, changed == 0 ? "<gray>No changes" : "<green>✔ Saved " + changed + " change" + (changed == 1 ? "" : "s"));
                        openPage(p, title, subtitle, settings, current, back);
                    }));
        }
        buttons.add(Dialogs.button("<aqua>ⓘ Info", "<gray>What does each setting do?", Dialogs.SMALL,
                p -> openInfo(p, pageTitle, slice, () -> openPage(p, title, subtitle, settings, current, back))));
        if (current > 0) {
            buttons.add(Dialogs.button("<yellow>◀ Prev", null, Dialogs.SMALL, (p, view) -> {
                if (edit) save(p, slice, view);
                openPage(p, title, subtitle, settings, current - 1, back);
            }));
        }
        if (current < pages - 1) {
            buttons.add(Dialogs.button("<yellow>Next ▶", null, Dialogs.SMALL, (p, view) -> {
                if (edit) save(p, slice, view);
                openPage(p, title, subtitle, settings, current + 1, back);
            }));
        }
        buttons.add(Dialogs.button("<gray>« Back", null, Dialogs.SMALL, back::accept));
        Dialogs.show(player, Dialogs.menu(pageTitle, body, inputs, buttons, null, Math.min(5, buttons.size())));
    }

    private static void openInfo(Player player, String title, List<Setting<?>> slice, Runnable back) {
        List<DialogBody> body = new ArrayList<>();
        for (Setting<?> s : slice) {
            String extra = s.restartRequired() ? " <red>(restart)</red>" : "";
            body.add(Dialogs.item(new ItemStack(s.icon()),
                    "<white><b>" + s.name() + "</b></white>" + extra + "<newline><gray>" + s.description()
                            + "<newline><dark_gray>Now: </dark_gray>" + s.display()));
        }
        Dialogs.show(player, Dialogs.notice(title + " <aqua>ⓘ", body, Dialogs.button("<yellow>« Back", null, Dialogs.SMALL, p -> back.run())));
    }

    private static DialogInput input(String key, Setting<?> s) {
        String label = "<white>" + s.name() + (s.restartRequired() ? " <red>*" : "");
        if (s instanceof BoolSetting b) {
            return DialogInput.bool(key, Msg.mm(label)).initial(b.on()).build();
        }
        if (s instanceof NumberSetting n) {
            String unit = n.unit().isEmpty() ? "" : " (" + n.unit() + ")";
            float initial = (float) Math.max(n.min(), Math.min(n.max(), n.get()));
            return DialogInput.numberRange(key, Msg.mm(label + "<gray>" + unit), (float) n.min(), (float) n.max())
                    .step((float) n.step())
                    .initial(initial)
                    .labelFormat("%s: %s")
                    .width(Dialogs.WIDE)
                    .build();
        }
        if (s instanceof ChoiceSetting c) {
            List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
            for (String opt : c.options()) {
                entries.add(SingleOptionDialogInput.OptionEntry.create(opt, Msg.mm(Msg.pretty(opt)), opt.equals(c.get())));
            }
            return DialogInput.singleOption(key, Msg.mm(label), entries).width(Dialogs.WIDE).build();
        }
        if (s instanceof ListSetting l) {
            return DialogInput.text(key, Msg.mm(label + " <dark_gray>(comma separated)"))
                    .initial(l.joined())
                    .maxLength(4000)
                    .width(Dialogs.WIDE)
                    .multiline(TextDialogInput.MultilineOptions.create(6, 48))
                    .build();
        }
        TextSetting t = (TextSetting) s;
        return DialogInput.text(key, Msg.mm(label)).initial(t.get()).maxLength(Math.max(1, t.maxLength())).width(Dialogs.WIDE).build();
    }

    /** Reads every input on the page and applies changed values. Returns the number of changes. */
    private static int save(Player player, List<Setting<?>> slice, DialogResponseView view) {
        if (!canEdit(player)) {
            return 0;
        }
        int changed = 0;
        boolean restart = false;
        for (int i = 0; i < slice.size(); i++) {
            String key = "s" + i;
            Setting<?> s = slice.get(i);
            boolean did = false;
            if (s instanceof BoolSetting b) {
                Boolean v = view.getBoolean(key);
                if (v != null && v != b.on()) {
                    b.set(v);
                    did = true;
                }
            } else if (s instanceof NumberSetting n) {
                Float f = view.getFloat(key);
                if (f != null) {
                    double v = Math.round(f / n.step()) * n.step();
                    v = Math.max(n.min(), Math.min(n.max(), v));
                    if (Math.abs(v - n.get()) > 1e-6) {
                        n.set(v);
                        did = true;
                    }
                }
            } else if (s instanceof ChoiceSetting c) {
                String v = view.getText(key);
                if (v != null && c.options().contains(v) && !v.equals(c.get())) {
                    c.set(v);
                    did = true;
                }
            } else if (s instanceof ListSetting l) {
                String v = view.getText(key);
                if (v != null) {
                    List<String> parsed = ListSetting.parse(v);
                    if (!parsed.equals(l.get())) {
                        l.set(parsed);
                        did = true;
                    }
                }
            } else if (s instanceof TextSetting t) {
                String v = view.getText(key);
                if (v != null && !v.equals(t.get())) {
                    t.set(v);
                    did = true;
                }
            }
            if (did) {
                changed++;
                restart |= s.restartRequired();
                SmpCore.get().getLogger().info(player.getName() + " changed " + s.path() + " to " + s.get());
            }
        }
        if (changed > 0) {
            SmpCore.get().saveSettings();
            if (restart) {
                Msg.send(player, "<yellow>Some changes need a server restart to take effect.");
            }
        }
        return changed;
    }
}
