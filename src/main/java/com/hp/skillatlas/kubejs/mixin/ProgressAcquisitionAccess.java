package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.data.PlayerProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(value = PlayerProgress.class, remap = false)
public interface ProgressAcquisitionAccess {
    @Accessor("awarded") Map<String, Long> atlasKjsAwarded();
}
