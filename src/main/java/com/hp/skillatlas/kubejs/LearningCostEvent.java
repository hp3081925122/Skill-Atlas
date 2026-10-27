package com.hp.skillatlas.kubejs;

import com.google.gson.JsonArray;
import com.hp.skillatlas.data.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class LearningCostEvent extends AtlasEvent {
    public final Object baseCost;
    public String type;
    public double amount;
    public List<?> items;
    LearningCostEvent(ServerPlayer player, SkillNode node, int level, String phase) {
        super(player, "quote", phase, UUID.randomUUID().toString());
        nodeId = node.id; categoryId = node.category; oldLevel = level; newLevel = level + 1;
        SkillLevel rank = node.levels.get(level);
        type = com.hp.skillatlas.server.AtlasServer.definition().usesExperience(node, rank) ? "experience" : "skill_points";
        poolId = com.hp.skillatlas.server.AtlasServer.definition().poolOf(node);
        amount = rank.cost; baseCost = Values.snapshot(rank.toJson());
        items = rank.items.stream().map(i -> Values.snapshot(i.toJson())).toList();
    }
    SkillLevel freeze(SkillLevel base, String expectedPool) {
        if (!Objects.equals(poolId, expectedPool)) throw new IllegalArgumentException("Dynamic fees cannot change the category pool");
        if (!type.equals("experience") && !type.equals("skill_points")) throw new IllegalArgumentException("Invalid cost type");
        if (type.equals("skill_points") && expectedPool.isEmpty()) throw new IllegalArgumentException("This category has no skill point pool");
        var json = base.toJson(); json.addProperty("cost", Values.integer(amount, 0, 1000000000));
        json.addProperty("experience", type.equals("experience"));
        JsonArray list = new JsonArray();
        if (items == null || items.size() > SkillLevel.MAX_ITEMS) throw new IllegalArgumentException("Invalid delivery list");
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("Delivery entries must be objects");
            var value = Values.plain(map).getAsJsonObject();
            if (map.containsKey("count")) Values.integer((Number)map.get("count"), 1, ItemCost.MAX_COUNT);
            list.add(ItemCost.fromJson(value).toJson());
        }
        json.add("items", list);
        return SkillLevel.fromJson(json);
    }
}
