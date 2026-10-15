package com.hp.skillatlas.mixin;

import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.server.FurnaceSkills;
import com.hp.skillatlas.server.FurnaceState;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceSkillsMixin implements FurnaceState {
    @Shadow int cookingProgress;
    @Shadow int cookingTotalTime;
    @Shadow int litTime;
    @Shadow int litDuration;
    @Unique private UUID skillAtlas$operator;
    @Unique private double skillAtlas$progressCarry;
    @Unique private boolean skillAtlas$rawInput;
    @Unique private int skillAtlas$outputBefore;

    @Override public UUID skillAtlas$owner() { return skillAtlas$operator; }
    @Override public void skillAtlas$owner(UUID owner) { skillAtlas$operator = owner; }
    @Override public double skillAtlas$carry() { return skillAtlas$progressCarry; }
    @Override public void skillAtlas$carry(double carry) { skillAtlas$progressCarry = carry; }
    @Override public int skillAtlas$progress() { return cookingProgress; }
    @Override public void skillAtlas$progress(int progress) { cookingProgress = progress; }
    @Override public int skillAtlas$totalTime() { return cookingTotalTime; }
    @Override public int skillAtlas$burnTime() { return litTime; }
    @Override public void skillAtlas$burnTime(int time) { litTime = time; }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void skillAtlas$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (skillAtlas$operator != null) {
            tag.putUUID("SkillAtlasOperator", skillAtlas$operator);
        } else {
            tag.remove("SkillAtlasOperator");
        }
        tag.putDouble("SkillAtlasProgressCarry", skillAtlas$progressCarry);
        tag.putInt("BurnTime", litTime);
        tag.putInt("CookTime", cookingProgress);
        tag.putInt("CookTimeTotal", cookingTotalTime);
        tag.putInt("SkillAtlasFuelDuration", litDuration);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void skillAtlas$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        skillAtlas$operator = tag.hasUUID("SkillAtlasOperator") ? tag.getUUID("SkillAtlasOperator") : null;
        double carry = tag.getDouble("SkillAtlasProgressCarry");
        skillAtlas$progressCarry = Double.isFinite(carry) ? Math.max(0, Math.min(Math.nextDown(1.0D), carry)) : 0;
        litTime = Math.max(0, tag.getInt("BurnTime"));
        cookingProgress = Math.max(0, tag.getInt("CookTime"));
        cookingTotalTime = Math.max(0, tag.getInt("CookTimeTotal"));
        litDuration = Math.max(0, tag.getInt("SkillAtlasFuelDuration"));
    }

    @Redirect(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;cookingProgress:I", opcode = Opcodes.PUTFIELD))
    private static void skillAtlas$advance(AbstractFurnaceBlockEntity furnace, int value) {
        ((FurnaceState) furnace).skillAtlas$progress(FurnaceSkills.advance(furnace, value));
    }

    @Redirect(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;litTime:I", opcode = Opcodes.PUTFIELD))
    private static void skillAtlas$fuel(AbstractFurnaceBlockEntity furnace, int value) {
        FurnaceState state = (FurnaceState) furnace;
        if (state.skillAtlas$burnTime() <= 0 && value > 0) {
            double bonus = FurnaceSkills.value(furnace, SkillAttributes.FUEL_EFFICIENCY);
            value = (int) Math.min(Integer.MAX_VALUE, Math.round(value * (1 + bonus / 100.0D)));
        }
        state.skillAtlas$burnTime(value);
    }

    @Inject(method = "burn", at = @At("HEAD"))
    private static void skillAtlas$beforeSmelt(RegistryAccess registry, RecipeHolder<?> recipe, NonNullList<ItemStack> items, int limit, AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Boolean> cir) {
        FurnaceSkillsMixin state = (FurnaceSkillsMixin) (Object) furnace;
        state.skillAtlas$rawInput = items.get(0).is(Tags.Items.RAW_MATERIALS);
        state.skillAtlas$outputBefore = items.get(2).getCount();
    }

    @Inject(method = "burn", at = @At("RETURN"))
    private static void skillAtlas$afterSmelt(RegistryAccess registry, RecipeHolder<?> recipe, NonNullList<ItemStack> items, int limit, AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Boolean> cir) {
        FurnaceSkillsMixin state = (FurnaceSkillsMixin) (Object) furnace;
        if (cir.getReturnValue() && state.skillAtlas$rawInput) {
            double chance = FurnaceSkills.value(furnace, SkillAttributes.RAW_ORE_DOUBLE_CHANCE);
            ItemStack output = items.get(2);
            int extra = output.getCount() - state.skillAtlas$outputBefore;
            if (chance > 0 && extra > 0 && output.getCount() + extra <= Math.min(limit, output.getMaxStackSize())
                    && furnace.getLevel().getRandom().nextDouble() < chance / 100.0D) {
                output.grow(extra);
            }
        }
        state.skillAtlas$outputBefore = 0;
        state.skillAtlas$rawInput = false;
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
