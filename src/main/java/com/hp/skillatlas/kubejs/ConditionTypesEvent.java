package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import java.util.*;
import java.util.function.Consumer;

public final class ConditionTypesEvent extends EventJS {
    private final Map<String, ConditionType> types = new LinkedHashMap<>();
    private boolean open = true;
    public void register(String id, Consumer<ConditionType> action) {
        if (!open) throw new IllegalStateException("Type registration is closed");
        ExtraData.typeId(id);
        if (types.size() >= 128 || types.containsKey(id)) throw new IllegalArgumentException("Duplicate or excessive condition types: " + id);
        ConditionType type = new ConditionType(); types.put(id, type);
        try { action.accept(type); } finally { type.close(); }
    }
    Map<String, ConditionType> finish() { open = false; types.values().forEach(ConditionType::close); return Map.copyOf(types); }
    void close() { open = false; }
}
