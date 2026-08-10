package ru.hanaka.magneticpoints.magnet;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Хранилище точек: points.yml в папке плагина. Точки переживают перезапуски сервера.
 */
public final class PointStorage {

    private final HanakaMagneticPoints plugin;
    private final File file;
    private final Map<String, MagnetPoint> points = new LinkedHashMap<>();

    public PointStorage(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");
    }

    public void load() {
        points.clear();
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина");
        }
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("points");
        if (root == null) {
            return;
        }
        double fallback = plugin.config().defaultRadius();
        for (String key : root.getKeys(false)) {
            MagnetPoint point = MagnetPoint.load(key, root.getConfigurationSection(key), fallback);
            if (point != null) {
                points.put(key.toLowerCase(Locale.ROOT), point);
            }
        }
        plugin.getLogger().info("Загружено магнитных точек: " + points.size());
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("points");
        for (MagnetPoint point : points.values()) {
            point.save(root.createSection(point.getName()));
        }
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина");
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить points.yml: " + exception.getMessage());
        }
    }

    public MagnetPoint get(String name) {
        if (name == null) {
            return null;
        }
        return points.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return get(name) != null;
    }

    public MagnetPoint create(String name, Location location, double radius, String creator) {
        MagnetPoint point = MagnetPoint.create(name, location, radius, creator);
        points.put(name.toLowerCase(Locale.ROOT), point);
        return point;
    }

    public boolean remove(String name) {
        if (name == null) {
            return false;
        }
        return points.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public Collection<MagnetPoint> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(points.values()));
    }

    public List<String> names() {
        List<String> names = new ArrayList<>(points.size());
        for (MagnetPoint point : points.values()) {
            names.add(point.getName());
        }
        return names;
    }

    public int size() {
        return points.size();
    }
}
