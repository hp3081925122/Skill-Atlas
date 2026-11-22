package com.hp.skillatlas.kubejs;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

public final class RewardType {
    Predicate<Map<String, Object>> validator = data -> true;
    BiFunction<RewardContext, Map<String, Object>, Component> description;
    BiConsumer<RewardContext, Map<String, Object>> learned;
    BiConsumer<RewardContext, Map<String, Object>> activate;
    BiConsumer<RewardContext, Map<String, Object>> deactivate;
    private boolean open = true;
    private void check() { if (!open) throw new IllegalStateException("Reward type registration is closed"); }
    public RewardType validate(Predicate<Map<String, Object>> callback) { check(); validator = Objects.requireNonNull(callback); return this; }
    public RewardType describe(BiFunction<RewardContext, Map<String, Object>, Component> callback) { check(); description = Objects.requireNonNull(callback); return this; }
    public RewardType onLearn(BiConsumer<RewardContext, Map<String, Object>> callback) { check(); learned = Objects.requireNonNull(callback); return this; }
    public RewardType onActivate(BiConsumer<RewardContext, Map<String, Object>> callback) { check(); activate = Objects.requireNonNull(callback); return this; }
    public RewardType onDeactivate(BiConsumer<RewardContext, Map<String, Object>> callback) { check(); deactivate = Objects.requireNonNull(callback); return this; }
    void close() {
        open = false;
        if (description == null || (activate == null) != (deactivate == null) || (learned == null && activate == null)) throw new IllegalArgumentException("Reward types require describe and onLearn or paired onActivate/onDeactivate");
    }
}
