package ru.hanaka.magneticpoints.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Маленький помощник для плейсхолдеров вида {name}.
 */
public final class Placeholders {

    private Placeholders() {
    }

    public static Map<String, String> empty() {
        return new LinkedHashMap<>();
    }

    public static Map<String, String> of(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }

    public static Map<String, String> merge(Map<String, String> base, String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        if (base != null) {
            map.putAll(base);
        }
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }
}
