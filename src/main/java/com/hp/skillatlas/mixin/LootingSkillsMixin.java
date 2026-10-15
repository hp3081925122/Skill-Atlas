package com.hp.skillatlas.mixin;

import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.server.LootingBonuses;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class LootingSkillsMixin {
    @Inject(method = "getEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void skillAtlas$looting(Holder<Enchantment> enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (enchantment.is(Enchantments.LOOTING) && entity instanceof ServerPlayer player) {
            double bonus = SkillAttributes.value(player, SkillAttributes.LOOTING);
            if (bonus > 0) {
                cir.setReturnValue(cir.getReturnValue() + SkillAttributes.roll(bonus, player.getRandom()));
            }
        }
    }

    @Inject(method = "processEquipmentDropChance", at = @At("RETURN"), cancellable = true)
    private static void skillAtlas$equipment(ServerLevel level, LivingEntity victim, DamageSource source, float chance, CallbackInfoReturnable<Float> cir) {
        if (chance > 0 && source.getEntity() instanceof ServerPlayer player) {
            int bonus = LootingBonuses.equipment(player);
            if (bonus > 0) {
                cir.setReturnValue(cir.getReturnValue() + 0.01F * bonus);
            }
        }
    }
}
