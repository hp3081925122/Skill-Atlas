package com.hp.skillatlas.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

public class UiConfirmScreen extends Screen {
    private final BooleanConsumer callback;
    private final Component message, yes, no;
    private List<FormattedCharSequence> lines = List.of();
    public UiConfirmScreen(BooleanConsumer callback, Component title, Component message) { this(callback, title, message, Component.translatable("gui.yes"), Component.translatable("gui.no")); }
    public UiConfirmScreen(BooleanConsumer callback, Component title, Component message, Component yes, Component no) { super(title); this.callback = callback; this.message = message; this.yes = yes; this.no = no; }
    @Override protected void init() {
        lines = font.split(message, Math.max(40, Math.min(420, width - 48)));
        int y = Math.min(height - 32, Math.max(height / 2 + 30, 45 + lines.size() * 12)); int w = Math.min(140, (width - 30) / 2);
        addRenderableWidget(Button.builder(yes, b -> callback.accept(true)).bounds(width / 2 - w - 4, y, w, 20).build(UiButton::new));
        addRenderableWidget(Button.builder(no, b -> callback.accept(false)).bounds(width / 2 + 4, y, w, 20).build(UiButton::new));
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        UiTheme.frame(g, width, height); g.drawCenteredString(font, title, width / 2, 11, UiTheme.color("title"));
        int top = Math.max(42, height / 2 - lines.size() * 6);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), (width - font.width(lines.get(i))) / 2, top + i * 12, UiTheme.color("text"));
        super.render(g, x, y, partial);
    }
    @Override public void onClose() { callback.accept(false); }
    @Override public boolean isPauseScreen() { return false; }
}
