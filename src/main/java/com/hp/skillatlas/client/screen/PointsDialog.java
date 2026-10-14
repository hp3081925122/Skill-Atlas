package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.PointPool;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PointsDialog extends Dialog {
    private ScrollGroup scroll;
    private int builtVersion;
    private boolean builtEdit;

    public PointsDialog() {
        super(Component.translatable("skill_atlas.ui.points"), 380, 320);
        ClientAtlas.requestProgress();
    }

    private boolean editable() {
        return ClientAtlas.editMode && !ClientAtlas.locked;
    }

    private void rebuild() {
        float s = 0;
        if (scroll != null) {
            s = scroll.scrollOffset();
        }
        layout(stack.width, stack.height);
        scroll.scrollTo(s);
    }

    private void send(JsonArray ops) {
        setStatus(Component.translatable("skill_atlas.ui.saving"), Theme.MUTED);
        ClientAtlas.edit(ops, r -> {
            if (r.ok()) {
                setStatus(Component.translatable("skill_atlas.ui.saved"), Theme.TEAL);
            } else {
                setStatus(Names.error(r.key(), r.args()), Theme.RED);
                Sounds.error();
            }
        });
    }

    @Override
    protected void build() {
        builtVersion = ClientAtlas.version;
        builtEdit = editable();
        scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 0);
        boolean edit = builtEdit;
        if (ClientAtlas.definition.pools.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.points.no_pools"), Theme.MUTED).centered(), 16);
        }
        for (PointPool pool : ClientAtlas.definition.pools.values()) {
            String id = pool.id;
            Widget row = new Widget() {
                @Override
                public void render(float pt) {
                    PointPool p = ClientAtlas.definition.pools.get(id);
                    if (p == null) {
                        return;
                    }
                    Gfx.sprite(Sprites.GEM, x + 2, y + 3, 9, 9, 0xFF000000 | p.color);
                    String amount = String.valueOf(ClientAtlas.progress.points(id));
                    float aw = Gfx.width(amount);
                    Gfx.text(Gfx.ellipsize(Names.pool(p), w - aw - 24).getVisualOrderText(), x + 15, y + 4, Theme.TEXT, true);
                    Gfx.text(amount, x + w - aw - 2, y + 4, Theme.GOLD);
                }
            };
            row.h = 16;
            if (edit) {
                Button e = Button.icon(Sprites.EDIT, Component.translatable("skill_atlas.ui.menu.edit"), () -> stack.push(new PoolDialog(pool, null)));
                Button d = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.delete"), () -> stack.push(new ConfirmDialog(Component.translatable("skill_atlas.ui.pool.delete"), List.of(Component.translatable("skill_atlas.ui.pool.delete.confirm", Names.pool(pool))), Component.translatable("skill_atlas.ui.delete"), () -> {
                    JsonArray ops = new JsonArray();
                    ops.add(EditOps.deleteOp("delete_pool", id));
                    send(ops);
                })));
                f.cells(null, new float[]{10, 1, 1}, row, e, d);
            } else {
                f.full(row, 16);
            }
            NodeEditorDialog.hint(f, Names.earn(pool), Theme.GOLD);
            for (Component line : Names.tooltipLines(pool.description)) {
                NodeEditorDialog.hint(f, line, Theme.TEXT);
            }
            f.full(new Label(() -> progressLine(id), Theme.MUTED), 10);
            if (edit) {
                f.full(new Label(Component.translatable("skill_atlas.ui.points.pool_initial", pool.initial), Theme.DIM), 10);
            }
            f.gap(5);
        }
        if (edit) {
            Button add = new Button(Component.translatable("skill_atlas.ui.pool.create"), () -> stack.push(new PoolDialog(null, null)));
            add.icon = Sprites.PLUS;
            f.full(add, 16);
        }
        f.finish();
        footer(new Button(Component.translatable("skill_atlas.ui.ok"), this::close).style(Button.Style.PRIMARY));
    }

    private Component progressLine(String id) {
        PointPool pool = ClientAtlas.definition.pools.get(id);
        if (pool == null) {
            return Component.empty();
        }
        return Names.earnProgress(pool, ClientAtlas.progress.counter(id), ClientAtlas.progress.awarded(id));
    }

    @Override
    public void render(float pt) {
        if (builtVersion != ClientAtlas.version || builtEdit != editable()) {
            rebuild();
        }
        super.render(pt);
    }
}
