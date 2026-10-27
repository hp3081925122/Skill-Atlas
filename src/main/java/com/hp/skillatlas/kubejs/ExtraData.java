package com.hp.skillatlas.kubejs;

import com.google.gson.*;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.data.Json;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExtraData {
    public static final String FIELD = "skill_atlas_kubejs";
    private static final Map<AtlasDefinition, JsonObject> DATA = Collections.synchronizedMap(new WeakHashMap<>());
    private ExtraData() {}
    public static String typeId(String id) {
        if (id == null || !id.contains(":") || id.length() > 128) throw new IllegalArgumentException("A namespaced type ID is required");
        return Json.resource(id, "type");
    }
    public static JsonObject get(AtlasDefinition definition) {
        synchronized (DATA) { return DATA.computeIfAbsent(definition, k -> new JsonObject()); }
    }
    public static void read(JsonObject json, AtlasDefinition definition) {
        if (json.has(FIELD)) {
            JsonObject data = json.getAsJsonObject(FIELD).deepCopy();
            bounded(data, 262144);
            DATA.put(definition, data);
        }
    }
    public static void write(AtlasDefinition definition, JsonObject json) {
        JsonObject data = DATA.get(definition);
        if (data != null && !data.entrySet().isEmpty()) json.add(FIELD, data.deepCopy());
    }
    public static void copy(AtlasDefinition source, AtlasDefinition target) {
        JsonObject data = DATA.get(source);
        if (data != null) DATA.put(target, data.deepCopy());
    }
    public static JsonObject node(AtlasDefinition definition, String id) {
        JsonObject root = get(definition);
        if (!root.has(id)) root.add(id, new JsonObject());
        return root.getAsJsonObject(id);
    }
    public static JsonObject rank(AtlasDefinition definition, String id, int level) {
        JsonObject node = node(definition, id);
        if (!node.has("levels")) node.add("levels", new JsonObject());
        JsonObject levels = node.getAsJsonObject("levels");
        String key = Integer.toString(level);
        if (!levels.has(key)) levels.add(key, new JsonObject());
        return levels.getAsJsonObject(key);
    }
    public static void append(JsonObject scope, String kind, String type, Map<?, ?> input) {
        JsonObject value = new JsonObject(); value.addProperty("type", typeId(type)); value.add("data", Values.plain(input).getAsJsonObject());
        bounded(value.get("data"), 16384);
        if (!scope.has(kind)) scope.add(kind, new JsonArray());
        JsonArray entries = scope.getAsJsonArray(kind);
        if (entries.size() >= 32) throw new IllegalArgumentException("Too many script entries in one scope");
        entries.add(value);
    }
    public static List<JsonObject> entries(AtlasDefinition definition, String id, int level, String kind, boolean includeNode) {
        JsonObject root = DATA.get(definition);
        if (root == null || !root.has(id)) return List.of();
        JsonObject node = root.getAsJsonObject(id); List<JsonObject> result = new ArrayList<>();
        if (includeNode) collect(node, kind, result);
        if (node.has("levels") && node.getAsJsonObject("levels").has(Integer.toString(level))) collect(node.getAsJsonObject("levels").getAsJsonObject(Integer.toString(level)), kind, result);
        return List.copyOf(result);
    }
    private static void collect(JsonObject scope, String kind, List<JsonObject> target) {
        if (scope.has(kind)) scope.getAsJsonArray(kind).forEach(e -> target.add(e.getAsJsonObject().deepCopy()));
    }
    public static boolean has(AtlasDefinition definition, String id) {
        JsonObject data = DATA.get(definition); return data != null && data.has(id) && !data.getAsJsonObject(id).entrySet().isEmpty();
    }
    public static boolean dynamic(AtlasDefinition definition) { JsonObject data = DATA.get(definition); return data != null && data.has("$dynamic_cost") && data.get("$dynamic_cost").getAsBoolean(); }
    public static void validate(AtlasDefinition definition, TypeRegistry registry) {
        JsonObject root = get(definition); bounded(root, 262144);
        Acquisitions.validate(definition, registry);
        for (var entry : root.entrySet()) {
            if (entry.getKey().equals(Acquisitions.FIELD)) continue;
            if (entry.getKey().equals("$dynamic_cost")) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid dynamic cost marker");
                continue;
            }
            SkillNode node = definition.nodes.get(entry.getKey());
            if (node == null) throw new IllegalArgumentException("Script data references unknown node " + entry.getKey());
            JsonObject value = entry.getValue().getAsJsonObject();
            scope(value, false, registry);
            for (String field : value.keySet()) if (!Set.of("conditions", "levels").contains(field)) throw new IllegalArgumentException("Unknown node script field " + field);
            if (value.has("levels")) for (var rank : value.getAsJsonObject("levels").entrySet()) {
                int level = Integer.parseInt(rank.getKey());
                if (!rank.getKey().equals(Integer.toString(level)) || level < 1 || level > node.maxLevel()) throw new IllegalArgumentException("Script data references unreachable rank");
                scope(rank.getValue().getAsJsonObject(), true, registry);
            }
        }
        for (String id : new ArrayList<>(root.keySet())) {
            if (id.equals("$dynamic_cost") || id.equals(Acquisitions.FIELD)) continue;
            JsonObject node = root.getAsJsonObject(id);
            if (node.has("conditions") && node.getAsJsonArray("conditions").isEmpty()) node.remove("conditions");
            if (node.has("levels")) {
                JsonObject levels = node.getAsJsonObject("levels");
                for (String level : new ArrayList<>(levels.keySet())) {
                    JsonObject rank = levels.getAsJsonObject(level);
                    for (String key : new ArrayList<>(rank.keySet())) if (rank.getAsJsonArray(key).isEmpty()) rank.remove(key);
                    if (rank.entrySet().isEmpty()) levels.remove(level);
                }
                if (levels.entrySet().isEmpty()) node.remove("levels");
            }
            if (node.entrySet().isEmpty()) root.remove(id);
        }
        bounded(root, 262144);
    }
    private static void scope(JsonObject value, boolean rank, TypeRegistry registry) {
        for (String field : value.keySet()) {
            if (field.equals("levels") && !rank) continue;
            if (!field.equals("conditions") && !(rank && field.equals("rewards"))) throw new IllegalArgumentException("Unknown script field " + field);
            JsonArray entries = value.getAsJsonArray(field);
            if (entries.size() > 32) throw new IllegalArgumentException("Too many script entries in one scope");
            for (JsonElement raw : entries) {
                JsonObject e = raw.getAsJsonObject();
                if (!e.keySet().equals(Set.of("type", "data"))) throw new IllegalArgumentException("Script entries require only type and data");
                String type = typeId(e.get("type").getAsString());
                JsonObject data = e.getAsJsonObject("data"); bounded(data, 16384); Values.plain(data);
                if (registry != null) registry.validate(field.equals("conditions"), type, data);
            }
        }
    }
    private static void bounded(JsonElement value, int max) {
        if (value.toString().getBytes(StandardCharsets.UTF_8).length > max) throw new IllegalArgumentException("Script definition data is too large");
    }
}
