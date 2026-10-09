package com.hp.skillatlas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

public class UiButton extends Button {
    public UiButton(Button.Builder builder) { super(builder); }
    @Override protected void renderWidget(GuiGraphics g, int x, int y, float partial) {
        String texture = !active ? "button_disabled" : isHoveredOrFocused() ? "button_hover" : "button";
        if (active && isHovered && Minecraft.getInstance().mouseHandler.isLeftPressed()) texture = "button_pressed";
        UiTheme.draw(g, texture, getX(), getY(), getWidth(), getHeight());
        renderString(g, Minecraft.getInstance().font, UiTheme.color(active ? "button" : "button_disabled"));
    }
}
