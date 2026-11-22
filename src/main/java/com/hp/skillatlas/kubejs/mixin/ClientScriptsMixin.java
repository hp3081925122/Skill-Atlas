package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.kubejs.client.*;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScriptManager.class, remap = false)
public abstract class ClientScriptsMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void atlasKjsReload(CallbackInfo ci) {
        if (((ScriptManager)(Object)this).scriptType == ScriptType.CLIENT) { ClientTooltips.clear(); ClientNavigation.cancel(); }
    }
}
