package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.Theme;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class ConfirmDialog extends Dialog {
    private final List<Component> message;
    private final Component confirm;
    private final Runnable action;

    public ConfirmDialog(Component title, List<Component> message, Component confirm, Runnable action) {
        super(title, 280, 120);
        this.message = message;
        this.confirm = confirm;
        this.action = action;
    }

    @Override
    public void layout(float screenW, float screenH) {
        float total = 0;
        for (Component c : message) {
            total += Math.max(10, new Label(c, Theme.TEXT).wrapped(prefW - 16).h) + 4;
        }
        prefH = HEADER + FOOTER + total + 14;
        super.layout(screenW, screenH);
    }

    @Override
    protected void build() {
        float cy = contentY() + 4;
        int color = Theme.TEXT;
        for (Component c : message) {
            Label l = new Label(c, color).wrapped(contentW());
            l.bounds(contentX(), cy, contentW(), l.h);
            add(l);
            cy += l.h + 4;
            color = Theme.MUTED;
        }
        Button cancel = new Button(Component.translatable("skill_atlas.ui.cancel"), this::close);
        Button ok = new Button(confirm, () -> {
            close();
            action.run();
        }).style(Button.Style.DANGER);
        footer(cancel, ok);
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            close();
            action.run();
            return true;
        }
        return super.key(key, scan, mods);
    }
}
