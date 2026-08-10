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
import ru.hanaka.magneticpoints.util.Placeholders;
import ru.hanaka.magneticpoints.util.Sounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /magnet и алиасы /hanakamagnetic, /hmp — всё управление точками.
 */
public final class MagnetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "add", "remove", "list", "info", "toggle", "tp", "radius", "gui", "particles", "reload", "help");

    private final HanakaMagneticPoints plugin;

    public MagnetCommand(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission("magnet.admin")) {
            messages.send(sender, "no-permission", Placeholders.of("permission", "magnet.admin"));
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            add(sender, label, args);
        } else if (sub.equals("remove") || sub.equals("delete") || sub.equals("del")) {
            remove(sender, label, args);
        } else if (sub.equals("list")) {
            list(sender);
        } else if (sub.equals("info")) {
            info(sender, label, args);
        } else if (sub.equals("toggle")) {
            toggle(sender, label, args);
        } else if (sub.equals("tp") || sub.equals("teleport")) {
            teleport(sender, label, args);
        } else if (sub.equals("radius")) {
            radius(sender, label, args);
        } else if (sub.equals("gui") || sub.equals("menu")) {
            gui(sender);
        } else if (sub.equals("particles")) {
            particles(sender, label, args);
        } else if (sub.equals("reload")) {
            reload(sender);
        } else if (sub.equals("help") || sub.equals("?")) {
            help(sender, label);
        } else {
            messages.send(sender, "unknown-subcommand", Placeholders.of("input", args[0], "label", label));
            error(sender);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Messages messages = plugin.messages();
        Map<String, String> placeholders = Placeholders.of("label", label, "version", plugin.version());
        messages.send(sender, "help.header", placeholders);
        messages.sendList(sender, "help.lines", placeholders);
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
            usage(sender, label, "add \\u2039\\u043d\\u0430\\u0437\\u0432\\u0430\\u043d\\u0438\\u0435\\u203a");
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
            usage(sender, label, "remove \\u2039\\u043d\\u0430\\u0437\\u0432\\u0430\\u043d\\u0438\\u0435\\u203a");
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
            usage(sender, label, "info \\u2039\\u043d\\u0430\\u0437\\u0432\\u0430\\u043d\\u0438\\u0435\\u203a");
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
            usage(sender, label, "toggle \\u2039\\u043d\\u0430\\u0437\\u0432\\u0430\\u043d\\u0438\\u0435\\u203a");
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
            usage(sender, label, "tp \\u2039\\u043d\\u0430\\u0437\\u0432\\u0430\\u043d\\u0438\\u0435\\u203a");
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
            usage(sender, label, "radius \\u2039\\u043d\\u0430\\u0437\\u0432\\u0430\\u043d\\u0438\\u0435\\u203a \\u2039\\u0440\\u0430\\u0434\\u0438\\u0443\\u0441\\u203a");
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
            usage(sender, label, "particles \\u2039on/off/admin\\u203a");
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
        if (!sender.hasPermission("magnet.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("remove") || sub.equals("delete") || sub.equals("del") || sub.equals("info")
                    || sub.equals("toggle") || sub.equals("tp") || sub.equals("teleport") || sub.equals("radius")) {
                return filter(plugin.storage().names(), args[1]);
            }
            if (sub.equals("particles")) {
                return filter(Arrays.asList("on", "off", "admin"), args[1]);
            }
            return Collections.emptyList();
        }
        if (args.length == 3 && sub.equals("radius")) {
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
