package com.hp.skillatlas.mixin;

import com.hp.skillatlas.data.RuleType;
import com.hp.skillatlas.server.PointTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void skillAtlas$smelted(ItemStack stack, CallbackInfo ci) {
        if (removeCount > 0 && player instanceof ServerPlayer serverPlayer && !stack.isEmpty()) {
            ItemStack taken = stack.copy();
            taken.setCount(removeCount);
            PointTracker.item(serverPlayer, RuleType.SMELT, taken);
        }
    }
}
