package com.hp.skillatlas.api;

import com.hp.skillatlas.model.Definition;
import net.minecraft.world.effect.MobEffect;

public interface RewardContext {
    void periodic(Definition.Reward reward);
    void immune(MobEffect effect);
}
