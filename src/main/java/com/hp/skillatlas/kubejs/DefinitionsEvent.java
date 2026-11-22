package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.data.*;
import dev.latvian.mods.kubejs.event.KubeEvent;
import java.util.*;
import java.util.function.Consumer;

public final class DefinitionsEvent implements KubeEvent {
    private final AtlasDefinition draft;
    private boolean open = true;

    public DefinitionsEvent(AtlasDefinition base) { draft = base.copy(); ExtraData.copy(base, draft); }
    void check() { if (!open) throw new IllegalStateException("Definition builder is closed"); }
    AtlasDefinition draft() { check(); return draft; }

    public NodeBuilder createNode(String id) {
        check(); Values.id(id);
        if (draft.nodes.containsKey(id)) throw new IllegalArgumentException("Duplicate node " + id);
        SkillNode n = new SkillNode(); n.id = id; n.name = id;
        draft.nodes.put(id, n);
        return new NodeBuilder(this, id);
    }

    public CategoryBuilder createCategory(String id) {
        check(); Values.id(id);
        if (draft.categories.containsKey(id)) throw new IllegalArgumentException("Duplicate category " + id);
        draft.categories.put(id, new Category(id, id, Icon.item("minecraft:book"), ""));
        return new CategoryBuilder(this, id);
    }

    public PoolBuilder createPool(String id) {
        check(); Values.id(id);
        if (draft.pools.containsKey(id)) throw new IllegalArgumentException("Duplicate pool " + id);
        draft.pools.put(id, new PointPool(id, id, 0x55ffff, 0));
        return new PoolBuilder(this, id);
    }

    public void modifyNode(String id, Consumer<NodeBuilder> action) {
        check(); if (!draft.nodes.containsKey(id)) throw new IllegalArgumentException("Unknown node " + id);
        action.accept(new NodeBuilder(this, id));
    }
    public void modifyCategory(String id, Consumer<CategoryBuilder> action) {
        check(); if (!draft.categories.containsKey(id)) throw new IllegalArgumentException("Unknown category " + id);
        action.accept(new CategoryBuilder(this, id));
    }
    public void modifyPool(String id, Consumer<PoolBuilder> action) {
        check(); if (!draft.pools.containsKey(id)) throw new IllegalArgumentException("Unknown pool " + id);
        action.accept(new PoolBuilder(this, id));
    }
    public void removeNode(String id) { check(); draft.nodes.remove(id); ExtraData.get(draft).remove(id); }
    public void removeCategory(String id) { check(); draft.categories.remove(id); }
    public void removePool(String id) { check(); draft.pools.remove(id); Acquisitions.remove(draft, id); }
    public boolean hasNode(String id) { check(); return draft.nodes.containsKey(id); }
    public List<String> getNodeIds() { check(); return List.copyOf(draft.nodes.keySet()); }
    public Object getSnapshot() { check(); var json = draft.toJson(); ExtraData.write(draft, json); return Values.snapshot(json); }

    public AtlasDefinition finish() { return finish(TypeRegistry.current()); }
    AtlasDefinition finish(TypeRegistry registry) {
        check(); open = false;
        var json = draft.toJson(); ExtraData.write(draft, json);
        AtlasDefinition result = AtlasDefinition.fromJson(json);
        result.texts = draft.texts.copy();
        ExtraData.read(json, result); ExtraData.validate(result, registry);
        return result;
    }
    public void close() { open = false; }
}
