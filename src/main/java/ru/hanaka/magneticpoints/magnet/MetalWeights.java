package ru.hanaka.magneticpoints.magnet;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Весовая физика: чем больше вес, тем сильнее притяжение.
 * Все списки грузятся из config.yml (секция materials) и поддерживают шаблоны NETHERITE_*
 */
public final class MetalWeights {

    private static final class Rule {
        private final Pattern pattern;
        private final int weight;

        private Rule(Pattern pattern, int weight) {
            this.pattern = pattern;
            this.weight = weight;
        }
    }

    private final Map<String, Integer> exact = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();
    private final Set<String> ignored = new HashSet<>();
    private final Map<Material, Integer> cache = new HashMap<>();
    private final int defaultWeight;

    private MetalWeights(int defaultWeight) {
        this.defaultWeight = defaultWeight;
    }

    public static MetalWeights load(ConfigurationSection section, Logger logger) {
        int defaultWeight = section == null ? 0 : section.getInt("default-weight", 0);
        MetalWeights weights = new MetalWeights(defaultWeight);
        if (section == null) {
            return weights;
        }

        for (String raw : section.getStringList("ignored")) {
            String normalized = normalize(raw);
            if (!normalized.isEmpty()) {
                weights.ignored.add(normalized);
            }
        }

        ConfigurationSection groups = section.getConfigurationSection("groups");
        if (groups == null) {
            return weights;
        }
        for (String key : groups.getKeys(false)) {
            ConfigurationSection group = groups.getConfigurationSection(key);
            if (group == null) {
                continue;
            }
            int weight = group.getInt("weight", defaultWeight);
            for (String raw : group.getStringList("items")) {
                String normalized = normalize(raw);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (normalized.indexOf('*') >= 0) {
                    weights.rules.add(new Rule(compile(normalized), weight));
                    continue;
                }
                Material material = Material.matchMaterial(normalized);
                if (material == null) {
                    if (logger != null) {
                        logger.warning("[materials." + key + "] Неизвестный материал: " + raw);
                    }
                    continue;
                }
                weights.exact.merge(material.name(), weight, Math::max);
            }
        }
        return weights;
    }

    public int weight(Material material) {
        if (material == null) {
            return 0;
        }
        Integer cached = cache.get(material);
        if (cached != null) {
            return cached;
        }
        int computed = compute(material);
        cache.put(material, computed);
        return computed;
    }

    public int size() {
        return exact.size() + rules.size();
    }

    private int compute(Material material) {
        String name = material.name();
        if (ignored.contains(name)) {
            return 0;
        }
        Integer exactWeight = exact.get(name);
        if (exactWeight != null) {
            return exactWeight;
        }
        int best = defaultWeight;
        for (Rule rule : rules) {
            if (rule.pattern.matcher(name).matches() && rule.weight > best) {
                best = rule.weight;
            }
        }
        return best;
    }

    private static Pattern compile(String wildcard) {
        String[] parts = wildcard.split("\\*", -1);
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                regex.append(".*");
            }
            if (!parts[index].isEmpty()) {
                regex.append(Pattern.quote(parts[index]));
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
    }
}
