package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

public class Label extends Widget {
    private final Supplier<Component> text;
    public int color;
    public boolean wrap;
    public boolean center;
    private List<FormattedCharSequence> lines;
    private String cached;

    public Label(Component text, int color) {
        this(() -> text, color);
    }

    public Label(Supplier<Component> text, int color) {
        this.text = text;
        this.color = color;
        this.h = 10;
    }

    public Label wrapped(float width) {
        this.wrap = true;
        this.w = width;
        layoutLines();
        this.h = Math.max(10, lines.size() * 10);
        return this;
    }

    public Label centered() {
        this.center = true;
        return this;
    }

    private void layoutLines() {
        Component c = text.get();
        String key = c.getString() + "|" + w;
        if (!key.equals(cached)) {
            cached = key;
            lines = Gfx.wrap(c, w);
        }
    }

    @Override
    public void render(float pt) {
        if (wrap) {
            layoutLines();
            float ty = y;
            for (FormattedCharSequence line : lines) {
                float tx = x;
                if (center) {
                    tx = x + (w - Gfx.font().width(line)) / 2.0F;
                }
                Gfx.text(line, tx, ty, color, true);
                ty += 10;
            }
            return;
        }
        Component c = Gfx.ellipsize(text.get(), w);
        float tx = x;
        if (center) {
            tx = x + (w - Gfx.width(c)) / 2.0F;
        }
        Gfx.text(c.getVisualOrderText(), tx, y + (h - 8) / 2.0F, color, true);
    }
}
