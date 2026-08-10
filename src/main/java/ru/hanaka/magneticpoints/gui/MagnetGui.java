package ru.hanaka.magneticpoints.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.Messages;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.magnet.MagnetPoint;
import ru.hanaka.magneticpoints.util.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * GUI-панель управления точками: ЛКМ — телепорт, ПКМ — вкл/выкл, Shift+ПКМ — удалить.
 */
public final class MagnetGui {

    private final HanakaMagneticPoints plugin;

    public MagnetGui(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        PluginConfig config = plugin.config();
        Messages messages = plugin.messages();

        List<MagnetPoint> points = new ArrayList<>(plugin.storage().all());
        points.sort(Comparator.comparing(MagnetPoint::getName, String.CASE_INSENSITIVE_ORDER));

        int size = config.guiRows() * 9;
        int perPage = size - 9;
        int pages = Math.max(1, (int) Math.ceil(points.size() / (double) perPage));
        int current = Math.max(0, Math.min(page, pages - 1));

        Map<String, String> menuPlaceholders = Placeholders.of(
                "page", String.valueOf(current + 1),
                "pages", String.valueOf(pages),
                "count", String.valueOf(points.size()));

        MagnetGuiHolder holder = new MagnetGuiHolder(current, pages);
        Inventory inventory = Bukkit.createInventory(holder, size, messages.component("gui.title", menuPlaceholders));
        holder.setInventory(inventory);

        int navigationRow = size - 9;
        if (config.fillerEnabled()) {
            ItemStack filler = icon(config.fillerMaterial(), Component.text(" "), Collections.emptyList());
            for (int slot = navigationRow; slot < size; slot++) {
                inventory.setItem(slot, filler);
            }
        }

        if (points.isEmpty()) {
            inventory.setItem(perPage / 2, icon(config.emptyMaterial(),
                    messages.component("gui.empty.name", menuPlaceholders),
                    messages.componentList("gui.empty.lore", menuPlaceholders)));
        }

        int start = current * perPage;
        for (int index = 0; index < perPage && start + index < points.size(); index++) {
            MagnetPoint point = points.get(start + index);
            Map<String, String> placeholders = messages.placeholders(point);
            ItemStack item = icon(point.isEnabled() ? config.activeMaterial() : config.disabledMaterial(),
                    messages.component("gui.point-name", placeholders),
                    messages.componentList("gui.point-lore", placeholders));
            inventory.setItem(index, item);
            holder.bind(index, point.getName());
        }

        if (current > 0) {
            int slot = navigationRow + 3;
            inventory.setItem(slot, icon(config.previousMaterial(),
                    messages.component("gui.previous.name", menuPlaceholders),
                    messages.componentList("gui.previous.lore", menuPlaceholders)));
            holder.setPreviousSlot(slot);
        }
        if (current < pages - 1) {
            int slot = navigationRow + 5;
            inventory.setItem(slot, icon(config.nextMaterial(),
                    messages.component("gui.next.name", menuPlaceholders),
                    messages.componentList("gui.next.lore", menuPlaceholders)));
            holder.setNextSlot(slot);
        }

        int closeSlot = navigationRow + 4;
        inventory.setItem(closeSlot, icon(config.closeMaterial(),
                messages.component("gui.close.name", menuPlaceholders),
                messages.componentList("gui.close.lore", menuPlaceholders)));
        holder.setCloseSlot(closeSlot);

        inventory.setItem(navigationRow + 8, icon(config.infoMaterial(),
                messages.component("gui.info.name", menuPlaceholders),
                messages.componentList("gui.info.lore", menuPlaceholders)));

        player.openInventory(inventory);
    }

    private ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
