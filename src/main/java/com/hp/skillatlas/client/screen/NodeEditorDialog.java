package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Checkbox;
import com.hp.skillatlas.client.ui.CycleButton;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Heading;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.Texts;
import com.hp.skillatlas.data.Prerequisite;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class NodeEditorDialog extends Dialog {
    private enum Kind {
        EDIT, CREATE, BATCH
    }

    private enum Section {
        NAME, CATEGORY, ICON, TOOLTIP, FLAGS, PREREQUISITES, LEVELS
    }

    private final MainLayer main;
    private final Kind kind;
    private final SkillNode n;
    private final List<String> ids;
    private final Set<Section> applied = EnumSet.noneOf(Section.class);
    private final Map<Section, Checkbox> applyBoxes = new EnumMap<>(Section.class);
    private final List<TextInput> inputs = new ArrayList<>();
    private ScrollGroup scroll;
    private Button save;
    private boolean saving;

    private NodeEditorDialog(MainLayer main, Kind kind, SkillNode working, List<String> ids, Component title) {
        super(title, 440, 360);
        this.main = main;
        this.kind = kind;
        this.n = working;
        this.ids = ids;
    }

    public static void edit(MainLayer main, SkillNode node) {
        main.stack.push(new NodeEditorDialog(main, Kind.EDIT, node.copy(), List.of(node.id), Component.translatable("skill_atlas.ui.editor.edit", Names.node(node))));
    }

    public static void create(MainLayer main, SkillNode node) {
        main.stack.push(new NodeEditorDialog(main, Kind.CREATE, node, List.of(), Component.translatable("skill_atlas.ui.editor.create")));
    }

    public static void batch(MainLayer main, List<SkillNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        if (nodes.size() == 1) {
            edit(main, nodes.get(0));
            return;
        }
        List<String> ids = new ArrayList<>();
        for (SkillNode s : nodes) {
            ids.add(s.id);
        }
        main.stack.push(new NodeEditorDialog(main, Kind.BATCH, nodes.get(0).copy(), ids, Component.translatable("skill_atlas.ui.editor.batch", nodes.size())));
    }

    public static Integer parseInt(String s, int min, int max) {
        try {
            long v = Long.parseLong(s.trim());
            if (v < min || v > max) {
                return null;
            }
            return (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDouble(String s, double min, double max) {
        try {
            double v = Double.parseDouble(s.trim());
            if (Double.isNaN(v) || Double.isInfinite(v) || v < min || v > max) {
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void hint(Form f, Component text, int color) {
        Label l = new Label(text, color).wrapped(f.width());
        f.full(l, l.h);
    }

    private void touch(Section s) {
        if (kind == Kind.BATCH && applied.add(s)) {
            Checkbox cb = applyBoxes.get(s);
            if (cb != null) {
                cb.value = true;
            }
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

    private void heading(Form f, Section s, String key) {
        float hy = f.y();
        Heading hd = f.heading(Component.translatable(key));
        if (kind == Kind.BATCH) {
            Checkbox cb = new Checkbox(Component.translatable("skill_atlas.ui.editor.apply"), applied.contains(s), v -> {
                if (v) {
                    applied.add(s);
                } else {
                    applied.remove(s);
                }
            });
            float cw = cb.w + 2;
            hd.w = f.width() - cw - 6;
            f.at(cb, f.left() + f.width() - cw, hy, cw, 12);
            applyBoxes.put(s, cb);
        }
    }

    private TextInput text(String value, int max, Section s, Consumer<String> set) {
        TextInput t = new TextInput(value).max(max);
        t.onChange = v -> {
            set.accept(v);
            touch(s);
        };
        inputs.add(t);
        return t;
    }

    private boolean canPrerequisite(SkillNode c) {
        if (c.id.equals(n.id) || ids.contains(c.id) || n.prerequisite(c.id) != null) {
            return false;
        }
        AtlasDefinition d = ClientAtlas.definition;
        if (kind == Kind.CREATE) {
            return !d.dependsOn(c.id, n.id);
        }
        for (String id : ids) {
            if (d.dependsOn(c.id, id)) {
                return false;
            }
        }
        return true;
    }

    private Widget nodeChip(String id) {
        return new Widget() {
            @Override
            public void render(float pt) {
                SkillNode target = ClientAtlas.definition.nodes.get(id);
                Gfx.sprite(Sprites.INSET, x, y, w, h);
                if (target == null) {
                    Gfx.text(Gfx.ellipsize(Component.literal(id), w - 8).getVisualOrderText(), x + 4, y + 4, Theme.RED, true);
                    return;
                }
                Icons.draw(target.icon, x + 2, y + 1, 14);
                Gfx.text(Gfx.ellipsize(Names.node(target), w - 22).getVisualOrderText(), x + 19, y + 4, Theme.TEXT, true);
            }
        };
    }

    @Override
    protected void build() {
        inputs.clear();
        applyBoxes.clear();
        AtlasDefinition d = ClientAtlas.definition;
        scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 74);
        if (kind == Kind.BATCH) {
            hint(f, Component.translatable("skill_atlas.ui.editor.batch_hint", ids.size()), Theme.TEAL);
            f.gap(2);
        }
        heading(f, Section.NAME, "skill_atlas.ui.editor.section.name");
        if (kind != Kind.BATCH) {
            TextInput id = new TextInput(n.id).max(64).filter(s -> s.matches("[a-z0-9_.\\-]*"));
            id.enabled = kind == Kind.CREATE;
            id.valid = s -> Json.validId(s) && (kind != Kind.CREATE || !d.nodes.containsKey(s));
            id.onChange = s -> n.id = s;
            inputs.add(f.row(Component.translatable("skill_atlas.ui.field.id"), id));
        }
        TextInput name = text(Texts.display(n.name), 128, Section.NAME, v -> n.name = v);
        name.placeholder(Component.translatable("skill_atlas.ui.field.name.placeholder"));
        f.row(Component.translatable("skill_atlas.ui.field.name"), name);
        TextInput aliases = text(Texts.display(n.aliases), 512, Section.NAME, v -> n.aliases = v);
        aliases.placeholder(Component.translatable("skill_atlas.ui.field.aliases.placeholder"));
        f.row(Component.translatable("skill_atlas.ui.field.aliases"), aliases);

        heading(f, Section.CATEGORY, "skill_atlas.ui.editor.section.category");
        List<String> cats = new ArrayList<>(d.categories.keySet());
        CycleButton<String> cat = new CycleButton<>(cats, n.category, id -> {
            Category c = d.categories.get(id);
            if (c == null) {
                return Component.literal(id);
            }
            return Names.category(c);
        }, v -> {
            n.category = v;
            touch(Section.CATEGORY);
        });
        f.row(Component.translatable("skill_atlas.ui.field.category"), cat);

        heading(f, Section.ICON, "skill_atlas.ui.editor.section.icon");
        CycleButton<String> iconType = new CycleButton<>(List.of(Icon.ITEM, Icon.TEXTURE), n.icon.type, t -> Component.translatable("skill_atlas.ui.icon_type." + t), v -> {
            n.icon = new Icon(v, n.icon.id, "");
            touch(Section.ICON);
            rebuild();
        });
        if (n.icon.isTexture()) {
            TextInput iconId = text(n.icon.id, 256, Section.ICON, v -> n.icon = new Icon(Icon.TEXTURE, Json.normalizeResource(v), ""));
            iconId.valid = v -> Json.validResource(Json.normalizeResource(v));
            Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.textures(id -> {
                n.icon = new Icon(Icon.TEXTURE, id, "");
                touch(Section.ICON);
                rebuild();
            })));
            IconButton preview = new IconButton(() -> n.icon, pick.action);
            f.cells(Component.translatable("skill_atlas.ui.field.icon"), new float[]{1.2F, 2.6F, 5, 2.2F}, preview, iconType, iconId, pick);
            hint(f, Component.translatable("skill_atlas.ui.icon_type.texture.hint"), Theme.DIM);
        } else {
            ItemSlot slot = new ItemSlot(() -> n.icon, () -> stack.push(PickerDialog.itemStacks(e -> {
                n.icon = new Icon(Icon.ITEM, e.id, e.nbt);
                touch(Section.ICON);
                rebuild();
            })));
            f.cells(Component.translatable("skill_atlas.ui.field.icon"), new float[]{2.6F, 8.2F}, iconType, slot);
        }

        heading(f, Section.TOOLTIP, "skill_atlas.ui.editor.section.tooltip");
        TextInput tip = text(Texts.display(n.tooltip), 4000, Section.TOOLTIP, v -> n.tooltip = v).multiline();
        tip.h = 54;
        f.row(Component.translatable("skill_atlas.ui.field.tooltip"), tip);
        hint(f, Component.translatable("skill_atlas.ui.editor.text_hint"), Theme.DIM);

        heading(f, Section.FLAGS, "skill_atlas.ui.editor.section.flags");
        Checkbox major = new Checkbox(Component.translatable("skill_atlas.ui.field.major"), n.major, v -> {
            n.major = v;
            touch(Section.FLAGS);
        });
        Checkbox hidden = new Checkbox(Component.translatable("skill_atlas.ui.field.hidden"), n.hidden, v -> {
            n.hidden = v;
            touch(Section.FLAGS);
        });
        f.cells(Component.empty(), new float[]{1, 1.6F}, major, hidden);
        CycleButton<Boolean> req = new CycleButton<>(List.of(true, false), n.requireAll, v -> {
            if (v) {
                return Component.translatable("skill_atlas.ui.requirement.all");
            }
            return Component.translatable("skill_atlas.ui.requirement.any");
        }, v -> {
            n.requireAll = v;
            touch(Section.FLAGS);
        });
        f.row(Component.translatable("skill_atlas.ui.field.requirement"), req);
        hint(f, Component.translatable("skill_atlas.ui.editor.hidden_hint"), Theme.DIM);

        heading(f, Section.PREREQUISITES, "skill_atlas.ui.editor.section.prerequisites");
        if (n.prerequisites.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.editor.no_prerequisites"), Theme.MUTED), 12);
        }
        for (Prerequisite pre : new ArrayList<>(n.prerequisites)) {
            Checkbox line = new Checkbox(Component.translatable("skill_atlas.ui.field.line"), pre.line, v -> {
                pre.line = v;
                touch(Section.PREREQUISITES);
            });
            line.tip(Component.translatable("skill_atlas.ui.field.line.desc"));
            Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.remove"), () -> {
                n.prerequisites.remove(pre);
                touch(Section.PREREQUISITES);
                rebuild();
            });
            f.cells(null, new float[]{7, 2.6F, 1}, nodeChip(pre.node), line, rm);
        }
        Button addPre = new Button(Component.translatable("skill_atlas.ui.editor.add_prerequisite"), () -> stack.push(PickerDialog.nodes(this::canPrerequisite, id -> {
            n.prerequisites.add(new Prerequisite(id, true));
            touch(Section.PREREQUISITES);
            rebuild();
        })));
        addPre.icon = Sprites.PLUS;
        addPre.enabled = n.prerequisites.size() < SkillNode.MAX_PREREQUISITES;
        f.full(addPre, 16);

        heading(f, Section.LEVELS, "skill_atlas.ui.editor.section.levels");
        for (int i = 0; i < n.levels.size(); i++) {
            SkillLevel lv = n.levels.get(i);
            int index = i;
            Component levelTitle = Component.translatable("skill_atlas.ui.editor.level_title", Names.node(n), i + 1);
            Button open = new Button(Component.translatable("skill_atlas.ui.editor.level_summary", i + 1, lv.cost, lv.rewards.size(), lv.items.size()), () -> stack.push(new LevelDialog(levelTitle, lv, edited -> {
                n.levels.set(index, edited);
                touch(Section.LEVELS);
                rebuild();
            })));
            open.tip(() -> {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("skill_atlas.ui.editor.level", index + 1).withStyle(st -> st.withColor(0xECC873)));
                for (Reward r : lv.rewards) {
                    lines.add(Component.literal(" - ").append(Names.reward(r, true)));
                }
                for (ItemCost c : lv.items) {
                    lines.add(Component.literal(" - ").append(Names.item(c)).append(" x" + c.count));
                }
                lines.add(Component.translatable("skill_atlas.ui.editor.level_open").withStyle(st -> st.withColor(0x8A93A8)));
                return lines;
            });
            Button up = Button.icon(Sprites.UP, Component.translatable("skill_atlas.ui.menu.move_up"), () -> {
                n.levels.add(index - 1, n.levels.remove(index));
                touch(Section.LEVELS);
                rebuild();
            });
            up.enabled = i > 0;
            Button dup = Button.icon(Sprites.COPY, Component.translatable("skill_atlas.ui.editor.duplicate_level"), () -> {
                n.levels.add(index + 1, lv.copy());
                touch(Section.LEVELS);
                rebuild();
            });
            dup.enabled = n.levels.size() < SkillNode.MAX_LEVELS;
            Button del = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_level"), () -> {
                n.levels.remove(index);
                touch(Section.LEVELS);
                rebuild();
            });
            del.enabled = n.levels.size() > 1;
            f.cells(null, new float[]{9, 1, 1, 1}, open, up, dup, del);
        }
        Button addLevel = new Button(Component.translatable("skill_atlas.ui.editor.add_level"), () -> {
            n.levels.add(n.levels.get(n.levels.size() - 1).copy());
            touch(Section.LEVELS);
            rebuild();
        });
        addLevel.icon = Sprites.PLUS;
        addLevel.enabled = n.levels.size() < SkillNode.MAX_LEVELS;
        f.full(addLevel, 16);
        f.finish();

        Button cancel = new Button(Component.translatable("skill_atlas.ui.cancel"), this::close);
        save = new Button(Component.translatable("skill_atlas.ui.save"), this::save).style(Button.Style.PRIMARY);
        save.enabled = !saving;
        footer(cancel, save);
    }

    private void applyTo(SkillNode c) {
        if (applied.contains(Section.NAME)) {
            c.name = n.name;
            c.aliases = n.aliases;
        }
        if (applied.contains(Section.CATEGORY)) {
            c.category = n.category;
        }
        if (applied.contains(Section.ICON)) {
            c.icon = n.icon.copy();
        }
        if (applied.contains(Section.TOOLTIP)) {
            c.tooltip = n.tooltip;
        }
        if (applied.contains(Section.FLAGS)) {
            c.major = n.major;
            c.hidden = n.hidden;
            c.requireAll = n.requireAll;
        }
        if (applied.contains(Section.PREREQUISITES)) {
            c.prerequisites.clear();
            for (Prerequisite p : n.prerequisites) {
                if (!p.node.equals(c.id)) {
                    c.prerequisites.add(p.copy());
                }
            }
        }
        if (applied.contains(Section.LEVELS)) {
            c.levels.clear();
            for (SkillLevel l : n.levels) {
                c.levels.add(l.copy());
            }
        }
    }

    private void save() {
        if (saving) {
            return;
        }
        for (TextInput t : inputs) {
            if (t.enabled && t.valid != null && !t.valid.test(t.text())) {
                setStatus(Component.translatable("skill_atlas.ui.invalid_fields"), Theme.RED);
                Sounds.error();
                return;
            }
        }
        JsonArray ops = new JsonArray();
        try {
            if (kind == Kind.BATCH) {
                if (applied.isEmpty()) {
                    setStatus(Component.translatable("skill_atlas.ui.editor.nothing_applied"), Theme.RED);
                    Sounds.error();
                    return;
                }
                for (String id : ids) {
                    SkillNode cur = ClientAtlas.definition.nodes.get(id);
                    if (cur == null) {
                        continue;
                    }
                    SkillNode c = cur.copy();
                    applyTo(c);
                    SkillNode.fromJson(c.toJson());
                    ops.add(EditOps.nodeOp(c, false));
                }
            } else {
                SkillNode cur = ClientAtlas.definition.nodes.get(n.id);
                if (kind == Kind.EDIT && cur != null) {
                    n.x = cur.x;
                    n.y = cur.y;
                }
                SkillNode.fromJson(n.toJson());
                ops.add(EditOps.nodeOp(n, kind == Kind.CREATE));
            }
        } catch (AtlasException e) {
            setStatus(Names.error(e.key(), e.args()), Theme.RED);
            Sounds.error();
            return;
        }
        if (ops.isEmpty()) {
            close();
            return;
        }
        saving = true;
        save.enabled = false;
        setStatus(Component.translatable("skill_atlas.ui.saving"), Theme.MUTED);
        String id = n.id;
        ClientAtlas.edit(ops, r -> {
            saving = false;
            save.enabled = true;
            if (r.ok()) {
                close();
                if (kind == Kind.CREATE) {
                    ClientAtlas.SELECTION.clear();
                    ClientAtlas.SELECTION.add(id);
                }
                main.status(Component.translatable("skill_atlas.ui.saved"), Theme.TEAL);
            } else {
                setStatus(Names.error(r.key(), r.args()), Theme.RED);
                Sounds.error();
            }
        });
    }
}
