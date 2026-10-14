package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public abstract class Dialog extends Layer {
    protected static final float HEADER = 22;
    protected static final float FOOTER = 24;

    protected Component title;
    protected float prefW;
    protected float prefH;
    protected Component status;
    protected int statusColor = Theme.MUTED;

    protected Dialog(Component title, float prefW, float prefH) {
        this.title = title;
        this.prefW = prefW;
        this.prefH = prefH;
    }

    @Override
    public void layout(float screenW, float screenH) {
        w = Math.min(prefW, screenW - 12);
        h = Math.min(prefH, screenH - 12);
        x = (float) Math.floor((screenW - w) / 2.0F);
        y = (float) Math.floor((screenH - h) / 2.0F);
        clear();
        Button close = add(Button.icon(Sprites.CLOSE, Component.translatable("skill_atlas.ui.close"), this::close));
        close.bounds(x + w - 20, y + 5, 14, 14);
        build();
    }

    protected abstract void build();

    protected float contentX() {
        return x + 8;
    }

    protected float contentY() {
        return y + HEADER + 2;
    }

    protected float contentW() {
        return w - 16;
    }

    protected float contentH() {
        return h - HEADER - FOOTER - 4;
    }

    protected float footerY() {
        return y + h - FOOTER + 2;
    }

    protected void footer(Button... buttons) {
        float bx = x + w - 8;
        for (int i = buttons.length - 1; i >= 0; i--) {
            Button b = buttons[i];
            float bw = Math.max(50, Gfx.width(b.label) + 16);
            bx -= bw;
            b.bounds(bx, footerY(), bw, 16);
            add(b);
            bx -= 4;
        }
    }

    public void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void render(float pt) {
        Gfx.sprite(Sprites.DIALOG, x, y, w, h);
        Gfx.text(Gfx.ellipsize(title, w - 40), x + 9, y + 8, Theme.GOLD);
        Gfx.fill(x + 6, y + HEADER - 1, w - 12, 1, Theme.SEPARATOR);
        super.render(pt);
        if (status != null) {
            float maxW = w - 16;
            for (Widget c : children) {
                if (c instanceof Button && c.y >= footerY() - 1) {
                    maxW = Math.min(maxW, c.x - x - 14);
                }
            }
            Gfx.text(Gfx.ellipsize(status, maxW).getVisualOrderText(), x + 9, footerY() + 4, statusColor, true);
        }
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (Ui.focus != null) {
                Ui.focus(null);
                return true;
            }
            return escape();
        }
        return false;
    }

    @Override
    public java.util.List<Component> tooltip(double mx, double my) {
        if (status != null && my >= footerY() && mx < x + w / 2.0F && Gfx.width(status) > w / 2.0F) {
            return java.util.List.of(status);
        }
        return null;
    }
}
