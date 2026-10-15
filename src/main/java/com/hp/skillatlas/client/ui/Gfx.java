package com.hp.skillatlas.client.ui;

import com.hp.skillatlas.SkillAtlas;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public final class Gfx {
    public static final Identifier ATLAS = SkillAtlas.id("textures/gui/atlas.png");
    public static final RenderPipeline COLOR_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(SkillAtlas.id("pipeline/gui")).withCull(false).build();
    public static final RenderPipeline TEXTURE_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(SkillAtlas.id("pipeline/gui_textured")).withCull(false).build();
    private static final int NONE = 0;
    private static final int COLOR = 1;
    private static final int TEX = 2;
    private static final List<Vertex> VERTICES = new ArrayList<>();
    private static int clipDepth;
    private static GuiGraphicsExtractor g;
    private static double scale = 1.0D;
    private static int mode = NONE;
    private static Identifier texture = ATLAS;

    private Gfx() {
    }

    public static void begin(GuiGraphicsExtractor graphics, double uiScale) {
        g = graphics;
        scale = uiScale;
        mode = NONE;
        clipDepth = 0;
    }

    public static void end() {
        flush();
        while (clipDepth > 0) {
            g.disableScissor();
            clipDepth--;
        }
        g = null;
    }

    public static GuiGraphicsExtractor graphics() {
        return g;
    }

    public static Matrix3x2fStack pose() {
        return g.pose();
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static double scale() {
        return scale;
    }

    private static void ensure(int m, Identifier tex) {
        if (mode == m && (m != TEX || texture.equals(tex))) {
            return;
        }
        flush();
        mode = m;
        if (m == TEX) {
            texture = tex;
        }
    }

    public static void flush() {
        if (mode == NONE || VERTICES.isEmpty()) {
            mode = NONE;
            return;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vertex v : VERTICES) {
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
        }
        ScreenRectangle clip = g.peekScissorStack();
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                Math.max(1, (int) Math.ceil(maxX) - (int) Math.floor(minX)), Math.max(1, (int) Math.ceil(maxY) - (int) Math.floor(minY)));
        if (clip != null) {
            bounds = bounds.intersection(clip);
        }
        TextureSetup setup = TextureSetup.noTexture();
        if (mode == TEX) {
            var image = Minecraft.getInstance().getTextureManager().getTexture(texture);
            setup = TextureSetup.singleTexture(image.getTextureView(), image.getSampler());
        }
        g.submitGuiElementRenderState(new Geometry(List.copyOf(VERTICES), mode == TEX ? TEXTURE_PIPELINE : COLOR_PIPELINE, setup, clip, bounds));
        VERTICES.clear();
        mode = NONE;
    }

    private record Vertex(float x, float y, float u, float v, int color) {
    }

    private record Geometry(List<Vertex> vertices, RenderPipeline pipeline, TextureSetup textureSetup,
                            ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (Vertex v : vertices) {
                var vertex = consumer.addVertex(v.x, v.y, 0).setColor(v.color);
                if (pipeline == TEXTURE_PIPELINE) {
                    vertex.setUv(v.u, v.v);
                }
            }
        }
    }

    private static void vertex(float x, float y, float u, float v, int color) {
        Vector2f p = g.pose().transformPosition(x, y, new Vector2f());
        VERTICES.add(new Vertex(p.x, p.y, u, v, color));
    }

    public static void clearDepth() {
        flush();
        g.nextStratum();
    }

    public static void fill(float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
            return;
        }
        quad(x, y, x, y + h, x + w, y + h, x + w, y, argb);
    }

    public static void outline(float x, float y, float w, float h, float t, int argb) {
        fill(x, y, w, t, argb);
        fill(x, y + h - t, w, t, argb);
        fill(x, y + t, t, h - 2 * t, argb);
        fill(x + w - t, y + t, t, h - 2 * t, argb);
    }

    public static void line(float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F || (argb >>> 24) == 0) {
            return;
        }
        float nx = -dy / len * width / 2.0F;
        float ny = dx / len * width / 2.0F;
        quad(x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, argb);
    }

    public static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        quad(x1, y1, x2, y2, x3, y3, x3, y3, argb);
    }

    private static void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int argb) {
        ensure(COLOR, null);
        vertex(x1, y1, 0, 0, argb);
        vertex(x2, y2, 0, 0, argb);
        vertex(x3, y3, 0, 0, argb);
        vertex(x4, y4, 0, 0, argb);
    }

    public static void sprite(Sprites.Sprite s, float x, float y, float w, float h) {
        sprite(s, x, y, w, h, 0xFFFFFFFF);
    }

    public static void sprite(Sprites.Sprite s, float x, float y) {
        sprite(s, x, y, s.w(), s.h(), 0xFFFFFFFF);
    }

    public static void sprite(Sprites.Sprite s, float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        ensure(TEX, ATLAS);
        int bd = s.border();
        if (bd == 0 || (w == s.w() && h == s.h())) {
            uv(x, y, w, h, s.u(), s.v(), s.w(), s.h(), argb);
            return;
        }
        float bw = Math.min(bd, w / 2.0F);
        float bh = Math.min(bd, h / 2.0F);
        float[] xs = {x, x + bw, x + w - bw, x + w};
        float[] ys = {y, y + bh, y + h - bh, y + h};
        int[] us = {s.u(), s.u() + bd, s.u() + s.w() - bd, s.u() + s.w()};
        int[] vs = {s.v(), s.v() + bd, s.v() + s.h() - bd, s.v() + s.h()};
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                float qw = xs[i + 1] - xs[i];
                float qh = ys[j + 1] - ys[j];
                if (qw > 0 && qh > 0) {
                    uv(xs[i], ys[j], qw, qh, us[i], vs[j], us[i + 1] - us[i], vs[j + 1] - vs[j], argb);
                }
            }
        }
    }

    public static void icon(int index, float x, float y, int argb) {
        sprite(Sprites.icon(index), x, y, 12, 12, argb);
    }

    public static void icon(int index, float x, float y, float size, int argb) {
        sprite(Sprites.icon(index), x, y, size, size, argb);
    }

    private static void uv(float x, float y, float w, float h, float u, float v, float uw, float vh, int argb) {
        texture(texture, x, y, w, h, u / 256.0F, v / 256.0F, (u + uw) / 256.0F, (v + vh) / 256.0F, argb);
    }

    public static void texture(Identifier tex, float x, float y, float w, float h, int argb) {
        texture(tex, x, y, w, h, 0, 0, 1, 1, argb);
    }

    public static void texture(Identifier tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
        ensure(TEX, tex);
        vertex(x, y, u0, v0, argb);
        vertex(x, y + h, u0, v1, argb);
        vertex(x + w, y + h, u1, v1, argb);
        vertex(x + w, y, u1, v0, argb);
    }

    public static void item(ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) {
            return;
        }
        flush();
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(size / 16.0F, size / 16.0F);
        g.item(stack, 0, 0);
        p.popMatrix();
    }

    public static int text(FormattedCharSequence s, float x, float y, int color, boolean shadow) {
        flush();
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        g.text(font(), s, 0, 0, color, shadow);
        p.popMatrix();
        return (int) (x + font().getSplitter().stringWidth(s)) + (shadow ? 1 : 0);
    }

    public static int text(String s, float x, float y, int color) {
        return text(Component.literal(s).getVisualOrderText(), x, y, color, true);
    }

    public static int text(Component c, float x, float y, int color) {
        return text(c.getVisualOrderText(), x, y, color, true);
    }

    public static void textScaled(Component c, float x, float y, float s, int color) {
        flush();
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(s, s);
        text(c.getVisualOrderText(), 0, 0, color, true);
        p.popMatrix();
    }

    public static void textCentered(Component c, float cx, float y, int color) {
        text(c, cx - width(c) / 2.0F, y, color);
    }

    public static int width(Component c) {
        return font().width(c);
    }

    public static int width(String s) {
        return font().width(s);
    }

    public static Component ellipsize(Component c, float maxWidth) {
        if (width(c) <= maxWidth) {
            return c;
        }
        String s = font().plainSubstrByWidth(c.getString(), Math.max(0, (int) maxWidth - width("...")));
        return Component.literal(s + "...").withStyle(c.getStyle());
    }

    public static List<FormattedCharSequence> wrap(Component c, float width) {
        return font().split(c, Math.max(10, (int) width));
    }

    public static void pushClip(float x, float y, float w, float h) {
        flush();
        g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
        clipDepth++;
    }

    public static void popClip() {
        flush();
        g.disableScissor();
        clipDepth--;
    }

    public static void tooltip(List<Component> lines, float mx, float my, float screenW, float screenH) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        float maxW = Math.min(240, screenW * 0.45F);
        List<FormattedCharSequence> rows = new ArrayList<>();
        for (Component c : lines) {
            if (c.getString().isEmpty()) {
                rows.add(FormattedCharSequence.EMPTY);
            } else {
                rows.addAll(wrap(c, maxW));
            }
        }
        int w = 0;
        for (FormattedCharSequence r : rows) {
            w = Math.max(w, font().width(r));
        }
        float bw = w + 10;
        float bh = rows.size() * 10 + 7;
        float x = mx + 10;
        float y = my - 6;
        if (x + bw > screenW - 2) {
            x = mx - 10 - bw;
        }
        if (x < 2) {
            x = 2;
        }
        if (y + bh > screenH - 2) {
            y = screenH - 2 - bh;
        }
        if (y < 2) {
            y = 2;
        }
        clearDepth();
        sprite(Sprites.TOOLTIP, x, y, bw, bh);
        float ty = y + 4;
        for (int i = 0; i < rows.size(); i++) {
            int color = Theme.TEXT;
            if (i == 0) {
                color = 0xFFFFFFFF;
            }
            text(rows.get(i), x + 5, ty, color, true);
            ty += 10;
        }
    }
}
