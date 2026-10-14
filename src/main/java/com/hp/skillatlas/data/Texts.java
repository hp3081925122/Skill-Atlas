package com.hp.skillatlas.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.UnaryOperator;

public final class Texts {
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";
    public static UnaryOperator<String> lookup = UnaryOperator.identity();

    private Texts() {
    }

    public static String colorize(String raw) {
        if (raw.indexOf('&') < 0) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '&' && i + 1 < raw.length() && CODES.indexOf(raw.charAt(i + 1)) >= 0) {
                sb.append('§');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String strip(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                i++;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String display(String raw) {
        if (raw == null) {
            return "";
        }
        return lookup.apply(raw);
    }

    public static MutableComponent component(String raw) {
        String text = display(raw);
        if (!text.equals(raw)) {
            return Component.literal(colorize(text));
        }
        return Component.translatableWithFallback(raw, colorize(raw));
    }

    public static MutableComponent component(String raw, String fallback) {
        if (raw == null || raw.isEmpty()) {
            return Component.literal(fallback);
        }
        return component(raw);
    }
}
