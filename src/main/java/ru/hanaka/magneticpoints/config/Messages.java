package ru.hanaka.magneticpoints.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.magnet.MagnetPoint;
import ru.hanaka.magneticpoints.util.Text;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Все тексты живут в messages.yml и поддерживают MiniMessage и градиенты &lt;g&gt;/&lt;e&gt;.
 */
public final class Messages {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    /** Прочерк для пустых значений. */
    private static final String DASH = "\u2014";

    private final HanakaMagneticPoints plugin;
    private final PluginConfig config;
    private FileConfiguration yaml;

    public Messages(HanakaMagneticPoints plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        String fileName = config.messagesFile();
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException exception) {
                plugin.saveResource("messages.yml", false);
                file = new File(plugin.getDataFolder(), "messages.yml");
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
    }

    public String raw(String key) {
        String value = yaml.getString(key);
        return value == null ? "" : value;
    }

    public List<String> rawList(String key) {
        List<String> list = yaml.getStringList(key);
        if (!list.isEmpty()) {
            return list;
        }
        String single = yaml.getString(key);
        List<String> result = new ArrayList<>();
        if (single != null && !single.isEmpty()) {
            result.add(single);
        }
        return result;
    }

    public Component text(String rawText, Map<String, String> placeholders) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        merged.putIfAbsent("prefix", yaml.getString("prefix", ""));
        merged.putIfAbsent("version", plugin.version());
        merged.putIfAbsent("min", number(config.minRadius()));
        merged.putIfAbsent("max", number(config.maxRadius()));
        String filled = Text.fill(rawText, merged);
        return Text.parse(filled, config.gradientStart(), config.gradientEnd(),
                config.errorStart(), config.errorEnd());
    }

    public Component component(String key, Map<String, String> placeholders) {
        return text(raw(key), placeholders);
    }

    public List<Component> componentList(String key, Map<String, String> placeholders) {
        List<Component> components = new ArrayList<>();
        for (String line : rawList(key)) {
            components.add(text(line, placeholders));
        }
        return components;
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String rawMessage = raw(key);
        if (rawMessage.isEmpty()) {
            return;
        }
        sender.sendMessage(text(rawMessage, placeholders));
    }

    public void sendList(CommandSender sender, String key, Map<String, String> placeholders) {
        for (String line : rawList(key)) {
            sender.sendMessage(text(line, placeholders));
        }
    }

    public Map<String, String> placeholders(MagnetPoint point) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", point.getName());
        map.put("world", point.getWorldName());
        map.put("x", number(point.getX()));
        map.put("y", number(point.getY()));
        map.put("z", number(point.getZ()));
        map.put("radius", number(point.getRadius()));
        map.put("status", raw(point.isEnabled() ? "point.status-enabled" : "point.status-disabled"));
        map.put("creator", point.getCreator());
        map.put("created", point.getCreatedAt() <= 0 ? DASH : DATE_FORMAT.format(Instant.ofEpochMilli(point.getCreatedAt())));
        map.put("distance", DASH);
        return map;
    }

    /**
     * Полоска силы для экшн-бара. Строится как MiniMessage-строка, чтобы подхватить градиент.
     */
    public String bar(int value, int max) {
        if (!config.barEnabled() || config.barLength() <= 0) {
            return "";
        }
        int length = config.barLength();
        int filled = max <= 0 ? 0 : (int) Math.round(((double) value / (double) max) * length);
        if (filled < 0) {
            filled = 0;
        }
        if (filled > length) {
            filled = length;
        }
        StringBuilder builder = new StringBuilder();
        if (filled > 0) {
            builder.append("<g>").append(repeat(config.barFilled(), filled)).append("</g>");
        }
        if (length - filled > 0) {
            builder.append("<dark_gray>").append(repeat(config.barEmpty(), length - filled)).append("</dark_gray>");
        }
        return builder.toString();
    }

    public static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
