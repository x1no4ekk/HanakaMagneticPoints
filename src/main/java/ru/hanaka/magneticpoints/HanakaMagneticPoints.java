package ru.hanaka.magneticpoints;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.hanaka.magneticpoints.command.MagnetCommand;
import ru.hanaka.magneticpoints.config.Messages;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.gui.GuiListener;
import ru.hanaka.magneticpoints.gui.MagnetGui;
import ru.hanaka.magneticpoints.magnet.EffectTask;
import ru.hanaka.magneticpoints.magnet.MagnetEngine;
import ru.hanaka.magneticpoints.magnet.MetalWeights;
import ru.hanaka.magneticpoints.magnet.PointStorage;
import ru.hanaka.magneticpoints.util.Text;

/**
 * ⚡ HanakaMagneticPoints — магнитные точки для Paper 1.21.
 */
public final class HanakaMagneticPoints extends JavaPlugin {

    private PluginConfig config;
    private Messages messages;
    private PointStorage storage;
    private MagnetGui gui;
    private BukkitTask engineTask;
    private BukkitTask effectTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new PluginConfig(this);
        this.messages = new Messages(this, config);
        this.storage = new PointStorage(this);
        this.storage.load();
        this.gui = new MagnetGui(this);

        PluginCommand command = getCommand("magnet");
        if (command != null) {
            MagnetCommand executor = new MagnetCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Команда /magnet не зарегистрирована, проверьте plugin.yml");
        }

        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        startTasks();

        Bukkit.getConsoleSender().sendMessage(Text.gradient(
                "⚡ HanakaMagneticPoints v" + version() + " включён · точек: " + storage.size(),
                config.gradientStart(), config.gradientEnd()));
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (storage != null) {
            storage.save();
        }
    }

    public void reloadEverything() {
        stopTasks();
        reloadConfig();
        config.reload();
        messages.reload();
        storage.load();
        startTasks();
    }

    public void restartTasks() {
        stopTasks();
        startTasks();
    }

    private void startTasks() {
        engineTask = Bukkit.getScheduler().runTaskTimer(this, new MagnetEngine(this), 20L, config.updateInterval());
        effectTask = Bukkit.getScheduler().runTaskTimer(this, new EffectTask(this), 40L, config.particleInterval());
        int autoSave = config.autoSaveInterval();
        if (autoSave > 0) {
            long period = autoSave * 20L;
            saveTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    storage.save();
                }
            }, period, period);
        }
    }

    private void stopTasks() {
        if (engineTask != null) {
            engineTask.cancel();
            engineTask = null;
        }
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    public String version() {
        try {
            return getPluginMeta().getVersion();
        } catch (Throwable throwable) {
            return "2.0.0";
        }
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public PointStorage storage() {
        return storage;
    }

    public MagnetGui gui() {
        return gui;
    }

    public MetalWeights weights() {
        return config.weights();
    }
}
