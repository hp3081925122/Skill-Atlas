package com.hp.skillatlas.client.screen;

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
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.LevelFormula;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.RewardOperation;
import com.hp.skillatlas.data.RewardType;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class FormulaDialog extends Dialog {
    private static final String[][] CHIPS = {
            {"lv", "lv", "lv"},
            {"max", "max", "max"},
            {"^", "^", "pow"},
            {"sqrt", "sqrt(lv)", "sqrt"},
            {"log", "log(lv, 2)", "log"},
            {"ln", "ln(lv)", "ln"},
            {"floor", "floor()", "floor"},
            {"ceil", "ceil()", "ceil"},
            {"round", "round()", "round"},
            {"if", "if(lv >= 5, lv, 0)", "if"}
    };

    private static final class PreviewLine extends Widget {
        private Component text = Component.empty();
        private int color = Theme.TEAL;

        PreviewLine() {
            this.h = 11;
        }

        void set(Component text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public void render(float pt) {
            if (text == null || text.getString().isEmpty()) {
                return;
            }
            Component shown = Gfx.ellipsize(text, w);
            Gfx.text(shown.getVisualOrderText(), x, y + 1, color, true);
        }

        @Override
        public List<Component> tooltip(double mx, double my) {
            if (text == null || text.getString().isEmpty() || Gfx.width(text) <= w) {
                return null;
            }
            return List.of(text);
        }
    }

    private static final class PreviewCard extends Widget {
        private final Component leftHeader;
        private final Component rightHeader;
        private final List<Component> bodyLines;

        PreviewCard(Component leftHeader, Component rightHeader, List<Component> bodyLines, float width) {
            this.leftHeader = leftHeader;
            this.rightHeader = rightHeader;
            this.bodyLines = bodyLines;
            this.w = width;
            float inner = Math.max(40, width - 10);
            float height = 16;
            for (Component line : bodyLines) {
                int wrapped = Math.max(1, Gfx.wrap(line, inner).size());
                height += wrapped * 10;
            }
            this.h = height + 4;
        }

        @Override
        public void render(float pt) {
            Gfx.sprite(Sprites.INSET, x, y, w, h);
            Gfx.text(leftHeader.getVisualOrderText(), x + 5, y + 4, Theme.GOLD, true);
            float rw = Gfx.width(rightHeader);
            Gfx.text(rightHeader.getVisualOrderText(), x + w - rw - 5, y + 4, Theme.TEXT, true);
            float inner = Math.max(40, w - 10);
            float cy = y + 16;
            for (Component line : bodyLines) {
                for (var seq : Gfx.wrap(line, inner)) {
                    Gfx.text(seq, x + 5, cy, Theme.TEXT, true);
                    cy += 10;
                }
            }
        }
    }

    private final LevelFormula.Spec spec;
    private final String pool;
    private final Consumer<List<SkillLevel>> onApply;
    private final List<TextInput> inputs = new ArrayList<>();
    private final Set<TextInput> formulaInputs = new HashSet<>();
    private final Set<TextInput> commandInputs = new HashSet<>();
    private final List<PreviewLine> rewardPreviews = new ArrayList<>();
    private final List<PreviewLine> itemPreviews = new ArrayList<>();
    private PreviewLine costPreview;
    private TextInput maxLevelInput;
    private TextInput costInput;
    private TextInput lastFormulaInput;
    private Button previewTabBtn;
    private Button applyBtn;
    private ScrollGroup scroll;
    private boolean previewTab;
    private boolean maxLevelValid = true;
    private String maxLevelText;

    public FormulaDialog(Component title, List<SkillLevel> existing, String pool, Consumer<List<SkillLevel>> onApply) {
        super(title, 440, 340);
        this.spec = LevelFormula.Spec.fromLevels(existing, pool.isEmpty());
        this.pool = pool;
        this.onApply = onApply;
        this.maxLevelText = String.valueOf(this.spec.maxLevel);
    }

    private static boolean registered(Registry<?> registry, String id) {
        Identifier rl = Identifier.tryParse(Json.normalizeResource(id));
        return rl != null && registry.containsKey(rl);
    }

    private void rebuild() {
        Ui.focus(null);
        float offset = 0;
        if (scroll != null) {
            offset = scroll.scrollOffset();
        }
        layout(stack.width, stack.height);
        if (scroll != null) {
            scroll.scrollTo(offset);
        }
    }

    private void switchTab(boolean toPreview) {
        if (previewTab == toPreview) {
            return;
        }
        previewTab = toPreview;
        Ui.focus(null);
        layout(stack.width, stack.height);
    }

    private TextInput textField(String value, int max, Consumer<String> set) {
        TextInput t = new TextInput(value).max(max);
        t.onChange = v -> {
            set.accept(v);
            refreshState();
        };
        inputs.add(t);
        return t;
    }

    private TextInput formulaField(String value, boolean optional, Consumer<String> set) {
        TextInput t = new TextInput(value).max(LevelFormula.MAX_EXPR_LENGTH);
        t.valid = v -> isFormulaSyntaxValid(v, optional);
        t.onChange = v -> {
            set.accept(v);
            refreshState();
        };
        inputs.add(t);
        formulaInputs.add(t);
        return t;
    }

    private static boolean isFormulaSyntaxValid(String raw, boolean optional) {
        if (optional && (raw == null || raw.trim().isEmpty())) {
            return true;
        }
        try {
            LevelFormula.parse(raw);
            return true;
        } catch (LevelFormula.FormulaException e) {
            return false;
        }
    }

    private static boolean isCommandSyntaxValid(String raw) {
        try {
            LevelFormula.validateCommandTemplate(raw);
            return true;
        } catch (LevelFormula.FormulaException e) {
            return false;
        }
    }

    private void insertChip(String snippet) {
        TextInput target = lastFormulaInput;
        if (Ui.focus instanceof TextInput focused && (formulaInputs.contains(focused) || commandInputs.contains(focused))) {
            target = focused;
        }
        if (target == null || !inputs.contains(target)) {
            target = costInput;
        }
        if (target == null) {
            return;
        }
        String insert = snippet;
        if (commandInputs.contains(target) && !"^".equals(snippet)) {
            insert = "${" + snippet + "}";
        }
        Ui.focus(target);
        target.insert(insert);
        lastFormulaInput = target;
    }

    @Override
    protected void build() {
        inputs.clear();
        formulaInputs.clear();
        commandInputs.clear();
        rewardPreviews.clear();
        itemPreviews.clear();
        costPreview = null;
        costInput = null;
        maxLevelInput = null;

        float tabGap = 4;
        float tabW = (contentW() - tabGap) / 2.0F;
        Button editTabBtn = add(new Button(Component.translatable("skill_atlas.ui.formula.tab.edit"), () -> switchTab(false)).active(() -> !previewTab));
        editTabBtn.bounds(contentX(), contentY(), tabW, 16);
        previewTabBtn = add(new Button(Component.translatable("skill_atlas.ui.formula.tab.preview", spec.maxLevel), () -> switchTab(true)).active(() -> previewTab));
        previewTabBtn.bounds(contentX() + tabW + tabGap, contentY(), tabW, 16);

        float scrollTop = contentY() + 20;
        if (!previewTab) {
            buildChipBar(contentY() + 20);
            scrollTop = contentY() + 39;
        }

        scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), scrollTop, contentW(), contentY() + contentH() - scrollTop);

        if (previewTab) {
            buildPreviewPane();
        } else {
            buildEditPane();
        }

        Button cancel = new Button(Component.translatable("skill_atlas.ui.cancel"), this::close);
        applyBtn = new Button(Component.translatable("skill_atlas.ui.formula.apply"), this::confirm).style(Button.Style.PRIMARY);
        footer(cancel, applyBtn);
        refreshState();
    }

    private void buildChipBar(float top) {
        float gap = 2;
        float cw = (contentW() - gap * (CHIPS.length - 1)) / CHIPS.length;
        float cx = contentX();
        for (String[] chip : CHIPS) {
            String label = chip[0];
            String snippet = chip[1];
            String tipKey = "skill_atlas.ui.formula.chip." + chip[2];
            Button btn = add(new Button(Component.literal(label), () -> insertChip(snippet)));
            btn.tip(Component.translatable(tipKey));
            btn.bounds(cx, top, cw, 15);
            cx += cw + gap;
        }
    }

    private void buildEditPane() {
        Form f = new Form(scroll, 74);
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.formula.hint"), Theme.DIM);

        f.heading(Component.translatable("skill_atlas.ui.formula.section.base"));
        maxLevelInput = new TextInput(maxLevelText).max(3).filter(v -> v.matches("[0-9]*"));
        maxLevelInput.valid = v -> NodeEditorDialog.parseInt(v, 1, SkillNode.MAX_LEVELS) != null;
        maxLevelInput.onChange = v -> {
            maxLevelText = v;
            Integer parsed = NodeEditorDialog.parseInt(v, 1, SkillNode.MAX_LEVELS);
            if (parsed != null) {
                spec.maxLevel = parsed;
                maxLevelValid = true;
            } else {
                maxLevelValid = false;
            }
            refreshState();
        };
        maxLevelInput.tip(Component.translatable("skill_atlas.ui.formula.max_level.desc", SkillNode.MAX_LEVELS));
        inputs.add(maxLevelInput);
        f.row(Component.translatable("skill_atlas.ui.formula.max_level"), maxLevelInput);

        CycleButton<Boolean> costType = new CycleButton<>(List.of(false, true), spec.experience,
                exp -> {
                    if (exp) {
                        return Component.translatable("skill_atlas.ui.cost_type.experience");
                    }
                    return Component.translatable("skill_atlas.ui.cost_type.points");
                },
                exp -> {
                    spec.experience = exp;
                    refreshState();
                });
        costInput = formulaField(spec.costFormula, false, v -> spec.costFormula = v);
        costInput.placeholder(Component.translatable("skill_atlas.ui.formula.cost.placeholder"));
        f.cells(Component.translatable("skill_atlas.ui.field.cost"), new float[]{3.4F, 6.6F}, costType, costInput);
        costPreview = addPreviewLine(f);

        f.heading(Component.translatable("skill_atlas.ui.formula.section.rewards"));
        if (spec.rewards.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.level.no_rewards"), Theme.MUTED), 12);
        }
        for (int i = 0; i < spec.rewards.size(); i++) {
            buildRewardCard(f, i);
        }
        Button addReward = new Button(Component.translatable("skill_atlas.ui.editor.add_reward"), () -> {
            spec.rewards.add(new LevelFormula.RewardTemplate());
            rebuild();
        });
        addReward.icon = Sprites.PLUS;
        addReward.enabled = spec.rewards.size() < SkillLevel.MAX_REWARDS;
        f.full(addReward, 16);

        f.heading(Component.translatable("skill_atlas.ui.formula.section.items"));
        if (spec.items.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.level.no_items"), Theme.MUTED), 12);
        }
        for (int i = 0; i < spec.items.size(); i++) {
            buildItemCard(f, i);
        }
        Button addItem = new Button(Component.translatable("skill_atlas.ui.editor.add_item"), () -> stack.push(PickerDialog.itemStacks(e -> {
            spec.items.add(new LevelFormula.ItemTemplate(e.id, e.nbt, "lv"));
            rebuild();
        })));
        addItem.icon = Sprites.PLUS;
        addItem.enabled = spec.items.size() < SkillLevel.MAX_ITEMS;
        f.full(addItem, 16);
        f.finish();
    }

    private PreviewLine addPreviewLine(Form f) {
        PreviewLine line = new PreviewLine();
        f.at(line, f.fieldX(), f.y() - 2, f.fieldW(), 11);
        f.advance(12);
        return line;
    }

    private void buildRewardCard(Form f, int index) {
        LevelFormula.RewardTemplate rt = spec.rewards.get(index);
        CycleButton<RewardType> type = new CycleButton<>(List.of(RewardType.values()), rt.type, Names::rewardType, v -> {
            rt.switchType(v);
            rebuild();
        });
        Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_reward"), () -> {
            spec.rewards.remove(rt);
            rebuild();
        });
        f.cells(Component.translatable("skill_atlas.ui.editor.reward", index + 1), new float[]{8, 1}, type, rm);
        switch (rt.type) {
            case ATTRIBUTE -> {
                TextInput target = textField(rt.target, 256, v -> rt.target = Json.normalizeResource(v));
                target.valid = v -> registered(BuiltInRegistries.ATTRIBUTE, v);
                target.tip(() -> List.of(Names.attribute(rt.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.attributes(id -> {
                    rt.target = id;
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.attribute"), new float[]{6, 2}, target, pick);
                TextInput amount = formulaField(rt.amountFormula, false, v -> rt.amountFormula = v);
                amount.placeholder(Component.translatable("skill_atlas.ui.formula.amount.placeholder"));
                CycleButton<RewardOperation> op = new CycleButton<>(List.of(RewardOperation.values()), rt.operation, Names::operation, v -> {
                    rt.operation = v;
                    refreshState();
                });
                op.tip(() -> List.of(Names.operation(op.value()), Component.translatable("skill_atlas.operation." + op.value().key + ".desc").withStyle(s -> s.withColor(0x8A93A8))));
                f.cells(Component.translatable("skill_atlas.ui.formula.amount_formula"), new float[]{5.5F, 3.5F}, amount, op);
            }
            case EFFECT -> {
                TextInput target = textField(rt.target, 256, v -> rt.target = Json.normalizeResource(v));
                target.valid = v -> registered(BuiltInRegistries.MOB_EFFECT, v);
                target.tip(() -> List.of(Names.effect(rt.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.effects(id -> {
                    rt.target = id;
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.effect"), new float[]{6, 2}, target, pick);
                TextInput amp = formulaField(rt.amplifierFormula, false, v -> rt.amplifierFormula = v);
                amp.tip(Component.translatable("skill_atlas.ui.formula.amplifier.desc"));
                TextInput dur = formulaField(rt.durationFormula, false, v -> rt.durationFormula = v);
                dur.tip(Component.translatable("skill_atlas.ui.field.duration.desc"));
                TextInput interval = formulaField(rt.intervalFormula, false, v -> rt.intervalFormula = v);
                interval.tip(Component.translatable("skill_atlas.ui.field.interval.desc"));
                f.cells(Component.translatable("skill_atlas.ui.field.effect_params"), new float[]{1.5F, 3.0F, 1.5F, 2.2F, 1.5F, 2.2F},
                        new Label(Component.translatable("skill_atlas.ui.field.amplifier"), Theme.MUTED), amp,
                        new Label(Component.translatable("skill_atlas.ui.field.duration"), Theme.MUTED), dur,
                        new Label(Component.translatable("skill_atlas.ui.field.interval"), Theme.MUTED), interval);
                Checkbox particles = new Checkbox(Component.translatable("skill_atlas.ui.field.particles"), rt.particles, v -> rt.particles = v);
                Checkbox icon = new Checkbox(Component.translatable("skill_atlas.ui.field.effect_icon"), rt.icon, v -> rt.icon = v);
                f.cells(Component.empty(), new float[]{1, 1}, particles, icon);
            }
            case IMMUNITY -> {
                TextInput target = textField(rt.target, 256, v -> rt.target = Json.normalizeResource(v));
                target.valid = v -> registered(BuiltInRegistries.MOB_EFFECT, v);
                target.tip(() -> List.of(Names.effect(rt.target)));
                Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.effects(id -> {
                    rt.target = id;
                    rebuild();
                })));
                f.cells(Component.translatable("skill_atlas.ui.field.effect"), new float[]{6, 2}, target, pick);
                TextInput cond = formulaField(rt.conditionFormula, true, v -> rt.conditionFormula = v);
                cond.placeholder(Component.translatable("skill_atlas.ui.formula.condition.placeholder"));
                cond.tip(Component.translatable("skill_atlas.ui.formula.condition.desc"));
                f.row(Component.translatable("skill_atlas.ui.formula.condition"), cond);
            }
            case COMMAND -> {
                TextInput cmd = textField(rt.target, 1024, v -> {
                    String c = v.trim();
                    if (c.startsWith("/")) {
                        c = c.substring(1);
                    }
                    rt.target = c;
                });
                cmd.placeholder(Component.translatable("skill_atlas.ui.formula.command.placeholder"));
                cmd.valid = FormulaDialog::isCommandSyntaxValid;
                cmd.tip(Component.translatable("skill_atlas.ui.formula.command.desc"));
                commandInputs.add(cmd);
                f.row(Component.translatable("skill_atlas.ui.field.command"), cmd);
                TextInput cond = formulaField(rt.conditionFormula, true, v -> rt.conditionFormula = v);
                cond.placeholder(Component.translatable("skill_atlas.ui.formula.condition.placeholder"));
                cond.tip(Component.translatable("skill_atlas.ui.formula.condition.desc"));
                f.row(Component.translatable("skill_atlas.ui.formula.condition"), cond);
            }
        }
        rewardPreviews.add(addPreviewLine(f));
    }

    private void buildItemCard(Form f, int index) {
        LevelFormula.ItemTemplate it = spec.items.get(index);
        ItemSlot slot = new ItemSlot(() -> new Icon(Icon.ITEM, it.item, it.nbt), () -> stack.push(PickerDialog.itemStacks(e -> {
            it.item = e.id;
            it.nbt = e.nbt;
            rebuild();
        })));
        TextInput count = formulaField(it.countFormula, false, v -> it.countFormula = v);
        count.placeholder(Component.translatable("skill_atlas.ui.formula.item_count.placeholder"));
        count.tip(Component.translatable("skill_atlas.ui.formula.item_count.desc"));
        Button rm = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.editor.remove_item"), () -> {
            spec.items.remove(it);
            rebuild();
        });
        f.cells(Component.translatable("skill_atlas.ui.editor.item", index + 1), new float[]{4.5F, 4.5F, 1}, slot, count, rm);
        itemPreviews.add(addPreviewLine(f));
    }

    private void buildPreviewPane() {
        float width = scroll.innerWidth() - 4;
        float cy = scroll.y + 2;
        Component err = validateSpecOnly();
        if (err != null) {
            Label l = new Label(err, Theme.RED).wrapped(width);
            l.bounds(scroll.x + 2, cy + 4, width, l.h);
            scroll.add(l);
            scroll.rebuilt();
            return;
        }
        List<SkillLevel> levels;
        try {
            levels = LevelFormula.generate(spec);
        } catch (LevelFormula.FormulaException e) {
            Label l = new Label(errorMessage(e), Theme.RED).wrapped(width);
            l.bounds(scroll.x + 2, cy + 4, width, l.h);
            scroll.add(l);
            scroll.rebuilt();
            return;
        }
        for (int i = 0; i < levels.size(); i++) {
            SkillLevel lv = levels.get(i);
            Component left = Component.translatable("skill_atlas.ui.editor.level", i + 1);
            Component right;
            if (lv.usesExperience(pool.isEmpty())) {
                right = Component.translatable("skill_atlas.ui.experience_cost", lv.cost);
            } else {
                right = Component.translatable("skill_atlas.ui.point_cost", lv.cost, Names.pool(pool));
            }
            List<Component> lines = new ArrayList<>();
            if (lv.rewards.isEmpty()) {
                lines.add(Component.translatable("skill_atlas.ui.no_rewards").withStyle(s -> s.withColor(0x8A93A8)));
            } else {
                MutableComponent joined = Component.empty();
                for (int r = 0; r < lv.rewards.size(); r++) {
                    if (r > 0) {
                        joined.append(Component.literal(" · ").withStyle(s -> s.withColor(0x5A6478)));
                    }
                    joined.append(Names.reward(lv.rewards.get(r), true));
                }
                lines.add(joined);
            }
            if (!lv.items.isEmpty()) {
                MutableComponent itemsLine = Component.translatable("skill_atlas.ui.formula.preview_items").withStyle(s -> s.withColor(0x8A93A8));
                for (int c = 0; c < lv.items.size(); c++) {
                    if (c > 0) {
                        itemsLine.append(Component.literal(", "));
                    }
                    ItemCost cost = lv.items.get(c);
                    itemsLine.append(Names.item(cost)).append(Component.literal(" ×" + cost.count));
                }
                lines.add(itemsLine);
            }
            PreviewCard card = new PreviewCard(left, right, lines, width);
            card.bounds(scroll.x + 2, cy, width, card.h);
            scroll.add(card);
            cy += card.h + 3;
        }
        scroll.rebuilt();
    }

    private Component validateSpecOnly() {
        if (!maxLevelValid) {
            return Component.translatable("skill_atlas.ui.formula.err.max_level", SkillNode.MAX_LEVELS);
        }
        for (LevelFormula.RewardTemplate rt : spec.rewards) {
            if (rt.type == RewardType.ATTRIBUTE && !registered(BuiltInRegistries.ATTRIBUTE, rt.target)) {
                return Component.translatable("skill_atlas.error.unknown_attribute", rt.target);
            }
            if ((rt.type == RewardType.EFFECT || rt.type == RewardType.IMMUNITY) && !registered(BuiltInRegistries.MOB_EFFECT, rt.target)) {
                return Component.translatable("skill_atlas.error.unknown_effect", rt.target);
            }
        }
        return null;
    }

    private static Component errorMessage(LevelFormula.FormulaException e) {
        return Names.error(e.key(), e.args());
    }

    private static int[] sampleIndices(int max) {
        if (max <= 4) {
            int[] out = new int[max];
            for (int i = 0; i < max; i++) {
                out[i] = i + 1;
            }
            return out;
        }
        return new int[]{1, 2, 3, max};
    }

    private void refreshState() {
        if (previewTabBtn != null) {
            previewTabBtn.label = Component.translatable("skill_atlas.ui.formula.tab.preview", spec.maxLevel);
        }
        Component firstError = validateSpecOnly();
        int max = spec.maxLevel;
        int[] samples = sampleIndices(max);

        if (costPreview != null) {
            if (!maxLevelValid) {
                costPreview.set(Component.translatable("skill_atlas.ui.formula.err.max_level", SkillNode.MAX_LEVELS), Theme.RED);
            } else {
                try {
                    LevelFormula.Expr expr = LevelFormula.parse(spec.costFormula);
                    boolean anyPaidPoints = false;
                    for (int lv = 1; lv <= max; lv++) {
                        int val = LevelFormula.evalIntRange(expr, lv, max, 0, LevelFormula.MAX_COST);
                        if (!spec.experience && pool.isEmpty() && val > 0) {
                            anyPaidPoints = true;
                        }
                    }
                    if (anyPaidPoints) {
                        Component err = Component.translatable("skill_atlas.ui.level.no_pool");
                        costPreview.set(err, Theme.RED);
                        if (firstError == null) {
                            firstError = err;
                        }
                    } else {
                        StringBuilder sb = new StringBuilder("→ ");
                        for (int i = 0; i < samples.length; i++) {
                            if (i > 0) {
                                if (max > 4 && i == 3) {
                                    sb.append(" · … · ");
                                } else {
                                    sb.append(" · ");
                                }
                            }
                            int lv = samples[i];
                            int val = LevelFormula.evalIntRange(expr, lv, max, 0, LevelFormula.MAX_COST);
                            sb.append("Lv").append(lv).append(": ").append(val);
                        }
                        costPreview.set(Component.literal(sb.toString()), Theme.TEAL);
                    }
                } catch (LevelFormula.FormulaException e) {
                    Component err = errorMessage(e);
                    costPreview.set(err, Theme.RED);
                    if (firstError == null) {
                        firstError = err;
                    }
                }
            }
        }

        for (int i = 0; i < rewardPreviews.size() && i < spec.rewards.size(); i++) {
            PreviewLine line = rewardPreviews.get(i);
            LevelFormula.RewardTemplate rt = spec.rewards.get(i);
            if (!maxLevelValid) {
                line.set(Component.empty(), Theme.MUTED);
                continue;
            }
            Component err = updateRewardPreview(rt, line, max, samples);
            if (err != null && firstError == null) {
                firstError = err;
            }
        }

        for (int i = 0; i < itemPreviews.size() && i < spec.items.size(); i++) {
            PreviewLine line = itemPreviews.get(i);
            LevelFormula.ItemTemplate it = spec.items.get(i);
            if (!maxLevelValid) {
                line.set(Component.empty(), Theme.MUTED);
                continue;
            }
            Component err = updateItemPreview(it, line, max, samples);
            if (err != null && firstError == null) {
                firstError = err;
            }
        }

        if (firstError == null) {
            try {
                List<SkillLevel> generated = LevelFormula.generate(spec);
                for (SkillLevel lv : generated) {
                    if (!lv.experience && pool.isEmpty() && lv.cost > 0) {
                        firstError = Component.translatable("skill_atlas.ui.level.no_pool");
                        break;
                    }
                }
            } catch (LevelFormula.FormulaException e) {
                firstError = errorMessage(e);
            }
        }

        if (applyBtn != null) {
            applyBtn.enabled = firstError == null;
        }
        if (firstError != null) {
            setStatus(firstError, Theme.RED);
        } else {
            setStatus(Component.translatable("skill_atlas.ui.formula.valid", max), Theme.GREEN);
        }
    }

    private Component updateRewardPreview(LevelFormula.RewardTemplate rt, PreviewLine line, int max, int[] samples) {
        try {
            if (rt.type == RewardType.ATTRIBUTE) {
                if (!registered(BuiltInRegistries.ATTRIBUTE, rt.target)) {
                    Component err = Component.translatable("skill_atlas.error.unknown_attribute", rt.target);
                    line.set(err, Theme.RED);
                    return err;
                }
                LevelFormula.Expr expr = LevelFormula.parse(rt.amountFormula);
                for (int lv = 1; lv <= max; lv++) {
                    LevelFormula.evalDoubleRange(expr, lv, max, LevelFormula.MIN_AMOUNT, LevelFormula.MAX_AMOUNT);
                }
                StringBuilder sb = new StringBuilder("→ ");
                for (int i = 0; i < samples.length; i++) {
                    if (i > 0) {
                        if (max > 4 && i == 3) {
                            sb.append(" · … · ");
                        } else {
                            sb.append(" · ");
                        }
                    }
                    int lv = samples[i];
                    double val = LevelFormula.evalDoubleRange(expr, lv, max, LevelFormula.MIN_AMOUNT, LevelFormula.MAX_AMOUNT);
                    sb.append("Lv").append(lv).append(": ").append(LevelFormula.formatCompact(val));
                }
                line.set(Component.literal(sb.toString()), Theme.TEAL);
                return null;
            }
            if (rt.type == RewardType.EFFECT) {
                if (!registered(BuiltInRegistries.MOB_EFFECT, rt.target)) {
                    Component err = Component.translatable("skill_atlas.error.unknown_effect", rt.target);
                    line.set(err, Theme.RED);
                    return err;
                }
                LevelFormula.Expr ampExpr = LevelFormula.parse(rt.amplifierFormula);
                LevelFormula.Expr durExpr = LevelFormula.parse(rt.durationFormula);
                LevelFormula.Expr intExpr = LevelFormula.parse(rt.intervalFormula);
                for (int lv = 1; lv <= max; lv++) {
                    int effLv = LevelFormula.evalEffectLevel(ampExpr, lv, max);
                    if (effLv > 0) {
                        LevelFormula.evalIntRange(durExpr, lv, max, 1, LevelFormula.MAX_DURATION);
                        LevelFormula.evalIntRange(intExpr, lv, max, 1, LevelFormula.MAX_INTERVAL);
                    }
                }
                String skip = Component.translatable("skill_atlas.ui.formula.skip").getString();
                StringBuilder sb = new StringBuilder("→ ");
                for (int i = 0; i < samples.length; i++) {
                    if (i > 0) {
                        if (max > 4 && i == 3) {
                            sb.append(" · … · ");
                        } else {
                            sb.append(" · ");
                        }
                    }
                    int lv = samples[i];
                    int effLv = LevelFormula.evalEffectLevel(ampExpr, lv, max);
                    sb.append("Lv").append(lv).append(": ");
                    if (effLv <= 0) {
                        sb.append(skip);
                    } else {
                        sb.append(Names.amplifier(effLv - 1).getString());
                    }
                }
                line.set(Component.literal(sb.toString()), Theme.TEAL);
                return null;
            }
            if (rt.type == RewardType.IMMUNITY) {
                if (!registered(BuiltInRegistries.MOB_EFFECT, rt.target)) {
                    Component err = Component.translatable("skill_atlas.error.unknown_effect", rt.target);
                    line.set(err, Theme.RED);
                    return err;
                }
                LevelFormula.Expr cond = null;
                if (rt.conditionFormula != null && !rt.conditionFormula.trim().isEmpty()) {
                    cond = LevelFormula.parse(rt.conditionFormula);
                }
                int active = 0;
                int firstLv = 0;
                for (int lv = 1; lv <= max; lv++) {
                    if (LevelFormula.evalCondition(cond, lv, max)) {
                        active++;
                        if (firstLv == 0) {
                            firstLv = lv;
                        }
                    }
                }
                if (cond == null) {
                    line.set(Component.translatable("skill_atlas.ui.formula.all_levels"), Theme.TEAL);
                } else {
                    line.set(Component.translatable("skill_atlas.ui.formula.active_count", active, max), Theme.TEAL);
                }
                return null;
            }
            LevelFormula.validateCommandTemplate(rt.target);
            LevelFormula.Expr cond = null;
            if (rt.conditionFormula != null && !rt.conditionFormula.trim().isEmpty()) {
                cond = LevelFormula.parse(rt.conditionFormula);
            }
            int active = 0;
            String sampleCmd = "";
            for (int lv = 1; lv <= max; lv++) {
                if (LevelFormula.evalCondition(cond, lv, max)) {
                    String cmd = LevelFormula.formatCommand(rt.target, lv, max);
                    if (active == 0) {
                        sampleCmd = "Lv" + lv + ": /" + cmd;
                    }
                    active++;
                }
            }
            if (active == 0) {
                line.set(Component.translatable("skill_atlas.ui.formula.active_count", 0, max), Theme.MUTED);
            } else {
                line.set(Component.literal("→ " + sampleCmd), Theme.TEAL);
            }
            return null;
        } catch (LevelFormula.FormulaException e) {
            Component err = errorMessage(e);
            line.set(err, Theme.RED);
            return err;
        }
    }

    private Component updateItemPreview(LevelFormula.ItemTemplate it, PreviewLine line, int max, int[] samples) {
        try {
            LevelFormula.Expr expr = LevelFormula.parse(it.countFormula);
            for (int lv = 1; lv <= max; lv++) {
                LevelFormula.evalItemCount(expr, lv, max);
            }
            String skip = Component.translatable("skill_atlas.ui.formula.skip").getString();
            StringBuilder sb = new StringBuilder("→ ");
            for (int i = 0; i < samples.length; i++) {
                if (i > 0) {
                    if (max > 4 && i == 3) {
                        sb.append(" · … · ");
                    } else {
                        sb.append(" · ");
                    }
                }
                int lv = samples[i];
                int count = LevelFormula.evalItemCount(expr, lv, max);
                sb.append("Lv").append(lv).append(": ");
                if (count <= 0) {
                    sb.append(skip);
                } else {
                    sb.append("×").append(count);
                }
            }
            line.set(Component.literal(sb.toString()), Theme.TEAL);
            return null;
        } catch (LevelFormula.FormulaException e) {
            Component err = errorMessage(e);
            line.set(err, Theme.RED);
            return err;
        }
    }

    private void confirm() {
        Component err = validateSpecOnly();
        if (err != null) {
            setStatus(err, Theme.RED);
            Sounds.error();
            return;
        }
        List<SkillLevel> generated;
        try {
            generated = LevelFormula.generate(spec);
        } catch (LevelFormula.FormulaException e) {
            setStatus(errorMessage(e), Theme.RED);
            Sounds.error();
            return;
        }
        for (SkillLevel lv : generated) {
            if (!lv.experience && pool.isEmpty() && lv.cost > 0) {
                setStatus(Component.translatable("skill_atlas.ui.level.no_pool"), Theme.RED);
                Sounds.error();
                return;
            }
        }
        close();
        onApply.accept(generated);
    }

    @Override
    public void render(float pt) {
        if (Ui.focus instanceof TextInput focused && (formulaInputs.contains(focused) || commandInputs.contains(focused))) {
            lastFormulaInput = focused;
        }
        super.render(pt);
    }
}
