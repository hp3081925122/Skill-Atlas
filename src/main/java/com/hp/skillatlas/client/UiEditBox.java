package com.hp.skillatlas.client;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class UiEditBox extends EditBox {
    private final int outerHeight;
    public UiEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x + 4, y + (height - 8) / 2, Math.max(1, width - 8), 8, message); outerHeight = height; setBordered(false);
    }
    @Override public void setY(int y) { super.setY(y + (outerHeight - 8) / 2); }
    public int outerY() { return getY() - (outerHeight - 8) / 2; }
    @Override public boolean isMouseOver(double x, double y) { return visible && x >= getX() - 4 && x < getX() + getWidth() + 4 && y >= outerY() && y < outerY() + outerHeight; }
    @Override protected boolean clicked(double x, double y) { return active && isMouseOver(x, y); }
    @Override public void renderWidget(GuiGraphics g, int x, int y, float partial) {
        UiTheme.draw(g, isFocused() ? "input_focus" : "input", getX() - 4, outerY(), getWidth() + 8, outerHeight);
        setTextColor(UiTheme.color("text")); setTextColorUneditable(UiTheme.color("muted")); super.renderWidget(g, x, y, partial);
    }
}
