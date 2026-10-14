package com.hp.skillatlas.client;

import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.PointRule;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.RewardOperation;
import com.hp.skillatlas.data.RuleType;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.data.Texts;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Names {
    private static final Set<String> FIELD_ERRORS = Set.of("skill_atlas.error.bad_field", "skill_atlas.error.too_long", "skill_atlas.error.range", "skill_atlas.error.too_many", "skill_atlas.error.bad_resource");

    private Names() {
    }

    public static MutableComponent node(SkillNode n) {
        return Texts.component(n.name, n.id);
    }

    public static String nodePlain(SkillNode n) {
        return Texts.strip(node(n).getString());
    }

    public static MutableComponent nodeById(String id) {
        SkillNode n = ClientAtlas.definition.nodes.get(id);
        if (n == null) {
            return Component.literal(id);
        }
        return node(n);
    }

    public static MutableComponent category(Category c) {
        return Texts.component(c.name, c.id);
    }

    public static MutableComponent pool(PointPool p) {
        return Texts.component(p.name, p.id).withStyle(s -> s.withColor(p.color));
    }

    public static MutableComponent pool(String id) {
        PointPool p = ClientAtlas.definition.pools.get(id);
        if (p == null) {
            return Component.literal(id);
        }
        return pool(p);
    }

    public static int poolColor(String id) {
        PointPool p = ClientAtlas.definition.pools.get(id);
        if (p == null) {
            return 0xFFECC873;
        }
        return 0xFF000000 | p.color;
    }

    public static List<Component> tooltipLines(String raw) {
        List<Component> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String text = Texts.component(raw).getString();
        for (String line : text.split("\n", -1)) {
            out.add(Component.literal(line).withStyle(s -> s.withColor(0xB8BED0)));
        }
        return out;
    }

    private static ResourceLocation rl(String id) {
        ResourceLocation r = ResourceLocation.tryParse(id);
        if (r == null) {
            return new ResourceLocation("minecraft", "air");
        }
        return r;
    }

    public static MutableComponent attribute(String id) {
        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(rl(id));
        if (a == null) {
            return Component.literal(id);
        }
        return Component.translatable(a.getDescriptionId());
    }

    public static MutableComponent effect(String id) {
        MobEffect e = ForgeRegistries.MOB_EFFECTS.getValue(rl(id));
        if (e == null) {
            return Component.literal(id);
        }
        return e.getDisplayName().copy();
    }

    public static MutableComponent target(RuleType type, String id) {
        if (id.startsWith("#")) {
            return Component.translatable("skill_atlas.ui.tag", id.substring(1));
        }
        ResourceLocation r = rl(id);
        switch (type.targets) {
            case ENTITY -> {
                EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(r);
                if (t != null && ForgeRegistries.ENTITY_TYPES.containsKey(r)) {
                    return t.getDescription().copy();
                }
            }
            case BLOCK -> {
                Block b = ForgeRegistries.BLOCKS.getValue(r);
                if (b != null && ForgeRegistries.BLOCKS.containsKey(r)) {
                    return b.getName();
                }
            }
            case ITEM -> {
                Item i = ForgeRegistries.ITEMS.getValue(r);
                if (i != null && ForgeRegistries.ITEMS.containsKey(r)) {
                    return i.getDescription().copy();
                }
            }
            case ADVANCEMENT -> {
                if (Minecraft.getInstance().getConnection() != null) {
                    ClientAdvancements adv = Minecraft.getInstance().getConnection().getAdvancements();
                    Advancement a = adv.getAdvancements().get(r);
                    if (a != null && a.getDisplay() != null) {
                        return a.getDisplay().getTitle().copy();
                    }
                }
            }
            case NONE -> {
            }
        }
        return Component.literal(id);
    }

    public static MutableComponent item(ItemCost c) {
        if (ItemCosts.item(c.item) == Items.AIR) {
            return Component.literal(c.item);
        }
        return Icons.stack(new Icon(Icon.ITEM, c.item, c.nbt)).getHoverName().copy();
    }

    public static String number(double v) {
        return Json.trim(Math.round(v * 1000.0D) / 1000.0D);
    }

    private static String signed(double v) {
        if (v >= 0) {
            return "+" + number(v);
        }
        return number(v);
    }

    public static MutableComponent amplifier(int amplifier) {
        if (amplifier < 10) {
            return Component.translatable("enchantment.level." + (amplifier + 1));
        }
        return Component.literal(String.valueOf(amplifier + 1));
    }

    public static MutableComponent reward(Reward r, boolean details) {
        switch (r.type) {
            case ATTRIBUTE -> {
                String value;
                if (r.operation == RewardOperation.ADD) {
                    value = signed(r.amount);
                } else {
                    value = signed(r.amount * 100.0D) + "%";
                }
                return Component.translatable("skill_atlas.reward.attribute", attribute(r.target), value).withStyle(s -> s.withColor(0x8EDB7A));
            }
            case EFFECT -> {
                MutableComponent name = effect(r.target).append(" ").append(amplifier(r.amplifier));
                return Component.translatable("skill_atlas.reward.effect", name, number(r.interval / 20.0D)).withStyle(s -> s.withColor(0x7FC8FF));
            }
            case IMMUNITY -> {
                return Component.translatable("skill_atlas.reward.immunity", effect(r.target)).withStyle(s -> s.withColor(0xC493FF));
            }
            default -> {
                if (details) {
                    return Component.translatable("skill_atlas.reward.command.detail", "/" + r.target).withStyle(s -> s.withColor(0xECC873));
                }
                return Component.translatable("skill_atlas.reward.command").withStyle(s -> s.withColor(0xECC873));
            }
        }
    }

    public static MutableComponent rewardType(com.hp.skillatlas.data.RewardType t) {
        return Component.translatable("skill_atlas.reward_type." + t.key);
    }

    public static MutableComponent operation(RewardOperation o) {
        return Component.translatable("skill_atlas.operation." + o.key);
    }

    public static MutableComponent ruleType(RuleType t) {
        return Component.translatable("skill_atlas.rule_type." + t.key);
    }

    public static MutableComponent rule(PointRule r) {
        if (!r.name.isEmpty()) {
            return Texts.component(r.name);
        }
        MutableComponent targets;
        if (r.targets.isEmpty()) {
            targets = Component.translatable("skill_atlas.ui.any");
        } else {
            targets = target(r.type, r.targets.get(0));
            if (r.targets.size() > 1) {
                targets = Component.translatable("skill_atlas.ui.and_more", targets, r.targets.size() - 1);
            }
        }
        return Component.translatable("skill_atlas.rule_desc." + r.type.key, targets, r.count);
    }

    public static MutableComponent state(AtlasLogic.State s) {
        return Component.translatable("skill_atlas.state." + s.name().toLowerCase(Locale.ROOT));
    }

    public static int stateColor(AtlasLogic.State s) {
        return switch (s) {
            case LOCKED -> Theme.MUTED;
            case UNAFFORDABLE -> Theme.RED;
            case AVAILABLE -> Theme.TEAL;
            case LEARNED -> Theme.GOLD;
            case UPGRADABLE -> Theme.GREEN;
            case MAXED -> Theme.VIOLET;
        };
    }

    public static Component error(String key, List<String> args) {
        Object[] parts = args.toArray();
        if (FIELD_ERRORS.contains(key) && !args.isEmpty()) {
            String field = "skill_atlas.field." + args.get(0);
            if (I18n.exists(field)) {
                parts[0] = Component.translatable(field);
            }
        }
        return Component.translatable(key, parts);
    }
}
