package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class StoneMineralsModifier extends LootModifier {
    public static final MapCodec<StoneMineralsModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, StoneMineralsModifier::new));

    public StoneMineralsModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (FortuneModifier.isRerolling()) {
            return loot;
        }
        BlockState state = ctx.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null || !state.is(Blocks.STONE)
                || !state.getBlock().getLootTable().location().equals(ctx.getQueriedLootTableId())
                || !(ctx.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)
                || player.getAbilities().instabuild || player.isSpectator()) {
            return loot;
        }
        double chance = SkillAttributes.value(player, SkillAttributes.STONE_MINERAL_CHANCE);
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance / 100.0D) {
            return loot;
        }
        int roll = player.getRandom().nextInt(20);
        ItemStack reward;
        if (roll < 10) {
            reward = new ItemStack(Items.COAL, 3);
        } else if (roll < 15) {
            reward = new ItemStack(Items.IRON_INGOT);
        } else if (roll < 18) {
            reward = new ItemStack(Items.GOLD_INGOT);
        } else {
            reward = new ItemStack(Items.DIAMOND);
        }
        loot.add(reward);
        SkillAtlas.LOGGER.debug("Stone mineral bonus for {} at {}: {}", player.getGameProfile().getName(), ctx.getParamOrNull(LootContextParams.ORIGIN), reward);
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
