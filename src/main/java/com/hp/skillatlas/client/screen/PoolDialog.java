package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Checkbox;
import com.hp.skillatlas.client.ui.CycleButton;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.Texts;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.RuleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class PoolDialog extends Dialog {
    private final boolean create;
    private final PointPool p;
    private final Runnable onSaved;
    private final List<TextInput> inputs = new ArrayList<>();
    private ScrollGroup scroll;
    private String manual = "";
    private boolean saving;
    private Button save;

    public PoolDialog(PointPool existing, Runnable onSaved) {
        super(title(existing), 360, 320);
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

    private void rebuild() {
        Ui.focus(null);
        float s = 0;
        if (scroll != null) {
            s = scroll.scrollOffset();
        }
        layout(stack.width, stack.height);
        scroll.scrollTo(s);
    }

    private void addTarget(String raw) {
        String t;
        try {
            t = PointPool.normalizeTarget(p.method, raw);
        } catch (AtlasException e) {
            setStatus(Names.error(e.key(), e.args()), Theme.RED);
            Sounds.error();
            return;
        }
        if (p.targets.contains(t)) {
            return;
        }
        if (p.targets.size() >= PointPool.MAX_TARGETS) {
            setStatus(Names.error("skill_atlas.error.too_many", List.of("targets", String.valueOf(PointPool.MAX_TARGETS))), Theme.RED);
            return;
        }
        p.targets.add(t);
        setStatus(null, Theme.MUTED);
        rebuild();
    }

    private PickerDialog picker(Consumer<String> pick) {
        return switch (p.method.targets) {
            case ENTITY -> PickerDialog.entities(pick);
            case BLOCK -> PickerDialog.blocks(pick);
            case ITEM -> PickerDialog.items(pick);
            case ADVANCEMENT -> PickerDialog.advancements(pick);
            case NONE -> null;
        };
    }

    private PickerDialog tagPicker(Consumer<String> pick) {
        return switch (p.method.targets) {
            case ENTITY -> PickerDialog.tags(Registries.ENTITY_TYPE, pick);
            case BLOCK -> PickerDialog.tags(Registries.BLOCK, pick);
            case ITEM -> PickerDialog.tags(Registries.ITEM, pick);
            default -> null;
        };
    }

    @Override
    protected void build() {
        inputs.clear();
        scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 70);
        TextInput id = new TextInput(p.id).max(64).filter(s -> s.matches("[a-z0-9_.\\-]*"));
        id.enabled = create;
        id.valid = s -> Json.validId(s) && (!create || !ClientAtlas.definition.pools.containsKey(s));
        id.onChange = s -> p.id = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.id"), id));
        TextInput name = new TextInput(Texts.display(p.name)).max(128).placeholder(Component.translatable("skill_atlas.ui.field.name.placeholder"));
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

        f.heading(Component.translatable("skill_atlas.ui.pool.earn"));
        CycleButton<RuleType> type = new CycleButton<>(List.of(RuleType.values()), p.method, Names::ruleType, v -> {
            if (v.targets != p.method.targets) {
                p.targets.clear();
            } else if (!v.acceptsTags()) {
                p.targets.removeIf(t -> t.startsWith("#"));
            }
            p.method = v;
            rebuild();
        });
        f.row(Component.translatable("skill_atlas.ui.field.rule_type"), type);
        if (p.method == RuleType.KILL) {
            Checkbox hostile = new Checkbox(Component.translatable("skill_atlas.ui.field.hostile"), p.hostile, v -> p.hostile = v);
            f.row(Component.empty(), hostile);
            NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.pool.hostile.hint"), Theme.DIM);
        }
        if (p.method.targets != RuleType.Targets.NONE) {
            TextInput in = new TextInput(manual).max(256).placeholder(Component.translatable("skill_atlas.ui.rule.targets.manual"));
            in.onChange = s -> manual = s;
            in.onEnter = () -> {
                if (!manual.isBlank()) {
                    String v = manual;
                    manual = "";
                    addTarget(v);
                }
            };
            Button add = new Button(Component.translatable("skill_atlas.ui.add"), in.onEnter);
            Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(picker(this::addTarget)));
            if (p.method.acceptsTags()) {
                Button tag = new Button(Component.translatable("skill_atlas.ui.pick_tag"), () -> stack.push(tagPicker(this::addTarget)));
                f.cells(Component.translatable("skill_atlas.ui.rule.targets"), new float[]{5, 1.6F, 1.6F, 1.6F}, in, add, pick, tag);
            } else {
                f.cells(Component.translatable("skill_atlas.ui.rule.targets"), new float[]{5, 1.6F, 1.6F}, in, add, pick);
            }
            if (p.targets.isEmpty()) {
                NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.rule.targets.any"), Theme.DIM);
            }
            for (String t : new ArrayList<>(p.targets)) {
                Label l = new Label(Names.target(p.method, t), Theme.TEXT);
                Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.remove"), () -> {
                    p.targets.remove(t);
                    rebuild();
                });
                f.cells(null, new float[]{8, 1}, l, rm);
            }
        }
        TextInput count = new TextInput(String.valueOf(p.count)).max(10).filter(s -> s.matches("\\d*"));
        count.valid = s -> NodeEditorDialog.parseInt(s, 1, 1_000_000_000) != null;
        count.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 1, 1_000_000_000);
            if (v != null) {
                p.count = v;
            }
        };
        inputs.add(f.row(Component.translatable("skill_atlas.ui.rule.count." + p.method.key), count));
        TextInput points = new TextInput(String.valueOf(p.points)).max(7).filter(s -> s.matches("\\d*"));
        points.valid = s -> NodeEditorDialog.parseInt(s, 1, 1_000_000) != null;
        points.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 1, 1_000_000);
            if (v != null) {
                p.points = v;
            }
        };
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.points"), points));
        TextInput limit = new TextInput(String.valueOf(p.limit)).max(10).filter(s -> s.matches("\\d*"));
        limit.valid = s -> NodeEditorDialog.parseInt(s, 0, 1_000_000_000) != null;
        limit.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 0, 1_000_000_000);
            if (v != null) {
                p.limit = v;
            }
        };
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.limit"), limit));
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.rule.limit.hint"), Theme.DIM);
        TextInput description = new TextInput(Texts.display(p.description)).max(PointPool.MAX_DESCRIPTION).multiline().placeholder(Component.translatable("skill_atlas.ui.rule.description.placeholder"));
        description.h = 40;
        description.onChange = s -> p.description = s;
        f.row(Component.translatable("skill_atlas.ui.field.earn_tooltip"), description);
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.editor.text_hint"), Theme.DIM);
        f.gap(4);
        f.full(new Label(() -> Component.translatable("skill_atlas.ui.rule.preview", Names.earn(p), p.points, Names.pool(p)), Theme.TEAL).wrapped(f.width()), 30);
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
