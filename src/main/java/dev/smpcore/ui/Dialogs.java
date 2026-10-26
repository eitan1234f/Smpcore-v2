package dev.smpcore.ui;

import dev.smpcore.SmpCore;
import dev.smpcore.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Thin builder layer over Paper's Dialog API - the native "new" Minecraft menus.
 */
public final class Dialogs {
    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(30))
            .build();

    public static final int WIDE = 310;
    public static final int NORMAL = 150;
    public static final int SMALL = 98;

    private Dialogs() {
    }

    public static ActionButton button(String label, String tooltip, int width, BiConsumer<Player, DialogResponseView> action) {
        DialogAction dialogAction = DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player p) {
                sync(() -> action.accept(p, view));
            }
        }, OPTIONS);
        return ActionButton.builder(Msg.mm(label))
                .tooltip(tooltip == null || tooltip.isEmpty() ? null : Msg.mm(tooltip))
                .width(width)
                .action(dialogAction)
                .build();
    }

    public static ActionButton button(String label, String tooltip, int width, Consumer<Player> action) {
        return button(label, tooltip, width, (p, v) -> action.accept(p));
    }

    public static ActionButton link(String label, String tooltip, int width, String url) {
        return ActionButton.builder(Msg.mm(label))
                .tooltip(tooltip == null ? null : Msg.mm(tooltip))
                .width(width)
                .action(DialogAction.staticAction(ClickEvent.openUrl(url)))
                .build();
    }

    public static ActionButton close(String label) {
        return ActionButton.builder(Msg.mm(label)).width(SMALL).build();
    }

    public static DialogBody text(String mini) {
        return DialogBody.plainMessage(Msg.mm(mini), WIDE);
    }

    public static DialogBody text(String mini, int width) {
        return DialogBody.plainMessage(Msg.mm(mini), width);
    }

    public static DialogBody item(ItemStack item, String description) {
        return DialogBody.item(item)
                .description(description == null ? null : DialogBody.plainMessage(Msg.mm(description), 260))
                .showDecorations(true)
                .showTooltip(true)
                .width(16).height(16)
                .build();
    }

    public static DialogBase base(String title, List<? extends DialogBody> body, List<? extends DialogInput> inputs) {
        return DialogBase.builder(Msg.mm(title))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
    }

    public static Dialog menu(String title, List<? extends DialogBody> body, List<? extends DialogInput> inputs,
                              List<ActionButton> buttons, ActionButton exit, int columns) {
        DialogBase base = base(title, body, inputs);
        DialogType type = DialogType.multiAction(buttons).exitAction(exit).columns(Math.max(1, columns)).build();
        return Dialog.create(f -> f.empty().base(base).type(type));
    }

    public static Dialog notice(String title, List<? extends DialogBody> body, ActionButton ok) {
        DialogBase base = base(title, body, List.of());
        return Dialog.create(f -> f.empty().base(base).type(DialogType.notice(ok)));
    }

    public static Dialog confirm(String title, String question, ActionButton yes, ActionButton no) {
        DialogBase base = base(title, List.of(text(question)), List.of());
        return Dialog.create(f -> f.empty().base(base).type(DialogType.confirmation(yes, no)));
    }

    public static void show(Player player, Dialog dialog) {
        player.showDialog(dialog);
    }

    public static void message(Player player, String title, String body, Runnable back) {
        ActionButton ok = back == null ? close("<gray>Close") : button("<yellow>« Back", null, SMALL, p -> back.run());
        show(player, notice(title, List.of(text(body)), ok));
    }

    public static void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(SmpCore.get(), r);
        }
    }

    public static Component label(String mini) {
        return Msg.mm(mini);
    }
}
