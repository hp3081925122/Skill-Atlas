package com.hp.skillatlas.mixin;

import com.hp.skillatlas.SkillAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class ElytraSpeedMixin {
    @ModifyArg(method = "travelFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0), index = 1)
    private Vec3 skillAtlas$elytraSpeed(Vec3 movement) {
        if ((Object) this instanceof Player player && !player.getAbilities().flying) {
            return movement.scale(1.0D + SkillAttributes.value(player, SkillAttributes.ELYTRA_SPEED) / 100.0D);
        }
        return movement;
    }
}
