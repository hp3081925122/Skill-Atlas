package com.hp.skillatlas.mixin;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.server.EnchantingBonuses;
import com.hp.skillatlas.server.EnchantingPreviews;
import com.hp.skillatlas.server.EnchantingTransactions;
import com.hp.skillatlas.server.EnchantmentMenuState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BiConsumer;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin implements EnchantmentMenuState {
    @Shadow @Final private RandomSource random;
    @Shadow private List<EnchantmentInstance> getEnchantmentList(RegistryAccess access, ItemStack stack, int slot, int cost) { throw new AssertionError(); }
    @Unique private Player skillAtlas$player;
    @Unique private CompoundTag skillAtlas$preview = new CompoundTag();

    @Override
    public List<EnchantmentInstance> skillAtlas$options(ItemStack stack, int slot, int cost) {
        return getEnchantmentList(skillAtlas$player.level().registryAccess(), stack, slot, cost);
    }

    @Override public CompoundTag skillAtlas$preview() { return skillAtlas$preview; }
    @Override public void skillAtlas$preview(CompoundTag preview) { skillAtlas$preview = preview; }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void skillAtlas$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        skillAtlas$player = inventory.player;
    }

    @Redirect(method = "clickMenuButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void skillAtlas$transaction(ContainerLevelAccess access, BiConsumer<Level, BlockPos> action) {
        EnchantingTransactions.run(skillAtlas$player, (EnchantmentMenu) (Object) this, access, action);
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void skillAtlas$preview(Container container, CallbackInfo ci) {
        EnchantingPreviews.sync(skillAtlas$player, (EnchantmentMenu) (Object) this);
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void skillAtlas$boostLevels(RegistryAccess access, ItemStack stack, int slot, int cost, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (skillAtlas$player == null) {
            return;
        }
        double chance = SkillAttributes.value(skillAtlas$player, SkillAttributes.ENCHANTING_LEVEL_CHANCE);
        List<EnchantmentInstance> original = cir.getReturnValue();
        if (chance > 0 && !original.isEmpty()) {
            List<EnchantmentInstance> boosted = EnchantingBonuses.apply(original, random, chance, stack.is(Items.BOOK) ? 255 : 127);
            cir.setReturnValue(boosted);
            SkillAtlas.LOGGER.debug("Enchanting level bonus: player={}, slot={}, chance={}, upgraded={}, total={}",
                    skillAtlas$player.getScoreboardName(), slot, chance,
                    boosted.stream().filter(entry -> !original.contains(entry)).count(), boosted.size());
        }
    }
}
