package ru.hanaka.magneticpoints.util;

import org.bukkit.permissions.Permissible;

/**
 * Права плагина.
 *
 * <p>Каждое действие открывается своим правом, поэтому можно выдать, например,
 * только телепорт и просмотр списка, но закрыть создание и удаление точек.
 * Право {@link #ADMIN} включает в себя все остальные.
 */
public final class Permissions {

    /** Полный доступ: автоматически даёт любое другое право плагина. */
    public static final String ADMIN = "magnet.admin";
    /** Базовый доступ к команде /magnet. */
    public static final String USE = "magnet.use";
    /** Создание точек. */
    public static final String CREATE = "magnet.create";
    /** Удаление точек. */
    public static final String DELETE = "magnet.delete";
    /** Список точек. */
    public static final String LIST = "magnet.list";
    /** Подробная информация о точке. */
    public static final String INFO = "magnet.info";
    /** Включение и выключение точки. */
    public static final String TOGGLE = "magnet.toggle";
    /** Изменение радиуса. */
    public static final String RADIUS = "magnet.radius";
    /** Телепорт к точке. */
    public static final String TELEPORT = "magnet.teleport";
    /** Открытие GUI-панели. */
    public static final String GUI = "magnet.gui";
    /** Смена видимости частиц командой. */
    public static final String PARTICLES = "magnet.particles";
    /** Видеть частицы, когда particles.visibility = admin. */
    public static final String PARTICLES_SEE = "magnet.particles.see";
    /** Перезагрузка конфига. */
    public static final String RELOAD = "magnet.reload";
    /** Игрок не притягивается точками. */
    public static final String BYPASS = "magnet.bypass";

    private Permissions() {
    }

    /**
     * Проверка права с учётом того, что magnet.admin открывает всё сразу.
     */
    public static boolean has(Permissible who, String permission) {
        if (who == null) {
            return false;
        }
        return who.hasPermission(ADMIN) || who.hasPermission(permission);
    }
}
