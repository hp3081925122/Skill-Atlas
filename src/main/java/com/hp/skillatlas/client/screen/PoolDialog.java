package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.PointPool;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class PoolDialog extends Dialog {
    private final boolean create;
    private final PointPool p;
    private final Runnable onSaved;
    private final List<TextInput> inputs = new ArrayList<>();
    private boolean saving;
    private Button save;

    public PoolDialog(PointPool existing, Runnable onSaved) {
        super(title(existing), 300, 190);
        this.create = existing == null;
        this.onSaved = onSaved;
        if (existing == null) {
            this.p = new PointPool(EditActions.freshId("points", ClientAtlas.definition.pools.keySet()), "", 0xECC873, 0);
        } else {
            this.p = existing.copy();
        }
    }

    private static Component title(PointPool existing) {
        if (existing == null) {
            return Component.translatable("skill_atlas.ui.pool.create");
        }
        return Component.translatable("skill_atlas.ui.pool.edit", Names.pool(existing));
    }

    private static boolean validColor(String s) {
        try {
            PointPool.parseColor(s);
            return true;
        } catch (AtlasException e) {
            return false;
        }
    }

    @Override
    protected void build() {
        inputs.clear();
        ScrollGroup scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 64);
        TextInput id = new TextInput(p.id).max(64).filter(s -> s.matches("[a-z0-9_.\\-]*"));
        id.enabled = create;
        id.valid = s -> Json.validId(s) && (!create || !ClientAtlas.definition.pools.containsKey(s));
        id.onChange = s -> p.id = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.id"), id));
        TextInput name = new TextInput(p.name).max(128).placeholder(Component.translatable("skill_atlas.ui.field.name.placeholder"));
        name.onChange = s -> p.name = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.name"), name));
        TextInput color = new TextInput(PointPool.hex(p.color)).max(7).filter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
        color.valid = PoolDialog::validColor;
        color.onChange = s -> {
            if (validColor(s)) {
                p.color = PointPool.parseColor(s);
            }
        };
        inputs.add(color);
        Widget swatch = new Widget() {
            @Override
            public void render(float pt) {
                Gfx.sprite(Sprites.INSET, x, y, w, h);
                Gfx.sprite(Sprites.GEM, x + (w - 9) / 2.0F, y + (h - 9) / 2.0F, 9, 9, 0xFF000000 | p.color);
            }
        };
        f.cells(Component.translatable("skill_atlas.ui.field.color"), new float[]{6, 1}, color, swatch);
        TextInput initial = new TextInput(String.valueOf(p.initial)).max(10).filter(s -> s.matches("\\d*"));
        initial.valid = s -> NodeEditorDialog.parseInt(s, 0, 1_000_000_000) != null;
        initial.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 0, 1_000_000_000);
            if (v != null) {
                p.initial = v;
            }
        };
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.initial"), initial));
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.pool.hint"), Theme.DIM);
        f.finish();
        Button cancel = new Button(Component.translatable("skill_atlas.ui.cancel"), this::close);
        save = new Button(Component.translatable("skill_atlas.ui.save"), this::save).style(Button.Style.PRIMARY);
        save.enabled = !saving;
        footer(cancel, save);
    }

    private void save() {
        for (TextInput t : inputs) {
            if (t.valid != null && !t.valid.test(t.text())) {
                setStatus(Component.translatable("skill_atlas.ui.invalid_fields"), Theme.RED);
                Sounds.error();
                return;
            }
        }
        try {
            PointPool.fromJson(p.toJson());
        } catch (AtlasException e) {
            setStatus(Names.error(e.key(), e.args()), Theme.RED);
            Sounds.error();
            return;
        }
        JsonArray ops = new JsonArray();
        ops.add(EditOps.poolOp(p, create));
        saving = true;
        save.enabled = false;
        setStatus(Component.translatable("skill_atlas.ui.saving"), Theme.MUTED);
        ClientAtlas.edit(ops, r -> {
            saving = false;
            save.enabled = true;
            if (r.ok()) {
                close();
                if (onSaved != null) {
                    onSaved.run();
                }
            } else {
                setStatus(Names.error(r.key(), r.args()), Theme.RED);
                Sounds.error();
            }
        });
    }
}
