package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.*;
import java.util.*;
import java.util.function.Consumer;

public final class NodeBuilder {
    private final DefinitionsEvent event;
    private final String id;
    NodeBuilder(DefinitionsEvent event, String id) { this.event = event; this.id = id; }
    private SkillNode node() {
        SkillNode node = event.draft().nodes.get(id);
        if (node == null) throw new IllegalStateException("Node was removed: " + id);
        return node;
    }
    public NodeBuilder category(String id) { node().category = Values.id(id); return this; }
    public NodeBuilder name(String text) { node().name = Objects.requireNonNull(text); return this; }
    public NodeBuilder description(String text) { node().tooltip = Objects.requireNonNull(text); return this; }
    public NodeBuilder icon(Object icon) {
        if (icon instanceof Map<?, ?> map) node().icon = Icon.fromJson(Values.json(map));
        else if (icon instanceof CharSequence text) node().icon = Icon.item(Json.resource(text.toString(), "icon"));
        else throw new IllegalArgumentException("Icon must be an item ID or an icon object");
        return this;
    }
    public NodeBuilder position(Number x, Number y) { node().x = (int)Values.integer(x, -100000, 100000); node().y = (int)Values.integer(y, -100000, 100000); return this; }
    public NodeBuilder hidden(boolean value) { node().hidden = value; return this; }
    public NodeBuilder requireAll(boolean value) { node().requireAll = value; return this; }
    public NodeBuilder choiceGroup(String group) {
        String value = Objects.requireNonNull(group);
        if (!value.isEmpty()) {
            Values.id(value);
        }
        node().choiceGroup = value;
        return this;
    }
    public NodeBuilder requires(String id) { return requiresLevelGreaterThan(id, 0); }
    public NodeBuilder requiresLevelGreaterThan(String id, Number level) {
        SkillNode node = node(); Values.id(id);
        node.prerequisites.removeIf(p -> p.node.equals(id));
        node.prerequisites.add(new Prerequisite(id, true, (int)Values.integer(level, 0, 99)));
        return this;
    }
    public NodeBuilder clearPrerequisites() { node().prerequisites.clear(); return this; }
    public NodeBuilder condition(String type, Map<?, ?> data) { node(); ExtraData.append(ExtraData.node(event.draft(), id), "conditions", type, data); return this; }
    public NodeBuilder clearConditions() { node(); ExtraData.node(event.draft(), id).remove("conditions"); return this; }
    public NodeBuilder level(Number index, Consumer<RankBuilder> action) {
        int n = (int)Values.integer(index, 1, SkillNode.MAX_LEVELS);
        SkillNode node = node();
        if (n > node.levels.size() + 1) throw new IllegalArgumentException("Levels must be contiguous");
        if (n > node.levels.size()) {
            SkillLevel rank = new SkillLevel(); rank.experience = null; node.levels.add(rank);
        }
        action.accept(new RankBuilder(event, id, n - 1));
        return this;
    }
    public NodeBuilder clearLevels() { node().levels.clear(); ExtraData.node(event.draft(), id).remove("levels"); return this; }
    public NodeBuilder json(Map<?, ?> patch) {
        if (patch.containsKey("kubejs")) Values.plain(patch.get("kubejs"));
        JsonObject json = node().toJson();
        Values.json(patch).entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        if (json.has("kubejs")) { ExtraData.get(event.draft()).add(id, json.remove("kubejs").deepCopy()); }
        if (!id.equals(json.get("id").getAsString())) throw new IllegalArgumentException("Node IDs cannot be renamed");
        event.draft().nodes.put(id, SkillNode.fromJson(json)); return this;
    }
}
