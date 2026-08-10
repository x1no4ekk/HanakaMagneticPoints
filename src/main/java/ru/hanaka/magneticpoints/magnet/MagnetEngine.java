package ru.hanaka.magneticpoints.magnet;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.util.Placeholders;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сердце плагина: каждые N тиков подтягивает металлические предметы и игроков с металлом.
 */
public final class MagnetEngine implements Runnable {

    private final HanakaMagneticPoints plugin;
    private final Map<UUID, Long> actionBarCooldowns = new HashMap<>();
    private long ticks;

    public MagnetEngine(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PluginConfig config = plugin.config();
        ticks += config.updateInterval();
        for (MagnetPoint point : plugin.storage().all()) {
            if (!point.isEnabled()) {
                continue;
            }
            Location center = point.getLocation();
            if (center == null) {
                continue;
            }
            World world = center.getWorld();
            if (world == null || config.worldDisabled(world.getName())) {
                continue;
            }
            double radius = point.getRadius();
            if (radius <= 0.0) {
                continue;
            }
            if (config.itemsEnabled()) {
                pullItems(config, world, center, radius);
            }
            if (config.playersEnabled()) {
                pullPlayers(config, point, world, center, radius);
            }
        }
    }

    private void pullItems(PluginConfig config, World world, Location center, double radius) {
        double radiusSquared = radius * radius;
        Collection<Entity> entities = world.getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : entities) {
            if (!(entity instanceof Item)) {
                continue;
            }
            Item item = (Item) entity;
            if (!config.itemsIgnorePickupDelay() && item.getPickupDelay() > 0) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            int weight = plugin.weights().weight(stack.getType());
            if (weight <= 0) {
                continue;
            }
            Location location = item.getLocation();
            double dx = center.getX() - location.getX();
            double dy = center.getY() - location.getY();
            double dz = center.getZ() - location.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            if (distance <= config.stopDistance()) {
                if (config.itemsFreeze()) {
                    item.setVelocity(new Vector(0.0, 0.0, 0.0));
                }
                continue;
            }
            double power = config.itemsSpeed() * (weight / 10.0) * config.strengthMultiplier();
            Vector pull = new Vector(dx / distance * power,
                    dy / distance * power + config.itemsVerticalBoost(),
                    dz / distance * power);
            item.setVelocity(clamp(item.getVelocity().add(pull), config.maxVelocity()));
        }
    }

    private void pullPlayers(PluginConfig config, MagnetPoint point, World world, Location center, double radius) {
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            double dx = center.getX() - location.getX();
            double dy = center.getY() - location.getY();
            double dz = center.getZ() - location.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (isProtected(config, player)) {
                continue;
            }
            int weight = weightOf(config, player);
            if (weight < config.minWeight()) {
                continue;
            }
            int capped = Math.min(weight, config.maxWeight());
            double distance = Math.sqrt(distanceSquared);
            if (distance > config.stopDistance()) {
                double power = config.playersSpeed() * (capped / 10.0) * config.strengthMultiplier();
                Vector pull = new Vector(dx / distance * power,
                        dy / distance * power + config.playersVerticalBoost(),
                        dz / distance * power);
                player.setVelocity(clamp(player.getVelocity().add(pull), config.maxVelocity()));
            }
            if (config.actionBarEnabled() && ready(player, config)) {
                Map<String, String> placeholders = plugin.messages().placeholders(point);
                placeholders.put("strength", String.valueOf(capped));
                placeholders.put("max", String.valueOf(config.maxWeight()));
                placeholders.put("bar", plugin.messages().bar(capped, config.maxWeight()));
                placeholders.put("point", point.getName());
                placeholders.put("distance", String.format(java.util.Locale.ROOT, "%.1f", distance));
                String rawMessage = plugin.messages().raw("action-bar");
                if (!rawMessage.isEmpty()) {
                    player.sendActionBar(plugin.messages().text(rawMessage, placeholders));
                }
            }
        }
    }

    private boolean ready(Player player, PluginConfig config) {
        UUID uuid = player.getUniqueId();
        Long last = actionBarCooldowns.get(uuid);
        if (last != null && ticks - last < config.actionBarInterval()) {
            return false;
        }
        actionBarCooldowns.put(uuid, ticks);
        return true;
    }

    private boolean isProtected(PluginConfig config, Player player) {
        if (config.protectSneaking() && player.isSneaking()) {
            return true;
        }
        if (config.protectElytra() && player.isGliding()) {
            return true;
        }
        if (config.protectFlying() && player.isFlying()) {
            return true;
        }
        GameMode mode = player.getGameMode();
        if (config.protectCreative() && mode == GameMode.CREATIVE) {
            return true;
        }
        if (config.protectSpectator() && mode == GameMode.SPECTATOR) {
            return true;
        }
        String permission = config.bypassPermission();
        return permission != null && !permission.isEmpty() && player.hasPermission(permission);
    }

    private int weightOf(PluginConfig config, Player player) {
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        if (config.countHands()) {
            total += weightOf(config, inventory.getItemInMainHand());
            total += weightOf(config, inventory.getItemInOffHand());
        }
        if (config.countArmor()) {
            for (ItemStack armor : inventory.getArmorContents()) {
                total += weightOf(config, armor);
            }
        }
        if (config.countInventory()) {
            for (ItemStack stack : inventory.getStorageContents()) {
                total += weightOf(config, stack);
            }
        }
        return total;
    }

    private int weightOf(PluginConfig config, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        int weight = plugin.weights().weight(stack.getType());
        if (weight <= 0) {
            return 0;
        }
        return config.countStackAmount() ? weight * Math.max(1, stack.getAmount()) : weight;
    }

    public void forget(UUID uuid) {
        actionBarCooldowns.remove(uuid);
    }

    private static Vector clamp(Vector vector, double max) {
        double length = vector.length();
        if (!Double.isFinite(length)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        if (max > 0.0 && length > max) {
            vector.multiply(max / length);
        }
        return vector;
    }

    static {
        // Ссылка на Placeholders оставлена намеренно выключенной — плейсхолдеры строятся через Messages.
        Class<?> unused = Placeholders.class;
    }
}
