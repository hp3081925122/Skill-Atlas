package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.client.screen.NodeTooltip;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.kubejs.client.ClientQuotes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = NodeTooltip.class, remap = false)
public abstract class NodeTooltipMixin {
    @WrapMethod(method = "build")
    private static List<Component> atlasKjsTooltip(SkillNode node, boolean hints, Operation<List<Component>> original) {
        List<Component> lines;
        if (!ClientQuotes.required(node.id)) lines = original.call(node, hints);
        else {
            ClientQuotes.view(node.id);
            var quote = ClientQuotes.get(node.id);
            lines = quote == null ? ClientQuotes.pending(node) : ClientQuotes.append(original.call(ClientQuotes.node(node), hints), quote);
        }
        return com.hp.skillatlas.kubejs.client.ClientTooltips.append(node, hints, lines);
    }
}
