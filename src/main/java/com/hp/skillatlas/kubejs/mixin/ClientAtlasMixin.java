package com.hp.skillatlas.kubejs.mixin;

import com.google.gson.JsonObject;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.kubejs.client.ClientQuotes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientAtlas.class, remap = false)
public abstract class ClientAtlasMixin {
    @Inject(method = "learn", at = @At("HEAD"), cancellable = true)
    private static void atlasKjsLearn(String node, CallbackInfo ci) { if (ClientQuotes.learn(node)) ci.cancel(); }
    @Inject(method = "onFull", at = @At("RETURN"))
    private static void atlasKjsFull(JsonObject json, CallbackInfo ci) { ClientQuotes.invalidate(); com.hp.skillatlas.kubejs.client.ClientTooltips.invalidate(); }
    @Inject(method = "onPatch", at = @At("RETURN"))
    private static void atlasKjsPatch(JsonObject json, CallbackInfo ci) { ClientQuotes.invalidate(); com.hp.skillatlas.kubejs.client.ClientTooltips.invalidate(); }
    @Inject(method = "onProgress", at = @At("RETURN"))
    private static void atlasKjsProgress(PlayerProgress p, boolean edit, CallbackInfo ci) { ClientQuotes.invalidate(); com.hp.skillatlas.kubejs.client.ClientTooltips.invalidate(); com.hp.skillatlas.kubejs.client.ClientNavigation.progress(); }
}
