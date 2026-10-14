package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AtlasDefinition {
    public static final int FORMAT = 1;
    public static final int MAX_NODES = 10_000;
    public static final int MAX_CATEGORIES = 64;
    public static final int MAX_POOLS = 64;

    public final Map<String, PointPool> pools = new LinkedHashMap<>();
    public final Map<String, Category> categories = new LinkedHashMap<>();
    public final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public AtlasDefinition copy() {
        AtlasDefinition d = new AtlasDefinition();
        pools.forEach((k, v) -> d.pools.put(k, v.copy()));
        categories.forEach((k, v) -> d.categories.put(k, v.copy()));
        nodes.forEach((k, v) -> d.nodes.put(k, v.copy()));
        return d;
    }

    public String poolOf(SkillNode node) {
        Category c = categories.get(node.category);
        if (c == null) {
            return "";
        }
        return c.pool;
    }

    public List<SkillNode> nodesIn(String category) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode n : nodes.values()) {
            if (n.category.equals(category)) {
                out.add(n);
            }
        }
        return out;
    }

    public boolean dependsOn(String nodeId, String ancestorId) {
        Set<String> seen = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(nodeId);
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (id.equals(ancestorId)) {
                return true;
            }
            if (!seen.add(id)) {
                continue;
            }
            SkillNode n = nodes.get(id);
            if (n != null) {
                for (Prerequisite p : n.prerequisites) {
                    stack.push(p.node);
                }
            }
        }
        return false;
    }

    public JsonArray poolsJson() {
        JsonArray a = new JsonArray();
        for (PointPool p : pools.values()) {
            a.add(p.toJson());
        }
        return a;
    }

    public JsonArray categoriesJson() {
        JsonArray a = new JsonArray();
        for (Category c : categories.values()) {
            a.add(c.toJson());
        }
        return a;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.add("pools", poolsJson());
        o.add("categories", categoriesJson());
        JsonArray n = new JsonArray();
        for (SkillNode node : nodes.values()) {
            n.add(node.toJson());
        }
        o.add("nodes", n);
        return o;
    }

    public void readMeta(JsonObject o) {
        pools.clear();
        for (PointPool p : Json.list(o, "pools", MAX_POOLS, PointPool::fromJson)) {
            if (pools.put(p.id, p) != null) {
                throw new AtlasException("skill_atlas.error.duplicate", p.id);
            }
        }
        categories.clear();
        for (Category c : Json.list(o, "categories", MAX_CATEGORIES, Category::fromJson)) {
            if (categories.put(c.id, c) != null) {
                throw new AtlasException("skill_atlas.error.duplicate", c.id);
            }
        }
    }

    public static AtlasDefinition fromJson(JsonObject o) {
        int format = Json.integer(o, "format", FORMAT, 1, 1_000);
        if (format > FORMAT) {
            throw new AtlasException("skill_atlas.error.format", String.valueOf(format));
        }
        AtlasDefinition d = new AtlasDefinition();
        d.readMeta(o);
        for (SkillNode n : Json.list(o, "nodes", MAX_NODES, SkillNode::fromJson)) {
            if (d.nodes.put(n.id, n) != null) {
                throw new AtlasException("skill_atlas.error.duplicate", n.id);
            }
        }
        d.validate();
        return d;
    }

    public void validate() {
        if (pools.size() > MAX_POOLS) {
            throw new AtlasException("skill_atlas.error.too_many", "pools", String.valueOf(MAX_POOLS));
        }
        if (categories.size() > MAX_CATEGORIES) {
            throw new AtlasException("skill_atlas.error.too_many", "categories", String.valueOf(MAX_CATEGORIES));
        }
        if (nodes.size() > MAX_NODES) {
            throw new AtlasException("skill_atlas.error.too_many", "nodes", String.valueOf(MAX_NODES));
        }
        for (Category c : categories.values()) {
            if (!pools.containsKey(c.pool)) {
                throw new AtlasException("skill_atlas.error.missing_pool", c.pool);
            }
        }
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (SkillNode n : nodes.values()) {
            if (!categories.containsKey(n.category)) {
                throw new AtlasException("skill_atlas.error.missing_category", n.id, n.category);
            }
            if (n.levels.isEmpty() || n.levels.size() > SkillNode.MAX_LEVELS) {
                throw new AtlasException("skill_atlas.error.no_levels", n.id);
            }
            Set<String> seen = new HashSet<>();
            for (Prerequisite p : n.prerequisites) {
                if (p.node.equals(n.id)) {
                    throw new AtlasException("skill_atlas.error.self_prerequisite", n.id);
                }
                if (!nodes.containsKey(p.node)) {
                    throw new AtlasException("skill_atlas.error.missing_prerequisite", n.id, p.node);
                }
                if (!seen.add(p.node)) {
                    throw new AtlasException("skill_atlas.error.duplicate_prerequisite", n.id, p.node);
                }
                children.computeIfAbsent(p.node, k -> new ArrayList<>()).add(n.id);
            }
            indegree.put(n.id, n.prerequisites.size());
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });
        int processed = 0;
        while (!queue.isEmpty()) {
            String id = queue.poll();
            processed++;
            for (String child : children.getOrDefault(id, Collections.emptyList())) {
                int left = indegree.merge(child, -1, Integer::sum);
                if (left == 0) {
                    queue.add(child);
                }
            }
        }
        if (processed < nodes.size()) {
            for (Map.Entry<String, Integer> e : indegree.entrySet()) {
                if (e.getValue() > 0) {
                    throw new AtlasException("skill_atlas.error.cycle", e.getKey());
                }
            }
        }
    }

    public static JsonElement nodeOrNull(AtlasDefinition d, String id) {
        SkillNode n = d.nodes.get(id);
        if (n == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        return n.toJson();
    }
}
