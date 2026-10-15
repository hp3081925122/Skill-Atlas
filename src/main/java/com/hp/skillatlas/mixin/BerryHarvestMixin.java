package com.hp.skillatlas.mixin;

import com.hp.skillatlas.server.HarvestRewards;
import com.hp.skillatlas.server.FarmingBonuses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SweetBerryBushBlock.class)
public abstract class BerryHarvestMixin {
    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/SweetBerryBushBlock;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
    private void skillAtlas$berryYield(Level dropLevel, BlockPos dropPos, ItemStack drop, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        FarmingBonuses.berries(state, dropLevel, dropPos, player, drop);
    }

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void skillAtlas$harvest(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && cir.getReturnValue().consumesAction()
                && state.getValue(SweetBerryBushBlock.AGE) > 1
                && level.getBlockState(pos).is(state.getBlock())
                && level.getBlockState(pos).getValue(SweetBerryBushBlock.AGE) == 1) {
            HarvestRewards.harvest(serverPlayer, state);
        }
    }
}
