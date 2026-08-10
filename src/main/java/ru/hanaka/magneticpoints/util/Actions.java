package ru.hanaka.magneticpoints.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.PluginConfig;
import ru.hanaka.magneticpoints.magnet.MagnetPoint;

/**
 * Общие действия для команд и GUI: телепорт, вкл/выкл, удаление.
 */
public final class Actions {

    private Actions() {
    }

    public static boolean teleport(HanakaMagneticPoints plugin, Player player, MagnetPoint point) {
        PluginConfig config = plugin.config();
        Location location = point.getLocation();
        if (location == null) {
            return false;
        }
        Location target = location.clone().add(0.0, config.teleportOffsetY(), 0.0);
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        if (config.teleportSafe()) {
            target = safeSpot(target);
        }
        player.teleport(target);
        Sounds.play(player, config.soundTeleport(), config.uiVolume(), config.uiPitch());
        return true;
    }

    public static void toggle(HanakaMagneticPoints plugin, MagnetPoint point) {
        point.setEnabled(!point.isEnabled());
        plugin.storage().save();
    }

    public static void delete(HanakaMagneticPoints plugin, MagnetPoint point) {
        plugin.storage().remove(point.getName());
        plugin.storage().save();
    }

    private static Location safeSpot(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location;
        }
        Location candidate = location.clone();
        for (int attempt = 0; attempt < 16; attempt++) {
            Block feet = candidate.getBlock();
            Block head = candidate.clone().add(0.0, 1.0, 0.0).getBlock();
            if (feet.isPassable() && head.isPassable()) {
                return candidate;
            }
            candidate = candidate.add(0.0, 1.0, 0.0);
        }
        return location;
    }
}
