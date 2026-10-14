package com.hp.skillatlas.server;

import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.PointRule;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.RewardType;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class RegistryCheck {
    private RegistryCheck() {
    }

    public static void check(AtlasDefinition before, AtlasDefinition after, EditOps.Changes changes, ServerPlayer editor) {
        boolean trusted = editor.hasPermissions(2);
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
        MinecraftServer server = editor.getServer();
        for (String id : changes.rules) {
            PointRule r = after.rules.get(id);
            if (r == null) {
                continue;
            }
            for (String t : r.targets) {
                if (t.startsWith("#")) {
                    continue;
                }
                ResourceLocation rl = new ResourceLocation(t);
                boolean known = switch (r.type.targets) {
                    case ENTITY -> ForgeRegistries.ENTITY_TYPES.containsKey(rl);
                    case BLOCK -> ForgeRegistries.BLOCKS.containsKey(rl);
                    case ITEM -> ForgeRegistries.ITEMS.containsKey(rl);
                    case ADVANCEMENT -> server != null && server.getAdvancements().getAdvancement(rl) != null;
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
                TagParser.parseTag(nbt);
            } catch (CommandSyntaxException e) {
                throw new AtlasException("skill_atlas.error.bad_nbt", e.getMessage());
            }
        }
    }

    private static void reward(Reward r) {
        if (r.type == RewardType.COMMAND) {
            return;
        }
        ResourceLocation rl = new ResourceLocation(r.target);
        if (r.type == RewardType.ATTRIBUTE) {
            if (!ForgeRegistries.ATTRIBUTES.containsKey(rl)) {
                throw new AtlasException("skill_atlas.error.unknown_attribute", r.target);
            }
        } else if (!ForgeRegistries.MOB_EFFECTS.containsKey(rl)) {
            throw new AtlasException("skill_atlas.error.unknown_effect", r.target);
        }
    }
}
