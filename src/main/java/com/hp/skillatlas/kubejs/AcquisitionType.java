package com.hp.skillatlas.kubejs;

import java.util.*;
import java.util.function.*;

public final class AcquisitionType {
    String name = "", description = "", unit = "count";
    Predicate<Map<String, Object>> validator = data -> true;
    BiPredicate<AcquisitionContext, Map<String, Object>> test = (ctx, data) -> true;
    private boolean open = true;
    private void check() { if (!open) throw new IllegalStateException("Acquisition type is closed"); }
    public AcquisitionType name(String value) { check(); name = text(value); return this; }
    public AcquisitionType description(String value) { check(); description = text(value); return this; }
    public AcquisitionType unit(String value) { check(); if (!Set.of("count", "seconds", "centimeters").contains(value)) throw new IllegalArgumentException("Invalid acquisition unit"); unit = value; return this; }
    public AcquisitionType validate(Predicate<Map<String, Object>> action) { check(); validator = Objects.requireNonNull(action); return this; }
    public AcquisitionType test(BiPredicate<AcquisitionContext, Map<String, Object>> action) { check(); test = Objects.requireNonNull(action); return this; }
    private static String text(String value) { if (value == null || value.isBlank() || value.length() > 512) throw new IllegalArgumentException("Invalid acquisition text"); return value; }
    void close() { open = false; text(name); if (description.isEmpty()) description = name; }
}
