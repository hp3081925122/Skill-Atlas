package com.hp.skillatlas.mixin;

import com.hp.skillatlas.server.FurnaceSkills;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceMenu.class)
public abstract class FurnaceQuickMoveMixin {
    @Unique private FurnaceSkills.InputSnapshot skillAtlas$quickMoveSnapshot;

    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void skillAtlas$beforeMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        skillAtlas$quickMoveSnapshot = FurnaceSkills.beforeClick((AbstractContainerMenu) (Object) this, player);
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void skillAtlas$afterMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        FurnaceSkills.afterClick(skillAtlas$quickMoveSnapshot, player);
        skillAtlas$quickMoveSnapshot = null;
    }
}
