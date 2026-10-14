package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.CycleButton;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.PointRule;
import com.hp.skillatlas.data.RuleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RuleDialog extends Dialog {
    private final boolean create;
    private final PointRule r;
    private final Runnable onSaved;
    private final List<TextInput> inputs = new ArrayList<>();
    private ScrollGroup scroll;
    private String manual = "";
    private boolean saving;
    private Button save;

    public RuleDialog(PointRule existing, Runnable onSaved) {
        super(title(existing), 360, 320);
        this.create = existing == null;
        this.onSaved = onSaved;
        if (existing == null) {
            PointRule n = new PointRule();
            n.id = EditActions.freshId("rule", ClientAtlas.definition.rules.keySet());
            n.count = 10;
            if (!ClientAtlas.definition.pools.isEmpty()) {
                n.pool = ClientAtlas.definition.pools.keySet().iterator().next();
            }
            this.r = n;
        } else {
            this.r = existing.copy();
        }
    }

    private static Component title(PointRule existing) {
        if (existing == null) {
            return Component.translatable("skill_atlas.ui.rule.create");
        }
        return Component.translatable("skill_atlas.ui.rule.edit", Names.rule(existing));
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
            t = PointRule.normalizeTarget(r.type, raw);
        } catch (AtlasException e) {
            setStatus(Names.error(e.key(), e.args()), Theme.RED);
            Sounds.error();
            return;
        }
        if (r.targets.contains(t)) {
            return;
        }
        if (r.targets.size() >= PointRule.MAX_TARGETS) {
            setStatus(Names.error("skill_atlas.error.too_many", List.of("targets", String.valueOf(PointRule.MAX_TARGETS))), Theme.RED);
            return;
        }
        r.targets.add(t);
        setStatus(null, Theme.MUTED);
        rebuild();
    }

    private PickerDialog picker(Consumer<String> pick) {
        return switch (r.type.targets) {
            case ENTITY -> PickerDialog.entities(pick);
            case BLOCK -> PickerDialog.blocks(pick);
            case ITEM -> PickerDialog.items(pick);
            case ADVANCEMENT -> PickerDialog.advancements(pick);
            case NONE -> null;
        };
    }

    private PickerDialog tagPicker(Consumer<String> pick) {
        return switch (r.type.targets) {
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
        TextInput id = new TextInput(r.id).max(64).filter(s -> s.matches("[a-z0-9_.\\-]*"));
        id.enabled = create;
        id.valid = s -> Json.validId(s) && (!create || !ClientAtlas.definition.rules.containsKey(s));
        id.onChange = s -> r.id = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.id"), id));
        TextInput name = new TextInput(r.name).max(128).placeholder(Component.translatable("skill_atlas.ui.rule.name.placeholder"));
        name.onChange = s -> r.name = s;
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.name"), name));
        CycleButton<RuleType> type = new CycleButton<>(List.of(RuleType.values()), r.type, Names::ruleType, v -> {
            if (v.targets != r.type.targets) {
                r.targets.clear();
            } else if (!v.acceptsTags()) {
                r.targets.removeIf(t -> t.startsWith("#"));
            }
            r.type = v;
            rebuild();
        });
        f.row(Component.translatable("skill_atlas.ui.field.rule_type"), type);
        if (r.type.targets != RuleType.Targets.NONE) {
            f.heading(Component.translatable("skill_atlas.ui.rule.targets"));
            if (r.targets.isEmpty()) {
                f.full(new Label(Component.translatable("skill_atlas.ui.rule.targets.any"), Theme.MUTED), 12);
            }
            for (String t : new ArrayList<>(r.targets)) {
                Label l = new Label(Names.target(r.type, t), Theme.TEXT);
                Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.remove"), () -> {
                    r.targets.remove(t);
                    rebuild();
                });
                f.cells(null, new float[]{8, 1}, l, rm);
            }
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
            if (r.type.acceptsTags()) {
                Button tag = new Button(Component.translatable("skill_atlas.ui.pick_tag"), () -> stack.push(tagPicker(this::addTarget)));
                f.cells(null, new float[]{5, 1.6F, 1.6F, 1.6F}, in, add, pick, tag);
            } else {
                f.cells(null, new float[]{5, 1.6F, 1.6F}, in, add, pick);
            }
        }
        f.heading(Component.translatable("skill_atlas.ui.rule.reward"));
        TextInput count = new TextInput(String.valueOf(r.count)).max(10).filter(s -> s.matches("\\d*"));
        count.valid = s -> NodeEditorDialog.parseInt(s, 1, 1_000_000_000) != null;
        count.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 1, 1_000_000_000);
            if (v != null) {
                r.count = v;
            }
        };
        inputs.add(f.row(Component.translatable("skill_atlas.ui.rule.count." + r.type.key), count));
        TextInput points = new TextInput(String.valueOf(r.points)).max(7).filter(s -> s.matches("\\d*"));
        points.valid = s -> NodeEditorDialog.parseInt(s, 1, 1_000_000) != null;
        points.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 1, 1_000_000);
            if (v != null) {
                r.points = v;
            }
        };
        inputs.add(points);
        List<String> pools = new ArrayList<>(ClientAtlas.definition.pools.keySet());
        if (!pools.isEmpty() && !pools.contains(r.pool)) {
            r.pool = pools.get(0);
        }
        CycleButton<String> pool = new CycleButton<>(pools, r.pool, Names::pool, v -> r.pool = v);
        f.cells(Component.translatable("skill_atlas.ui.field.points"), new float[]{2, 4}, points, pool);
        TextInput limit = new TextInput(String.valueOf(r.limit)).max(10).filter(s -> s.matches("\\d*"));
        limit.valid = s -> NodeEditorDialog.parseInt(s, 0, 1_000_000_000) != null;
        limit.onChange = s -> {
            Integer v = NodeEditorDialog.parseInt(s, 0, 1_000_000_000);
            if (v != null) {
                r.limit = v;
            }
        };
        inputs.add(f.row(Component.translatable("skill_atlas.ui.field.limit"), limit));
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.rule.limit.hint"), Theme.DIM);
        f.gap(4);
        f.full(new Label(() -> {
            PointRule preview = r.copy();
            preview.name = "";
            return Component.translatable("skill_atlas.ui.rule.preview", Names.rule(preview), preview.points, Names.pool(preview.pool));
        }, Theme.TEAL).wrapped(f.width()), 30);
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
            PointRule.fromJson(r.toJson());
        } catch (AtlasException e) {
            setStatus(Names.error(e.key(), e.args()), Theme.RED);
            Sounds.error();
            return;
        }
        JsonArray ops = new JsonArray();
        ops.add(EditOps.ruleOp(r, create));
        saving = true;
        save.enabled = false;
        setStatus(Component.translatable("skill_atlas.ui.saving"), Theme.MUTED);
        ClientAtlas.edit(ops, res -> {
            saving = false;
            save.enabled = true;
            if (res.ok()) {
                close();
                if (onSaved != null) {
                    onSaved.run();
                }
            } else {
                setStatus(Names.error(res.key(), res.args()), Theme.RED);
                Sounds.error();
            }
        });
    }
}
