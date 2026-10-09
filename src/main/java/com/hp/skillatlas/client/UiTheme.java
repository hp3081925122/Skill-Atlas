package com.hp.skillatlas.client;

import com.google.gson.JsonObject;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.core.StrictJson;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class UiTheme {
    private static final ResourceLocation MANIFEST = ResourceLocation.parse("skill_atlas:ui/theme.json");
    private static final UiSkin BUNDLED = bundled();
    private static UiSkin current = BUNDLED;
    private static Map<String, ResourceLocation> locations = locations(BUNDLED);
    private UiTheme() {}
    private static UiSkin bundled() {
        try (InputStream in = UiTheme.class.getResourceAsStream("/assets/skill_atlas/ui/default_theme.json")) {
            if (in == null) throw new IOException("Missing bundled UI skin");
            UiSkin skin = StrictJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), UiSkin.class); skin.validate(skin); return skin;
        } catch (Exception ex) { throw new IllegalStateException("Cannot load bundled Skill Atlas UI skin", ex); }
    }
    private static Map<String, ResourceLocation> locations(UiSkin skin) {
        Map<String, ResourceLocation> map = new HashMap<>(); skin.textures.forEach((key, sprite) -> map.put(key, ResourceLocation.parse(sprite.texture))); return map;
    }
    public static void reload(ResourceManager manager) {
        UiSkin skin = UiSkin.merge(BUNDLED, new JsonObject());
        try {
            var resource = manager.getResource(MANIFEST);
            if (resource.isPresent()) try (var reader = resource.get().openAsReader()) {
                StringBuilder text = new StringBuilder(); char[] buffer = new char[4096]; int size;
                while ((size = reader.read(buffer)) >= 0) { text.append(buffer, 0, size); if (text.length() > 131072) throw new IOException("UI skin exceeds size limit"); }
                skin = UiSkin.merge(BUNDLED, StrictJson.parse(text.toString(), JsonObject.class));
            }
            for (var entry : new ArrayList<>(skin.textures.entrySet())) {
                var sprite = entry.getValue(); var texture = manager.getResource(ResourceLocation.parse(sprite.texture));
                boolean valid = false;
                if (texture.isPresent()) try (DataInputStream in = new DataInputStream(texture.get().open())) {
                    valid = in.readLong() == 0x89504e470d0a1a0aL && in.readInt() == 13 && in.readInt() == 0x49484452
                        && in.readInt() == sprite.width && in.readInt() == sprite.height;
                } catch (IOException ignored) {}
                if (!valid) {
                    SkillAtlas.LOGGER.warn("Invalid or missing UI texture for {}: {}; using bundled slot", entry.getKey(), sprite.texture);
                    skin.textures.put(entry.getKey(), BUNDLED.textures.get(entry.getKey()));
                }
            }
        } catch (Exception ex) { SkillAtlas.LOGGER.warn("Cannot load UI skin; keeping bundled theme: {}", ex.getMessage()); skin = BUNDLED; }
        current = skin; locations = locations(skin);
    }
    public static int color(String key) { return current.color(key); }
    public static int metric(String key) { return current.metrics.get(key); }
    public static void draw(GuiGraphics g, String key, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        UiSkin.Sprite s = current.textures.get(key); ResourceLocation texture = locations.get(key);
        if (s == null || texture == null) return;
        int tint = (int)Long.parseLong(s.tint.substring(1), 16);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, ((tint >>> 24) & 255) / 255f * s.alpha);
        if (s.border == 0 || (width == s.regionWidth && height == s.regionHeight)) {
            blit(g, texture, s, x, y, width, height, s.u, s.v, s.regionWidth, s.regionHeight);
        } else {
            int[] dx = UiSkin.destinationCuts(width, s.border), dy = UiSkin.destinationCuts(height, s.border);
            int[] sx = {0, s.border, s.regionWidth - s.border, s.regionWidth}, sy = {0, s.border, s.regionHeight - s.border, s.regionHeight};
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                if (dx[col + 1] == dx[col] || dy[row + 1] == dy[row]) continue;
                blit(g, texture, s, x + dx[col], y + dy[row], dx[col + 1] - dx[col], dy[row + 1] - dy[row], s.u + sx[col], s.v + sy[row], sx[col + 1] - sx[col], sy[row + 1] - sy[row]);
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private static void blit(GuiGraphics g, ResourceLocation texture, UiSkin.Sprite s, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        g.blit(texture, x, y, w, h, (float)u, (float)v, sw, sh, s.width, s.height);
    }
    public static void frame(GuiGraphics g, int width, int height) {
        draw(g, "background", 0, 0, width, height); draw(g, "panel", 8, 28, width - 16, height - 62);
        draw(g, "toolbar", 0, 0, width, 27); draw(g, "footer", 0, height - 32, width, 32);
    }
    public static void tooltip(GuiGraphics g, List<Component> components, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        var font = Minecraft.getInstance().font; int padding = metric("tooltipPadding"), maxWidth = Math.max(30, Math.min(metric("tooltipMaxWidth"), screenWidth - padding * 2 - 8));
        List<FormattedCharSequence> lines = new ArrayList<>(); for (var component : components) lines.addAll(font.split(component, maxWidth));
        int limit = Math.max(1, (screenHeight - padding * 2 - 8) / 11);
        if (lines.size() > limit) { lines = new ArrayList<>(lines.subList(0, limit)); lines.set(limit - 1, Component.literal("…").getVisualOrderText()); }
        int width = lines.stream().mapToInt(font::width).max().orElse(1) + padding * 2, height = lines.size() * 11 + padding * 2;
        int x = mouseX + 12; if (x + width > screenWidth - 4) x = mouseX - width - 10; x = Math.max(4, x);
        int y = Math.max(4, Math.min(mouseY - 12, screenHeight - height - 4));
        g.pose().pushPose(); g.pose().translate(0, 0, 400); draw(g, "tooltip", x, y, width, height);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + padding, y + padding + i * 11, color("text"));
        g.pose().popPose();
    }
}
