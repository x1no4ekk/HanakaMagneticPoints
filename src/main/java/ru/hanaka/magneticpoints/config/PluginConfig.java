package ru.hanaka.magneticpoints.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import ru.hanaka.magneticpoints.HanakaMagneticPoints;
import ru.hanaka.magneticpoints.magnet.MetalWeights;
import ru.hanaka.magneticpoints.util.Sounds;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Всё из config.yml в одном месте. Любой параметр плагина настраивается игроком.
 */
public final class PluginConfig {

    public enum Visibility {
        ALL,
        ADMIN,
        NONE;

        public static Visibility parse(String value, Visibility fallback) {
            if (value == null) {
                return fallback;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (Visibility visibility : values()) {
                if (visibility.name().equals(normalized)) {
                    return visibility;
                }
            }
            return fallback;
        }
    }

    private final HanakaMagneticPoints plugin;

    private double defaultRadius;
    private double minRadius;
    private double maxRadius;
    private double strengthMultiplier;
    private int updateInterval;
    private double maxVelocity;
    private double stopDistance;
    private int autoSaveInterval;
    private Set<String> disabledWorlds = new HashSet<>();
    private int maxPoints;
    private String namePattern;
    private Pattern compiledNamePattern;
    private String messagesFile;
    private boolean debug;

    private String gradientStart;
    private String gradientEnd;
    private String errorStart;
    private String errorEnd;

    private boolean itemsEnabled;
    private double itemsSpeed;
    private double itemsVerticalBoost;
    private boolean itemsFreeze;
    private boolean itemsIgnorePickupDelay;

    private boolean playersEnabled;
    private double playersSpeed;
    private double playersVerticalBoost;
    private boolean countHands;
    private boolean countArmor;
    private boolean countInventory;
    private boolean countStackAmount;
    private int minWeight;
    private int maxWeight;
    private boolean actionBarEnabled;
    private int actionBarInterval;
    private boolean barEnabled;
    private int barLength;
    private String barFilled;
    private String barEmpty;
    private boolean protectSneaking;
    private boolean protectElytra;
    private boolean protectFlying;
    private boolean protectCreative;
    private boolean protectSpectator;
    private String bypassPermission;

    private boolean particlesEnabled;
    private Particle particle;
    private int particleCount;
    private Visibility particleVisibility;
    private int particleInterval;
    private boolean particleRotate;
    private int particleRings;
    private double ringHeight;
    private double verticalOffset;
    private double particleSpeed;
    private double particleSpread;
    private double viewDistance;

    private boolean soundsEnabled;
    private String ambientSound;
    private float soundVolume;
    private float soundPitch;
    private int soundInterval;
    private float uiVolume;
    private float uiPitch;
    private String soundClick;
    private String soundSuccess;
    private String soundError;
    private String soundDelete;
    private String soundTeleport;

    private int guiRows;
    private boolean fillerEnabled;
    private Material fillerMaterial;
    private Material activeMaterial;
    private Material disabledMaterial;
    private Material previousMaterial;
    private Material nextMaterial;
    private Material closeMaterial;
    private Material infoMaterial;
    private Material emptyMaterial;
    private boolean guiAutoRefresh;

    private double teleportOffsetY;
    private boolean teleportSafe;

    private MetalWeights weights;

    public PluginConfig(HanakaMagneticPoints plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        defaultRadius = config.getDouble("settings.default-radius", 12.0);
        minRadius = config.getDouble("settings.min-radius", 3.0);
        maxRadius = config.getDouble("settings.max-radius", 100.0);
        strengthMultiplier = config.getDouble("settings.strength-multiplier", 1.0);
        updateInterval = Math.max(1, config.getInt("settings.update-interval", 2));
        maxVelocity = config.getDouble("settings.max-velocity", 1.2);
        stopDistance = config.getDouble("settings.stop-distance", 0.8);
        autoSaveInterval = config.getInt("settings.auto-save-interval", 300);
        maxPoints = config.getInt("settings.max-points", 0);
        messagesFile = config.getString("settings.messages-file", "messages.yml");
        debug = config.getBoolean("settings.debug", false);

        Set<String> worlds = new HashSet<>();
        List<String> rawWorlds = config.getStringList("settings.disabled-worlds");
        for (String world : rawWorlds) {
            if (world != null && !world.trim().isEmpty()) {
                worlds.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }
        disabledWorlds = worlds;

        namePattern = config.getString("settings.name-pattern", "[A-Za-z\\u0410-\\u042f\\u0430-\\u044f\\u0401\\u04510-9_\\\\-]{1,32}");
        compiledNamePattern = compilePattern(namePattern);

        gradientStart = config.getString("gradient.start", "#00E676");
        gradientEnd = config.getString("gradient.end", "#C6FF00");
        errorStart = config.getString("gradient.error-start", "#FF5252");
        errorEnd = config.getString("gradient.error-end", "#FFD180");

        itemsEnabled = config.getBoolean("items.enabled", true);
        itemsSpeed = config.getDouble("items.speed", 0.09);
        itemsVerticalBoost = config.getDouble("items.vertical-boost", 0.02);
        itemsFreeze = config.getBoolean("items.freeze-at-center", true);
        itemsIgnorePickupDelay = config.getBoolean("items.ignore-pickup-delay", false);

        playersEnabled = config.getBoolean("players.enabled", true);
        playersSpeed = config.getDouble("players.speed", 0.05);
        playersVerticalBoost = config.getDouble("players.vertical-boost", 0.012);
        countHands = config.getBoolean("players.count-hands", true);
        countArmor = config.getBoolean("players.count-armor", true);
        countInventory = config.getBoolean("players.count-inventory", false);
        countStackAmount = config.getBoolean("players.count-stack-amount", false);
        minWeight = config.getInt("players.min-weight", 2);
        maxWeight = Math.max(1, config.getInt("players.max-weight", 60));
        actionBarEnabled = config.getBoolean("players.action-bar.enabled", true);
        actionBarInterval = Math.max(1, config.getInt("players.action-bar.interval", 10));
        barEnabled = config.getBoolean("players.action-bar.bar.enabled", true);
        barLength = Math.max(0, config.getInt("players.action-bar.bar.length", 10));
        barFilled = config.getString("players.action-bar.bar.filled", "\\u258c");
        barEmpty = config.getString("players.action-bar.bar.empty", "\\u258c");
        protectSneaking = config.getBoolean("players.protection.sneaking", true);
        protectElytra = config.getBoolean("players.protection.elytra", true);
        protectFlying = config.getBoolean("players.protection.flying", true);
        protectCreative = config.getBoolean("players.protection.creative", true);
        protectSpectator = config.getBoolean("players.protection.spectator", true);
        bypassPermission = config.getString("players.protection.bypass-permission", "magnet.bypass");

        particlesEnabled = config.getBoolean("particles.enabled", true);
        particle = parseParticle(config.getString("particles.type", "WAX_ON"));
        particleCount = Math.max(1, config.getInt("particles.count", 24));
        particleVisibility = Visibility.parse(config.getString("particles.visibility", "admin"), Visibility.ADMIN);
        particleInterval = Math.max(1, config.getInt("particles.interval", 10));
        particleRotate = config.getBoolean("particles.rotate", true);
        particleRings = Math.max(1, config.getInt("particles.rings", 1));
        ringHeight = config.getDouble("particles.ring-height", 0.7);
        verticalOffset = config.getDouble("particles.vertical-offset", 0.2);
        particleSpeed = config.getDouble("particles.particle-speed", 0.0);
        particleSpread = config.getDouble("particles.spread", 0.0);
        viewDistance = config.getDouble("particles.view-distance", 48.0);

        soundsEnabled = config.getBoolean("sounds.enabled", true);
        ambientSound = Sounds.key(config.getString("sounds.ambient", "BLOCK_BEACON_AMBIENT"), "BLOCK_BEACON_AMBIENT");
        soundVolume = (float) config.getDouble("sounds.volume", 0.3);
        soundPitch = (float) config.getDouble("sounds.pitch", 1.5);
        soundInterval = Math.max(1, config.getInt("sounds.interval", 80));
        uiVolume = (float) config.getDouble("sounds.ui.volume", 0.7);
        uiPitch = (float) config.getDouble("sounds.ui.pitch", 1.4);
        soundClick = Sounds.key(config.getString("sounds.ui.click", "UI_BUTTON_CLICK"), "UI_BUTTON_CLICK");
        soundSuccess = Sounds.key(config.getString("sounds.ui.success", "ENTITY_PLAYER_LEVELUP"), "ENTITY_PLAYER_LEVELUP");
        soundError = Sounds.key(config.getString("sounds.ui.error", "ENTITY_VILLAGER_NO"), "ENTITY_VILLAGER_NO");
        soundDelete = Sounds.key(config.getString("sounds.ui.delete", "BLOCK_ANVIL_LAND"), "BLOCK_ANVIL_LAND");
        soundTeleport = Sounds.key(config.getString("sounds.ui.teleport", "ENTITY_ENDERMAN_TELEPORT"), "ENTITY_ENDERMAN_TELEPORT");

        guiRows = Math.max(2, Math.min(6, config.getInt("gui.rows", 6)));
        fillerEnabled = config.getBoolean("gui.filler.enabled", true);
        fillerMaterial = material(config.getString("gui.filler.material"), "BLACK_STAINED_GLASS_PANE");
        activeMaterial = material(config.getString("gui.point.active-material"), "LODESTONE");
        disabledMaterial = material(config.getString("gui.point.disabled-material"), "GRAY_DYE");
        previousMaterial = material(config.getString("gui.navigation.previous-material"), "ARROW");
        nextMaterial = material(config.getString("gui.navigation.next-material"), "ARROW");
        closeMaterial = material(config.getString("gui.navigation.close-material"), "BARRIER");
        infoMaterial = material(config.getString("gui.navigation.info-material"), "COMPASS");
        emptyMaterial = material(config.getString("gui.navigation.empty-material"), "BARRIER");
        guiAutoRefresh = config.getBoolean("gui.auto-refresh", true);

        teleportOffsetY = config.getDouble("teleport.offset-y", 1.0);
        teleportSafe = config.getBoolean("teleport.safe", true);

        weights = MetalWeights.load(config.getConfigurationSection("materials"), plugin.getLogger());

        if (debug) {
            plugin.getLogger().info("[debug] Конфиг загружен, правил материалов: " + weights.size());
        }
    }

    private Pattern compilePattern(String raw) {
        try {
            return Pattern.compile(raw);
        } catch (PatternSyntaxException exception) {
            plugin.getLogger().warning("[settings.name-pattern] Неверное выражение: " + exception.getMessage());
            return Pattern.compile(".{1,32}");
        }
    }

    private Material material(String raw, String fallback) {
        Material material = null;
        if (raw != null && !raw.trim().isEmpty()) {
            material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("[gui] Неизвестный материал: " + raw);
            }
        }
        if (material == null) {
            material = Material.matchMaterial(fallback);
        }
        return material == null ? Material.STONE : material;
    }

    private Particle parseParticle(String raw) {
        String normalized = raw == null
                ? ""
                : raw.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('.', '_');
        Particle particle = particleByName(normalized);
        if (particle != null) {
            return particle;
        }
        if (!normalized.isEmpty()) {
            plugin.getLogger().warning("[particles.type] Неизвестный тип частиц: " + raw + ", использую WAX_ON");
        }
        Particle fallback = particleByName("WAX_ON");
        if (fallback != null) {
            return fallback;
        }
        Particle[] all = Particle.values();
        return all.length > 0 ? all[0] : null;
    }

    private static Particle particleByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Particle particle : Particle.values()) {
            if (particle.name().equals(name)) {
                return particle;
            }
        }
        return null;
    }

    public boolean nameAllowed(String name) {
        return name != null && !name.isEmpty() && compiledNamePattern.matcher(name).matches();
    }

    public boolean worldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public double defaultRadius() {
        return defaultRadius;
    }

    public double minRadius() {
        return minRadius;
    }

    public double maxRadius() {
        return maxRadius;
    }

    public double strengthMultiplier() {
        return strengthMultiplier;
    }

    public int updateInterval() {
        return updateInterval;
    }

    public double maxVelocity() {
        return maxVelocity;
    }

    public double stopDistance() {
        return stopDistance;
    }

    public int autoSaveInterval() {
        return autoSaveInterval;
    }

    public int maxPoints() {
        return maxPoints;
    }

    public String namePattern() {
        return namePattern;
    }

    public String messagesFile() {
        return messagesFile == null || messagesFile.trim().isEmpty() ? "messages.yml" : messagesFile.trim();
    }

    public boolean debug() {
        return debug;
    }

    public String gradientStart() {
        return gradientStart;
    }

    public String gradientEnd() {
        return gradientEnd;
    }

    public String errorStart() {
        return errorStart;
    }

    public String errorEnd() {
        return errorEnd;
    }

    public boolean itemsEnabled() {
        return itemsEnabled;
    }

    public double itemsSpeed() {
        return itemsSpeed;
    }

    public double itemsVerticalBoost() {
        return itemsVerticalBoost;
    }

    public boolean itemsFreeze() {
        return itemsFreeze;
    }

    public boolean itemsIgnorePickupDelay() {
        return itemsIgnorePickupDelay;
    }

    public boolean playersEnabled() {
        return playersEnabled;
    }

    public double playersSpeed() {
        return playersSpeed;
    }

    public double playersVerticalBoost() {
        return playersVerticalBoost;
    }

    public boolean countHands() {
        return countHands;
    }

    public boolean countArmor() {
        return countArmor;
    }

    public boolean countInventory() {
        return countInventory;
    }

    public boolean countStackAmount() {
        return countStackAmount;
    }

    public int minWeight() {
        return minWeight;
    }

    public int maxWeight() {
        return maxWeight;
    }

    public boolean actionBarEnabled() {
        return actionBarEnabled;
    }

    public int actionBarInterval() {
        return actionBarInterval;
    }

    public boolean barEnabled() {
        return barEnabled;
    }

    public int barLength() {
        return barLength;
    }

    public String barFilled() {
        return barFilled == null ? "\\u258c" : barFilled;
    }

    public String barEmpty() {
        return barEmpty == null ? "\\u258c" : barEmpty;
    }

    public boolean protectSneaking() {
        return protectSneaking;
    }

    public boolean protectElytra() {
        return protectElytra;
    }

    public boolean protectFlying() {
        return protectFlying;
    }

    public boolean protectCreative() {
        return protectCreative;
    }

    public boolean protectSpectator() {
        return protectSpectator;
    }

    public String bypassPermission() {
        return bypassPermission;
    }

    public boolean particlesEnabled() {
        return particlesEnabled;
    }

    public Particle particle() {
        return particle;
    }

    public int particleCount() {
        return particleCount;
    }

    public Visibility particleVisibility() {
        return particleVisibility;
    }

    public int particleInterval() {
        return particleInterval;
    }

    public boolean particleRotate() {
        return particleRotate;
    }

    public int particleRings() {
        return particleRings;
    }

    public double ringHeight() {
        return ringHeight;
    }

    public double verticalOffset() {
        return verticalOffset;
    }

    public double particleSpeed() {
        return particleSpeed;
    }

    public double particleSpread() {
        return particleSpread;
    }

    public double viewDistance() {
        return viewDistance;
    }

    public boolean soundsEnabled() {
        return soundsEnabled;
    }

    public String ambientSound() {
        return ambientSound;
    }

    public float soundVolume() {
        return soundVolume;
    }

    public float soundPitch() {
        return soundPitch;
    }

    public int soundInterval() {
        return soundInterval;
    }

    public float uiVolume() {
        return uiVolume;
    }

    public float uiPitch() {
        return uiPitch;
    }

    public String soundClick() {
        return soundClick;
    }

    public String soundSuccess() {
        return soundSuccess;
    }

    public String soundError() {
        return soundError;
    }

    public String soundDelete() {
        return soundDelete;
    }

    public String soundTeleport() {
        return soundTeleport;
    }

    public int guiRows() {
        return guiRows;
    }

    public boolean fillerEnabled() {
        return fillerEnabled;
    }

    public Material fillerMaterial() {
        return fillerMaterial;
    }

    public Material activeMaterial() {
        return activeMaterial;
    }

    public Material disabledMaterial() {
        return disabledMaterial;
    }

    public Material previousMaterial() {
        return previousMaterial;
    }

    public Material nextMaterial() {
        return nextMaterial;
    }

    public Material closeMaterial() {
        return closeMaterial;
    }

    public Material infoMaterial() {
        return infoMaterial;
    }

    public Material emptyMaterial() {
        return emptyMaterial;
    }

    public boolean guiAutoRefresh() {
        return guiAutoRefresh;
    }

    public double teleportOffsetY() {
        return teleportOffsetY;
    }

    public boolean teleportSafe() {
        return teleportSafe;
    }

    public MetalWeights weights() {
        return weights;
    }
}
