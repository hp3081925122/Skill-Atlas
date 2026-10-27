package com.hp.skillatlas.kubejs;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

public final class ConditionType {
    Predicate<Map<String, Object>> validator = data -> true;
    BiPredicate<ConditionContext, Map<String, Object>> test;
    BiFunction<ConditionContext, Map<String, Object>, Component> description;
    private boolean open = true;
    private void check() { if (!open) throw new IllegalStateException("Condition type registration is closed"); }
    public ConditionType validate(Predicate<Map<String, Object>> callback) { check(); validator = Objects.requireNonNull(callback); return this; }
    public ConditionType test(BiPredicate<ConditionContext, Map<String, Object>> callback) { check(); test = Objects.requireNonNull(callback); return this; }
    public ConditionType describe(BiFunction<ConditionContext, Map<String, Object>, Component> callback) { check(); description = Objects.requireNonNull(callback); return this; }
    void close() {
        open = false;
        if (test == null || description == null) throw new IllegalArgumentException("Condition types require test and describe");
    }
}
