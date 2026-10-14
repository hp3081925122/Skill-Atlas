package com.hp.skillatlas.client.ui;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ListView<T> extends Widget {
    public interface RowRenderer<T> {
        void render(T item, float x, float y, float w, float h, boolean hovered);
    }

    private List<T> items = List.of();
    public float rowHeight = 18;
    public float cellWidth;
    public RowRenderer<T> renderer;
    public BiConsumer<T, Integer> onClick;
    public Consumer<T> onDouble;
    public Function<T, List<Component>> tooltips;
    public Component empty;
    public boolean background = true;
    private float scroll;
    private boolean dragging;
    private double dragStart;
    private float dragScroll;
    private T lastClicked;
    private long lastClickTime;

    public ListView(RowRenderer<T> renderer) {
        this.renderer = renderer;
    }

    public List<T> items() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
        clamp();
    }

    public void resetScroll() {
        scroll = 0;
    }

    private int columns() {
        if (cellWidth <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.floor((w - ScrollGroup.BAR - 5) / cellWidth));
    }

    private float content() {
        int cols = columns();
        return (items.size() + cols - 1) / cols * rowHeight;
    }

    private float max() {
        return Math.max(0, content() - (h - 4));
    }

    private void clamp() {
        scroll = Math.max(0, Math.min(max(), scroll));
    }

    private float listWidth() {
        if (cellWidth > 0 || max() > 0) {
            return w - ScrollGroup.BAR - 5;
        }
        return w - 4;
    }

    private int rowAt(double mx, double my) {
        if (!contains(mx, my) || mx > x + 2 + listWidth()) {
            return -1;
        }
        int row = (int) Math.floor((my - y - 2 + scroll) / rowHeight);
        int cols = columns();
        int col = Math.min(cols - 1, (int) Math.floor((mx - x - 2) / (listWidth() / cols)));
        int i = row * cols + col;
        if (row < 0 || col < 0 || i >= items.size()) {
            return -1;
        }
        return i;
    }

    @Override
    public void render(float pt) {
        if (background) {
            Gfx.sprite(Sprites.INSET, x, y, w, h);
        }
        clamp();
        if (items.isEmpty()) {
            if (empty != null) {
                Gfx.textCentered(empty, x + w / 2.0F, y + h / 2.0F - 4, Theme.MUTED);
            }
            return;
        }
        int hoverRow = -1;
        if (hovered()) {
            hoverRow = rowAt(Ui.mouseX, Ui.mouseY);
        }
        Gfx.pushClip(x + 2, y + 2, w - 4, h - 4);
        int cols = columns();
        float lw = listWidth();
        float cw = lw / cols;
        int first = Math.max(0, (int) Math.floor(scroll / rowHeight)) * cols;
        int last = Math.min(items.size() - 1, ((int) Math.ceil((scroll + h) / rowHeight) + 1) * cols - 1);
        for (int i = first; i <= last; i++) {
            float rx = x + 2 + i % cols * cw;
            float ry = y + 2 + i / cols * rowHeight - scroll;
            if (i == hoverRow) {
                Gfx.fill(rx, ry, cw, rowHeight, Theme.ROW_HOVER);
            }
            renderer.render(items.get(i), rx, ry, cw, rowHeight, i == hoverRow);
        }
        Gfx.popClip();
        if (max() > 0) {
            float bx = x + w - ScrollGroup.BAR - 2;
            float th = Math.max(12, (h - 4) * (h - 4) / content());
            float ty = y + 2 + (h - 4 - th) * (scroll / max());
            Gfx.sprite(Sprites.SCROLL_TRACK, bx, y + 2, ScrollGroup.BAR, h - 4);
            if (dragging) {
                Gfx.sprite(Sprites.SCROLL_THUMB_HOVER, bx, ty, ScrollGroup.BAR, th);
            } else {
                Gfx.sprite(Sprites.SCROLL_THUMB, bx, ty, ScrollGroup.BAR, th);
            }
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (max() > 0 && button == 0 && mx >= x + w - ScrollGroup.BAR - 3) {
            dragging = true;
            dragStart = my;
            dragScroll = scroll;
            Ui.capture = this;
            return true;
        }
        int i = rowAt(mx, my);
        if (i < 0) {
            return true;
        }
        T item = items.get(i);
        long now = Util.getMillis();
        if (button == 0 && onDouble != null && item.equals(lastClicked) && now - lastClickTime < 300) {
            lastClicked = null;
            onDouble.accept(item);
            return true;
        }
        lastClicked = item;
        lastClickTime = now;
        if (onClick != null) {
            onClick.accept(item, button);
        }
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my, int button) {
        if (dragging) {
            float th = Math.max(12, (h - 4) * (h - 4) / content());
            float range = h - 4 - th;
            if (range > 0) {
                scroll = dragScroll + (float) ((my - dragStart) / range * max());
                clamp();
            }
        }
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        dragging = false;
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        if (max() <= 0) {
            return false;
        }
        scroll -= (float) delta * rowHeight * 2;
        clamp();
        return true;
    }

    @Override
    public List<Component> tooltip(double mx, double my) {
        if (tooltips == null) {
            return super.tooltip(mx, my);
        }
        int i = rowAt(mx, my);
        if (i < 0) {
            return null;
        }
        return tooltips.apply(items.get(i));
    }
}
