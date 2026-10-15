package com.hp.skillatlas.mixin;

import com.hp.skillatlas.server.PointTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class MovementStatsMixin {
    @Unique private static final Set<ResourceLocation> skillAtlas$movementStats = Set.of(
            Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.CROUCH_ONE_CM,
            Stats.SWIM_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.WALK_ON_WATER_ONE_CM);

    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("RETURN"))
    private void skillAtlas$movement(Stat<?> stat, int amount, CallbackInfo ci) {
        if (stat.getType() == Stats.CUSTOM && skillAtlas$movementStats.contains(stat.getValue())) {
            PointTracker.movement((ServerPlayer) (Object) this, amount);
        }
    }
}
