package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.*;
import java.util.Map;

public final class RankBuilder {
    private final DefinitionsEvent event;
    private final String node;
    private final int index;
    RankBuilder(DefinitionsEvent event, String node, int index) { this.event = event; this.node = node; this.index = index; }
    private SkillLevel rank() { return event.draft().nodes.get(node).levels.get(index); }
    public RankBuilder cost(Map<?, ?> value) {
        Object type = value.get("type");
        if (!"experience".equals(type) && !"skill_points".equals(type)) throw new IllegalArgumentException("Cost type must be experience or skill_points");
        rank().experience = "experience".equals(type);
        rank().cost = (int)Values.integer((Number)value.get("amount"), 0, 1000000000);
        return this;
    }
    public RankBuilder item(String item, Number count) { return item(item, count, ""); }
    public RankBuilder item(String item, Number count, String nbt) {
        rank().items.add(new ItemCost(Json.resource(item, "item"), (int)Values.integer(count, 1, ItemCost.MAX_COUNT), nbt)); return this;
    }
    public RankBuilder clearItems() { rank().items.clear(); return this; }
    public RankBuilder reward(Map<?, ?> value) {
        Object type = value.get("type");
        if (type instanceof String id && id.contains(":")) return customReward(id, (Map<?, ?>)value.get("data"));
        rank().rewards.add(Reward.fromJson(Values.json(value))); return this;
    }
    public RankBuilder condition(String type, Map<?, ?> data) { rank(); ExtraData.append(ExtraData.rank(event.draft(), node, index + 1), "conditions", type, data); return this; }
    public RankBuilder clearConditions() { rank(); ExtraData.rank(event.draft(), node, index + 1).remove("conditions"); return this; }
    public RankBuilder customReward(String type, Map<?, ?> data) { rank(); ExtraData.append(ExtraData.rank(event.draft(), node, index + 1), "rewards", type, data); return this; }
    public RankBuilder clearCustomRewards() { rank(); ExtraData.rank(event.draft(), node, index + 1).remove("rewards"); return this; }
    public RankBuilder attributeReward(String id, double value, String operation) {
        JsonObject reward = new JsonObject(); reward.addProperty("type", "attribute"); reward.addProperty("target", id); reward.addProperty("amount", value); reward.addProperty("operation", operation);
        rank().rewards.add(Reward.fromJson(reward)); return this;
    }
    public RankBuilder effectReward(String id, Number amplifier, Number duration, Number interval) {
        rank().rewards.add(Reward.effect(Json.resource(id, "effect"), (int)Values.integer(amplifier, 0, 255), (int)Values.integer(duration, 1, 72000), (int)Values.integer(interval, 1, 72000))); return this;
    }
    public RankBuilder immunityReward(String id) { rank().rewards.add(Reward.immunity(Json.resource(id, "effect"))); return this; }
    public RankBuilder commandReward(String command) {
        JsonObject reward = new JsonObject(); reward.addProperty("type", "command"); reward.addProperty("command", command); rank().rewards.add(Reward.fromJson(reward)); return this;
    }
    public RankBuilder clearRewards() { rank().rewards.clear(); return this; }
    public RankBuilder json(Map<?, ?> patch) {
        if (patch.containsKey("kubejs")) Values.plain(patch.get("kubejs"));
        JsonObject json = rank().toJson(); Values.json(patch).entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        if (json.has("kubejs")) {
            ExtraData.node(event.draft(), node);
            var levels = ExtraData.node(event.draft(), node).getAsJsonObject("levels");
            if (levels == null) { ExtraData.rank(event.draft(), node, index + 1); levels = ExtraData.node(event.draft(), node).getAsJsonObject("levels"); }
            levels.add(Integer.toString(index + 1), json.remove("kubejs").deepCopy());
        }
        event.draft().nodes.get(node).levels.set(index, SkillLevel.fromJson(json)); return this;
    }
}
