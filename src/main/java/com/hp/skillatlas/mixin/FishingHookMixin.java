package com.hp.skillatlas.mixin;

import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.data.ActivityMath;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Shadow @Final @Mutable private int luck;
    @Shadow @Final @Mutable private int lureSpeed;
    @Unique private boolean skillAtlas$hasLure;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void skillAtlas$fishingAttributes(Player player, Level level, int luck, int lure, CallbackInfo ci) {
        this.luck += SkillAttributes.roll(SkillAttributes.value(player, SkillAttributes.FISHING_LUCK), player.getRandom());
        int bonus = SkillAttributes.roll(SkillAttributes.value(player, SkillAttributes.FISHING_LURE), player.getRandom());
        this.lureSpeed += bonus * 100;
        skillAtlas$hasLure = bonus > 0;
    }

    @Redirect(method = "catchingFish", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I"))
    private int skillAtlas$minimumWait(RandomSource random, int min, int max) {
        int value = Mth.nextInt(random, min, max);
        if (skillAtlas$hasLure && min == 100 && max == 600) {
            return Math.max(value, lureSpeed + 20);
        }
        return value;
    }
}
