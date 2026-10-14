package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.Prerequisite;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class NodeTooltip {
    private static final int HEADER = 0xECC873;
    private static final int MUTED = 0x8A93A8;
    private static final int OK = 0x8EDB7A;
    private static final int BAD = 0xE0685A;

    private NodeTooltip() {
    }

    public static List<Component> lines(SkillNode n) {
        return build(n, true);
    }

    public static List<Component> details(SkillNode n) {
        return build(n, false);
    }

    private static List<Component> build(SkillNode n, boolean hints) {
        AtlasDefinition d = ClientAtlas.definition;
        PlayerProgress p = ClientAtlas.progress;
        List<Component> out = new ArrayList<>();
        out.add(Names.node(n));
        AtlasLogic.State state = AtlasLogic.state(d, n, p, ClientAtlas::hasItems);
        int level = Math.min(p.level(n.id), n.maxLevel());
        int stateColor = Names.stateColor(state) & 0xFFFFFF;
        MutableComponent line = Names.state(state).withStyle(s -> s.withColor(stateColor));
        if (n.maxLevel() > 1 || level > 0) {
            line = line.append(Component.literal("  ")).append(Component.translatable("skill_atlas.ui.level", level, n.maxLevel()).withStyle(s -> s.withColor(MUTED)));
        }
        out.add(line);
        List<Component> desc = Names.tooltipLines(n.tooltip);
        if (!desc.isEmpty()) {
            out.add(Component.empty());
            out.addAll(desc);
        }
        boolean details = ClientAtlas.editMode;
        if (level > 0) {
            SkillLevel cur = n.levels.get(level - 1);
            if (!cur.rewards.isEmpty()) {
                out.add(Component.empty());
                out.add(Component.translatable("skill_atlas.ui.current_rewards").withStyle(s -> s.withColor(HEADER)));
                for (Reward r : cur.rewards) {
                    out.add(Component.literal(" • ").append(Names.reward(r, details)));
                }
            }
        }
        if (level < n.maxLevel()) {
            SkillLevel next = n.levels.get(level);
            out.add(Component.empty());
            String pool = d.poolOf(n);
            long have = p.points(pool);
            int color = OK;
            if (have < next.cost) {
                color = BAD;
            }
            int costColor = color;
            Component cost = Component.translatable("skill_atlas.ui.cost", next.cost, Names.pool(pool)).withStyle(s -> s.withColor(costColor));
            String headKey = "skill_atlas.ui.next_rewards";
            if (level == 0) {
                headKey = "skill_atlas.ui.learn_rewards";
            }
            out.add(Component.translatable(headKey).withStyle(s -> s.withColor(HEADER)).append(Component.literal("  ")).append(cost));
            if (next.rewards.isEmpty()) {
                out.add(Component.literal(" • ").append(Component.translatable("skill_atlas.ui.no_rewards").withStyle(s -> s.withColor(MUTED))));
            }
            for (Reward r : next.rewards) {
                out.add(Component.literal(" • ").append(Names.reward(r, details)));
            }
            if (!next.items.isEmpty()) {
                out.add(Component.translatable("skill_atlas.ui.items_required").withStyle(s -> s.withColor(HEADER)));
                int[] owned = ClientAtlas.ownedItems(next.items);
                for (int i = 0; i < next.items.size(); i++) {
                    ItemCost c = next.items.get(i);
                    int itemColor = BAD;
                    if (owned[i] >= c.count) {
                        itemColor = OK;
                    }
                    int ic = itemColor;
                    MutableComponent row = Component.literal(" • ").append(Component.translatable("skill_atlas.ui.item_cost", Names.item(c), owned[i], c.count).withStyle(s -> s.withColor(ic)));
                    if (details && !c.nbt.isEmpty()) {
                        row = row.append(Component.literal(" " + c.nbt).withStyle(s -> s.withColor(MUTED)));
                    }
                    out.add(row);
                }
            }
        }
        List<Prerequisite> pres = new ArrayList<>();
        for (Prerequisite pre : n.prerequisites) {
            if (d.nodes.containsKey(pre.node)) {
                pres.add(pre);
            }
        }
        if (!pres.isEmpty() && (level == 0 || details || !hints)) {
            out.add(Component.empty());
            String key = "skill_atlas.ui.requires_all";
            if (!n.requireAll) {
                key = "skill_atlas.ui.requires_any";
            }
            out.add(Component.translatable(key).withStyle(s -> s.withColor(HEADER)));
            for (Prerequisite pre : pres) {
                int color = BAD;
                String mark = " ✘ ";
                if (p.level(pre.node) > 0) {
                    color = OK;
                    mark = " ✔ ";
                }
                int c = color;
                out.add(Component.literal(mark).withStyle(s -> s.withColor(c)).append(Names.nodeById(pre.node)));
            }
        }
        if (hints) {
            if (ClientAtlas.editMode) {
                out.add(Component.empty());
                out.add(Component.translatable("skill_atlas.ui.hint_edit_node").withStyle(s -> s.withColor(MUTED)));
            } else if (state == AtlasLogic.State.AVAILABLE || state == AtlasLogic.State.UPGRADABLE) {
                out.add(Component.empty());
                out.add(Component.translatable("skill_atlas.ui.hint_learn").withStyle(s -> s.withColor(MUTED)));
            }
        }
        return out;
    }
}
