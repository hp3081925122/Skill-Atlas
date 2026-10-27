package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import java.util.*;
import java.util.function.Consumer;

public final class RewardTypesEvent extends EventJS {
    private final Map<String, RewardType> types = new LinkedHashMap<>();
    private boolean open = true;
    public void register(String id, Consumer<RewardType> action) {
        if (!open) throw new IllegalStateException("Type registration is closed");
        ExtraData.typeId(id);
        if (types.size() >= 128 || types.containsKey(id)) throw new IllegalArgumentException("Duplicate or excessive reward types: " + id);
        RewardType type = new RewardType(); types.put(id, type);
        try { action.accept(type); } finally { type.close(); }
    }
    Map<String, RewardType> finish() { open = false; types.values().forEach(RewardType::close); return Map.copyOf(types); }
    void close() { open = false; }
}
