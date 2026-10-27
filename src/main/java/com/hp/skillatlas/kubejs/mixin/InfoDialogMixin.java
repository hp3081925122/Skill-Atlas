package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.client.screen.InfoDialog;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.kubejs.client.ClientQuotes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = InfoDialog.class, remap = false)
public abstract class InfoDialogMixin {
    @Shadow @Final private String nodeId;
    @ModifyExpressionValue(method = "build", at = @At(value = "INVOKE", target = "Lcom/hp/skillatlas/data/AtlasLogic;check(Lcom/hp/skillatlas/data/AtlasDefinition;Lcom/hp/skillatlas/data/SkillNode;Lcom/hp/skillatlas/data/PlayerProgress;Ljava/util/function/Predicate;J)Lcom/hp/skillatlas/data/AtlasLogic$Check;"))
    private AtlasLogic.Check atlasKjsPending(AtlasLogic.Check original) {
        return ClientQuotes.required(nodeId) && ClientQuotes.get(nodeId) == null ? AtlasLogic.Check.LOCKED : original;
    }
}
