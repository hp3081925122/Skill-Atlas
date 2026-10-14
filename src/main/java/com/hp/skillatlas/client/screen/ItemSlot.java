package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public final class ItemSlot extends Button {
    private final Supplier<Icon> icon;

    public ItemSlot(Supplier<Icon> icon, Runnable action) {
        super(null, action);
        this.icon = icon;
        tip(() -> List.of(name(), Component.translatable("skill_atlas.ui.item_slot.tip").withStyle(s -> s.withColor(0x8A93A8))));
    }

    private Component name() {
        Icon i = icon.get();
        return Names.item(new ItemCost(i.id, 1, i.nbt));
    }

    @Override
    public void render(float pt) {
        if (hovered()) {
            Gfx.sprite(Sprites.BUTTON_HOVER, x, y, w, h);
        } else {
            Gfx.sprite(Sprites.INSET, x, y, w, h);
        }
        float s = Math.min(16, h - 2);
        Icons.draw(icon.get(), x + 2, y + (h - s) / 2.0F, s);
        int color = Theme.TEXT;
        if (hovered()) {
            color = 0xFFFFFFFF;
        }
        Gfx.text(Gfx.ellipsize(name(), w - s - 8).getVisualOrderText(), x + s + 5, y + (h - 8) / 2.0F, color, true);
    }
}
