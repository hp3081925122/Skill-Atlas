package com.hp.skillatlas.client;

import com.google.gson.*;
import com.hp.skillatlas.model.Definition;
import java.util.*;

public final class UiSkin {
    public int schemaVersion = 1;
    public Map<String, Sprite> textures = new LinkedHashMap<>();
    public Map<String, String> colors = new LinkedHashMap<>();
    public Map<String, Integer> metrics = new LinkedHashMap<>();
    public static final class Sprite {
        public String texture;
        public int width, height, u, v, regionWidth, regionHeight, border;
        public float alpha = 1;
        public String tint = "#FFFFFFFF";
    }
    public static UiSkin merge(UiSkin base, JsonObject overlay) {
        JsonObject merged = Definition.JSON.toJsonTree(base).getAsJsonObject(); mergeObject(merged, overlay);
        UiSkin skin = Definition.JSON.fromJson(merged, UiSkin.class); skin.validate(base); return skin;
    }
    private static void mergeObject(JsonObject target, JsonObject overlay) {
        overlay.entrySet().forEach(entry -> {
            JsonElement value = entry.getValue(), old = target.get(entry.getKey());
            if (value.isJsonObject() && old != null && old.isJsonObject()) mergeObject(old.getAsJsonObject(), value.getAsJsonObject());
            else target.add(entry.getKey(), value.deepCopy());
        });
    }
    public void validate(UiSkin required) {
        if (schemaVersion != 1 || textures == null || colors == null || metrics == null || textures.size() > 128 || colors.size() > 64 || metrics.size() > 64)
            throw new IllegalArgumentException("Invalid UI skin schema or size");
        if (!textures.keySet().equals(required.textures.keySet()) || !colors.keySet().equals(required.colors.keySet()) || !metrics.keySet().equals(required.metrics.keySet()))
            throw new IllegalArgumentException("UI skin is missing required slots");
        textures.forEach((key, s) -> {
            if (s == null || s.texture == null || !s.texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || s.texture.contains("..")
                || s.width < 1 || s.width > 4096 || s.height < 1 || s.height > 4096 || s.u < 0 || s.v < 0 || s.regionWidth < 1 || s.regionHeight < 1
                || s.u + s.regionWidth > s.width || s.v + s.regionHeight > s.height || s.border < 0 || 2 * s.border >= Math.min(s.regionWidth, s.regionHeight)
                || !Float.isFinite(s.alpha) || s.alpha < 0 || s.alpha > 1 || s.tint == null || !s.tint.matches("#[0-9a-fA-F]{8}")) throw new IllegalArgumentException("Invalid UI sprite: " + key);
        });
        colors.forEach((key, value) -> { if (value == null || !value.matches("#[0-9a-fA-F]{8}")) throw new IllegalArgumentException("Invalid UI color: " + key); });
        metrics.forEach((key, value) -> { if (value == null || value < 1 || value > 4096) throw new IllegalArgumentException("Invalid UI metric: " + key); });
        checkMetric("gridSpacing", 8, 256); checkMetric("gridSize", 1, 8); checkMetric("lineWidth", 1, 12);
        checkMetric("tooltipPadding", 2, 16); checkMetric("tooltipMaxWidth", 64, 1024);
    }
    private void checkMetric(String key, int min, int max) { Integer value = metrics.get(key); if (value != null && (value < min || value > max)) throw new IllegalArgumentException("Invalid UI metric: " + key); }
    public int color(String key) { return (int)Long.parseLong(colors.get(key).substring(1), 16); }
    public static int[] destinationCuts(int size, int border) {
        int edge = Math.min(border, Math.max(0, size / 2)); return new int[]{0, edge, Math.max(edge, size - edge), Math.max(0, size)};
    }
}
