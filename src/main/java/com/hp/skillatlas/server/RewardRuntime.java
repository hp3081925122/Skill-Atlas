package com.hp.skillatlas.server;

import com.hp.skillatlas.model.*;
import com.hp.skillatlas.core.RewardPlan;
import com.hp.skillatlas.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class RewardRuntime {
    private final Map<UUID, Active> active = new HashMap<>();
    private static final class Active implements RewardContext {
        ServerPlayer owner;
        long nextCheck;
        final List<Applied> applied = new ArrayList<>();
        final Set<MobEffect> immune = new HashSet<>();
        final List<Periodic> periodic = new ArrayList<>();
        public void periodic(Definition.Reward r) { periodic.add(new Periodic(r)); }
        public void immune(MobEffect effect) { if (effect != null) immune.add(effect); }
    }
    private record Applied(RewardType type, RewardPlan.Entry entry) {}
    private static final class Periodic {
        Definition.Reward reward; long next;
        Periodic(Definition.Reward reward) { this.reward = reward; }
    }
    public void remove(ServerPlayer player) {
        Active old = active.remove(player.getUUID());
        if (old != null) old.applied.forEach(a -> a.type.revoke(old.owner, a.entry.reward(), a.entry.key()));
    }
    public void rebuild(ServerPlayer player, Definition d, PlayerProgress progress) {
        remove(player); Active state = new Active(); state.owner = player; active.put(player.getUUID(), state);
        for (var entry : RewardPlan.build(d, progress)) {
            RewardType type = RewardTypes.get(entry.reward().type);
            if (type != null) { type.apply(player, entry.reward(), entry.key(), state); state.applied.add(new Applied(type, entry)); }
        }
        state.immune.forEach(player::removeEffect); tick(player);
    }
    public boolean immune(ServerPlayer player, MobEffect effect) { Active state = active.get(player.getUUID()); return state != null && state.immune.contains(effect); }
    public void tick(ServerPlayer player) {
        Active state = active.get(player.getUUID()); if (state == null || state.owner != player || !player.isAlive()) return;
        long time = player.serverLevel().getGameTime();
        if (time < state.nextCheck) return;
        state.nextCheck = Long.MAX_VALUE;
        for (var periodic : state.periodic) {
            if (time >= periodic.next) {
                var r = periodic.reward; periodic.next = time + r.interval;
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.parse(r.target));
                if (effect != null && !state.immune.contains(effect)) player.addEffect(new MobEffectInstance(effect, r.duration, r.amplifier, false, r.particles, r.showIcon));
            }
            state.nextCheck = Math.min(state.nextCheck, periodic.next);
        }
    }
    public void clear() { active.clear(); }
}
