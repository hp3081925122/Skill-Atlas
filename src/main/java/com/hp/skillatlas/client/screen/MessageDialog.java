package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Heading;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Theme;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class MessageDialog extends Dialog {
    public record Section(Component heading, List<Component> lines) {
    }

    private final List<Section> sections;

    public MessageDialog(Component title, List<Section> sections, float width, float height) {
        super(title, width, height);
        this.sections = sections;
    }

    public static MessageDialog help() {
        List<Section> out = new ArrayList<>();
        String[][] keys = {
                {"browse", "4"},
                {"edit", "7"},
                {"nodes", "5"},
                {"points", "3"}
        };
        for (String[] k : keys) {
            List<Component> lines = new ArrayList<>();
            int count = Integer.parseInt(k[1]);
            for (int i = 1; i <= count; i++) {
                lines.add(Component.translatable("skill_atlas.help." + k[0] + "." + i));
            }
            out.add(new Section(Component.translatable("skill_atlas.help." + k[0]), lines));
        }
        return new MessageDialog(Component.translatable("skill_atlas.ui.help"), out, 380, 320);
    }

    @Override
    protected void build() {
        ScrollGroup scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        float cy = scroll.y + 2;
        float width = scroll.innerWidth() - 2;
        for (Section s : sections) {
            if (s.heading != null) {
                Heading hd = new Heading(s.heading);
                hd.bounds(scroll.x + 2, cy, width, 12);
                scroll.add(hd);
                cy += 15;
            }
            for (Component line : s.lines) {
                Label l = new Label(line, Theme.TEXT).wrapped(width - 6);
                l.bounds(scroll.x + 8, cy, width - 6, l.h);
                scroll.add(l);
                cy += l.h + 3;
            }
            cy += 6;
        }
        scroll.rebuilt();
        footer(new Button(Component.translatable("skill_atlas.ui.ok"), this::close).style(Button.Style.PRIMARY));
    }
}
