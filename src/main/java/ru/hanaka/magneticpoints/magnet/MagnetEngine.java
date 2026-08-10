package ru.hanaka.magneticpoints.magnet;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.config.PluginConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Сердце плагина: каждые N тиков подтягивает металлические предметы и игроков с металлом.
 *
 * <p>v2.1:
 * <ul>
 *   <li>анти-застревание — предметы перепрыгивают низкие блоки, не вдавливаются в стены,
 *       пол и потолок, а уже застрявшие вытаскиваются на свободное место;</li>
 *   <li>оптимизация — фильтр сущностей на стороне сервера, переиспользуемые объекты,
 *       кэш веса инвентаря, «спящие» точки без игроков рядом и никаких обращений
 *       к незагруженным чанкам.</li>
 * </ul>
 */
public final class MagnetEngine implements Runnable {

    /** Фильтр применяется прямо в getNearbyEntities — лишние сущности даже не попадают в список. */
    private static final Predicate<Entity> ITEM_FILTER = new Predicate<Entity>() {
        @Override
        public boolean test(Entity entity) {
            return entity instanceof Item;
        }
    };

    /** На сколько «щупаем» блок по направлению рывка. */
    private static final double PROBE_DISTANCE = 0.6;
    /** Квадрат смещения, ниже которого предмет считается стоящим на месте (0.05 блока). */
    private static final double STUCK_EPSILON = 0.0025;
    private static final long CLEANUP_INTERVAL = 1200L;
    private static final long TRACKER_TTL = 400L;

    private static final class ItemTracker {
        private double x;
        private double y;
        private double z;
        private int stuckChecks;
        private boolean blocked;
        private long lastSeen;
    }

    private static final class WeightCache {
        private int weight;
        private long tick;
    }

    private final HanakaMagneticPoints plugin;
    private final Map<UUID, Long> actionBarCooldowns = new HashMap<>();
    private final Map<UUID, ItemTracker> itemTrackers = new HashMap<>();
    private final Map<UUID, WeightCache> weightCache = new HashMap<>();
    private final Map<UUID, List<Player>> playersByWorld = new HashMap<>();
    private final Location itemLocation = new Location(null, 0.0, 0.0, 0.0);
    private final Location playerLocation = new Location(null, 0.0, 0.0, 0.0);
    private final Vector zero = new Vector();
    private long ticks;
    private long lastDeepCheck;
    private long lastCleanup;
    private boolean deepCheck;

    public MagnetEngine(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PluginConfig config = plugin.config();
        ticks += config.updateInterval();

        boolean items = config.itemsEnabled();
        boolean players = config.playersEnabled();
        if (!items && !players) {
            cleanup(config);
            return;
        }

        deepCheck = ticks - lastDeepCheck >= config.antiStuckInterval();
        if (deepCheck) {
            lastDeepCheck = ticks;
        }
        double activation = config.activationDistance();

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
            double radius = point.getRadius();
            if (radius <= 0.0) {
                continue;
            }
            List<Player> worldPlayers = players(world);
            // Точка «спит», пока рядом нет игроков: без них чанки всё равно не тикают.
            if (activation > 0.0 && !anyPlayerNear(worldPlayers, center, radius + activation)) {
                continue;
            }
            if (items && chunkLoaded(world, center.getX(), center.getZ())) {
                pullItems(config, world, center, radius);
            }
            if (players && !worldPlayers.isEmpty()) {
                pullPlayers(config, point, center, worldPlayers, radius);
            }
        }

        playersByWorld.clear();
        cleanup(config);
    }

    private void pullItems(PluginConfig config, World world, Location center, double radius) {
        Collection<Entity> nearby = world.getNearbyEntities(center, radius, radius, radius, ITEM_FILTER);
        if (nearby.isEmpty()) {
            return;
        }
        double radiusSquared = radius * radius;
        double stopDistance = config.stopDistance();
        double stopSquared = stopDistance * stopDistance;
        double basePower = config.itemsSpeed() * config.strengthMultiplier() * 0.1;
        double verticalBoost = config.itemsVerticalBoost();
        double maxVelocity = config.maxVelocity();
        double climbBoost = config.antiStuckClimbBoost();
        boolean freeze = config.itemsFreeze();
        boolean ignorePickupDelay = config.itemsIgnorePickupDelay();
        boolean antiStuck = config.antiStuckEnabled();
        boolean lineOfSight = antiStuck && config.antiStuckLineOfSight();
        int limit = config.maxItemsPerPoint();
        double centerX = center.getX();
        double centerY = center.getY();
        double centerZ = center.getZ();
        int processed = 0;

        for (Entity entity : nearby) {
            if (limit > 0 && processed >= limit) {
                break;
            }
            Item item = (Item) entity;
            if (!item.isValid()) {
                continue;
            }
            if (!ignorePickupDelay && item.getPickupDelay() > 0) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            int weight = plugin.weights().weight(stack.getType());
            if (weight <= 0) {
                continue;
            }
            item.getLocation(itemLocation);
            double x = itemLocation.getX();
            double y = itemLocation.getY();
            double z = itemLocation.getZ();
            double dx = centerX - x;
            double dy = centerY - y;
            double dz = centerZ - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            processed++;
            if (distanceSquared <= stopSquared) {
                if (freeze) {
                    item.setVelocity(zero);
                }
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            ItemTracker tracker = antiStuck ? tracker(item.getUniqueId()) : null;
            if (lineOfSight && tracker != null) {
                if (deepCheck) {
                    tracker.blocked = obstructed(world, x, y, z, dx, dy, dz, distance);
                }
                if (tracker.blocked) {
                    continue;
                }
            }

            double power = basePower * weight;
            double pullX = dx / distance * power;
            double pullY = dy / distance * power + verticalBoost;
            double pullZ = dz / distance * power;

            if (antiStuck) {
                // 1. Предмет уже внутри блока — поднимаем его на ближайшее свободное место.
                if (deepCheck && unstick(config, world, item, x, y, z)) {
                    continue;
                }
                // 2. Впереди блок: низкий перепрыгиваем, в высокий не вдавливаем.
                double horizontal = Math.sqrt(pullX * pullX + pullZ * pullZ);
                if (horizontal > 1.0E-4) {
                    double probeX = x + pullX / horizontal * PROBE_DISTANCE;
                    double probeZ = z + pullZ / horizontal * PROBE_DISTANCE;
                    if (solid(world, probeX, y + 0.05, probeZ)) {
                        if (!solid(world, probeX, y + 1.05, probeZ) && !solid(world, x, y + 1.05, z)) {
                            pullY += climbBoost;
                        } else {
                            pullX = 0.0;
                            pullZ = 0.0;
                            if (pullY < 0.0) {
                                pullY = 0.0;
                            }
                        }
                    }
                }
                // 3. Не вдавливаем предмет в пол и в потолок.
                if (pullY < 0.0 && solid(world, x, y - 0.1, z)) {
                    pullY = 0.0;
                }
                if (pullY > 0.0 && solid(world, x, y + 0.4, z)) {
                    pullY = 0.0;
                }
                // 4. Предмет стоит на месте, хотя его тянет — подбрасываем через край блока.
                if (deepCheck && tracker != null && stuck(tracker, x, y, z, config)) {
                    pullY += climbBoost;
                }
            }

            Vector velocity = item.getVelocity();
            velocity.setX(velocity.getX() + pullX);
            velocity.setY(velocity.getY() + pullY);
            velocity.setZ(velocity.getZ() + pullZ);
            item.setVelocity(clamp(velocity, maxVelocity));
        }
    }

    private void pullPlayers(PluginConfig config, MagnetPoint point, Location center, List<Player> players, double radius) {
        double radiusSquared = radius * radius;
        double stopDistance = config.stopDistance();
        double basePower = config.playersSpeed() * config.strengthMultiplier() * 0.1;
        double verticalBoost = config.playersVerticalBoost();
        double maxVelocity = config.maxVelocity();
        int minWeight = config.minWeight();
        int maxWeight = config.maxWeight();
        boolean actionBar = config.actionBarEnabled();
        double centerX = center.getX();
        double centerY = center.getY();
        double centerZ = center.getZ();

        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            player.getLocation(playerLocation);
            double dx = centerX - playerLocation.getX();
            double dy = centerY - playerLocation.getY();
            double dz = centerZ - playerLocation.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (isProtected(config, player)) {
                continue;
            }
            int weight = weightOf(config, player);
            if (weight < minWeight) {
                continue;
            }
            int capped = Math.min(weight, maxWeight);
            double distance = Math.sqrt(distanceSquared);
            if (distance > stopDistance) {
                double power = basePower * capped;
                Vector velocity = player.getVelocity();
                velocity.setX(velocity.getX() + dx / distance * power);
                velocity.setY(velocity.getY() + dy / distance * power + verticalBoost);
                velocity.setZ(velocity.getZ() + dz / distance * power);
                player.setVelocity(clamp(velocity, maxVelocity));
            }
            if (actionBar && ready(player, config)) {
                Map<String, String> placeholders = plugin.messages().placeholders(point);
                placeholders.put("strength", String.valueOf(capped));
                placeholders.put("max", String.valueOf(maxWeight));
                placeholders.put("bar", plugin.messages().bar(capped, maxWeight));
                placeholders.put("point", point.getName());
                placeholders.put("distance", String.format(Locale.ROOT, "%.1f", distance));
                String rawMessage = plugin.messages().raw("action-bar");
                if (!rawMessage.isEmpty()) {
                    player.sendActionBar(plugin.messages().text(rawMessage, placeholders));
                }
            }
        }
    }

    /**
     * Вытаскивает предмет, который уже оказался внутри блока.
     *
     * @return true, если предмет был перемещён и притяжение в этом проходе применять не нужно
     */
    private boolean unstick(PluginConfig config, World world, Item item, double x, double y, double z) {
        int blockX = floor(x);
        int blockZ = floor(z);
        if (!chunkLoaded(world, x, z)) {
            return false;
        }
        int blockY = floor(y + 0.05);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        if (blockY < minY || blockY > maxY) {
            return false;
        }
        if (world.getBlockAt(blockX, blockY, blockZ).isPassable()) {
            return false;
        }
        int lift = config.antiStuckUnstickHeight();
        for (int offset = 1; offset <= lift; offset++) {
            int candidate = blockY + offset;
            if (candidate > maxY) {
                break;
            }
            if (world.getBlockAt(blockX, candidate, blockZ).isPassable()) {
                item.teleport(new Location(world, blockX + 0.5, candidate + 0.05, blockZ + 0.5));
                item.setVelocity(zero);
                return true;
            }
        }
        // Свободного места сверху нет — просто выталкиваем предмет вверх.
        item.setVelocity(new Vector(0.0, Math.max(0.1, config.antiStuckClimbBoost()), 0.0));
        return true;
    }

    private boolean stuck(ItemTracker tracker, double x, double y, double z, PluginConfig config) {
        double dx = x - tracker.x;
        double dy = y - tracker.y;
        double dz = z - tracker.z;
        tracker.x = x;
        tracker.y = y;
        tracker.z = z;
        if (dx * dx + dy * dy + dz * dz > STUCK_EPSILON) {
            tracker.stuckChecks = 0;
            return false;
        }
        tracker.stuckChecks++;
        if (tracker.stuckChecks < config.antiStuckChecks()) {
            return false;
        }
        tracker.stuckChecks = 0;
        return true;
    }

    private boolean obstructed(World world, double x, double y, double z, double dx, double dy, double dz, double distance) {
        try {
            Vector direction = new Vector(dx / distance, dy / distance, dz / distance);
            Location start = new Location(world, x, y, z);
            return world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private ItemTracker tracker(UUID uuid) {
        ItemTracker tracker = itemTrackers.get(uuid);
        if (tracker == null) {
            tracker = new ItemTracker();
            itemTrackers.put(uuid, tracker);
        }
        tracker.lastSeen = ticks;
        return tracker;
    }

    private List<Player> players(World world) {
        UUID id = world.getUID();
        List<Player> cached = playersByWorld.get(id);
        if (cached == null) {
            cached = world.getPlayers();
            playersByWorld.put(id, cached);
        }
        return cached;
    }

    private boolean anyPlayerNear(List<Player> players, Location center, double distance) {
        double squared = distance * distance;
        double centerX = center.getX();
        double centerY = center.getY();
        double centerZ = center.getZ();
        for (int index = 0; index < players.size(); index++) {
            players.get(index).getLocation(playerLocation);
            double dx = playerLocation.getX() - centerX;
            double dy = playerLocation.getY() - centerY;
            double dz = playerLocation.getZ() - centerZ;
            if (dx * dx + dy * dy + dz * dz <= squared) {
                return true;
            }
        }
        return false;
    }

    private boolean ready(Player player, PluginConfig config) {
        UUID uuid = player.getUniqueId();
        Long last = actionBarCooldowns.get(uuid);
        if (last != null && ticks - last < config.actionBarInterval()) {
            return false;
        }
        actionBarCooldowns.put(uuid, ticks);
        return true;
    }

    private boolean isProtected(PluginConfig config, Player player) {
        if (config.protectSneaking() && player.isSneaking()) {
            return true;
        }
        if (config.protectElytra() && player.isGliding()) {
            return true;
        }
        if (config.protectFlying() && player.isFlying()) {
            return true;
        }
        GameMode mode = player.getGameMode();
        if (config.protectCreative() && mode == GameMode.CREATIVE) {
            return true;
        }
        if (config.protectSpectator() && mode == GameMode.SPECTATOR) {
            return true;
        }
        String permission = config.bypassPermission();
        return permission != null && !permission.isEmpty() && player.hasPermission(permission);
    }

    /** Перебор инвентаря — самая дорогая часть, поэтому результат кэшируется на несколько тиков. */
    private int weightOf(PluginConfig config, Player player) {
        int cacheTicks = config.weightCacheTicks();
        UUID uuid = player.getUniqueId();
        WeightCache cache = weightCache.get(uuid);
        if (cache != null && cacheTicks > 0 && ticks - cache.tick < cacheTicks) {
            return cache.weight;
        }
        int weight = computeWeight(config, player);
        if (cache == null) {
            cache = new WeightCache();
            weightCache.put(uuid, cache);
        }
        cache.weight = weight;
        cache.tick = ticks;
        return weight;
    }

    private int computeWeight(PluginConfig config, Player player) {
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        if (config.countHands()) {
            total += weightOf(config, inventory.getItemInMainHand());
            total += weightOf(config, inventory.getItemInOffHand());
        }
        if (config.countArmor()) {
            ItemStack[] armor = inventory.getArmorContents();
            for (int index = 0; index < armor.length; index++) {
                total += weightOf(config, armor[index]);
            }
        }
        if (config.countInventory()) {
            ItemStack[] storage = inventory.getStorageContents();
            for (int index = 0; index < storage.length; index++) {
                total += weightOf(config, storage[index]);
            }
        }
        return total;
    }

    private int weightOf(PluginConfig config, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        int weight = plugin.weights().weight(stack.getType());
        if (weight <= 0) {
            return 0;
        }
        return config.countStackAmount() ? weight * Math.max(1, stack.getAmount()) : weight;
    }

    public void forget(UUID uuid) {
        actionBarCooldowns.remove(uuid);
        weightCache.remove(uuid);
    }

    /** Раз в минуту чистим кэши, чтобы они не росли бесконечно. */
    private void cleanup(PluginConfig config) {
        if (ticks - lastCleanup < CLEANUP_INTERVAL) {
            return;
        }
        lastCleanup = ticks;
        Iterator<Map.Entry<UUID, ItemTracker>> trackers = itemTrackers.entrySet().iterator();
        while (trackers.hasNext()) {
            if (ticks - trackers.next().getValue().lastSeen > TRACKER_TTL) {
                trackers.remove();
            }
        }
        Iterator<UUID> weights = weightCache.keySet().iterator();
        while (weights.hasNext()) {
            if (Bukkit.getPlayer(weights.next()) == null) {
                weights.remove();
            }
        }
        Iterator<UUID> bars = actionBarCooldowns.keySet().iterator();
        while (bars.hasNext()) {
            if (Bukkit.getPlayer(bars.next()) == null) {
                bars.remove();
            }
        }
        if (config.debug()) {
            plugin.getLogger().info("[debug] Кэш магнита: предметов " + itemTrackers.size()
                    + ", игроков " + weightCache.size());
        }
    }

    private static boolean solid(World world, double x, double y, double z) {
        if (!chunkLoaded(world, x, z)) {
            return false;
        }
        int blockY = floor(y);
        if (blockY < world.getMinHeight() || blockY > world.getMaxHeight() - 1) {
            return false;
        }
        return !world.getBlockAt(floor(x), blockY, floor(z)).isPassable();
    }

    private static boolean chunkLoaded(World world, double x, double z) {
        return world.isChunkLoaded(floor(x) >> 4, floor(z) >> 4);
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static Vector clamp(Vector vector, double max) {
        double length = vector.length();
        if (!Double.isFinite(length)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        if (max > 0.0 && length > max) {
            vector.multiply(max / length);
        }
        return vector;
    }
}
