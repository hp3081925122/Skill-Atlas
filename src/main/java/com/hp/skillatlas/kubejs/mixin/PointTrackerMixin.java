package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.server.PointTracker;
import com.hp.skillatlas.kubejs.Hooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PointTracker.class, remap = false)
public abstract class PointTrackerMixin {
    @Inject(method = "progress", at = @At("HEAD"), cancellable = true)
    private static void atlasKjsProgress(ServerPlayer player, PointPool pool, long amount, CallbackInfo ci) {
        Hooks.naturalProgress(player, pool, amount);
        ci.cancel();
    }
}
