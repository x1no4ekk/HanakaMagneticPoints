package ru.hanaka.magneticpoints.magnet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * Одна магнитная точка. Сохраняется в points.yml и переживает перезапуски сервера.
 */
public final class MagnetPoint {

    private final String name;
    private final String worldName;
    private final UUID worldId;
    private final double x;
    private final double y;
    private final double z;
    private double radius;
    private boolean enabled;
    private final String creator;
    private final long createdAt;

    public MagnetPoint(String name,
                       String worldName,
                       UUID worldId,
                       double x,
                       double y,
                       double z,
                       double radius,
                       boolean enabled,
                       String creator,
                       long createdAt) {
        this.name = name;
        this.worldName = worldName;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.enabled = enabled;
        this.creator = creator;
        this.createdAt = createdAt;
    }

    public static MagnetPoint create(String name, Location location, double radius, String creator) {
        World world = location.getWorld();
        return new MagnetPoint(
                name,
                world == null ? "world" : world.getName(),
                world == null ? null : world.getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                radius,
                true,
                creator == null ? "CONSOLE" : creator,
                System.currentTimeMillis());
    }

    public static MagnetPoint load(String name, ConfigurationSection section, double fallbackRadius) {
        if (section == null) {
            return null;
        }
        UUID worldId = null;
        String rawId = section.getString("world-id");
        if (rawId != null && !rawId.isEmpty()) {
            try {
                worldId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ignored) {
                worldId = null;
            }
        }
        return new MagnetPoint(
                name,
                section.getString("world", "world"),
                worldId,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                section.getDouble("radius", fallbackRadius),
                section.getBoolean("enabled", true),
                section.getString("creator", "—"),
                section.getLong("created-at", 0L));
    }

    public void save(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("world-id", worldId == null ? null : worldId.toString());
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("radius", radius);
        section.set("enabled", enabled);
        section.set("creator", creator);
        section.set("created-at", createdAt);
    }

    public World getWorld() {
        if (worldId != null) {
            World world = Bukkit.getWorld(worldId);
            if (world != null) {
                return world;
            }
        }
        return Bukkit.getWorld(worldName);
    }

    public Location getLocation() {
        World world = getWorld();
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public boolean isInside(Location location) {
        if (location == null) {
            return false;
        }
        World world = getWorld();
        if (world == null || location.getWorld() == null || !world.equals(location.getWorld())) {
            return false;
        }
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreator() {
        return creator;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
