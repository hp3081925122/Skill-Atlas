package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LayerStack {
    public final List<Layer> layers = new ArrayList<>();
    public float width;
    public float height;

    public void push(Layer l) {
        l.stack = this;
        layers.add(l);
        Ui.focus(null);
        Ui.capture = null;
        l.layout(width, height);
    }

    public void pop(Layer l) {
        if (layers.remove(l)) {
            Ui.focus(null);
            Ui.capture = null;
            l.onClosed();
        }
    }

    public Layer top() {
        if (layers.isEmpty()) {
            return null;
        }
        return layers.get(layers.size() - 1);
    }

    public void resize(float w, float h) {
        width = w;
        height = h;
        Ui.focus(null);
        for (Layer l : new ArrayList<>(layers)) {
            l.layout(w, h);
        }
    }

    public void render(float pt) {
        Layer top = top();
        if (top == null) {
            return;
        }
        Widget owner = null;
        if (Ui.capture == null) {
            owner = top.hit(Ui.mouseX, Ui.mouseY);
        } else {
            owner = Ui.capture;
        }
        if (owner == null) {
            Ui.hoverOwner = top;
        } else {
            Ui.hoverOwner = owner;
        }
        List<Layer> snapshot = new ArrayList<>(layers);
        for (int i = 0; i < snapshot.size(); i++) {
            Layer l = snapshot.get(i);
            Gfx.clearDepth();
            if (i > 0 && l.dim) {
                Gfx.fill(0, 0, width, height, Theme.SHADE);
            }
            l.render(pt);
            l.renderOverlay(pt);
        }
        if (Ui.capture == null && owner != null) {
            List<Component> tip = owner.tooltip(Ui.mouseX, Ui.mouseY);
            if (tip != null && !tip.isEmpty()) {
                Gfx.tooltip(tip, (float) Ui.mouseX, (float) Ui.mouseY, width, height);
            }
        }
    }

    public boolean mouseDown(double mx, double my, int button) {
        Layer top = top();
        if (top == null) {
            return false;
        }
        if (top.outsideCloses && !top.contains(mx, my)) {
            pop(top);
            return true;
        }
        Widget hit = top.hit(mx, my);
        if (hit == null || !hit.focusable()) {
            Ui.focus(null);
        }
        Ui.capture = null;
        return top.mouseDown(mx, my, button);
    }

    public void mouseDrag(double mx, double my, int button) {
        if (Ui.capture != null) {
            Ui.capture.mouseDrag(mx, my, button);
        }
    }

    public void mouseUp(double mx, double my, int button) {
        Widget c = Ui.capture;
        Ui.capture = null;
        if (c != null) {
            c.mouseUp(mx, my, button);
        }
    }

    public boolean scroll(double mx, double my, double delta) {
        Layer top = top();
        if (top == null) {
            return false;
        }
        if (top.outsideCloses && !top.contains(mx, my)) {
            return true;
        }
        return top.scroll(mx, my, delta);
    }

    public boolean key(int key, int scan, int mods) {
        if (Ui.focus != null && Ui.focus.key(key, scan, mods)) {
            return true;
        }
        Layer top = top();
        return top != null && top.key(key, scan, mods);
    }

    public boolean chr(char c, int mods) {
        return Ui.focus != null && Ui.focus.chr(c, mods);
    }

    public void tick() {
        for (Layer l : new ArrayList<>(layers)) {
            l.tick();
        }
    }
}
