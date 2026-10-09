package com.hp.skillatlas.api;

import com.hp.skillatlas.model.Definition;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public interface RewardType {
    List<String> validate(Definition.Reward reward);
    void apply(ServerPlayer player, Definition.Reward reward, UUID key, RewardContext context);
    default void revoke(ServerPlayer player, Definition.Reward reward, UUID key) {}
}
