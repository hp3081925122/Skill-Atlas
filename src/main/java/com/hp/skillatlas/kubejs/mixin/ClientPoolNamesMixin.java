package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.kubejs.Acquisitions;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Names.class, remap = false)
public abstract class ClientPoolNamesMixin {
    @Inject(method = "earn", at = @At("HEAD"), cancellable = true)
    private static void atlasKjsEarn(PointPool pool, CallbackInfoReturnable<MutableComponent> cir) {
        var binding = Acquisitions.binding(ClientAtlas.definition, pool.id);
        if (binding != null) cir.setReturnValue(Component.translatable(binding.get("description").getAsString()));
    }
    @Inject(method = "earnProgress", at = @At("HEAD"), cancellable = true)
    private static void atlasKjsProgress(PointPool pool, long counter, long awarded, CallbackInfoReturnable<MutableComponent> cir) {
        var binding = Acquisitions.binding(ClientAtlas.definition, pool.id);
        if (binding == null) return;
        MutableComponent result = Component.translatable("skill_atlas_kubejs.acquisition_progress", Component.translatable(binding.get("name").getAsString()), Long.toString(counter), Integer.toString(pool.count), Component.translatable("skill_atlas_kubejs.unit." + binding.get("unit").getAsString()));
        if (pool.limit > 0) result.append(Component.translatable("skill_atlas_kubejs.acquisition_limit", Long.toString(awarded), Integer.toString(pool.limit)));
        cir.setReturnValue(result);
    }
}
