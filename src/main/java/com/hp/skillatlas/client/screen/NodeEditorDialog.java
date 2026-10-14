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
import com.hp.skillatlas.data.RewardOperation;
import com.hp.skillatlas.data.RewardType;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

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

    private static boolean registered(IForgeRegistry<?> registry, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(Json.normalizeResource(id));
        return rl != null && registry.containsKey(rl);
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

    private TextInput intField(int value, int min, int max, Section s, IntConsumer set) {
        TextInput t = new TextInput(String.valueOf(value)).max(11).filter(v -> v.matches("-?\\d*"));
        t.valid = v -> parseInt(v, min, max) != null;
        t.onChange = v -> {
            Integer p = parseInt(v, min, max);
            if (p != null) {
                set.accept(p);
            }
            touch(s);
        };
        inputs.add(t);
        return t;
    }

    private TextInput doubleField(double value, double min, double max, Section s, DoubleConsumer set) {
        TextInput t = new TextInput(Json.trim(value)).max(24).filter(v -> v.matches("-?\\d*(\\.\\d*)?([eE]-?\\d*)?"));
        t.valid = v -> parseDouble(v, min, max) != null;
        t.onChange = v -> {
            Double p = parseDouble(v, min, max);
            if (p != null) {
                set.accept(p);
            }
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
        hint(f, Component.translatable("skill_atlas.ui.editor.levels_hint"), Theme.DIM);
        for (int i = 0; i < n.levels.size(); i++) {
            SkillLevel lv = n.levels.get(i);
            int index = i;
            Label title = new Label(Component.translatable("skill_atlas.ui.editor.level", i + 1), Theme.GOLD);
            Label costLabel = new Label(Component.translatable("skill_atlas.ui.field.cost"), Theme.MUTED);
            TextInput cost = intField(lv.cost, 0, 1_000_000_000, Section.LEVELS, v -> lv.cost = v);
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
            f.cells(null, new float[]{3, 1.6F, 3, 1, 1, 1}, title, costLabel, cost, up, dup, del);
            for (int j = 0; j < lv.rewards.size(); j++) {
                rewardCard(f, lv, j);
            }
            for (int j = 0; j < lv.items.size(); j++) {
                itemCard(f, lv, j);
            }
            Button addReward = new Button(Component.translatable("skill_atlas.ui.editor.add_reward"), () -> {
                lv.rewards.add(new Reward());
                touch(Section.LEVELS);
                rebuild();
            });
            addReward.icon = Sprites.PLUS;
            addReward.enabled = lv.rewards.size() < SkillLevel.MAX_REWARDS;
            Button addItem = new Button(Component.translatable("skill_atlas.ui.editor.add_item"), () -> stack.push(PickerDialog.itemStacks(e -> {
                lv.items.add(new ItemCost(e.id, 1, e.nbt));
                touch(Section.LEVELS);
                rebuild();
            })));
            addItem.icon = Sprites.PLUS;
            addItem.enabled = lv.items.size() < SkillLevel.MAX_ITEMS;
            f.cells(Component.empty(), new float[]{1, 1}, addReward, addItem);
            f.gap(4);
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

    private void itemCard(Form f, SkillLevel lv, int j) {
        ItemCost c = lv.items.get(j);
        Runnable pickItem = () -> stack.push(PickerDialog.itemStacks(e -> {
            c.item = e.id;
            c.nbt = e.nbt;
            touch(Section.LEVELS);
            rebuild();
        }));
        ItemSlot slot = new ItemSlot(() -> new Icon(Icon.ITEM, c.item, c.nbt), pickItem);
        TextInput count = intField(c.count, 1, ItemCost.MAX_COUNT, Section.LEVELS, v -> c.count = v);
        count.tip(Component.translatable("skill_atlas.ui.field.item_count"));
        Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_item"), () -> {
            lv.items.remove(c);
            touch(Section.LEVELS);
            rebuild();
        });
        f.cells(Component.translatable("skill_atlas.ui.editor.item", j + 1), new float[]{8.2F, 1.8F, 1}, slot, count, rm);
    }

    private void rewardCard(Form f, SkillLevel lv, int j) {
        Reward r = lv.rewards.get(j);
        CycleButton<RewardType> type = new CycleButton<>(List.of(RewardType.values()), r.type, Names::rewardType, v -> {
            r.switchType(v);
            touch(Section.LEVELS);
            rebuild();
        });
        Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_reward"), () -> {
            lv.rewards.remove(r);
            touch(Section.LEVELS);
            rebuild();
        });
        f.cells(Component.translatable("skill_atlas.ui.editor.reward", j + 1), new float[]{8, 1}, type, rm);
        switch (r.type) {
            case ATTRIBUTE -> {
                TextInput target = text(r.target, 256, Section.LEVELS, v -> r.target = Json.normalizeResource(v));
                target.valid = v -> registered(ForgeRegistries.ATTRIBUTES, v);
                target.tip(() -> List.of(Names.attribute(r.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.attributes(id -> {
                    r.target = id;
                    touch(Section.LEVELS);
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.attribute"), new float[]{6, 2}, target, pick);
                TextInput amount = doubleField(r.amount, -1.0E6D, 1.0E6D, Section.LEVELS, v -> r.amount = v);
                CycleButton<RewardOperation> op = new CycleButton<>(List.of(RewardOperation.values()), r.operation, Names::operation, v -> {
                    r.operation = v;
                    touch(Section.LEVELS);
                });
                op.tip(() -> List.of(Names.operation(op.value()), Component.translatable("skill_atlas.operation." + op.value().key + ".desc").withStyle(s -> s.withColor(0x8A93A8))));
                f.cells(Component.translatable("skill_atlas.ui.field.amount"), new float[]{3, 5}, amount, op);
            }
            case EFFECT, IMMUNITY -> {
                TextInput target = text(r.target, 256, Section.LEVELS, v -> r.target = Json.normalizeResource(v));
                target.valid = v -> registered(ForgeRegistries.MOB_EFFECTS, v);
                target.tip(() -> List.of(Names.effect(r.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.effects(id -> {
                    r.target = id;
                    touch(Section.LEVELS);
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.effect"), new float[]{6, 2}, target, pick);
                if (r.type == RewardType.EFFECT) {
                    TextInput amp = intField(r.amplifier + 1, 1, 256, Section.LEVELS, v -> r.amplifier = v - 1);
                    amp.tip(Component.translatable("skill_atlas.ui.field.amplifier.desc"));
                    TextInput dur = intField(r.duration, 1, 1_000_000, Section.LEVELS, v -> r.duration = v);
                    dur.tip(Component.translatable("skill_atlas.ui.field.duration.desc"));
                    TextInput interval = intField(r.interval, 1, 72_000, Section.LEVELS, v -> r.interval = v);
                    interval.tip(Component.translatable("skill_atlas.ui.field.interval.desc"));
                    f.cells(Component.translatable("skill_atlas.ui.field.effect_params"), new float[]{1.7F, 2, 1.7F, 2.4F, 1.7F, 2.4F},
                            new Label(Component.translatable("skill_atlas.ui.field.amplifier"), Theme.MUTED), amp,
                            new Label(Component.translatable("skill_atlas.ui.field.duration"), Theme.MUTED), dur,
                            new Label(Component.translatable("skill_atlas.ui.field.interval"), Theme.MUTED), interval);
                    Checkbox particles = new Checkbox(Component.translatable("skill_atlas.ui.field.particles"), r.particles, v -> {
                        r.particles = v;
                        touch(Section.LEVELS);
                    });
                    Checkbox icon = new Checkbox(Component.translatable("skill_atlas.ui.field.effect_icon"), r.icon, v -> {
                        r.icon = v;
                        touch(Section.LEVELS);
                    });
                    f.cells(Component.empty(), new float[]{1, 1}, particles, icon);
                }
            }
            case COMMAND -> {
                TextInput cmd = text(r.target, 1024, Section.LEVELS, v -> {
                    String c = v.trim();
                    if (c.startsWith("/")) {
                        c = c.substring(1);
                    }
                    r.target = c;
                });
                cmd.placeholder(Component.translatable("skill_atlas.ui.field.command.placeholder"));
                cmd.valid = v -> !v.isBlank();
                f.row(Component.translatable("skill_atlas.ui.field.command"), cmd);
                hint(f, Component.translatable("skill_atlas.ui.field.command.desc"), Theme.DIM);
            }
        }
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
