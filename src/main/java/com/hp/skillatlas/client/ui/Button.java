package com.hp.skillatlas.client.ui;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class Button extends Widget {
    public enum Style {
        NORMAL, PRIMARY, DANGER
    }

    public Component label;
    public int icon = -1;
    public int iconColor = 0xFFFFFFFF;
    public Style style = Style.NORMAL;
    public Runnable action;
    public Runnable rightAction;
    public BooleanSupplier active;
    private boolean pressed;

    public Button(Component label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public static Button icon(int icon, Component tip, Runnable action) {
        Button b = new Button(null, action);
        b.icon = icon;
        b.tip(tip);
        return b;
    }

    public Button style(Style style) {
        this.style = style;
        return this;
    }

    public Button active(BooleanSupplier active) {
        this.active = active;
        return this;
    }

    private Sprites.Sprite sprite(boolean hot) {
        if (!enabled) {
            return Sprites.BUTTON_DISABLED;
        }
        boolean on = active != null && active.getAsBoolean();
        boolean down = pressed && hot;
        return switch (style) {
            case PRIMARY -> {
                if (down) {
                    yield Sprites.PRIMARY_PRESSED;
                }
                if (hot) {
                    yield Sprites.PRIMARY_HOVER;
                }
                yield Sprites.PRIMARY;
            }
            case DANGER -> {
                if (down) {
                    yield Sprites.DANGER_PRESSED;
                }
                if (hot) {
                    yield Sprites.DANGER_HOVER;
                }
                yield Sprites.DANGER;
            }
            case NORMAL -> {
                if (down || on) {
                    yield Sprites.BUTTON_PRESSED;
                }
                if (hot) {
                    yield Sprites.BUTTON_HOVER;
                }
                yield Sprites.BUTTON;
            }
        };
    }

    @Override
    public void render(float pt) {
        boolean hot = hovered();
        Gfx.sprite(sprite(hot), x, y, w, h);
        int color = Theme.TEXT;
        if (!enabled) {
            color = Theme.DIM;
        } else if (style == Style.PRIMARY) {
            color = 0xFF2A1A08;
        } else if (active != null && active.getAsBoolean()) {
            color = Theme.GOLD;
        }
        float offset = 0;
        if (pressed && hot) {
            offset = 1;
        }
        if (label == null) {
            if (icon >= 0) {
                int ic = iconColor;
                if (!enabled) {
                    ic = 0x80FFFFFF;
                }
                Gfx.icon(icon, x + (w - 12) / 2.0F, y + (h - 12) / 2.0F + offset, ic);
            }
            return;
        }
        Component text = label;
        float avail = w - 8;
        if (icon >= 0) {
            avail -= 14;
        }
        text = Gfx.ellipsize(text, avail);
        float tw = Gfx.width(text);
        float total = tw;
        if (icon >= 0) {
            total += 14;
        }
        float tx = x + (w - total) / 2.0F;
        if (icon >= 0) {
            Gfx.icon(icon, tx, y + (h - 12) / 2.0F + offset, iconColor);
            tx += 14;
        }
        boolean shadow = style != Style.PRIMARY;
        Gfx.text(text.getVisualOrderText(), tx, y + (h - 8) / 2.0F + offset, color, shadow);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!enabled) {
            return true;
        }
        if (button == 0) {
            pressed = true;
            Ui.capture = this;
            return true;
        }
        if (button == 1 && rightAction != null) {
            rightAction.run();
            return true;
        }
        return false;
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        boolean fire = pressed && contains(mx, my) && enabled;
        pressed = false;
        if (fire && action != null) {
            Sounds.click();
            action.run();
        }
    }
}
