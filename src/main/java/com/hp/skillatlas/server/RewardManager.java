package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.Reward;
import com.hp.skillatlas.data.RewardType;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.core.registries.BuiltInRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RewardManager {
    public static final String MODIFIER = "skill_atlas";

    private record Periodic(Holder<MobEffect> effect, int amplifier, int duration, int interval, int phase, boolean particles, boolean icon) {
    }

    private static final class Active {
        final List<Periodic> periodic = new ArrayList<>();
        final Set<Holder<MobEffect>> immune = new HashSet<>();
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private RewardManager() {
    }

    public static void refresh(ServerPlayer player) {
        AtlasDefinition d = AtlasServer.definition();
        PlayerProgress p = AtlasServer.progress(player);
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            AttributeInstance inst = player.getAttributes().getInstance(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
            if (inst == null) {
                continue;
            }
            for (AttributeModifier m : inst.getModifiers()) {
                if (m.id().getNamespace().equals(SkillAtlas.MOD_ID) && m.id().getPath().startsWith("reward/")) {
                    inst.removeModifier(m);
                }
            }
        }
        Active active = new Active();
        for (Map.Entry<String, Integer> e : p.levels().entrySet()) {
            SkillNode n = d.nodes.get(e.getKey());
            if (n == null || e.getValue() <= 0) {
                continue;
            }
            SkillLevel level = n.levels.get(Math.min(e.getValue(), n.maxLevel()) - 1);
            for (int i = 0; i < level.rewards.size(); i++) {
                Reward r = level.rewards.get(i);
                if (r.type == RewardType.ATTRIBUTE) {
                    Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(Identifier.parse(r.target));
                    AttributeInstance inst = null;
                    if (attribute != null) {
                        inst = player.getAttributes().getInstance(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
                    }
                    if (inst == null) {
                        warn("attribute", r.target);
                        continue;
                    }
                    Identifier id = SkillAtlas.id("reward/" + n.id + "/" + i);
                    inst.removeModifier(id);
                    inst.addPermanentModifier(new AttributeModifier(id, r.amount, operation(r)));
                } else if (r.type == RewardType.EFFECT || r.type == RewardType.IMMUNITY) {
                    Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(r.target)).orElse(null);
                    if (effect == null) {
                        warn("effect", r.target);
                        continue;
                    }
                    if (r.type == RewardType.EFFECT) {
                        int phase = Math.floorMod((n.id + i).hashCode(), r.interval);
                        active.periodic.add(new Periodic(effect, r.amplifier, r.duration, r.interval, phase, r.particles, r.icon));
                    } else {
                        active.immune.add(effect);
                    }
                }
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        for (Holder<MobEffect> effect : active.immune) {
            if (player.hasEffect(effect)) {
                player.removeEffect(effect);
            }
        }
        if (active.periodic.isEmpty() && active.immune.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        } else {
            ACTIVE.put(player.getUUID(), active);
        }
    }

    public static void tick(ServerPlayer player) {
        Active active = ACTIVE.get(player.getUUID());
        if (active == null || player.isDeadOrDying()) {
            return;
        }
        for (Periodic e : active.periodic) {
            if ((player.tickCount + e.phase) % e.interval == 0) {
                player.addEffect(new MobEffectInstance(e.effect, e.duration, e.amplifier, true, e.particles, e.icon));
            }
        }
    }

    public static boolean immune(ServerPlayer player, Holder<MobEffect> effect) {
        Active active = ACTIVE.get(player.getUUID());
        return active != null && active.immune.contains(effect);
    }

    public static void runCommands(ServerPlayer player, SkillLevel level) {
        for (Reward r : level.rewards) {
            if (r.type == RewardType.COMMAND) {
                try {
                    player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput(), r.target);
                } catch (RuntimeException e) {
                    SkillAtlas.LOGGER.warn("Skill reward command failed for {}: {} ({})", player.getGameProfile().name(), r.target, e.toString());
                }
            }
        }
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);
    }

    public static void clear() {
        ACTIVE.clear();
        WARNED.clear();
    }

    private static AttributeModifier.Operation operation(Reward r) {
        return switch (r.operation) {
            case ADD -> AttributeModifier.Operation.ADD_VALUE;
            case MULTIPLY_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private static void warn(String kind, String id) {
        if (WARNED.add(kind + ":" + id)) {
            SkillAtlas.LOGGER.warn("Skill atlas references unknown {} {}, reward skipped", kind, id);
        }
    }
}
