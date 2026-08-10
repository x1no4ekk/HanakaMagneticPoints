package ru.hanaka.magneticpoints.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.magnet.MagnetPoint;
import ru.hanaka.magneticpoints.util.Actions;
import ru.hanaka.magneticpoints.util.Placeholders;
import ru.hanaka.magneticpoints.util.Sounds;

/**
 * Обработка кликов в GUI-панели.
 */
public final class GuiListener implements Listener {

    private final HanakaMagneticPoints plugin;

    public GuiListener(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MagnetGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof MagnetGuiHolder)) {
            return;
        }
        MagnetGuiHolder holder = (MagnetGuiHolder) inventory.getHolder();
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        PluginConfig config = plugin.config();

        if (!player.hasPermission("magnet.admin") && !player.hasPermission("magnet.gui")) {
            plugin.messages().send(player, "no-permission", Placeholders.of("permission", "magnet.gui"));
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        if (slot == holder.getCloseSlot()) {
            Sounds.play(player, config.soundClick(), config.uiVolume(), config.uiPitch());
            player.closeInventory();
            return;
        }
        if (slot == holder.getPreviousSlot()) {
            Sounds.play(player, config.soundClick(), config.uiVolume(), config.uiPitch());
            plugin.gui().open(player, holder.getPage() - 1);
            return;
        }
        if (slot == holder.getNextSlot()) {
            Sounds.play(player, config.soundClick(), config.uiVolume(), config.uiPitch());
            plugin.gui().open(player, holder.getPage() + 1);
            return;
        }

        String name = holder.pointAt(slot);
        if (name == null) {
            return;
        }
        MagnetPoint point = plugin.storage().get(name);
        if (point == null) {
            plugin.gui().open(player, holder.getPage());
            return;
        }

        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_RIGHT) {
            Actions.delete(plugin, point);
            Sounds.play(player, config.soundDelete(), config.uiVolume(), config.uiPitch());
            plugin.messages().send(player, "point.removed", plugin.messages().placeholders(point));
            if (config.guiAutoRefresh()) {
                plugin.gui().open(player, holder.getPage());
            }
            return;
        }
        if (click == ClickType.RIGHT) {
            Actions.toggle(plugin, point);
            Sounds.play(player, config.soundClick(), config.uiVolume(), config.uiPitch());
            plugin.messages().send(player, "point.toggled", plugin.messages().placeholders(point));
            if (config.guiAutoRefresh()) {
                plugin.gui().open(player, holder.getPage());
            }
            return;
        }
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            player.closeInventory();
            if (Actions.teleport(plugin, player, point)) {
                plugin.messages().send(player, "point.teleported", plugin.messages().placeholders(point));
            } else {
                plugin.messages().send(player, "point.world-missing", plugin.messages().placeholders(point));
            }
        }
    }
}
