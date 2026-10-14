package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

public class Heading extends Widget {
    private final Component text;
    public int color = Theme.GOLD;

    public Heading(Component text) {
        this.text = text;
        this.h = 12;
    }

    @Override
    public void render(float pt) {
        float tw = Gfx.width(text);
        Gfx.text(text, x, y + 2, color);
        if (tw + 6 < w) {
            Gfx.fill(x + tw + 6, y + 6, w - tw - 6, 1, Theme.SEPARATOR);
        }
    }
}
