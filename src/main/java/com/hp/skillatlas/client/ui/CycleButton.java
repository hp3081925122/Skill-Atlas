package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class CycleButton<T> extends Button {
    private final List<T> values;
    private final Function<T, Component> labeler;
    private final Consumer<T> onChange;
    private T value;

    public CycleButton(List<T> values, T value, Function<T, Component> labeler, Consumer<T> onChange) {
        super(null, null);
        this.values = values;
        this.labeler = labeler;
        this.onChange = onChange;
        this.value = value;
        this.action = () -> step(1);
        this.rightAction = () -> step(-1);
        refresh();
    }

    public T value() {
        return value;
    }

    private void step(int dir) {
        if (values.isEmpty()) {
            return;
        }
        int i = values.indexOf(value);
        if (i < 0) {
            i = 0;
        } else {
            i = Math.floorMod(i + dir, values.size());
        }
        value = values.get(i);
        if (dir < 0) {
            Sounds.click();
        }
        refresh();
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    private void refresh() {
        if (value == null) {
            label = Component.literal("-");
        } else {
            label = labeler.apply(value);
        }
    }

    @Override
    public void render(float pt) {
        super.render(pt);
        if (enabled && w > 30) {
            Gfx.icon(Sprites.DOWN, x + w - 12, y + (h - 12) / 2.0F, 0x80FFFFFF);
        }
    }
}
