package com.hp.skillatlas.mixin;

import com.hp.skillatlas.client.EnchantingTooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
    @Unique private int skillAtlas$mouseX;
    @Unique private int skillAtlas$mouseY;

    @Inject(method = "render", at = @At("HEAD"))
    private void skillAtlas$mouse(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        skillAtlas$mouseX = mouseX;
        skillAtlas$mouseY = mouseY;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"), index = 1)
    private List<Component> skillAtlas$allEnchantments(List<Component> original) {
        EnchantmentScreen screen = (EnchantmentScreen) (Object) this;
        return EnchantingTooltip.expand(screen.getMenu(), original, skillAtlas$mouseX, skillAtlas$mouseY, screen.getGuiLeft(), screen.getGuiTop());
    }
}
