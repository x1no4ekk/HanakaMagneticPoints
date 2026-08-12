package ru.hanaka.magneticpoints.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.Messages;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.magnet.MagnetPoint;
import ru.hanaka.magneticpoints.util.Actions;
import ru.hanaka.magneticpoints.util.Permissions;
import ru.hanaka.magneticpoints.util.Placeholders;
import ru.hanaka.magneticpoints.util.Sounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /magnet и алиасы /hanakamagnetic, /hmp — всё управление точками.
 *
 * <p>Каждая подкоманда требует своё право (см. {@link Permissions}), а в помощи и в подсказках
 * Tab показываются только те команды, которые игроку действительно доступны.
 */
public final class MagnetCommand implements CommandExecutor, TabCompleter {

    /** Подкоманда → право, которое её открывает. Порядок совпадает с порядком строк в /magnet help. */
    private static final String[][] ACTIONS = {
            {"add", Permissions.CREATE},
            {"remove", Permissions.DELETE},
            {"list", Permissions.LIST},
            {"info", Permissions.INFO},
            {"toggle", Permissions.TOGGLE},
            {"tp", Permissions.TELEPORT},
            {"radius", Permissions.RADIUS},
            {"gui", Permissions.GUI},
            {"particles", Permissions.PARTICLES},
            {"reload", Permissions.RELOAD},
    };

    private final HanakaMagneticPoints plugin;

    public MagnetCommand(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!Permissions.has(sender, Permissions.USE)) {
            deny(sender, Permissions.USE);
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            if (allowed(sender, Permissions.CREATE)) {
                add(sender, label, args);
            }
        } else if (sub.equals("remove") || sub.equals("delete") || sub.equals("del")) {
            if (allowed(sender, Permissions.DELETE)) {
                remove(sender, label, args);
            }
        } else if (sub.equals("list")) {
            if (allowed(sender, Permissions.LIST)) {
                list(sender);
            }
        } else if (sub.equals("info")) {
            if (allowed(sender, Permissions.INFO)) {
                info(sender, label, args);
            }
        } else if (sub.equals("toggle")) {
            if (allowed(sender, Permissions.TOGGLE)) {
                toggle(sender, label, args);
            }
        } else if (sub.equals("tp") || sub.equals("teleport")) {
            if (allowed(sender, Permissions.TELEPORT)) {
                teleport(sender, label, args);
            }
        } else if (sub.equals("radius")) {
            if (allowed(sender, Permissions.RADIUS)) {
                radius(sender, label, args);
            }
        } else if (sub.equals("gui") || sub.equals("menu")) {
            if (allowed(sender, Permissions.GUI)) {
                gui(sender);
            }
        } else if (sub.equals("particles")) {
            if (allowed(sender, Permissions.PARTICLES)) {
                particles(sender, label, args);
            }
        } else if (sub.equals("reload")) {
            if (allowed(sender, Permissions.RELOAD)) {
                reload(sender);
            }
        } else if (sub.equals("help") || sub.equals("?")) {
            help(sender, label);
        } else {
            messages.send(sender, "unknown-subcommand", Placeholders.of("input", args[0], "label", label));
            error(sender);
        }
        return true;
    }

    /** Проверяет право и сам сообщает об отказе. */
    private boolean allowed(CommandSender sender, String permission) {
        if (Permissions.has(sender, permission)) {
            return true;
        }
        deny(sender, permission);
        return false;
    }

    private void deny(CommandSender sender, String permission) {
        plugin.messages().send(sender, "no-permission", Placeholders.of("permission", permission));
        error(sender);
    }

    private void help(CommandSender sender, String label) {
        Messages messages = plugin.messages();
        PluginConfig config = plugin.config();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("label", label);
        placeholders.put("version", plugin.version());
        placeholders.put("min", Messages.number(config.minRadius()));
        placeholders.put("max", Messages.number(config.maxRadius()));

        messages.send(sender, "help.header", placeholders);
        boolean shown = false;
        for (int index = 0; index < ACTIONS.length; index++) {
            if (!Permissions.has(sender, ACTIONS[index][1])) {
                continue;
            }
            String key = "help.entries." + ACTIONS[index][0];
            if (messages.raw(key).isEmpty()) {
                continue;
            }
            messages.send(sender, key, placeholders);
            shown = true;
        }
        if (!shown) {
            // Старый messages.yml без секции help.entries — показываем общий список.
            messages.sendList(sender, "help.lines", placeholders);
        }
        messages.send(sender, "help.footer", placeholders);
    }

    private void add(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        PluginConfig config = plugin.config();
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only", Placeholders.empty());
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            usage(sender, label, "add ‹название›");
            return;
        }
        String name = args[1];
        if (!config.nameAllowed(name)) {
            messages.send(sender, "point.invalid-name",
                    Placeholders.of("name", name, "pattern", config.namePattern()));
            error(sender);
            return;
        }
        if (plugin.storage().exists(name)) {
            messages.send(sender, "point.exists", Placeholders.of("name", name));
            error(sender);
            return;
        }
        int limit = config.maxPoints();
        if (limit > 0 && plugin.storage().size() >= limit) {
            messages.send(sender, "point.limit", Placeholders.of("limit", String.valueOf(limit)));
            error(sender);
            return;
        }
        MagnetPoint point = plugin.storage().create(name, player.getLocation(), config.defaultRadius(), player.getName());
        plugin.storage().save();
        messages.send(sender, "point.created", messages.placeholders(point));
        success(sender);
    }

    private void remove(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            usage(sender, label, "remove ‹название›");
            return;
        }
        MagnetPoint point = find(sender, args[1]);
        if (point == null) {
            return;
        }
        Actions.delete(plugin, point);
        messages.send(sender, "point.removed", messages.placeholders(point));
        if (sender instanceof Player) {
            Sounds.play((Player) sender, plugin.config().soundDelete(),
                    plugin.config().uiVolume(), plugin.config().uiPitch());
        }
    }

    private void list(CommandSender sender) {
        Messages messages = plugin.messages();
        Collection<MagnetPoint> points = plugin.storage().all();
        if (points.isEmpty()) {
            messages.send(sender, "point.list-empty", Placeholders.empty());
            return;
        }
        messages.send(sender, "point.list-header", Placeholders.of("count", String.valueOf(points.size())));
        for (MagnetPoint point : points) {
            messages.send(sender, "point.list-entry", messages.placeholders(point));
        }
    }

    private void info(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            usage(sender, label, "info ‹название›");
            return;
        }
        MagnetPoint point = find(sender, args[1]);
        if (point == null) {
            return;
        }
        Map<String, String> placeholders = messages.placeholders(point);
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (player.getWorld().getName().equals(point.getWorldName())) {
                double dx = player.getLocation().getX() - point.getX();
                double dy = player.getLocation().getY() - point.getY();
                double dz = player.getLocation().getZ() - point.getZ();
                placeholders.put("distance", Messages.number(Math.sqrt(dx * dx + dy * dy + dz * dz)));
            }
        }
        messages.sendList(sender, "point.info", placeholders);
    }

    private void toggle(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            usage(sender, label, "toggle ‹название›");
            return;
        }
        MagnetPoint point = find(sender, args[1]);
        if (point == null) {
            return;
        }
        Actions.toggle(plugin, point);
        messages.send(sender, "point.toggled", messages.placeholders(point));
        success(sender);
    }

    private void teleport(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only", Placeholders.empty());
            return;
        }
        if (args.length < 2) {
            usage(sender, label, "tp ‹название›");
            return;
        }
        MagnetPoint point = find(sender, args[1]);
        if (point == null) {
            return;
        }
        Player player = (Player) sender;
        if (Actions.teleport(plugin, player, point)) {
            messages.send(sender, "point.teleported", messages.placeholders(point));
        } else {
            messages.send(sender, "point.world-missing", messages.placeholders(point));
            error(sender);
        }
    }

    private void radius(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        PluginConfig config = plugin.config();
        if (args.length < 3) {
            usage(sender, label, "radius ‹название› ‹радиус›");
            return;
        }
        MagnetPoint point = find(sender, args[1]);
        if (point == null) {
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[2].replace(',', '.'));
        } catch (NumberFormatException exception) {
            messages.send(sender, "point.radius-invalid", Placeholders.of(
                    "min", Messages.number(config.minRadius()),
                    "max", Messages.number(config.maxRadius()),
                    "input", args[2]));
            error(sender);
            return;
        }
        if (value < config.minRadius() || value > config.maxRadius()) {
            messages.send(sender, "point.radius-invalid", Placeholders.of(
                    "min", Messages.number(config.minRadius()),
                    "max", Messages.number(config.maxRadius()),
                    "input", args[2]));
            error(sender);
            return;
        }
        point.setRadius(value);
        plugin.storage().save();
        messages.send(sender, "point.radius-changed", messages.placeholders(point));
        success(sender);
    }

    private void gui(CommandSender sender) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only", Placeholders.empty());
            return;
        }
        plugin.gui().open((Player) sender, 0);
    }

    private void particles(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            usage(sender, label, "particles ‹on/off/admin›");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("on") || mode.equals("all") || mode.equals("true")) {
            plugin.getConfig().set("particles.enabled", true);
            plugin.getConfig().set("particles.visibility", "all");
        } else if (mode.equals("off") || mode.equals("none") || mode.equals("false")) {
            plugin.getConfig().set("particles.enabled", false);
            plugin.getConfig().set("particles.visibility", "none");
        } else if (mode.equals("admin") || mode.equals("ops") || mode.equals("op")) {
            plugin.getConfig().set("particles.enabled", true);
            plugin.getConfig().set("particles.visibility", "admin");
        } else {
            messages.send(sender, "particles.invalid", Placeholders.of("input", args[1]));
            error(sender);
            return;
        }
        plugin.saveConfig();
        plugin.config().reload();
        plugin.restartTasks();
        messages.send(sender, "particles.updated", Placeholders.of("mode", mode));
        success(sender);
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.reloadEverything();
        long elapsed = System.currentTimeMillis() - start;
        plugin.messages().send(sender, "reloaded", Placeholders.of(
                "ms", String.valueOf(elapsed),
                "points", String.valueOf(plugin.storage().size())));
        success(sender);
    }

    private MagnetPoint find(CommandSender sender, String name) {
        MagnetPoint point = plugin.storage().get(name);
        if (point == null) {
            plugin.messages().send(sender, "point.not-found", Placeholders.of("name", name));
            error(sender);
        }
        return point;
    }

    private void usage(CommandSender sender, String label, String usage) {
        plugin.messages().send(sender, "usage", Placeholders.of("label", label, "usage", usage));
    }

    private void success(CommandSender sender) {
        if (sender instanceof Player) {
            PluginConfig config = plugin.config();
            Sounds.play((Player) sender, config.soundSuccess(), config.uiVolume(), config.uiPitch());
        }
    }

    private void error(CommandSender sender) {
        if (sender instanceof Player) {
            PluginConfig config = plugin.config();
            Sounds.play((Player) sender, config.soundError(), config.uiVolume(), config.uiPitch());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Permissions.has(sender, Permissions.USE)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            // Подсказываем только те команды, на которые есть права.
            List<String> available = new ArrayList<>();
            for (int index = 0; index < ACTIONS.length; index++) {
                if (Permissions.has(sender, ACTIONS[index][1])) {
                    available.add(ACTIONS[index][0]);
                }
            }
            available.add("help");
            return filter(available, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if ((sub.equals("remove") || sub.equals("delete") || sub.equals("del")) && Permissions.has(sender, Permissions.DELETE)) {
                return filter(plugin.storage().names(), args[1]);
            }
            if (sub.equals("info") && Permissions.has(sender, Permissions.INFO)) {
                return filter(plugin.storage().names(), args[1]);
            }
            if (sub.equals("toggle") && Permissions.has(sender, Permissions.TOGGLE)) {
                return filter(plugin.storage().names(), args[1]);
            }
            if ((sub.equals("tp") || sub.equals("teleport")) && Permissions.has(sender, Permissions.TELEPORT)) {
                return filter(plugin.storage().names(), args[1]);
            }
            if (sub.equals("radius") && Permissions.has(sender, Permissions.RADIUS)) {
                return filter(plugin.storage().names(), args[1]);
            }
            if (sub.equals("particles") && Permissions.has(sender, Permissions.PARTICLES)) {
                return filter(Arrays.asList("on", "off", "admin"), args[1]);
            }
            return Collections.emptyList();
        }
        if (args.length == 3 && sub.equals("radius") && Permissions.has(sender, Permissions.RADIUS)) {
            return filter(Arrays.asList("8", "12", "16", "24", "32", "48"), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        Collections.sort(result);
        return result;
    }
}
