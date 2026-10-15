package com.hp.skillatlas.mixin;

import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.server.FurnaceSkills;
import com.hp.skillatlas.server.FurnaceState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceSkillsMixin implements FurnaceState {
    @Shadow private int cookingTimer;
    @Shadow private int cookingTotalTime;
    @Shadow private int litTimeRemaining;
    @Shadow private int litTotalTime;
    @Unique private UUID skillAtlas$operator;
    @Unique private double skillAtlas$progressCarry;

    @Override public UUID skillAtlas$owner() { return skillAtlas$operator; }
    @Override public void skillAtlas$owner(UUID owner) { skillAtlas$operator = owner; }
    @Override public double skillAtlas$carry() { return skillAtlas$progressCarry; }
    @Override public void skillAtlas$carry(double carry) { skillAtlas$progressCarry = carry; }
    @Override public int skillAtlas$progress() { return cookingTimer; }
    @Override public void skillAtlas$progress(int progress) { cookingTimer = progress; }
    @Override public int skillAtlas$totalTime() { return cookingTotalTime; }
    @Override public int skillAtlas$burnTime() { return litTimeRemaining; }
    @Override public void skillAtlas$burnTime(int time) { litTimeRemaining = time; }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void skillAtlas$save(ValueOutput output, CallbackInfo ci) {
        if (skillAtlas$operator != null) {
            output.store("SkillAtlasOperator", UUIDUtil.CODEC, skillAtlas$operator);
        }
        output.putDouble("SkillAtlasProgressCarry", skillAtlas$progressCarry);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void skillAtlas$load(ValueInput input, CallbackInfo ci) {
        skillAtlas$operator = input.read("SkillAtlasOperator", UUIDUtil.CODEC).orElse(null);
        double carry = input.getDoubleOr("SkillAtlasProgressCarry", 0);
        skillAtlas$progressCarry = Double.isFinite(carry) ? Math.max(0, Math.min(Math.nextDown(1.0D), carry)) : 0;
    }

    @Redirect(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;cookingTimer:I", opcode = Opcodes.PUTFIELD))
    private static void skillAtlas$advance(AbstractFurnaceBlockEntity furnace, int value) {
        ((FurnaceState) furnace).skillAtlas$progress(FurnaceSkills.advance(furnace, value));
    }

    @Redirect(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;litTimeRemaining:I", opcode = Opcodes.PUTFIELD))
    private static void skillAtlas$fuel(AbstractFurnaceBlockEntity furnace, int value) {
        FurnaceState state = (FurnaceState) furnace;
        if (state.skillAtlas$burnTime() <= 0 && value > 0) {
            double bonus = FurnaceSkills.value(furnace, SkillAttributes.FUEL_EFFICIENCY);
            value = (int) Math.min(Integer.MAX_VALUE, Math.round(value * (1 + bonus / 100.0D)));
        }
        state.skillAtlas$burnTime(value);
    }

    @WrapOperation(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;litTotalTime:I", opcode = Opcodes.PUTFIELD))
    private static void skillAtlas$fuelGauge(AbstractFurnaceBlockEntity furnace, int value, Operation<Void> original) {
        original.call(furnace, ((FurnaceState) furnace).skillAtlas$burnTime());
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void skillAtlas$doubleOutput(NonNullList<ItemStack> items, ItemStack input, ItemStack result, Operation<Void> original, ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        boolean raw = input.is(Tags.Items.RAW_MATERIALS);
        original.call(items, input, result);
        if (raw) {
            double chance = FurnaceSkills.value(furnace, SkillAttributes.RAW_ORE_DOUBLE_CHANCE);
            ItemStack output = items.get(2);
            int extra = result.getCount();
            if (chance > 0 && ItemStack.isSameItemSameComponents(output, result)
                    && (long) output.getCount() + extra <= Math.min(furnace.getMaxStackSize(), output.getMaxStackSize())
                    && level.getRandom().nextDouble() < chance / 100.0D) {
                output.grow(extra);
            }
        }
    }

    @Redirect(method = "awardUsedRecipesAndPopExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;getRecipesToAwardAndPopExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;)Ljava/util/List;"))
    private List<RecipeHolder<?>> skillAtlas$experienceContext(AbstractFurnaceBlockEntity furnace, ServerLevel level, Vec3 pos) {
        return FurnaceSkills.collectExperience(furnace, level, pos);
    }

    @Redirect(method = "createExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private static void skillAtlas$experience(ServerLevel level, Vec3 pos, int amount) {
        ExperienceOrb.award(level, pos, FurnaceSkills.experienceAmount(amount, level));
    }
}
