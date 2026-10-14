package com.hp.skillatlas.client.ui;

import java.util.ArrayList;
import java.util.List;

public class Group extends Widget {
    public final List<Widget> children = new ArrayList<>();

    public <T extends Widget> T add(T w) {
        children.add(w);
        return w;
    }

    public void remove(Widget w) {
        children.remove(w);
    }

    public void clear() {
        children.clear();
    }

    @Override
    public void move(float dx, float dy) {
        super.move(dx, dy);
        for (Widget c : children) {
            c.move(dx, dy);
        }
    }

    @Override
    public void render(float pt) {
        for (Widget c : children) {
            if (c.visible) {
                c.render(pt);
            }
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible && c.enabled && c.contains(mx, my) && c.mouseDown(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible && c.contains(mx, my) && c.scroll(mx, my, delta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        for (Widget c : children) {
            c.tick();
        }
    }

    @Override
    public Widget hit(double mx, double my) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible) {
                Widget h = c.hit(mx, my);
                if (h != null) {
                    return h;
                }
            }
        }
        return super.hit(mx, my);
    }

    public float bottom() {
        float b = y;
        for (Widget c : children) {
            if (c.visible) {
                b = Math.max(b, c.y + c.h);
            }
        }
        return b;
    }
}
