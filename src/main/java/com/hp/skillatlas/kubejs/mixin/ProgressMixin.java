package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.kubejs.Hooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PlayerProgress.class, remap = false)
public abstract class ProgressMixin {
    @WrapMethod(method = "setPoints")
    private void atlasKjsPoints(String pool, long value, Operation<Void> original) {
        Hooks.balance((PlayerProgress)(Object)this, pool, value, target -> original.call(pool, target));
    }
}
