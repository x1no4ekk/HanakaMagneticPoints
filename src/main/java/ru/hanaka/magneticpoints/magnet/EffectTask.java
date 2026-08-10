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
 *
 * <p>v2.1: частицы не рисуются внутри блоков (при необходимости поднимаются на поверхность),
 * синусы и косинусы кольца считаются один раз, а списки зрителей переиспользуются.
 */
public final class EffectTask implements Runnable {

    private final HanakaMagneticPoints plugin;
    private final List<Player> viewers = new ArrayList<>();
    private final List<Player> allowed = new ArrayList<>();
    private final Location scratch = new Location(null, 0.0, 0.0, 0.0);
    private double[] ringCos = new double[0];
    private double[] ringSin = new double[0];
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
        double phaseCos = Math.cos(phase);
        double phaseSin = Math.sin(phase);
        if (drawParticles) {
            prepareTable(config.particleCount());
        }

        for (MagnetPoint point : plugin.storage().view()) {
            if (!point.isEnabled()) {
                continue;
            }
            Location center = point.cachedLocation();
            if (center == null) {
                continue;
            }
            World world = center.getWorld();
            if (world == null || config.worldDisabled(world.getName())) {
                continue;
            }
            collectViewers(config, world, center);
            if (viewers.isEmpty()) {
                continue;
            }
            if (drawParticles) {
                drawRings(config, point, center, world, phaseCos, phaseSin);
            }
            if (playSound) {
                for (int index = 0; index < viewers.size(); index++) {
                    Sounds.play(viewers.get(index), center, config.ambientSound(),
                            config.soundVolume(), config.soundPitch());
                }
            }
            viewers.clear();
        }
        viewers.clear();
    }

    private void collectViewers(PluginConfig config, World world, Location center) {
        viewers.clear();
        double maxDistanceSquared = config.viewDistance() * config.viewDistance();
        double centerX = center.getX();
        double centerY = center.getY();
        double centerZ = center.getZ();
        List<Player> players = world.getPlayers();
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            player.getLocation(scratch);
            double dx = scratch.getX() - centerX;
            double dy = scratch.getY() - centerY;
            double dz = scratch.getZ() - centerZ;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            viewers.add(player);
        }
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

    private void drawRings(PluginConfig config, MagnetPoint point, Location center, World world,
                           double phaseCos, double phaseSin) {
        double radius = point.getRadius();
        int count = ringCos.length;
        if (radius <= 0.0 || count == 0) {
            return;
        }
        allowed.clear();
        for (int index = 0; index < viewers.size(); index++) {
            Player viewer = viewers.get(index);
            if (canSee(config, viewer)) {
                allowed.add(viewer);
            }
        }
        if (allowed.isEmpty()) {
            return;
        }

        Particle particle = config.particle();
        int rings = config.particleRings();
        double spread = config.particleSpread();
        double speed = config.particleSpeed();
        double ringHeight = config.ringHeight();
        boolean avoidBlocks = config.particlesAvoidBlocks();
        int lift = config.particleSurfaceLift();
        double centerX = center.getX();
        double centerZ = center.getZ();
        double baseY = center.getY() + config.verticalOffset();

        for (int ring = 0; ring < rings; ring++) {
            double ringAngle = ring * 0.15;
            double ringCosOffset = Math.cos(ringAngle);
            double ringSinOffset = Math.sin(ringAngle);
            double rotateCos = phaseCos * ringCosOffset - phaseSin * ringSinOffset;
            double rotateSin = phaseSin * ringCosOffset + phaseCos * ringSinOffset;
            double y = baseY + ring * ringHeight;
            for (int index = 0; index < count; index++) {
                double cos = ringCos[index] * rotateCos - ringSin[index] * rotateSin;
                double sin = ringSin[index] * rotateCos + ringCos[index] * rotateSin;
                double x = centerX + cos * radius;
                double z = centerZ + sin * radius;
                double drawY = y;
                if (avoidBlocks) {
                    drawY = freeHeight(world, x, y, z, lift);
                    if (Double.isNaN(drawY)) {
                        continue;
                    }
                }
                for (int viewer = 0; viewer < allowed.size(); viewer++) {
                    allowed.get(viewer).spawnParticle(particle, x, drawY, z, 1, spread, spread, spread, speed);
                }
            }
        }
        allowed.clear();
    }

    /** Таблица синусов/косинусов кольца считается один раз на весь цикл жизни задачи. */
    private void prepareTable(int count) {
        if (ringCos.length == count) {
            return;
        }
        ringCos = new double[count];
        ringSin = new double[count];
        for (int index = 0; index < count; index++) {
            double angle = 2.0 * Math.PI * index / count;
            ringCos[index] = Math.cos(angle);
            ringSin[index] = Math.sin(angle);
        }
    }

    /**
     * Ищет высоту, на которой частица не окажется внутри блока.
     *
     * @return высота для частицы или {@link Double#NaN}, если свободного места нет
     */
    private static double freeHeight(World world, double x, double y, double z, int lift) {
        int blockX = floor(x);
        int blockZ = floor(z);
        // Частицы не должны подгружать чанки.
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return Double.NaN;
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int blockY = floor(y);
        if (blockY < minY || blockY > maxY) {
            return Double.NaN;
        }
        if (world.getBlockAt(blockX, blockY, blockZ).isPassable()) {
            return y;
        }
        for (int offset = 1; offset <= lift; offset++) {
            int candidate = blockY + offset;
            if (candidate > maxY) {
                break;
            }
            if (world.getBlockAt(blockX, candidate, blockZ).isPassable()) {
                return candidate + 0.15;
            }
        }
        return Double.NaN;
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
