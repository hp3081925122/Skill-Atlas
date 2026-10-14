package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public abstract class Widget {
    public float x;
    public float y;
    public float w;
    public float h;
    public boolean visible = true;
    public boolean enabled = true;
    public Supplier<List<Component>> tooltip;

    public Widget bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public Widget tip(Component... lines) {
        List<Component> list = List.of(lines);
        this.tooltip = () -> list;
        return this;
    }

    public Widget tip(Supplier<List<Component>> lines) {
        this.tooltip = lines;
        return this;
    }

    public void move(float dx, float dy) {
        x += dx;
        y += dy;
    }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public boolean hovered() {
        return contains(Ui.mouseX, Ui.mouseY) && Ui.hoverAllowed(this);
    }

    public abstract void render(float pt);

    public boolean mouseDown(double mx, double my, int button) {
        return false;
    }

    public void mouseDrag(double mx, double my, int button) {
    }

    public void mouseUp(double mx, double my, int button) {
    }

    public boolean scroll(double mx, double my, double delta) {
        return false;
    }

    public boolean key(int key, int scan, int mods) {
        return false;
    }

    public boolean chr(char c, int mods) {
        return false;
    }

    public void tick() {
    }

    public boolean focusable() {
        return false;
    }

    public void focusLost() {
    }

    public Widget hit(double mx, double my) {
        if (contains(mx, my)) {
            return this;
        }
        return null;
    }

    public List<Component> tooltip(double mx, double my) {
        if (tooltip == null) {
            return null;
        }
        return tooltip.get();
    }
}
