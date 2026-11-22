package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.data.*;
import com.hp.skillatlas.kubejs.client.ClientQuotes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = AtlasLogic.class, remap = false)
public abstract class ClientLogicMixin {
    @WrapMethod(method = "check(Lcom/hp/skillatlas/data/AtlasDefinition;Lcom/hp/skillatlas/data/SkillNode;Lcom/hp/skillatlas/data/PlayerProgress;Ljava/util/function/Predicate;J)Lcom/hp/skillatlas/data/AtlasLogic$Check;")
    private static AtlasLogic.Check atlasKjsCheck(AtlasDefinition definition, SkillNode node, PlayerProgress p, Predicate<List<ItemCost>> items, long xp, Operation<AtlasLogic.Check> original) {
        if (p != ClientAtlas.progress || node == null) return original.call(definition, node, p, items, xp);
        AtlasLogic.Check result = original.call(definition, ClientQuotes.node(node), p, items, xp);
        return ClientQuotes.check(node, result);
    }
}
