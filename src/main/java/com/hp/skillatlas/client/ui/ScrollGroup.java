package com.hp.skillatlas.client.ui;

public class ScrollGroup extends Group {
    public static final float BAR = 6;
    private float scroll;
    private float applied;
    private float content;
    private boolean dragging;
    private double dragStart;
    private float dragScroll;

    public void rebuilt() {
        applied = 0;
        content = bottom() - y + 4;
        clamp();
        sync();
    }

    public float scrollOffset() {
        return scroll;
    }

    public void scrollTo(float value) {
        scroll = value;
        clamp();
        sync();
    }

    private float max() {
        return Math.max(0, content - h);
    }

    private void clamp() {
        scroll = Math.max(0, Math.min(max(), scroll));
    }

    private void sync() {
        float target = -scroll;
        if (target != applied) {
            float d = target - applied;
            for (Widget c : children) {
                c.move(0, d);
            }
            applied = target;
        }
    }

    public float innerWidth() {
        return w - BAR - 3;
    }

    @Override
    public void render(float pt) {
        sync();
        Gfx.pushClip(x, y, w, h);
        for (Widget c : children) {
            if (c.visible && c.y + c.h >= y && c.y <= y + h) {
                c.render(pt);
            }
        }
        Gfx.popClip();
        if (max() > 0) {
            float bx = x + w - BAR;
            Gfx.sprite(Sprites.SCROLL_TRACK, bx, y, BAR, h);
            float th = Math.max(12, h * h / content);
            float ty = y + (h - th) * (scroll / max());
            boolean hot = dragging || (Ui.mouseX >= bx && Ui.mouseX < bx + BAR && Ui.mouseY >= ty && Ui.mouseY < ty + th);
            if (hot) {
                Gfx.sprite(Sprites.SCROLL_THUMB_HOVER, bx, ty, BAR, th);
            } else {
                Gfx.sprite(Sprites.SCROLL_THUMB, bx, ty, BAR, th);
            }
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (max() > 0 && button == 0 && mx >= x + w - BAR) {
            dragging = true;
            dragStart = my;
            dragScroll = scroll;
            Ui.capture = this;
            return true;
        }
        return super.mouseDown(mx, my, button);
    }

    @Override
    public void mouseDrag(double mx, double my, int button) {
        if (dragging) {
            float th = Math.max(12, h * h / content);
            float range = h - th;
            if (range > 0) {
                scroll = dragScroll + (float) ((my - dragStart) / range * max());
                clamp();
                sync();
            }
        }
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        dragging = false;
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        if (super.scroll(mx, my, delta)) {
            return true;
        }
        if (max() <= 0) {
            return false;
        }
        scroll -= (float) delta * 24;
        clamp();
        sync();
        return true;
    }

    @Override
    public Widget hit(double mx, double my) {
        if (!contains(mx, my)) {
            return null;
        }
        return super.hit(mx, my);
    }
}
