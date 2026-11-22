package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Supplier;

public final class TypeRegistry {
    private static TypeRegistry current = new TypeRegistry(Map.of(), Map.of());
    private static final ThreadLocal<Integer> EVALUATING = ThreadLocal.withInitial(() -> 0);
    final Map<String, ConditionType> conditions;
    final Map<String, RewardType> rewards;
    final Map<String, AcquisitionType> acquisitions;
    TypeRegistry(Map<String, ConditionType> conditions, Map<String, RewardType> rewards) {
        this(conditions, rewards, Map.of());
    }
    TypeRegistry(Map<String, ConditionType> conditions, Map<String, RewardType> rewards, Map<String, AcquisitionType> acquisitions) { this.conditions = conditions; this.rewards = rewards; this.acquisitions = acquisitions; }
    public static TypeRegistry current() { return current; }
    public static boolean evaluating() { return EVALUATING.get() > 0; }
    public static <T> T evaluate(Supplier<T> action) {
        EVALUATING.set(EVALUATING.get() + 1);
        try { return action.get(); }
        finally { int depth = EVALUATING.get() - 1; if (depth == 0) EVALUATING.remove(); else EVALUATING.set(depth); }
    }
    public static TypeRegistry build() {
        ConditionTypesEvent conditions = new ConditionTypesEvent(); RewardTypesEvent rewards = new RewardTypesEvent(); AcquisitionTypesEvent acquisitions = new AcquisitionTypesEvent();
        try {
            var a = AtlasEvents.post(AtlasEvents.CONDITION_TYPES, conditions, null);
            var b = AtlasEvents.post(AtlasEvents.REWARD_TYPES, rewards, null);
            var c = AtlasEvents.post(AtlasEvents.ACQUISITION_TYPES, acquisitions, null);
            if (a.type() == dev.latvian.mods.kubejs.event.EventResult.Type.ERROR || a.interruptFalse() || b.type() == dev.latvian.mods.kubejs.event.EventResult.Type.ERROR || b.interruptFalse() || c.type() == dev.latvian.mods.kubejs.event.EventResult.Type.ERROR || c.interruptFalse()) throw new IllegalStateException("Custom type registration failed");
            return new TypeRegistry(conditions.finish(), rewards.finish(), acquisitions.finish());
        } finally { conditions.close(); rewards.close(); acquisitions.close(); }
    }
    public static void commit(TypeRegistry registry) { current = registry; }
    public static void clear() { current = new TypeRegistry(Map.of(), Map.of()); EVALUATING.remove(); }
    @SuppressWarnings("unchecked")
    static Map<String, Object> data(JsonObject value) { return (Map<String, Object>)Values.snapshot(value); }
    void validate(boolean condition, String id, JsonObject data) {
        boolean valid = evaluate(() -> {
            if (condition) {
                ConditionType type = conditions.get(id);
                if (type == null) throw new IllegalArgumentException("Unknown condition type " + id);
                return type.validator.test(data(data));
            }
            RewardType type = rewards.get(id);
            if (type == null) throw new IllegalArgumentException("Unknown reward type " + id);
            return type.validator.test(data(data));
        });
        if (!valid) throw new IllegalArgumentException("Custom type data rejected by " + id);
    }
}
