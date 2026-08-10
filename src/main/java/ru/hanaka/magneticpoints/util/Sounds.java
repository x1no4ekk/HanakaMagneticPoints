package ru.hanaka.magneticpoints.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Звуки берутся из конфига строкой, поэтому работают и кастомные ключи из ресурспака.
 * Поддерживаются оба формата: BLOCK_BEACON_AMBIENT и block.beacon.ambient
 */
public final class Sounds {

    private Sounds() {
    }

    public static String key(String raw, String fallback) {
        String value = (raw == null || raw.trim().isEmpty()) ? fallback : raw.trim();
        if (value == null || value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.indexOf('.') >= 0 || lower.indexOf(':') >= 0) {
            return lower;
        }
        return lower.replace('_', '.');
    }

    public static void play(Player player, String soundKey, float volume, float pitch) {
        if (player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        play(player, player.getLocation(), soundKey, volume, pitch);
    }

    public static void play(Player player, Location location, String soundKey, float volume, float pitch) {
        if (player == null || location == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        try {
            player.playSound(location, soundKey, volume, pitch);
        } catch (Throwable ignored) {
            // Неверный ключ звука не должен ломать логику плагина
        }
    }
}
