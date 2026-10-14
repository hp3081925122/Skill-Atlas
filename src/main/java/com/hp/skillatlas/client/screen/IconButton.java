package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.data.Icon;

import java.util.function.Supplier;

public final class IconButton extends Button {
    private final Supplier<Icon> icon;

    public IconButton(Supplier<Icon> icon, Runnable action) {
        super(null, action);
        this.icon = icon;
    }

    @Override
    public void render(float pt) {
        if (hovered()) {
            Gfx.sprite(Sprites.BUTTON_HOVER, x, y, w, h);
        } else {
            Gfx.sprite(Sprites.INSET, x, y, w, h);
        }
        float s = Math.min(16, Math.min(w, h) - 2);
        Icons.draw(icon.get(), x + (w - s) / 2.0F, y + (h - s) / 2.0F, s);
    }
}
