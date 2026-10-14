package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class Checkbox extends Widget {
    public Component label;
    public boolean value;
    public Consumer<Boolean> onChange;

    public Checkbox(Component label, boolean value, Consumer<Boolean> onChange) {
        this.label = label;
        this.value = value;
        this.onChange = onChange;
        this.h = 12;
        this.w = 14;
        if (label != null) {
            this.w = 14 + Gfx.width(label);
        }
    }

    @Override
    public void render(float pt) {
        float by = y + (h - 10) / 2.0F;
        if (value) {
            Gfx.sprite(Sprites.CHECK_ON, x, by);
        } else {
            Gfx.sprite(Sprites.CHECK_OFF, x, by);
        }
        if (label != null) {
            int color = Theme.TEXT;
            if (!enabled) {
                color = Theme.DIM;
            } else if (hovered()) {
                color = 0xFFFFFFFF;
            }
            Gfx.text(Gfx.ellipsize(label, w - 14).getVisualOrderText(), x + 14, y + (h - 8) / 2.0F, color, true);
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0 || !enabled) {
            return true;
        }
        value = !value;
        Sounds.click();
        if (onChange != null) {
            onChange.accept(value);
        }
        return true;
    }
}
