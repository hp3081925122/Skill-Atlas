package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Checkbox;
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
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.RewardOperation;
import com.hp.skillatlas.data.RewardType;
import com.hp.skillatlas.data.SkillLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

public final class LevelDialog extends Dialog {
    private final SkillLevel lv;
    private final Consumer<SkillLevel> onSave;
    private final String pool;
    private final List<TextInput> inputs = new ArrayList<>();
    private ScrollGroup scroll;

    public LevelDialog(Component title, SkillLevel level, String pool, Consumer<SkillLevel> onSave) {
        super(title, 420, 320);
        this.lv = level.copy();
        this.onSave = onSave;
        this.pool = pool;
        this.lv.experience = level.usesExperience(pool.isEmpty());
    }

    private static boolean registered(IForgeRegistry<?> registry, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(Json.normalizeResource(id));
        return rl != null && registry.containsKey(rl);
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

    private TextInput text(String value, int max, Consumer<String> set) {
        TextInput t = new TextInput(value).max(max);
        t.onChange = set::accept;
        inputs.add(t);
        return t;
    }

    private TextInput intField(int value, int min, int max, IntConsumer set) {
        TextInput t = new TextInput(String.valueOf(value)).max(11).filter(v -> v.matches("-?\\d*"));
        t.valid = v -> NodeEditorDialog.parseInt(v, min, max) != null;
        t.onChange = v -> {
            Integer p = NodeEditorDialog.parseInt(v, min, max);
            if (p != null) {
                set.accept(p);
            }
        };
        inputs.add(t);
        return t;
    }

    private TextInput doubleField(double value, double min, double max, DoubleConsumer set) {
        TextInput t = new TextInput(Json.trim(value)).max(24).filter(v -> v.matches("-?\\d*(\\.\\d*)?([eE]-?\\d*)?"));
        t.valid = v -> NodeEditorDialog.parseDouble(v, min, max) != null;
        t.onChange = v -> {
            Double p = NodeEditorDialog.parseDouble(v, min, max);
            if (p != null) {
                set.accept(p);
            }
        };
        inputs.add(t);
        return t;
    }

    @Override
    protected void build() {
        inputs.clear();
        scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 74);
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.editor.levels_hint"), Theme.DIM);
        CycleButton<Boolean> costType = new CycleButton<>(List.of(false, true), lv.experience,
                experience -> Component.translatable(experience ? "skill_atlas.ui.cost_type.experience" : "skill_atlas.ui.cost_type.points"),
                experience -> lv.experience = experience);
        f.cells(Component.translatable("skill_atlas.ui.field.cost"), new float[]{3.5F, 6.5F}, costType, intField(lv.cost, 0, 1_000_000_000, v -> lv.cost = v));

        f.heading(Component.translatable("skill_atlas.ui.level.rewards"));
        if (lv.rewards.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.level.no_rewards"), Theme.MUTED), 12);
        }
        for (int j = 0; j < lv.rewards.size(); j++) {
            rewardCard(f, j);
        }
        Button addReward = new Button(Component.translatable("skill_atlas.ui.editor.add_reward"), () -> {
            lv.rewards.add(new Reward());
            rebuild();
        });
        addReward.icon = Sprites.PLUS;
        addReward.enabled = lv.rewards.size() < SkillLevel.MAX_REWARDS;
        f.full(addReward, 16);

        f.heading(Component.translatable("skill_atlas.ui.level.items"));
        if (lv.items.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.level.no_items"), Theme.MUTED), 12);
        }
        for (int j = 0; j < lv.items.size(); j++) {
            itemCard(f, j);
        }
        Button addItem = new Button(Component.translatable("skill_atlas.ui.editor.add_item"), () -> stack.push(PickerDialog.itemStacks(e -> {
            lv.items.add(new ItemCost(e.id, 1, e.nbt));
            rebuild();
        })));
        addItem.icon = Sprites.PLUS;
        addItem.enabled = lv.items.size() < SkillLevel.MAX_ITEMS;
        f.full(addItem, 16);
        f.finish();

        Button cancel = new Button(Component.translatable("skill_atlas.ui.cancel"), this::close);
        Button ok = new Button(Component.translatable("skill_atlas.ui.ok"), this::confirm).style(Button.Style.PRIMARY);
        footer(cancel, ok);
    }

    private void confirm() {
        if (!lv.experience && pool.isEmpty() && lv.cost > 0) {
            setStatus(Component.translatable("skill_atlas.ui.level.no_pool"), Theme.RED);
            Sounds.error();
            return;
        }
        for (TextInput t : inputs) {
            if (t.enabled && t.valid != null && !t.valid.test(t.text())) {
                setStatus(Component.translatable("skill_atlas.ui.invalid_fields"), Theme.RED);
                Sounds.error();
                return;
            }
        }
        close();
        onSave.accept(lv);
    }

    private void itemCard(Form f, int j) {
        ItemCost c = lv.items.get(j);
        ItemSlot slot = new ItemSlot(() -> new Icon(Icon.ITEM, c.item, c.nbt), () -> stack.push(PickerDialog.itemStacks(e -> {
            c.item = e.id;
            c.nbt = e.nbt;
            rebuild();
        })));
        TextInput count = intField(c.count, 1, ItemCost.MAX_COUNT, v -> c.count = v);
        count.tip(Component.translatable("skill_atlas.ui.field.item_count"));
        Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_item"), () -> {
            lv.items.remove(c);
            rebuild();
        });
        f.cells(Component.translatable("skill_atlas.ui.editor.item", j + 1), new float[]{8.2F, 1.8F, 1}, slot, count, rm);
    }

    private void rewardCard(Form f, int j) {
        Reward r = lv.rewards.get(j);
        CycleButton<RewardType> type = new CycleButton<>(List.of(RewardType.values()), r.type, Names::rewardType, v -> {
            r.switchType(v);
            rebuild();
        });
        Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_reward"), () -> {
            lv.rewards.remove(r);
            rebuild();
        });
        f.cells(Component.translatable("skill_atlas.ui.editor.reward", j + 1), new float[]{8, 1}, type, rm);
        switch (r.type) {
            case ATTRIBUTE -> {
                TextInput target = text(r.target, 256, v -> r.target = Json.normalizeResource(v));
                target.valid = v -> registered(ForgeRegistries.ATTRIBUTES, v);
                target.tip(() -> List.of(Names.attribute(r.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.attributes(id -> {
                    r.target = id;
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.attribute"), new float[]{6, 2}, target, pick);
                TextInput amount = doubleField(r.amount, -1.0E6D, 1.0E6D, v -> r.amount = v);
                CycleButton<RewardOperation> op = new CycleButton<>(List.of(RewardOperation.values()), r.operation, Names::operation, v -> r.operation = v);
                op.tip(() -> List.of(Names.operation(op.value()), Component.translatable("skill_atlas.operation." + op.value().key + ".desc").withStyle(s -> s.withColor(0x8A93A8))));
                f.cells(Component.translatable("skill_atlas.ui.field.amount"), new float[]{3, 5}, amount, op);
            }
            case EFFECT, IMMUNITY -> {
                TextInput target = text(r.target, 256, v -> r.target = Json.normalizeResource(v));
                target.valid = v -> registered(ForgeRegistries.MOB_EFFECTS, v);
                target.tip(() -> List.of(Names.effect(r.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.effects(id -> {
                    r.target = id;
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.effect"), new float[]{6, 2}, target, pick);
                if (r.type == RewardType.EFFECT) {
                    TextInput amp = intField(r.amplifier + 1, 1, 256, v -> r.amplifier = v - 1);
                    amp.tip(Component.translatable("skill_atlas.ui.field.amplifier.desc"));
                    TextInput dur = intField(r.duration, 1, 1_000_000, v -> r.duration = v);
                    dur.tip(Component.translatable("skill_atlas.ui.field.duration.desc"));
                    TextInput interval = intField(r.interval, 1, 72_000, v -> r.interval = v);
                    interval.tip(Component.translatable("skill_atlas.ui.field.interval.desc"));
                    f.cells(Component.translatable("skill_atlas.ui.field.effect_params"), new float[]{1.7F, 2, 1.7F, 2.4F, 1.7F, 2.4F},
                            new Label(Component.translatable("skill_atlas.ui.field.amplifier"), Theme.MUTED), amp,
                            new Label(Component.translatable("skill_atlas.ui.field.duration"), Theme.MUTED), dur,
                            new Label(Component.translatable("skill_atlas.ui.field.interval"), Theme.MUTED), interval);
                    Checkbox particles = new Checkbox(Component.translatable("skill_atlas.ui.field.particles"), r.particles, v -> r.particles = v);
                    Checkbox icon = new Checkbox(Component.translatable("skill_atlas.ui.field.effect_icon"), r.icon, v -> r.icon = v);
                    f.cells(Component.empty(), new float[]{1, 1}, particles, icon);
                }
            }
            case COMMAND -> {
                TextInput cmd = text(r.target, 1024, v -> {
                    String c = v.trim();
                    if (c.startsWith("/")) {
                        c = c.substring(1);
                    }
                    r.target = c;
                });
                cmd.placeholder(Component.translatable("skill_atlas.ui.field.command.placeholder"));
                cmd.valid = v -> !v.isBlank();
                f.row(Component.translatable("skill_atlas.ui.field.command"), cmd);
                NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.field.command.desc"), Theme.DIM);
            }
        }
    }
}
