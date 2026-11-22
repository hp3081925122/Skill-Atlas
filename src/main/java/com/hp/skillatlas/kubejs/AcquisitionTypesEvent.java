package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import java.util.*;
import java.util.function.Consumer;

public final class AcquisitionTypesEvent implements KubeEvent {
    private final Map<String, AcquisitionType> types = new LinkedHashMap<>();
    private boolean open = true;
    public void register(String id, Consumer<AcquisitionType> action) {
        if (!open) throw new IllegalStateException("Type registration is closed");
        ExtraData.typeId(id);
        if (types.size() >= 128 || types.containsKey(id)) throw new IllegalArgumentException("Duplicate or excessive acquisition types: " + id);
        AcquisitionType type = new AcquisitionType(); types.put(id, type);
        try { action.accept(type); } finally { type.close(); }
    }
    Map<String, AcquisitionType> finish() { open = false; types.values().forEach(AcquisitionType::close); return Map.copyOf(types); }
    void close() { open = false; }
}
