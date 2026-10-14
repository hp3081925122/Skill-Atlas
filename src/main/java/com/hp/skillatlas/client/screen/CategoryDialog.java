package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.CycleButton;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.Json;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class CategoryDialog extends Dialog {
    private final MainLayer main;
    private final boolean create;
    private final Category c;
    private final List<TextInput> inputs = new ArrayList<>();
    private boolean saving;
    private Button save;

    public CategoryDialog(MainLayer main, Category existing) {
        super(title(existing), 320, 200);
        this.main = main;
        this.create = existing == null;
        if (existing == null) {
            Category n = new Category();
            n.id = EditActions.freshId("category", ClientAtlas.definition.categories.keySet());
            n.icon = Icon.item("minecraft:book");
            if (!ClientAtlas.definition.pools.isEmpty()) {
                n.pool = ClientAtlas.definition.pools.keySet().iterator().next();
            }
            this.c = n;
        } else {
            this.c = existing.copy();
        }
    }

    private static Component title(Category existing) {
        if (existing == null) {
            return Component.translatable("skill_atlas.ui.category.create");
        }
        return Component.translatable("skill_atlas.ui.category.edit", Names.category(existing));
    }

    @Override
    protected void build() {
        inputs.clear();
        ScrollGroup scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 64);
        TextInput id = new TextInput(c.id).max(64).filter(s -> s.matches("[a-z0-9_.\\-]*"));
        id.enabled = create;
        id.valid = s -> Json.validId(s) && (!create || !ClientAtlas.definition.categories.containsKey(s));
        id.onChange = s -> c.id = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.id"), id));
        TextInput name = new TextInput(c.name).max(128).placeholder(Component.translatable("skill_atlas.ui.field.name.placeholder"));
        name.onChange = s -> c.name = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.name"), name));
        TextInput icon = new TextInput(c.icon.id).max(256);
        icon.valid = s -> Json.validResource(Json.normalizeResource(s));
        icon.onChange = s -> c.icon = Icon.item(Json.normalizeResource(s));
        inputs.add(icon);
        IconButton preview = new IconButton(() -> c.icon, () -> stack.push(PickerDialog.items(picked -> {
            c.icon = Icon.item(picked);
            icon.setText(picked);
        })));
        Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), preview.action);
        f.cells(Component.translatable("skill_atlas.ui.field.icon"), new float[]{1, 6, 2.4F}, preview, icon, pick);
        List<String> pools = new ArrayList<>(ClientAtlas.definition.pools.keySet());
        if (pools.isEmpty()) {
            NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.category.no_pools"), Theme.RED);
        } else {
            if (!pools.contains(c.pool)) {
                c.pool = pools.get(0);
            }
            CycleButton<String> pool = new CycleButton<>(pools, c.pool, Names::pool, v -> c.pool = v);
            f.row(Component.translatable("skill_atlas.ui.field.pool"), pool);
        }
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.category.hint"), Theme.DIM);
        f.finish();
        Button cancel = new Button(Component.translatable("skill_atlas.ui.cancel"), this::close);
        save = new Button(Component.translatable("skill_atlas.ui.save"), this::save).style(Button.Style.PRIMARY);
        save.enabled = !saving && !pools.isEmpty();
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
            Category.fromJson(c.toJson());
        } catch (AtlasException e) {
            setStatus(Names.error(e.key(), e.args()), Theme.RED);
            Sounds.error();
            return;
        }
        JsonArray ops = new JsonArray();
        ops.add(EditOps.categoryOp(c, create));
        saving = true;
        save.enabled = false;
        setStatus(Component.translatable("skill_atlas.ui.saving"), Theme.MUTED);
        String id = c.id;
        ClientAtlas.edit(ops, r -> {
            saving = false;
            save.enabled = true;
            if (r.ok()) {
                close();
                if (create) {
                    main.selectCategory(id);
                }
                main.status(Component.translatable("skill_atlas.ui.saved"), Theme.TEAL);
            } else {
                setStatus(Names.error(r.key(), r.args()), Theme.RED);
                Sounds.error();
            }
        });
    }
}
