package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ContextMenu extends Layer {
    private record Entry(Component label, int icon, Runnable action, boolean enabled, boolean danger) {
    }

    private static final float ROW = 14;
    private final List<Entry> entries = new ArrayList<>();
    private final float ax;
    private final float ay;

    public ContextMenu(float ax, float ay) {
        this.ax = ax;
        this.ay = ay;
        this.dim = false;
        this.outsideCloses = true;
    }

    public ContextMenu add(Component label, int icon, Runnable action) {
        entries.add(new Entry(label, icon, action, true, false));
        return this;
    }

    public ContextMenu add(Component label, int icon, boolean enabled, Runnable action) {
        entries.add(new Entry(label, icon, action, enabled, false));
        return this;
    }

    public ContextMenu danger(Component label, int icon, Runnable action) {
        entries.add(new Entry(label, icon, action, true, true));
        return this;
    }

    public ContextMenu separator() {
        if (!entries.isEmpty() && entries.get(entries.size() - 1).label != null) {
            entries.add(new Entry(null, -1, null, false, false));
        }
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private float rowHeight(Entry e) {
        if (e.label == null) {
            return 5;
        }
        return ROW;
    }

    @Override
    public void layout(float screenW, float screenH) {
        if (!entries.isEmpty() && entries.get(entries.size() - 1).label == null) {
            entries.remove(entries.size() - 1);
        }
        float mw = 60;
        float mh = 6;
        for (Entry e : entries) {
            if (e.label != null) {
                mw = Math.max(mw, Gfx.width(e.label) + 30);
            }
            mh += rowHeight(e);
        }
        w = mw;
        h = mh;
        x = ax;
        y = ay;
        if (x + w > screenW - 2) {
            x = Math.max(2, ax - w);
        }
        if (y + h > screenH - 2) {
            y = Math.max(2, screenH - 2 - h);
        }
    }

    private int entryAt(double mx, double my) {
        if (!contains(mx, my)) {
            return -1;
        }
        float ry = y + 3;
        for (int i = 0; i < entries.size(); i++) {
            float rh = rowHeight(entries.get(i));
            if (my >= ry && my < ry + rh) {
                return i;
            }
            ry += rh;
        }
        return -1;
    }

    @Override
    public void render(float pt) {
        Gfx.sprite(Sprites.TOOLTIP, x, y, w, h);
        int hover = entryAt(Ui.mouseX, Ui.mouseY);
        float ry = y + 3;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float rh = rowHeight(e);
            if (e.label == null) {
                Gfx.fill(x + 4, ry + 2, w - 8, 1, Theme.SEPARATOR);
            } else {
                if (i == hover && e.enabled) {
                    Gfx.fill(x + 2, ry, w - 4, rh, Theme.ROW_HOVER);
                }
                int color = Theme.TEXT;
                if (!e.enabled) {
                    color = Theme.DIM;
                } else if (e.danger) {
                    color = Theme.RED;
                }
                if (e.icon >= 0) {
                    int ic = 0xFFFFFFFF;
                    if (!e.enabled) {
                        ic = 0x60FFFFFF;
                    }
                    Gfx.icon(e.icon, x + 5, ry + 1, ic);
                }
                Gfx.text(e.label.getVisualOrderText(), x + 21, ry + 3, color, true);
            }
            ry += rh;
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        int i = entryAt(mx, my);
        if (i < 0) {
            return true;
        }
        Entry e = entries.get(i);
        if (e.label == null || !e.enabled) {
            return true;
        }
        close();
        Sounds.click();
        e.action.run();
        return true;
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public Widget hit(double mx, double my) {
        if (contains(mx, my)) {
            return this;
        }
        return null;
    }
}
