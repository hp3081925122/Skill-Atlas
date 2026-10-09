package com.hp.skillatlas.client;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

public final class UiMultilineEditBox extends MultiLineEditBox {
    public UiMultilineEditBox(Font font, int x, int y, int width, int height, Component message, Component placeholder) { super(font, x, y, width, height, message, placeholder); }
    @Override protected void renderBackground(GuiGraphics g) { UiTheme.draw(g, isFocused() ? "input_focus" : "input", getX(), getY(), getWidth(), getHeight()); }
    @Override protected void renderDecorations(GuiGraphics g) {
        if (scrollbarVisible()) {
            int thumb = Math.min(getHeight(), Math.max(16, getHeight() * getHeight() / Math.max(1, getInnerHeight() + 4)));
            int y = getY() + (int)(scrollAmount() * (getHeight() - thumb) / Math.max(1, getMaxScrollAmount()));
            UiTheme.draw(g, "scroll_track", getX() + getWidth(), getY(), 8, getHeight()); UiTheme.draw(g, "scroll_thumb", getX() + getWidth(), y, 8, thumb);
        }
    }
}
