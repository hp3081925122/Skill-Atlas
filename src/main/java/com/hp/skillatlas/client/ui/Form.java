package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

public final class Form {
    public final ScrollGroup group;
    public final float labelW;
    private float cy;

    public Form(ScrollGroup group, float labelW) {
        this.group = group;
        this.labelW = labelW;
        this.cy = group.y + 2;
    }

    public float left() {
        return group.x + 2;
    }

    public float width() {
        return group.innerWidth() - 2;
    }

    public float fieldX() {
        return left() + labelW;
    }

    public float fieldW() {
        return width() - labelW;
    }

    public float y() {
        return cy;
    }

    public void gap(float g) {
        cy += g;
    }

    public Heading heading(Component text) {
        Heading hd = new Heading(text);
        hd.bounds(left(), cy, width(), 12);
        group.add(hd);
        cy += 15;
        return hd;
    }

    public <T extends Widget> T row(Component label, T field) {
        float hh = Math.max(16, field.h);
        if (label != null) {
            Label l = new Label(label, Theme.MUTED);
            l.bounds(left(), cy, labelW - 4, 16);
            group.add(l);
        }
        field.bounds(fieldX(), cy, fieldW(), hh);
        group.add(field);
        cy += hh + 4;
        return field;
    }

    public void cells(Component label, float[] weights, Widget... widgets) {
        float hh = 16;
        for (Widget wd : widgets) {
            hh = Math.max(hh, wd.h);
        }
        float startX = left();
        float avail = width();
        if (label != null) {
            Label l = new Label(label, Theme.MUTED);
            l.bounds(left(), cy, labelW - 4, 16);
            group.add(l);
            startX = fieldX();
            avail = fieldW();
        }
        float total = 0;
        for (float wt : weights) {
            total += wt;
        }
        float gap = 4;
        float usable = avail - gap * (widgets.length - 1);
        float cx = startX;
        for (int i = 0; i < widgets.length; i++) {
            float cw = usable * weights[i] / total;
            Widget wd = widgets[i];
            float wh = 16;
            if (wd.h > 16) {
                wh = wd.h;
            }
            wd.bounds(cx, cy, cw, wh);
            group.add(wd);
            cx += cw + gap;
        }
        cy += hh + 4;
    }

    public <T extends Widget> T full(T widget, float height) {
        widget.bounds(left(), cy, width(), height);
        group.add(widget);
        cy += height + 4;
        return widget;
    }

    public <T extends Widget> T at(T widget, float x, float y, float w, float h) {
        widget.bounds(x, y, w, h);
        group.add(widget);
        return widget;
    }

    public void advance(float h) {
        cy += h;
    }

    public void finish() {
        group.rebuilt();
    }
}
