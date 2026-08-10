package ru.hanaka.magneticpoints.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Держатель инвентаря: по нему слушатель понимает, что открыта панель магнитных точек.
 */
public final class MagnetGuiHolder implements InventoryHolder {

    private final Map<Integer, String> points = new HashMap<>();
    private final int page;
    private final int pages;
    private int previousSlot = -1;
    private int nextSlot = -1;
    private int closeSlot = -1;
    private Inventory inventory;

    public MagnetGuiHolder(int page, int pages) {
        this.page = page;
        this.pages = pages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void bind(int slot, String pointName) {
        points.put(slot, pointName);
    }

    public String pointAt(int slot) {
        return points.get(slot);
    }

    public int getPage() {
        return page;
    }

    public int getPages() {
        return pages;
    }

    public int getPreviousSlot() {
        return previousSlot;
    }

    public void setPreviousSlot(int previousSlot) {
        this.previousSlot = previousSlot;
    }

    public int getNextSlot() {
        return nextSlot;
    }

    public void setNextSlot(int nextSlot) {
        this.nextSlot = nextSlot;
    }

    public int getCloseSlot() {
        return closeSlot;
    }

    public void setCloseSlot(int closeSlot) {
        this.closeSlot = closeSlot;
    }
}
