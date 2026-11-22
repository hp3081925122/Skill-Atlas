package com.hp.skillatlas.kubejs.mixin;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.kubejs.ExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AtlasDefinition.class, remap = false)
public abstract class AtlasDefinitionMixin {
    @Inject(method = "toJson", at = @At("RETURN"))
    private void atlasKjsWrite(CallbackInfoReturnable<JsonObject> cir) { ExtraData.write((AtlasDefinition)(Object)this, cir.getReturnValue()); }
    @Inject(method = "fromJson", at = @At("RETURN"))
    private static void atlasKjsRead(JsonObject json, CallbackInfoReturnable<AtlasDefinition> cir) { ExtraData.read(json, cir.getReturnValue()); }
    @Inject(method = "copy", at = @At("RETURN"))
    private void atlasKjsCopy(CallbackInfoReturnable<AtlasDefinition> cir) { ExtraData.copy((AtlasDefinition)(Object)this, cir.getReturnValue()); }
}
