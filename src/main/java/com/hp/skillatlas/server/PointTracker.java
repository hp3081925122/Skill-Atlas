package com.hp.skillatlas.server;

import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.RuleType;
import com.hp.skillatlas.data.Texts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class PointTracker {
    public static final String BEST_LEVEL = "#best_level";

    private record Compiled(PointPool pool, Set<ResourceLocation> ids, List<ResourceLocation> tags) {
        boolean matches(ResourceLocation id, Predicate<ResourceLocation> tag) {
            if (ids.isEmpty() && tags.isEmpty()) {
                return true;
            }
            if (id != null && ids.contains(id)) {
                return true;
            }
            for (ResourceLocation t : tags) {
                if (tag.test(t)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Map<RuleType, List<Compiled>> RULES = new EnumMap<>(RuleType.class);

    private PointTracker() {
    }

    public static void rebuild(AtlasDefinition d) {
        RULES.clear();
        for (PointPool p : d.pools.values()) {
            Set<ResourceLocation> ids = new HashSet<>();
            List<ResourceLocation> tags = new ArrayList<>();
            for (String t : p.targets) {
                if (t.startsWith("#")) {
                    tags.add(new ResourceLocation(t.substring(1)));
                } else {
                    ids.add(new ResourceLocation(t));
                }
            }
            RULES.computeIfAbsent(p.method, k -> new ArrayList<>()).add(new Compiled(p, ids, tags));
        }
    }

    private static List<Compiled> rules(RuleType type) {
        return RULES.getOrDefault(type, Collections.emptyList());
    }

    private static boolean counts(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!AtlasConfig.COUNT_CREATIVE.get() && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return true;
    }

    public static void kill(ServerPlayer player, LivingEntity victim) {
        if (rules(RuleType.KILL).isEmpty() || !counts(player)) {
            return;
        }
        EntityType<?> type = victim.getType();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        boolean hostile = victim instanceof Enemy;
        SkillAtlas.LOGGER.debug("Kill tracked: player={}, entity={}, hostile={}", player.getGameProfile().getName(), id, hostile);
        for (Compiled c : rules(RuleType.KILL)) {
            if (c.pool.hostileOnly() && !hostile) {
                continue;
            }
            if (c.matches(id, t -> type.is(TagKey.create(Registries.ENTITY_TYPE, t)))) {
                progress(player, c.pool, 1);
            }
        }
    }

    public static void mine(ServerPlayer player, BlockState state) {
        if (rules(RuleType.MINE).isEmpty() || state.isAir() || !counts(player)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        for (Compiled c : rules(RuleType.MINE)) {
            if (c.matches(id, t -> state.is(TagKey.create(Registries.BLOCK, t)))) {
                progress(player, c.pool, 1);
            }
        }
    }

    public static void item(ServerPlayer player, RuleType type, ItemStack stack) {
        if (rules(type).isEmpty() || stack.isEmpty() || !counts(player)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (Compiled c : rules(type)) {
            if (c.matches(id, t -> stack.is(TagKey.create(Registries.ITEM, t)))) {
                progress(player, c.pool, stack.getCount());
            }
        }
    }

    public static void advancement(ServerPlayer player, ResourceLocation id, boolean displayed) {
        if (rules(RuleType.ADVANCEMENT).isEmpty() || !counts(player)) {
            return;
        }
        for (Compiled c : rules(RuleType.ADVANCEMENT)) {
            if (c.ids.isEmpty()) {
                if (displayed) {
                    progress(player, c.pool, 1);
                }
            } else if (c.ids.contains(id)) {
                progress(player, c.pool, 1);
            }
        }
    }

    public static void second(ServerPlayer player) {
        boolean playtime = !rules(RuleType.PLAYTIME).isEmpty();
        boolean level = !rules(RuleType.LEVEL).isEmpty();
        if ((!playtime && !level) || !counts(player)) {
            return;
        }
        if (playtime) {
            for (Compiled c : rules(RuleType.PLAYTIME)) {
                progress(player, c.pool, 1);
            }
        }
        if (level) {
            PlayerProgress p = AtlasServer.progress(player);
            long best = p.counter(BEST_LEVEL);
            if (player.experienceLevel > best) {
                p.setCounter(BEST_LEVEL, player.experienceLevel);
                AtlasServer.data().setDirty();
                for (Compiled c : rules(RuleType.LEVEL)) {
                    progress(player, c.pool, player.experienceLevel - best);
                }
            }
        }
    }

    private static void progress(ServerPlayer player, PointPool pool, long amount) {
        PlayerProgress p = AtlasServer.progress(player);
        long awarded = p.awarded(pool.id);
        if (pool.limit > 0 && awarded >= pool.limit) {
            return;
        }
        long total = p.counter(pool.id) + amount;
        long gain = (total / pool.count) * pool.points;
        p.setCounter(pool.id, total % pool.count);
        if (pool.limit > 0) {
            gain = Math.min(gain, pool.limit - awarded);
        }
        AtlasServer.data().setDirty();
        AtlasServer.markSync(player);
        if (gain <= 0) {
            return;
        }
        p.addPoints(pool.id, gain);
        p.addAwarded(pool.id, gain);
        if (AtlasConfig.NOTIFY_POINTS.get()) {
            Component name = Texts.component(pool.name, pool.id).withStyle(s -> s.withColor(pool.color));
            player.displayClientMessage(Component.translatable("skill_atlas.message.points_gained", gain, name), true);
        }
    }
}
