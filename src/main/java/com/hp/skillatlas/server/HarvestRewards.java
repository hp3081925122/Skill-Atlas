package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class HarvestRewards {
    private HarvestRewards() {
    }

    public static boolean mature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }
        return state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= 2;
    }

    public static void harvest(ServerPlayer player, BlockState state) {
        if (player instanceof FakePlayer || player.isCreative() || player.isSpectator() || !mature(state)) {
            return;
        }
        PointTracker.harvest(player, state);
        double amount = SkillAttributes.value(player, SkillAttributes.HARVEST_EXPERIENCE);
        if (amount > 0) {
            int experience = SkillAttributes.roll(amount, player.getRandom());
            player.giveExperiencePoints(experience);
            SkillAtlas.LOGGER.debug("Crop harvest experience for {}: {} points", player.getGameProfile().getName(), experience);
        }
    }
}
