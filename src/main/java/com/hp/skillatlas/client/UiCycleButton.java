package com.hp.skillatlas.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

public final class UiCycleButton extends UiButton {
    private final String key;
    private final List<String> options;
    private final Consumer<String> changed;
    private int index;
    public UiCycleButton(int x, int y, int width, int height, String key, String initial, String[] options, Consumer<String> changed) {
        super(Button.builder(Component.empty(), b -> {}).bounds(x, y, width, height));
        this.key = key; this.options = List.of(options); this.changed = changed; this.index = Math.max(0, this.options.indexOf(initial)); refreshLabel();
    }
    private void refreshLabel() { setMessage(FormScreen.tr(key).copy().append(": ").append(FormScreen.tr("value." + options.get(index)))); }
    private void cycle(int delta) { index = Math.floorMod(index + delta, options.size()); refreshLabel(); changed.accept(options.get(index)); }
    @Override public void onPress() { cycle(Screen.hasShiftDown() ? -1 : 1); }
    @Override public boolean mouseScrolled(double x, double y, double delta) { if (!active || !visible || !isMouseOver(x, y) || delta == 0) return false; cycle(delta > 0 ? -1 : 1); return true; }
}
