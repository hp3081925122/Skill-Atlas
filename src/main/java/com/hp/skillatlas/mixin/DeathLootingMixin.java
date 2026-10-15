package com.hp.skillatlas.mixin;

import com.hp.skillatlas.server.LootingBonuses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DeathLootingMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void skillAtlas$beginLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LootingBonuses.begin(source);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void skillAtlas$endLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LootingBonuses.end();
    }
}
