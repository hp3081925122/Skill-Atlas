package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.server.RewardManager;
import com.hp.skillatlas.kubejs.CustomRewards;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(value = RewardManager.class, remap = false)
public abstract class RewardManagerMixin {
    @Inject(method = "refresh", at = @At("RETURN"))
    private static void atlasKjsRewards(ServerPlayer player, CallbackInfo ci) { CustomRewards.refresh(player); }
    @Inject(method = "forget", at = @At("HEAD"))
    private static void atlasKjsForget(UUID player, CallbackInfo ci) { CustomRewards.forget(player, "logout"); }
    @Inject(method = "clear", at = @At("HEAD"))
    private static void atlasKjsClear(CallbackInfo ci) { CustomRewards.clear("stop"); }
}
