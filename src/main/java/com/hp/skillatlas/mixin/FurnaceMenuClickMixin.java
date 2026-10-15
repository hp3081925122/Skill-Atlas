package com.hp.skillatlas.mixin;

import com.hp.skillatlas.server.FurnaceSkills;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class FurnaceMenuClickMixin {
    @Unique private FurnaceSkills.InputSnapshot skillAtlas$clickSnapshot;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void skillAtlas$beforeClick(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        skillAtlas$clickSnapshot = FurnaceSkills.beforeClick((AbstractContainerMenu) (Object) this, player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void skillAtlas$afterClick(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        FurnaceSkills.afterClick(skillAtlas$clickSnapshot, player);
        skillAtlas$clickSnapshot = null;
    }
}
