package com.hp.skillatlas.client.ui;

import com.hp.skillatlas.SkillAtlas;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Gfx {
    public static final ResourceLocation ATLAS = SkillAtlas.id("textures/gui/atlas.png");
    private static final int NONE = 0;
    private static final int COLOR = 1;
    private static final int TEX = 2;
    private static final float TEXT_Z = 300.0F;

    private static final BufferBuilder BUF = new BufferBuilder(1 << 16);
    private static final Deque<int[]> CLIPS = new ArrayDeque<>();
    private static GuiGraphics g;
    private static double scale = 1.0D;
    private static int mode = NONE;
    private static ResourceLocation texture = ATLAS;

    private Gfx() {
    }

    public static void begin(GuiGraphics graphics, double uiScale) {
        g = graphics;
        scale = uiScale;
        mode = NONE;
        CLIPS.clear();
    }

    public static void end() {
        flush();
        if (!CLIPS.isEmpty()) {
            CLIPS.clear();
            RenderSystem.disableScissor();
        }
        g = null;
    }

    public static GuiGraphics graphics() {
        return g;
    }

    public static PoseStack pose() {
        return g.pose();
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static double scale() {
        return scale;
    }

    private static void ensure(int m, ResourceLocation tex) {
        if (mode == m && (m != TEX || texture.equals(tex))) {
            return;
        }
        flush();
        mode = m;
        if (m == COLOR) {
            BUF.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        } else {
            texture = tex;
            BUF.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        }
    }

    public static void flush() {
        if (mode == NONE) {
            return;
        }
        int m = mode;
        mode = NONE;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        if (m == COLOR) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
            RenderSystem.setShaderTexture(0, texture);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferUploader.drawWithShader(BUF.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    public static void clearDepth() {
        flush();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public static void fill(float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
            return;
        }
        ensure(COLOR, null);
        Matrix4f m = g.pose().last().pose();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int gr = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        BUF.vertex(m, x, y, 0).color(r, gr, b, a).endVertex();
        BUF.vertex(m, x, y + h, 0).color(r, gr, b, a).endVertex();
        BUF.vertex(m, x + w, y + h, 0).color(r, gr, b, a).endVertex();
        BUF.vertex(m, x + w, y, 0).color(r, gr, b, a).endVertex();
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
        Matrix4f m = g.pose().last().pose();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int gr = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        BUF.vertex(m, x1, y1, 0).color(r, gr, b, a).endVertex();
        BUF.vertex(m, x2, y2, 0).color(r, gr, b, a).endVertex();
        BUF.vertex(m, x3, y3, 0).color(r, gr, b, a).endVertex();
        BUF.vertex(m, x4, y4, 0).color(r, gr, b, a).endVertex();
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
        Matrix4f m = g.pose().last().pose();
        float u0 = u / 256.0F;
        float v0 = v / 256.0F;
        float u1 = (u + uw) / 256.0F;
        float v1 = (v + vh) / 256.0F;
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int gr = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        BUF.vertex(m, x, y, 0).color(r, gr, b, a).uv(u0, v0).endVertex();
        BUF.vertex(m, x, y + h, 0).color(r, gr, b, a).uv(u0, v1).endVertex();
        BUF.vertex(m, x + w, y + h, 0).color(r, gr, b, a).uv(u1, v1).endVertex();
        BUF.vertex(m, x + w, y, 0).color(r, gr, b, a).uv(u1, v0).endVertex();
    }

    public static void texture(ResourceLocation tex, float x, float y, float w, float h, int argb) {
        ensure(TEX, tex);
        Matrix4f m = g.pose().last().pose();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int gr = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        BUF.vertex(m, x, y, 0).color(r, gr, b, a).uv(0, 0).endVertex();
        BUF.vertex(m, x, y + h, 0).color(r, gr, b, a).uv(0, 1).endVertex();
        BUF.vertex(m, x + w, y + h, 0).color(r, gr, b, a).uv(1, 1).endVertex();
        BUF.vertex(m, x + w, y, 0).color(r, gr, b, a).uv(1, 0).endVertex();
    }

    public static void item(ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) {
            return;
        }
        flush();
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(x, y, 0);
        p.scale(size / 16.0F, size / 16.0F, 1.0F);
        g.renderItem(stack, 0, 0);
        p.popPose();
    }

    public static int text(FormattedCharSequence s, float x, float y, int color, boolean shadow) {
        flush();
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(0, 0, TEXT_Z);
        int w = g.drawString(font(), s, x, y, color, shadow);
        p.popPose();
        return w;
    }

    public static int text(String s, float x, float y, int color) {
        flush();
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(0, 0, TEXT_Z);
        int w = g.drawString(font(), s, x, y, color, true);
        p.popPose();
        return w;
    }

    public static int text(Component c, float x, float y, int color) {
        return text(c.getVisualOrderText(), x, y, color, true);
    }

    public static void textScaled(Component c, float x, float y, float s, int color) {
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(x, y, 0);
        p.scale(s, s, 1.0F);
        text(c.getVisualOrderText(), 0, 0, color, true);
        p.popPose();
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
        int x0 = (int) Math.floor(x * scale);
        int y0 = (int) Math.floor(y * scale);
        int x1 = (int) Math.ceil((x + w) * scale);
        int y1 = (int) Math.ceil((y + h) * scale);
        int[] top = CLIPS.peek();
        if (top != null) {
            x0 = Math.max(x0, top[0]);
            y0 = Math.max(y0, top[1]);
            x1 = Math.min(x1, top[2]);
            y1 = Math.min(y1, top[3]);
        }
        CLIPS.push(new int[]{x0, y0, Math.max(x0, x1), Math.max(y0, y1)});
        applyClip();
    }

    public static void popClip() {
        flush();
        CLIPS.pop();
        applyClip();
    }

    private static void applyClip() {
        int[] c = CLIPS.peek();
        if (c == null) {
            RenderSystem.disableScissor();
            return;
        }
        int fbh = Minecraft.getInstance().getWindow().getHeight();
        RenderSystem.enableScissor(c[0], fbh - c[3], c[2] - c[0], c[3] - c[1]);
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
