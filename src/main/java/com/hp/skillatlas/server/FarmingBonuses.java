package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.function.BiConsumer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;

public final class FarmingBonuses {
    private FarmingBonuses() {
    }

    private static boolean eligible(Player player, BlockState state) {
        return player instanceof ServerPlayer && !(player instanceof FakePlayer)
                && !player.isCreative() && !player.isSpectator() && HarvestRewards.mature(state);
    }

    public static List<ItemStack> drops(BlockState state, ServerLevel level, BlockPos pos, Entity entity, List<ItemStack> original) {
        if (!(entity instanceof ServerPlayer player) || !eligible(player, state)
                || !level.getGameRules().get(GameRules.BLOCK_DROPS) || level.restoringBlockSnapshots) {
            return original;
        }
        double yield = SkillAttributes.value(player, SkillAttributes.HARVEST_YIELD);
        boolean replant = SkillAttributes.value(player, SkillAttributes.CROP_REPLANTING) > 0;
        if (yield <= 0 && !replant) {
            return original;
        }
        List<ItemStack> drops = new ArrayList<>(original);
        increaseYield(player, drops, yield);
        if (replant) {
            replant(player, state, level, pos, drops);
        }
        return drops;
    }

    private static void increaseYield(ServerPlayer player, List<ItemStack> drops, double amount) {
        if (amount <= 0) {
            return;
        }
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack.isEmpty() || stack.is(Tags.Items.SEEDS)) {
                continue;
            }
            int bonus = SkillAttributes.roll(stack.getCount() * amount / 100.0D, player.getRandom());
            while (bonus > 0) {
                ItemStack added = stack.copy();
                int count = Math.min(bonus, Math.max(1, added.getMaxStackSize()));
                added.setCount(count);
                extra.add(added);
                bonus -= count;
            }
        }
        drops.addAll(extra);
    }

    private static void replant(ServerPlayer player, BlockState state, ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        BlockState planted;
        if (state.getBlock() instanceof CropBlock crop) {
            planted = crop.getStateForAge(0);
        } else if (state.getBlock() instanceof NetherWartBlock) {
            planted = state.setValue(NetherWartBlock.AGE, 0);
        } else if (state.getBlock() instanceof CocoaBlock) {
            planted = state.setValue(CocoaBlock.AGE, 0);
        } else if (state.getBlock() instanceof SweetBerryBushBlock) {
            planted = state.setValue(SweetBerryBushBlock.AGE, 0);
        } else {
            return;
        }
        if (!level.getBlockState(pos).isAir() || !planted.canSurvive(level, pos)) {
            return;
        }
        ItemStack seed = state.getCloneItemStack(level, pos, false);
        if (seed.isEmpty()) {
            return;
        }
        List<ItemStack> candidates = new ArrayList<>(drops);
        candidates.addAll(player.getInventory().getNonEquipmentItems());
        candidates.add(player.getOffhandItem());
        for (ItemStack stack : candidates) {
            if (!stack.isEmpty() && stack.is(seed.getItem())) {
                if (level.setBlock(pos, planted, 3)) {
                    stack.shrink(1);
                    player.getInventory().setChanged();
                    SkillAtlas.LOGGER.debug("Crop replanted: player={}, position={}, block={}", player.getScoreboardName(), pos, planted);
                }
                return;
            }
        }
    }

    public static void berries(BlockState state, ServerLevel level, BlockPos pos, Player player, ItemStack original, BiConsumer<ServerLevel, ItemStack> drop) {
        if (!(player instanceof ServerPlayer serverPlayer) || !eligible(player, state)
                || !level.getGameRules().get(GameRules.BLOCK_DROPS) || level.restoringBlockSnapshots) {
            drop.accept(level, original);
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        drops.add(original);
        increaseYield(serverPlayer, drops, SkillAttributes.value(player, SkillAttributes.HARVEST_YIELD));
        for (ItemStack stack : drops) {
            drop.accept(level, stack);
        }
    }
}
