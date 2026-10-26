package dev.smpcore.ui;

import dev.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Small chest inventory used where real item slots are needed (inventory previews/editing).
 */
public final class ChestGui implements InventoryHolder {
    private final Inventory inventory;
    private final boolean editable;
    private final Map<Integer, Consumer<Player>> buttons = new HashMap<>();
    private Consumer<Inventory> onClose;

    public ChestGui(int rows, String title, boolean editable) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Msg.mm(title));
        this.editable = editable;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public ChestGui set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        return this;
    }

    public ChestGui button(int slot, ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        buttons.put(slot, action);
        return this;
    }

    public ChestGui onClose(Consumer<Inventory> onClose) {
        this.onClose = onClose;
        return this;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    public static final class Events implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder(false) instanceof ChestGui gui)) return;
            boolean top = e.getClickedInventory() == gui.inventory;
            Consumer<Player> action = top ? gui.buttons.get(e.getSlot()) : null;
            if (action != null) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player p) action.accept(p);
                return;
            }
            if (!gui.editable) {
                e.setCancelled(true);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder(false) instanceof ChestGui gui) {
                if (!gui.editable) {
                    e.setCancelled(true);
                    return;
                }
                for (int slot : e.getRawSlots()) {
                    if (slot < gui.inventory.getSize() && gui.buttons.containsKey(slot)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder(false) instanceof ChestGui gui && gui.onClose != null) {
                gui.onClose.accept(gui.inventory);
            }
        }
    }
}
