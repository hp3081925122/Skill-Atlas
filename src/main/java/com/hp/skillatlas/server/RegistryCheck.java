package com.hp.skillatlas.server;

import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.RewardType;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public final class RegistryCheck {
    private RegistryCheck() {
    }

    public static void check(AtlasDefinition before, AtlasDefinition after, EditOps.Changes changes, ServerPlayer editor) {
        boolean trusted = editor.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        for (String id : changes.nodes) {
            SkillNode n = after.nodes.get(id);
            if (n == null) {
                continue;
            }
            icon(n.icon);
            for (SkillLevel level : n.levels) {
                for (Reward r : level.rewards) {
                    reward(r);
                }
                for (ItemCost c : level.items) {
                    item(c.item, c.nbt);
                }
            }
            if (!trusted && !commands(n).equals(commands(before.nodes.get(id)))) {
                throw new AtlasException("skill_atlas.error.command_permission");
            }
        }
        for (String id : changes.categories) {
            Category c = after.categories.get(id);
            if (c != null) {
                icon(c.icon);
            }
        }
        MinecraftServer server = editor.level().getServer();
        for (String id : changes.pools) {
            PointPool p = after.pools.get(id);
            if (p == null) {
                continue;
            }
            for (String t : p.targets) {
                if (t.startsWith("#")) {
                    continue;
                }
                Identifier rl = Identifier.parse(t);
                boolean known = switch (p.method.targets) {
                    case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
                    case BLOCK -> BuiltInRegistries.BLOCK.containsKey(rl);
                    case ITEM -> BuiltInRegistries.ITEM.containsKey(rl);
                    case ADVANCEMENT -> server != null && server.getAdvancements().get(rl) != null;
                    case NONE -> true;
                };
                if (!known) {
                    throw new AtlasException("skill_atlas.error.unknown_target", t);
                }
            }
        }
    }

    private static List<String> commands(SkillNode n) {
        List<String> out = new ArrayList<>();
        if (n == null) {
            return out;
        }
        for (SkillLevel level : n.levels) {
            for (Reward r : level.rewards) {
                if (r.type == RewardType.COMMAND) {
                    out.add(r.target);
                }
            }
        }
        return out;
    }

    private static void icon(Icon icon) {
        if (icon.isTexture()) {
            return;
        }
        item(icon.id, icon.nbt);
    }

    private static void item(String id, String nbt) {
        if (ItemCosts.item(id) == Items.AIR) {
            throw new AtlasException("skill_atlas.error.unknown_item", id);
        }
        if (!nbt.isEmpty()) {
            try {
                com.hp.skillatlas.StackComponents.patch(nbt);
            } catch (RuntimeException e) {
                throw new AtlasException("skill_atlas.error.bad_nbt", e.getMessage());
            }
        }
    }

    private static void reward(Reward r) {
        if (r.type == RewardType.COMMAND) {
            return;
        }
        Identifier rl = Identifier.parse(r.target);
        if (r.type == RewardType.ATTRIBUTE) {
            if (!BuiltInRegistries.ATTRIBUTE.containsKey(rl)) {
                throw new AtlasException("skill_atlas.error.unknown_attribute", r.target);
            }
        } else if (!BuiltInRegistries.MOB_EFFECT.containsKey(rl)) {
            throw new AtlasException("skill_atlas.error.unknown_effect", r.target);
        }
    }
}
