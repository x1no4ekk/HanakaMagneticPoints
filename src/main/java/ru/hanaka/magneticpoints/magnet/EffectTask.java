package ru.hanaka.magneticpoints.magnet;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.util.Sounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Анимация частиц по кругу вокруг точки и фоновый звук маяка.
 */
public final class EffectTask implements Runnable {

    private final HanakaMagneticPoints plugin;
    private long ticks;

    public EffectTask(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PluginConfig config = plugin.config();
        int interval = config.particleInterval();
        ticks += interval;

        boolean drawParticles = config.particlesEnabled()
                && config.particleVisibility() != PluginConfig.Visibility.NONE
                && config.particle() != null;
        boolean playSound = config.soundsEnabled() && ticks % Math.max(interval, config.soundInterval()) < interval;
        if (!drawParticles && !playSound) {
            return;
        }

        double phase = config.particleRotate() ? Math.toRadians((ticks * 2L) % 360L) : 0.0;

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
            List<Player> viewers = viewers(config, world, center);
            if (viewers.isEmpty()) {
                continue;
            }
            if (drawParticles) {
                drawRings(config, point, center, viewers, phase);
            }
            if (playSound) {
                for (Player viewer : viewers) {
                    Sounds.play(viewer, center, config.ambientSound(), config.soundVolume(), config.soundPitch());
                }
            }
        }
    }

    private List<Player> viewers(PluginConfig config, World world, Location center) {
        List<Player> viewers = new ArrayList<>();
        double maxDistanceSquared = config.viewDistance() * config.viewDistance();
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            double dx = location.getX() - center.getX();
            double dy = location.getY() - center.getY();
            double dz = location.getZ() - center.getZ();
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            viewers.add(player);
        }
        return viewers;
    }

    private boolean canSee(PluginConfig config, Player player) {
        switch (config.particleVisibility()) {
            case ALL:
                return true;
            case ADMIN:
                return player.hasPermission("magnet.particles.see") || player.hasPermission("magnet.admin");
            default:
                return false;
        }
    }

    private void drawRings(PluginConfig config, MagnetPoint point, Location center, List<Player> viewers, double phase) {
        Particle particle = config.particle();
        int count = config.particleCount();
        double radius = point.getRadius();
        double spread = config.particleSpread();
        double speed = config.particleSpeed();

        List<Player> allowed = new ArrayList<>();
        for (Player viewer : viewers) {
            if (canSee(config, viewer)) {
                allowed.add(viewer);
            }
        }
        if (allowed.isEmpty()) {
            return;
        }

        for (int ring = 0; ring < config.particleRings(); ring++) {
            double y = center.getY() + config.verticalOffset() + ring * config.ringHeight();
            for (int index = 0; index < count; index++) {
                double angle = (2.0 * Math.PI * index / count) + phase + ring * 0.15;
                double x = center.getX() + Math.cos(angle) * radius;
                double z = center.getZ() + Math.sin(angle) * radius;
                for (Player viewer : allowed) {
                    viewer.spawnParticle(particle, x, y, z, 1, spread, spread, spread, speed);
                }
            }
        }
    }
}
