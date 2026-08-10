package ru.hanaka.magneticpoints.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * Работа с текстом: MiniMessage, легаси-коды и зелёно-лаймовый градиент.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /**
     * Превращает строку из конфига в компонент.
     * &lt;g&gt;...&lt;/g&gt; — основной градиент, &lt;e&gt;...&lt;/e&gt; — градиент ошибок.
     */
    public static Component parse(String input,
                                  String gradientStart,
                                  String gradientEnd,
                                  String errorStart,
                                  String errorEnd) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String prepared = input
                .replace("<g>", "<gradient:" + gradientStart + ":" + gradientEnd + ">")
                .replace("</g>", "</gradient>")
                .replace("<e>", "<gradient:" + errorStart + ":" + errorEnd + ">")
                .replace("</e>", "</gradient>");
        try {
            if (prepared.indexOf('<') >= 0) {
                return MINI.deserialize(prepared).decoration(TextDecoration.ITALIC, false);
            }
            return LEGACY.deserialize(prepared).decoration(TextDecoration.ITALIC, false);
        } catch (Throwable throwable) {
            return Component.text(prepared.replaceAll("<[^>]*>", "")).decoration(TextDecoration.ITALIC, false);
        }
    }

    /**
     * Ручной градиент по символам — для консоли и служебных строк.
     */
    public static Component gradient(String text, TextColor from, TextColor to) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        int length = text.length();
        TextComponent.Builder builder = Component.text();
        for (int index = 0; index < length; index++) {
            float ratio = length == 1 ? 0.0F : (float) index / (float) (length - 1);
            TextColor color = TextColor.color(
                    lerp(from.red(), to.red(), ratio),
                    lerp(from.green(), to.green(), ratio),
                    lerp(from.blue(), to.blue(), ratio));
            builder.append(Component.text(String.valueOf(text.charAt(index)), color));
        }
        return builder.build().decoration(TextDecoration.ITALIC, false);
    }

    public static Component gradient(String text, String from, String to) {
        return gradient(text, color(from, TextColor.color(0x00E676)), color(to, TextColor.color(0xC6FF00)));
    }

    public static TextColor color(String hex, TextColor fallback) {
        if (hex == null || hex.trim().isEmpty()) {
            return fallback;
        }
        String value = hex.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        TextColor parsed = TextColor.fromHexString(value);
        return parsed == null ? fallback : parsed;
    }

    public static String fill(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("{" + entry.getKey() + "}", value);
            }
        }
        return result;
    }

    private static int lerp(int from, int to, float ratio) {
        return Math.round(from + (to - from) * ratio);
    }
}
