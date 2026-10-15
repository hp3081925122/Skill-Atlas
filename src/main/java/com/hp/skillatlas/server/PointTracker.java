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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class PointTracker {
    private record Compiled(PointPool pool, Set<Identifier> ids, List<Identifier> tags) {
        boolean matches(Identifier id, Predicate<Identifier> tag) {
            if (ids.isEmpty() && tags.isEmpty()) {
                return true;
            }
            if (id != null && ids.contains(id)) {
                return true;
            }
            for (Identifier t : tags) {
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
            Set<Identifier> ids = new HashSet<>();
            List<Identifier> tags = new ArrayList<>();
            for (String t : p.targets) {
                if (t.startsWith("#")) {
                    tags.add(Identifier.parse(t.substring(1)));
                } else {
                    ids.add(Identifier.parse(t));
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
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        boolean hostile = victim instanceof Enemy;
        SkillAtlas.LOGGER.debug("Kill tracked: player={}, entity={}, hostile={}", player.getGameProfile().name(), id, hostile);
        for (Compiled c : rules(RuleType.KILL)) {
            if (c.pool.hostileOnly() && !hostile) {
                continue;
            }
            if (c.matches(id, t -> type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, t)))) {
                progress(player, c.pool, 1);
            }
        }
    }

    public static void mine(ServerPlayer player, BlockState state) {
        if (rules(RuleType.MINE).isEmpty() || state.isAir() || !counts(player)) {
            return;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Compiled c : rules(type)) {
            if (c.matches(id, t -> stack.is(TagKey.create(Registries.ITEM, t)))) {
                progress(player, c.pool, stack.getCount());
            }
        }
    }

    public static void harvest(ServerPlayer player, BlockState state) {
        if (!counts(player)) {
            return;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (Compiled c : rules(RuleType.HARVEST)) {
            if (c.matches(id, t -> state.is(TagKey.create(Registries.BLOCK, t)))) {
                progress(player, c.pool, 1);
            }
        }
    }

    public static void fish(ServerPlayer player, List<ItemStack> drops) {
        if (!counts(player) || drops.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }
        for (Compiled c : rules(RuleType.FISH)) {
            boolean matches = drops.stream().filter(stack -> !stack.isEmpty()).anyMatch(stack ->
                    c.matches(BuiltInRegistries.ITEM.getKey(stack.getItem()), t -> stack.is(TagKey.create(Registries.ITEM, t))));
            if (matches) {
                progress(player, c.pool, 1);
            }
        }
    }

    public static void advancement(ServerPlayer player, Identifier id, boolean displayed) {
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
        if (rules(RuleType.PLAYTIME).isEmpty() || !counts(player)) {
            return;
        }
        for (Compiled c : rules(RuleType.PLAYTIME)) {
            progress(player, c.pool, 1);
        }
    }

    public static void movement(ServerPlayer player, int centimeters) {
        if (AtlasServer.server() == null || centimeters <= 0 || rules(RuleType.MOVEMENT).isEmpty() || !counts(player)) {
            return;
        }
        for (Compiled c : rules(RuleType.MOVEMENT)) {
            progress(player, c.pool, centimeters);
        }
    }

    private static void progress(ServerPlayer player, PointPool pool, long amount) {
        PlayerProgress p = AtlasServer.progress(player);
        long awarded = p.awarded(pool.id);
        if (pool.limit > 0 && awarded >= pool.limit) {
            return;
        }
        long total = p.counter(pool.id) + amount;
        long target = pool.counterTarget();
        long gain = (total / target) * pool.points;
        p.setCounter(pool.id, total % target);
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
            Component name = Texts.component(AtlasServer.definition().texts.display(pool.name, player.getLanguage()), pool.id).withStyle(s -> s.withColor(pool.color));
            player.sendSystemMessage(Component.translatable("skill_atlas.message.points_gained", gain, name), true);
        }
    }
}
