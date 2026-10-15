package com.hp.skillatlas.mixin;

import com.hp.skillatlas.server.HarvestRewards;
import com.hp.skillatlas.server.FarmingBonuses;
import net.minecraft.core.BlockPos;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.entity.Entity;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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
    @WrapOperation(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropFromBlockInteractLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemInstance;Lnet/minecraft/world/entity/Entity;Ljava/util/function/BiConsumer;)Z"))
    private boolean skillAtlas$berryYield(ServerLevel level, ResourceKey<LootTable> table, BlockState state, BlockEntity blockEntity, ItemInstance tool, Entity entity, BiConsumer<ServerLevel, ItemStack> drop, Operation<Boolean> original, BlockState usedState, Level usedLevel, BlockPos pos, Player player, BlockHitResult hit) {
        return original.call(level, table, state, blockEntity, tool, entity,
                (BiConsumer<ServerLevel, ItemStack>) (dropLevel, stack) -> FarmingBonuses.berries(state, dropLevel, pos, player, stack, drop));
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
