package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.ContextMenu;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Group;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.PointRule;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class CategoryBar extends Group {
    private static final float TAB = 22;
    private final MainLayer main;
    private final Button create;
    private float scroll;

    public CategoryBar(MainLayer main) {
        this.main = main;
        this.create = add(new Button(Component.translatable("skill_atlas.ui.new_category"), () -> main.stack.push(new CategoryDialog(main, null))));
        this.create.icon = Sprites.PLUS;
    }

    private List<Category> categories() {
        return new ArrayList<>(ClientAtlas.definition.categories.values());
    }

    private float tabsBottom() {
        float b = y + h;
        if (ClientAtlas.editMode) {
            b -= 20;
        }
        return b;
    }

    private float maxScroll() {
        return Math.max(0, categories().size() * TAB - (tabsBottom() - y));
    }

    private int tabAt(double mx, double my) {
        if (mx < x || mx >= x + w || my < y || my >= tabsBottom()) {
            return -1;
        }
        int i = (int) Math.floor((my - y + scroll) / TAB);
        if (i < 0 || i >= categories().size()) {
            return -1;
        }
        return i;
    }

    @Override
    public void render(float pt) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        List<Category> cats = categories();
        float bottom = tabsBottom();
        int hover = -1;
        if (hovered() || Ui.hoverOwner == this) {
            hover = tabAt(Ui.mouseX, Ui.mouseY);
        }
        Gfx.pushClip(x, y, w, bottom - y);
        for (int i = 0; i < cats.size(); i++) {
            float ty = y + i * TAB - scroll;
            if (ty + TAB < y || ty > bottom) {
                continue;
            }
            Category c = cats.get(i);
            boolean selected = c.id.equals(ClientAtlas.category);
            Sprites.Sprite s = Sprites.TAB;
            if (selected) {
                s = Sprites.TAB_SELECTED;
            } else if (i == hover) {
                s = Sprites.TAB_HOVER;
            }
            Gfx.sprite(s, x, ty, w, TAB - 2);
        }
        for (int i = 0; i < cats.size(); i++) {
            float ty = y + i * TAB - scroll;
            if (ty + TAB < y || ty > bottom) {
                continue;
            }
            Icons.draw(cats.get(i).icon, x + 4, ty + 2, 16);
        }
        for (int i = 0; i < cats.size(); i++) {
            float ty = y + i * TAB - scroll;
            if (ty + TAB < y || ty > bottom) {
                continue;
            }
            Category c = cats.get(i);
            int color = Theme.MUTED;
            if (c.id.equals(ClientAtlas.category)) {
                color = Theme.GOLD;
            } else if (i == hover) {
                color = Theme.TEXT;
            }
            Gfx.text(Gfx.ellipsize(Names.category(c), w - 28).getVisualOrderText(), x + 24, ty + 6, color, true);
        }
        Gfx.popClip();
        create.visible = ClientAtlas.editMode && !ClientAtlas.locked;
        create.bounds(x, bottom + 2, w, 16);
        if (create.visible) {
            create.render(pt);
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (create.visible && create.contains(mx, my)) {
            return create.mouseDown(mx, my, button);
        }
        int i = tabAt(mx, my);
        if (i < 0) {
            return true;
        }
        Category c = categories().get(i);
        if (button == 0) {
            if (!c.id.equals(ClientAtlas.category)) {
                Sounds.click();
            }
            main.selectCategory(c.id);
            return true;
        }
        if (button == 1 && ClientAtlas.editMode && !ClientAtlas.locked) {
            openMenu(c, i, mx, my);
        }
        return true;
    }

    private void openMenu(Category c, int index, double mx, double my) {
        List<Category> cats = categories();
        ContextMenu menu = new ContextMenu((float) mx, (float) my);
        menu.add(Component.translatable("skill_atlas.ui.menu.edit"), Sprites.EDIT, () -> main.stack.push(new CategoryDialog(main, c)));
        menu.add(Component.translatable("skill_atlas.ui.menu.move_up"), Sprites.UP, index > 0, () -> reorder(c.id, index - 1));
        menu.add(Component.translatable("skill_atlas.ui.menu.move_down"), Sprites.DOWN, index < cats.size() - 1, () -> reorder(c.id, index + 1));
        menu.separator();
        menu.danger(Component.translatable("skill_atlas.ui.menu.delete_category"), Sprites.TRASH, () -> confirmDelete(c));
        main.stack.push(menu);
    }

    private void reorder(String id, int target) {
        List<String> ids = new ArrayList<>(ClientAtlas.definition.categories.keySet());
        ids.remove(id);
        ids.add(Math.max(0, Math.min(ids.size(), target)), id);
        JsonArray ops = new JsonArray();
        ops.add(EditOps.orderCategoriesOp(ids));
        EditActions.send(main, ops, null);
    }

    private void confirmDelete(Category c) {
        int count = 0;
        for (SkillNode n : ClientAtlas.definition.nodes.values()) {
            if (n.category.equals(c.id)) {
                count++;
            }
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("skill_atlas.ui.confirm_delete_category", Names.category(c), count));
        lines.add(Component.translatable("skill_atlas.ui.confirm_delete.refund"));
        main.stack.push(new ConfirmDialog(Component.translatable("skill_atlas.ui.menu.delete_category"), lines, Component.translatable("skill_atlas.ui.delete"), () -> {
            JsonArray ops = new JsonArray();
            ops.add(EditOps.deleteOp("delete_category", c.id));
            EditActions.send(main, ops, () -> main.status(Component.translatable("skill_atlas.ui.category_deleted"), Theme.MUTED));
        }));
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        if (maxScroll() <= 0) {
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) delta * TAB));
        return true;
    }

    @Override
    public Widget hit(double mx, double my) {
        if (create.visible && create.contains(mx, my)) {
            return create;
        }
        if (contains(mx, my)) {
            return this;
        }
        return null;
    }

    @Override
    public List<Component> tooltip(double mx, double my) {
        int i = tabAt(mx, my);
        if (i < 0) {
            return null;
        }
        Category c = categories().get(i);
        List<Component> out = new ArrayList<>();
        out.add(Names.category(c));
        int count = 0;
        int learned = 0;
        for (SkillNode n : ClientAtlas.definition.nodes.values()) {
            if (n.category.equals(c.id)) {
                count++;
                if (ClientAtlas.progress.level(n.id) > 0) {
                    learned++;
                }
            }
        }
        out.add(Component.translatable("skill_atlas.ui.category_progress", learned, count).withStyle(s -> s.withColor(0x8A93A8)));
        out.add(Component.translatable("skill_atlas.ui.category_pool", Names.pool(c.pool)).withStyle(s -> s.withColor(0x8A93A8)));
        out.add(Component.empty());
        out.add(Component.translatable("skill_atlas.ui.category_rules").withStyle(s -> s.withColor(0xE8C26A)));
        boolean any = false;
        for (PointRule r : ClientAtlas.definition.rules.values()) {
            if (!r.pool.equals(c.pool)) {
                continue;
            }
            any = true;
            long awarded = ClientAtlas.progress.awarded(r.id);
            MutableComponent state;
            if (r.limit > 0 && awarded >= r.limit) {
                state = Component.translatable("skill_atlas.ui.points.rule_done").withStyle(s -> s.withColor(0xE0685A));
            } else {
                state = Component.translatable("skill_atlas.ui.category_rule_progress", ClientAtlas.progress.counter(r.id), r.count, r.points).withStyle(s -> s.withColor(0x7FD8C8));
                if (r.limit > 0) {
                    state = state.append(Component.literal("  ")).append(Component.translatable("skill_atlas.ui.points.rule_limit", awarded, r.limit).withStyle(s -> s.withColor(0x8A93A8)));
                }
            }
            out.add(Names.rule(r).withStyle(s -> s.withColor(0xE6E9F2)).append(Component.literal("  ")).append(state));
            List<Component> desc = Names.tooltipLines(r.description);
            if (desc.isEmpty() && !r.name.isEmpty()) {
                PointRule auto = r.copy();
                auto.name = "";
                desc.add(Component.translatable("skill_atlas.ui.rule.preview", Names.rule(auto), r.points, Names.pool(r.pool)).withStyle(s -> s.withColor(0xB8BED0)));
            }
            for (Component line : desc) {
                out.add(Component.literal("  ").append(line));
            }
        }
        if (!any) {
            out.add(Component.translatable("skill_atlas.ui.category_no_rules").withStyle(s -> s.withColor(0x8A93A8)));
        }
        if (ClientAtlas.editMode) {
            out.add(Component.translatable("skill_atlas.ui.category_hint").withStyle(s -> s.withColor(0x5A6478)));
        }
        return out;
    }
}
