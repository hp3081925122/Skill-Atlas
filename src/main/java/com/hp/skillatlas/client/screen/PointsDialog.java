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
import com.hp.skillatlas.data.PointRule;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class PointsDialog extends Dialog {
    private static boolean poolsTab;
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
        float tabW = (contentW() - 4) / 2.0F;
        Button rules = add(new Button(Component.translatable("skill_atlas.ui.points.rules"), () -> {
            poolsTab = false;
            rebuild();
        }));
        rules.active(() -> !poolsTab);
        rules.bounds(contentX(), contentY(), tabW, 16);
        Button pools = add(new Button(Component.translatable("skill_atlas.ui.points.pools"), () -> {
            poolsTab = true;
            rebuild();
        }));
        pools.active(() -> poolsTab);
        pools.bounds(contentX() + tabW + 4, contentY(), tabW, 16);
        scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY() + 20, contentW(), contentH() - 20);
        Form f = new Form(scroll, 0);
        if (poolsTab) {
            buildPools(f);
        } else {
            buildRules(f);
        }
        f.finish();
        footer(new Button(Component.translatable("skill_atlas.ui.ok"), this::close).style(Button.Style.PRIMARY));
    }

    private void buildRules(Form f) {
        boolean edit = builtEdit;
        if (ClientAtlas.definition.rules.isEmpty()) {
            f.full(new Label(Component.translatable("skill_atlas.ui.points.no_rules"), Theme.MUTED).centered(), 16);
        }
        for (PointRule rule : ClientAtlas.definition.rules.values()) {
            String id = rule.id;
            Label name = new Label(Names.rule(rule), Theme.GOLD);
            if (edit) {
                Button e = Button.icon(Sprites.EDIT, Component.translatable("skill_atlas.ui.menu.edit"), () -> stack.push(new RuleDialog(rule, null)));
                Button d = Button.icon(Sprites.TRASH, Component.translatable("skill_atlas.ui.delete"), () -> stack.push(new ConfirmDialog(Component.translatable("skill_atlas.ui.rule.delete"), List.of(Component.translatable("skill_atlas.ui.rule.delete.confirm", Names.rule(rule))), Component.translatable("skill_atlas.ui.delete"), () -> {
                    JsonArray ops = new JsonArray();
                    ops.add(EditOps.deleteOp("delete_rule", id));
                    send(ops);
                })));
                f.cells(null, new float[]{10, 1, 1}, name, e, d);
            } else {
                f.full(name, 12);
            }
            for (Component line : Names.tooltipLines(rule.description)) {
                NodeEditorDialog.hint(f, line, Theme.TEXT);
            }
            f.full(new Label(() -> progressLine(id), Theme.MUTED), 10);
            f.gap(5);
        }
        if (edit) {
            Button add = new Button(Component.translatable("skill_atlas.ui.rule.create"), () -> {
                if (ClientAtlas.definition.pools.isEmpty()) {
                    setStatus(Component.translatable("skill_atlas.ui.category.no_pools"), Theme.RED);
                    return;
                }
                stack.push(new RuleDialog(null, null));
            });
            add.icon = Sprites.PLUS;
            f.full(add, 16);
        }
    }

    private Component progressLine(String id) {
        PointRule rule = ClientAtlas.definition.rules.get(id);
        if (rule == null) {
            return Component.empty();
        }
        long counter = ClientAtlas.progress.counter(id);
        long awarded = ClientAtlas.progress.awarded(id);
        MutableComponent line = Component.translatable("skill_atlas.ui.points.rule_progress", counter, rule.count, rule.points, Names.pool(rule.pool));
        if (rule.limit > 0) {
            line = line.append(Component.literal("  ")).append(Component.translatable("skill_atlas.ui.points.rule_limit", awarded, rule.limit));
            if (awarded >= rule.limit) {
                line = line.append(Component.literal("  ")).append(Component.translatable("skill_atlas.ui.points.rule_done").withStyle(s -> s.withColor(0xE0685A)));
            }
        }
        return line;
    }

    private void buildPools(Form f) {
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
                f.full(new Label(Component.translatable("skill_atlas.ui.points.pool_initial", pool.initial), Theme.DIM), 10);
            } else {
                f.full(row, 16);
            }
            f.gap(3);
        }
        if (edit) {
            Button add = new Button(Component.translatable("skill_atlas.ui.pool.create"), () -> stack.push(new PoolDialog(null, null)));
            add.icon = Sprites.PLUS;
            f.full(add, 16);
        }
    }

    @Override
    public void render(float pt) {
        if (builtVersion != ClientAtlas.version || builtEdit != editable()) {
            rebuild();
        }
        super.render(pt);
    }
}
