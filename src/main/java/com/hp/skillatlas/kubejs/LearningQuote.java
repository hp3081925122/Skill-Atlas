package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import java.util.*;

public record LearningQuote(String nodeId, long revision, int level, String token, long expires, boolean allowed,
                            String code, Component message, JsonObject cost, List<ConditionLine> conditions,
                            List<Component> currentRewards, List<Component> nextRewards) {
    public record ConditionLine(boolean met, Component description) {}
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId); result.put("revision", revision); result.put("level", level); result.put("token", token); result.put("expires", expires);
        result.put("allowed", allowed); result.put("code", code); result.put("message", message); result.put("cost", Values.snapshot(cost));
        result.put("conditions", conditions.stream().map(c -> Map.of("met", c.met(), "description", c.description())).toList());
        result.put("currentRewards", currentRewards); result.put("nextRewards", nextRewards);
        return Collections.unmodifiableMap(result);
    }
}
